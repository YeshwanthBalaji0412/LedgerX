package dev.ledgerx.transfer;

public class RateLimitExceededException extends RuntimeException {

    public RateLimitExceededException() {
        super("Too many transfers in a short period; try again shortly");
    }
}
