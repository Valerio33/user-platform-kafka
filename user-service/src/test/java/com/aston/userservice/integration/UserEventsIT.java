package com.aston.userservice.integration;

import com.aston.userservice.dto.UserRequest;
import com.aston.userservice.event.*;
import com.aston.userservice.repository.UserRepository;
import com.aston.userservice.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.*;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import java.time.Duration;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {"app.outbox.enabled=false", "app.kafka.topic=user-events-it"})
@EmbeddedKafka(partitions = 1, topics = "user-events-it", bootstrapServersProperty = "spring.kafka.bootstrap-servers")
@Testcontainers
@DirtiesContext
class UserEventsIT {
    @Container static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
        r.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }
    @Autowired UserService users;
    @Autowired UserRepository repository;
    @Autowired OutboxRepository outbox;
    @Autowired OutboxPublisher publisher;
    @Autowired EmbeddedKafkaBroker broker;
    @Autowired ObjectMapper mapper;
    @Autowired PlatformTransactionManager transactions;
    @BeforeEach void clean() { outbox.deleteAllInBatch(); repository.deleteAllInBatch(); }

    @Test void committedCreateAndDeleteReachKafka() throws Exception {
        var props = KafkaTestUtils.consumerProps(UUID.randomUUID().toString(), "false", broker);
        try (var consumer = new KafkaConsumer<String, String>(props, new StringDeserializer(), new StringDeserializer())) {
            broker.consumeFromAnEmbeddedTopic(consumer, true, "user-events-it");
            var user = users.create(new UserRequest("Иван", "a@example.test", 25));
            assertEquals(1, outbox.count());
            assertTrue(publisher.publishNext());
            var created = KafkaTestUtils.getSingleRecord(consumer, "user-events-it", Duration.ofSeconds(15));
            assertEquals("a@example.test", created.key());
            assertEquals("CREATED", mapper.readTree(created.value()).get("operation").asText());
            users.delete(user.id());
            assertTrue(publisher.publishNext());
            var deleted = KafkaTestUtils.getSingleRecord(consumer, "user-events-it", Duration.ofSeconds(15));
            assertEquals("DELETED", mapper.readTree(deleted.value()).get("operation").asText());
            assertEquals("a@example.test", mapper.readTree(deleted.value()).get("email").asText());
            assertEquals(0, outbox.count());
            assertEquals(0, repository.count());
        }
    }
    @Test void rollbackRemovesUserAndEventTogether() {
        var tx = new TransactionTemplate(transactions);
        assertThrows(IllegalStateException.class, () -> tx.executeWithoutResult(status -> {
            users.create(new UserRequest("Иван", "a@example.test", 25));
            throw new IllegalStateException("force rollback");
        }));
        assertEquals(0, repository.count());
        assertEquals(0, outbox.count());
    }
    @Test void rollbackRestoresDeletedUserAndRemovesDeletionEvent() {
        var user = users.create(new UserRequest("Иван", "a@example.test", 25));
        outbox.deleteAllInBatch();
        var tx = new TransactionTemplate(transactions);
        assertThrows(IllegalStateException.class, () -> tx.executeWithoutResult(status -> {
            users.delete(user.id());
            throw new IllegalStateException("force rollback");
        }));
        assertTrue(repository.existsById(user.id()));
        assertEquals(0, outbox.count());
    }
}
