package dev.ledgerx.transfer;

import dev.ledgerx.audit.OutboxRecorder;
import dev.ledgerx.ledger.Account;
import dev.ledgerx.ledger.AccountType;
import dev.ledgerx.ledger.LedgerService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * The single ACID unit of a transfer, kept in its own bean so the retry loop in
 * {@link TransferService} sits outside the transaction. Retrying inside would
 * reuse a transaction already marked rollback-only and fail on every attempt.
 * <p>
 * REQUIRES_NEW guarantees a genuinely fresh transaction per attempt even if a
 * caller happens to be inside one, so each retry re-reads the current version
 * rather than replaying against the stale state that lost the previous race.
 */
@Component
public class TransferWriter {

    private final LedgerService ledgerService;
    private final TransferRepository transferRepository;
    private final OutboxRecorder outboxRecorder;

    public TransferWriter(LedgerService ledgerService,
                          TransferRepository transferRepository,
                          OutboxRecorder outboxRecorder) {
        this.ledgerService = ledgerService;
        this.transferRepository = transferRepository;
        this.outboxRecorder = outboxRecorder;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Transfer write(UUID sourceAccountId, UUID destinationAccountId, long amount) {
        Account source = ledgerService.load(sourceAccountId);
        Account destination = ledgerService.load(destinationAccountId);

        // Read the balance inside this transaction, immediately before the write
        // that depends on it. The version column is what makes that read binding:
        // if another transfer moves this balance first, the flush below fails
        // rather than silently overdrawing.
        //
        // The treasury is exempt. It is the account money is issued from, so its
        // balance is the negative of everything the platform owes and is
        // supposed to run below zero.
        if (source.getAccountType() != AccountType.TREASURY) {
            ledgerService.requireSufficientFunds(source, amount);
        }

        Transfer transfer = transferRepository.saveAndFlush(
                new Transfer(source, destination, amount, source.getCurrency()));

        // Flushed above because ledger_entries.transfer_id is a foreign key: the
        // transfer row has to exist before its entries can reference it.
        ledgerService.postBalancedPair(
                transfer.getId(), sourceAccountId, destinationAccountId, amount, source.getCurrency());

        // Same transaction as the money. Publishing to Kafka from here instead
        // would be a dual write: the broker call can succeed and this
        // transaction still roll back, leaving an event for a transfer that
        // never happened. One commit covers both, and a poller publishes after.
        outboxRecorder.record("TRANSFER", transfer.getId(), "TRANSFER_CREATED",
                TransferEvent.from(transfer));

        return transfer;
    }
}
