package com.aston.userservice.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {
    @Mock OutboxRepository repository;
    @Mock KafkaTemplate<String, String> kafka;
    OutboxPublisher publisher;
    final OutboxEvent event = new OutboxEvent(UserEvent.Operation.CREATED, "a@example.test");
    @BeforeEach void setup() { publisher = new OutboxPublisher(repository, kafka, new ObjectMapper(), "user-events"); }
    @Test void acknowledgedEventIsRemoved() throws Exception {
        when(repository.lockOldest()).thenReturn(Optional.of(event));
        when(kafka.send(eq("user-events"), eq("a@example.test"), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));
        assertTrue(publisher.publishNext());
        var json = ArgumentCaptor.forClass(String.class);
        var order = inOrder(kafka, repository);
        order.verify(kafka).send(eq("user-events"), eq("a@example.test"), json.capture());
        order.verify(repository).delete(event);
        var parsed = new ObjectMapper().readTree(json.getValue());
        assertEquals("CREATED", parsed.get("operation").asText());
        assertEquals("a@example.test", parsed.get("email").asText());
    }
    @Test void failedPublishKeepsEventForRetry() {
        when(repository.lockOldest()).thenReturn(Optional.of(event));
        when(kafka.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker offline")));
        assertThrows(IllegalStateException.class, publisher::publishNext);
        verify(repository, never()).delete(any());
    }
    @Test void emptyOutboxDoesNotSend() {
        when(repository.lockOldest()).thenReturn(Optional.empty());
        assertFalse(publisher.publishNext());
        verifyNoInteractions(kafka);
    }
}
