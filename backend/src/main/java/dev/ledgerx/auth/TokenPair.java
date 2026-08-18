package dev.ledgerx.auth;

/**
 * The raw credentials handed back to a caller. The refresh token is the only
 * time the raw value exists outside the client: only its hash is persisted.
 */
public record TokenPair(String accessToken, String refreshToken) {
}
