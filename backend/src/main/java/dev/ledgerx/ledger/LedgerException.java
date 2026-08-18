package dev.ledgerx.ledger;

/** Base for ledger rule violations that a caller can be told about safely. */
public class LedgerException extends RuntimeException {

    public LedgerException(String message) {
        super(message);
    }
}
