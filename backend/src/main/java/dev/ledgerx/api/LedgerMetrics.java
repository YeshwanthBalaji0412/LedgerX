package dev.ledgerx.api;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * The counters worth having for a payment system, in one place so the metric
 * names stay consistent and nobody has to guess whether it is
 * {@code ledgerx.transfers} or {@code ledgerx.transfer}.
 * <p>
 * These are deliberately about things that are invisible from the outside:
 * a retry that succeeded, a replay that cost nothing, a rejection that never
 * became an error. Request counts and latencies already come free from the
 * web layer, so duplicating them here would add noise rather than signal.
 */
@Component
public class LedgerMetrics {

    private final MeterRegistry registry;
    private final Counter optimisticRetries;
    private final Counter idempotencyReplays;
    private final Counter rateLimitRejections;

    public LedgerMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.optimisticRetries = Counter.builder("ledgerx.transfer.optimistic.retries")
                .description("Transfer writes replayed after losing a version race")
                .register(registry);
        this.idempotencyReplays = Counter.builder("ledgerx.idempotency.replays")
                .description("Requests answered from a stored response instead of executing")
                .register(registry);
        this.rateLimitRejections = Counter.builder("ledgerx.transfer.ratelimit.rejections")
                .description("Transfers refused because the caller's window was full")
                .register(registry);

        // Tagged counters are created lazily on first increment, so a metric
        // that has never fired simply does not exist — which is exactly when you
        // want to see a zero. Registering every known tag value up front means a
        // dashboard or alert can reference them before anything has happened.
        for (dev.ledgerx.transfer.TransferStatus status : dev.ledgerx.transfer.TransferStatus.values()) {
            registry.counter("ledgerx.transfers", "status", status.name());
        }
        for (dev.ledgerx.fraud.FraudRule rule : dev.ledgerx.fraud.FraudRule.values()) {
            registry.counter("ledgerx.fraud.flags", "rule", rule.name());
        }
    }

    /** Tagged by status so a rise in FAILED or FLAGGED is visible on its own. */
    public void transferRecorded(String status) {
        registry.counter("ledgerx.transfers", "status", status).increment();
    }

    public void optimisticRetry() {
        optimisticRetries.increment();
    }

    public void idempotencyReplay() {
        idempotencyReplays.increment();
    }

    public void rateLimitRejected() {
        rateLimitRejections.increment();
    }

    /** Tagged by rule, because the two rules mean different things operationally. */
    public void fraudFlagRaised(String rule) {
        registry.counter("ledgerx.fraud.flags", "rule", rule).increment();
    }
}
