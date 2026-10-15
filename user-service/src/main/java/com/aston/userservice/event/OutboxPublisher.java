package com.aston.userservice.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.concurrent.TimeUnit;

@Service
public class OutboxPublisher {
    private final OutboxRepository repository;
    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper mapper;
    private final String topic;

    public OutboxPublisher(OutboxRepository repository, KafkaTemplate<String, String> kafka,
                           ObjectMapper mapper, @Value("${app.kafka.topic}") String topic) {
        this.repository = repository;
        this.kafka = kafka;
        this.mapper = mapper;
        this.topic = topic;
    }
    @Transactional
    public boolean publishNext() {
        var pending = repository.lockOldest();
        if (pending.isEmpty()) return false;
        var row = pending.get();
        try {
            // Broker acknowledgement is required before removing the pending event.
            kafka.send(topic, row.toEvent().email(), mapper.writeValueAsString(row.toEvent()))
                    .get(15, TimeUnit.SECONDS);
            repository.delete(row);
            return true;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Публикация события прервана", ex);
        } catch (Exception ex) {
            throw new IllegalStateException("Kafka не подтвердила событие " + row.getId(), ex);
        }
    }
}
