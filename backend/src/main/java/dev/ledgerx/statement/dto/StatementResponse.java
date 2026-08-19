package dev.ledgerx.statement.dto;

import dev.ledgerx.statement.Statement;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/**
 * {@code lineItems} is passed through as the stored JSON rather than reparsed
 * and re-serialised, so what a reader sees is exactly what was recorded.
 */
@Schema(description = "An immutable statement for one closed period, derived from ledger entries")
public record StatementResponse(
        UUID id,
        UUID accountId,
        @Schema(description = "The period, YYYY-MM", example = "2026-05")
        String period,
        long openingBalanceMinorUnits,
        long closingBalanceMinorUnits,
        long netMovementMinorUnits,
        int entryCount,
        @Schema(description = "JSON array of entries in the period, each with the running balance after it")
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
