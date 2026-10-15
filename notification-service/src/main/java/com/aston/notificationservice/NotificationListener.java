package com.aston.notificationservice;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class NotificationListener {
    private final ObjectMapper mapper;
    private final NotificationService service;
    public NotificationListener(ObjectMapper mapper, NotificationService service) {
        this.mapper = mapper;
        this.service = service;
    }
    @KafkaListener(topics = "${app.kafka.topic}", groupId = "${spring.kafka.consumer.group-id}")
    public void receive(String json) {
        try {
            service.send(mapper.readValue(json, NotificationRequest.class));
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Неверный формат события", ex);
        }
    }
}
