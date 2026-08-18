package dev.ledgerx.statement;

public class StatementNotFoundException extends RuntimeException {

    public StatementNotFoundException() {
        super("No statement for that account and period");
    }
}
