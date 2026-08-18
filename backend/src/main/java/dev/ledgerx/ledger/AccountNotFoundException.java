package dev.ledgerx.ledger;

import java.util.UUID;

public class AccountNotFoundException extends LedgerException {

    public AccountNotFoundException(UUID accountId) {
        super("No account with id " + accountId);
    }
}
