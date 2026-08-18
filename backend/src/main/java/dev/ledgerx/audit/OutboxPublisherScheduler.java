package dev.ledgerx.audit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * A trigger with no logic of its own, so a test can drive publication directly
 * and prove that stopping this scheduler delays delivery without losing it.
 */
@Component
@ConditionalOnProperty(name = "ledgerx.outbox.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPublisherScheduler {

    private final OutboxPublisher publisher;

    public OutboxPublisherScheduler(OutboxPublisher publisher) {
        this.publisher = publisher;
    }

    @Scheduled(fixedDelayString = "${ledgerx.outbox.poll-interval:500}")
    void drain() {
        publisher.publishPending();
    }
}
