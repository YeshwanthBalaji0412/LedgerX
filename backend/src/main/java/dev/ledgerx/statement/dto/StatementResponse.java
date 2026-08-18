package dev.ledgerx.statement.dto;

import dev.ledgerx.statement.Statement;

import java.time.Instant;
import java.util.UUID;

/**
 * {@code lineItems} is passed through as the stored JSON rather than reparsed
 * and re-serialised, so what a reader sees is exactly what was recorded.
 */
public record StatementResponse(
        UUID id,
        UUID accountId,
        String period,
        long openingBalanceMinorUnits,
        long closingBalanceMinorUnits,
        long netMovementMinorUnits,
        int entryCount,
        String lineItems,
        Instant generatedAt
) {

    public static StatementResponse from(Statement statement) {
        return new StatementResponse(
                statement.getId(),
                statement.getAccount().getId(),
                statement.getPeriod(),
                statement.getOpeningBalance(),
                statement.getClosingBalance(),
                statement.netMovement(),
                statement.getEntryCount(),
                statement.getLineItems(),
                statement.getGeneratedAt());
    }
}
