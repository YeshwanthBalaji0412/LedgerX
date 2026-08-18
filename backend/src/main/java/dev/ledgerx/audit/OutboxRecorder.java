package dev.ledgerx.audit;

import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

/**
 * Records an event for later publication. Deliberately has no transaction
 * annotation of its own: it must join whatever transaction its caller is in, so
 * the event and the business data it describes commit together or not at all.
 * Opening a transaction here would recreate the dual write the outbox exists to
 * remove.
 */
@Component
public class OutboxRecorder {

    private final OutboxEventRepository outbox;
    private final ObjectMapper objectMapper;

    public OutboxRecorder(OutboxEventRepository outbox, ObjectMapper objectMapper) {
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    public OutboxEvent record(String aggregateType, UUID aggregateId, String eventType, Object payload) {
        return outbox.save(new OutboxEvent(
                aggregateType, aggregateId, eventType, objectMapper.writeValueAsString(payload)));
    }
}
