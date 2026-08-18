package dev.ledgerx.ledger;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    List<LedgerEntry> findAllByAccountIdOrderByCreatedAtAsc(UUID accountId);

    List<LedgerEntry> findAllByTransferId(UUID transferId);

    long countByTransferId(UUID transferId);

    /**
     * The authoritative balance: re-derived from primary records every time it
     * is asked for, with no dependence on any cached column.
     */
    @Query("""
            select coalesce(sum(case when e.direction = dev.ledgerx.ledger.Direction.CREDIT
                                     then e.amount else -e.amount end), 0)
            from LedgerEntry e
            where e.account.id = :accountId
            """)
    long deriveBalance(@Param("accountId") UUID accountId);

    /**
     * The balance as of an instant, derived from entries alone.
     * <p>
     * This single query is what makes statement continuity automatic rather than
     * arithmetic that has to be kept in step: a period's opening balance is the
     * balance before its start, and its closing balance is the balance before
     * the next period's start. Those are the same instant, so closing of one
     * period and opening of the next are the same number by construction, not
     * because two calculations happen to agree.
     */
    @Query("""
            select coalesce(sum(case when e.direction = dev.ledgerx.ledger.Direction.CREDIT
                                     then e.amount else -e.amount end), 0)
            from LedgerEntry e
            where e.account.id = :accountId
              and e.createdAt < :before
            """)
    long deriveBalanceBefore(@Param("accountId") UUID accountId, @Param("before") Instant before);

    @Query("""
            select e from LedgerEntry e
            where e.account.id = :accountId
              and e.createdAt >= :from
              and e.createdAt < :to
            order by e.createdAt asc, e.id asc
            """)
    List<LedgerEntry> findForPeriod(@Param("accountId") UUID accountId,
                                    @Param("from") Instant from,
                                    @Param("to") Instant to);

    /**
     * Every account whose cached figure disagrees with its entries, computed in
     * one pass rather than by looping. Accounts with no entries are included via
     * the outer join, so a corrupted balance on an empty account is still caught.
     */
    @Query(value = """
            SELECT a.id AS account_id,
                   a.cached_balance AS cached_balance,
                   COALESCE(SUM(CASE WHEN e.direction = 'CREDIT' THEN e.amount ELSE -e.amount END), 0)
                       AS derived_balance
            FROM accounts a
            LEFT JOIN ledger_entries e ON e.account_id = a.id
            GROUP BY a.id, a.cached_balance
            HAVING a.cached_balance <>
                   COALESCE(SUM(CASE WHEN e.direction = 'CREDIT' THEN e.amount ELSE -e.amount END), 0)
            """, nativeQuery = true)
    List<BalanceMismatchView> findBalanceMismatches();

    /**
     * The double-entry invariant itself: within one transfer, debits and credits
     * must sum to the same number. Anything returned here is a torn write.
     */
    @Query(value = """
            SELECT transfer_id AS transfer_id,
                   SUM(CASE WHEN direction = 'DEBIT' THEN amount ELSE 0 END) AS debit_total,
                   SUM(CASE WHEN direction = 'CREDIT' THEN amount ELSE 0 END) AS credit_total
            FROM ledger_entries
            GROUP BY transfer_id
            HAVING SUM(CASE WHEN direction = 'DEBIT' THEN amount ELSE 0 END)
                <> SUM(CASE WHEN direction = 'CREDIT' THEN amount ELSE 0 END)
            """, nativeQuery = true)
    List<UnbalancedTransferView> findUnbalancedTransfers();

    /**
     * Across the whole platform every credit is matched by a debit, so the sum
     * of every signed entry must be exactly zero. A single number that proves
     * the books balance.
     */
    @Query(value = """
            SELECT COALESCE(SUM(CASE WHEN direction = 'CREDIT' THEN amount ELSE -amount END), 0)
            FROM ledger_entries
            """, nativeQuery = true)
    long sumOfAllSignedEntries();
}
