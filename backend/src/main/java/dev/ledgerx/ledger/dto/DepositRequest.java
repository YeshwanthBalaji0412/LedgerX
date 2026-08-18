package dev.ledgerx.ledger.dto;

import jakarta.validation.constraints.Positive;

/** Amounts are minor units, so 1000 is ten dollars. */
public record DepositRequest(
        @Positive(message = "must be a positive number of minor units")
        long amountMinorUnits
) {
}
