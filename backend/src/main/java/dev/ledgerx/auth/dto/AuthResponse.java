package dev.ledgerx.auth.dto;

import dev.ledgerx.auth.TokenPair;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresInSeconds
) {

    public static AuthResponse bearer(TokenPair tokens, long expiresInSeconds) {
        return new AuthResponse(tokens.accessToken(), tokens.refreshToken(), "Bearer", expiresInSeconds);
    }
}
