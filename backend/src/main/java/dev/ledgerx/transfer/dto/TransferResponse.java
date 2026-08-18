package dev.ledgerx.transfer.dto;

import dev.ledgerx.transfer.Transfer;

import java.time.Instant;
import java.util.UUID;

public record TransferResponse(
        UUID id,
        UUID sourceAccountId,
        UUID destinationAccountId,
        long amountMinorUnits,
        String currency,
        String status,
        String failureReason,
        Instant createdAt,
        Instant settledAt
) {

    public static TransferResponse from(Transfer transfer) {
        return new TransferResponse(
                transfer.getId(),
                transfer.getSourceAccount().getId(),
                transfer.getDestinationAccount().getId(),
                transfer.getAmount(),
                transfer.getCurrency(),
                transfer.getStatus().name(),
                transfer.getFailureReason(),
                transfer.getCreatedAt(),
                transfer.getSettledAt());
    }
}
