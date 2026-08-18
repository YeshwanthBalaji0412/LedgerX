package dev.ledgerx.api;

import java.time.Instant;
import java.util.Map;

/**
 * One shape for every error the API returns, so a client never has to branch on
 * which layer failed. {@code fieldErrors} is populated only for validation
 * failures and is an empty map otherwise rather than null.
 */
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        Map<String, String> fieldErrors
) {

    public static ErrorResponse of(Instant timestamp, int status, String error, String message) {
        return new ErrorResponse(timestamp, status, error, message, Map.of());
    }
}
