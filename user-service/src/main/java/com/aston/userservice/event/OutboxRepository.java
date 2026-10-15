package com.aston.userservice.event;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.Optional;

public interface OutboxRepository extends JpaRepository<OutboxEvent, Long> {
    // Lock the oldest event. No SKIP LOCKED: deletion must not overtake creation.
    @Query(value = "select * from notification_outbox order by id limit 1 for update", nativeQuery = true)
    Optional<OutboxEvent> lockOldest();
}
