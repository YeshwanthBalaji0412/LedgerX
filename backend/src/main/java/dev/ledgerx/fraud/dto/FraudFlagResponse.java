package dev.ledgerx.fraud.dto;

import dev.ledgerx.fraud.FraudFlag;

import java.time.Instant;
import java.util.UUID;

public record FraudFlagResponse(
        UUID id,
        UUID transferId,
        String rule,
        String status,
        String details,
        UUID reviewedBy,
        Instant reviewedAt,
        Instant createdAt
) {

    public static FraudFlagResponse from(FraudFlag flag) {
        return new FraudFlagResponse(
                flag.getId(),
                flag.getTransfer().getId(),
                flag.getRule().name(),
                flag.getStatus().name(),
                flag.getDetails(),
                flag.getReviewedBy() == null ? null : flag.getReviewedBy().getId(),
                flag.getReviewedAt(),
                flag.getCreatedAt());
    }
}
