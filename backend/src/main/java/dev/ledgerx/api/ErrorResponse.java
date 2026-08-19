package dev.ledgerx.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.Map;

/**
 * One shape for every error the API returns, so a client never has to branch on
 * which layer failed. {@code fieldErrors} is populated only for validation
 * failures and is an empty map otherwise rather than null.
 */
@Schema(description = "The shape every error in this API takes, whatever layer produced it")
public record ErrorResponse(
        Instant timestamp,
        @Schema(description = "HTTP status, repeated in the body so logged payloads stand alone", example = "422")
        int status,
        @Schema(description = "Stable machine-readable code; prefer this over the message",
                example = "INSUFFICIENT_FUNDS")
        String error,
        @Schema(description = "Human-readable and safe to show; never contains internals",
                example = "Insufficient available balance for this movement")
        String message,
        @Schema(description = "Populated only for validation failures; empty otherwise, never null")
        Map<String, String> fieldErrors
) {

    public static ErrorResponse of(Instant timestamp, int status, String error, String message) {
        return new ErrorResponse(timestamp, status, error, message, Map.of());
    }
}
