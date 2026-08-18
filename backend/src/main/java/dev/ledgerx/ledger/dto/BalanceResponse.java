package dev.ledgerx.ledger.dto;

import java.util.UUID;

/**
 * Exposes both figures deliberately. The derived value is authoritative; the
 * cached one is shown beside it so a drift is visible to a caller rather than
 * silently papered over.
 */
public record BalanceResponse(
        UUID accountId,
        String currency,
        long derivedBalanceMinorUnits,
        long cachedBalanceMinorUnits,
        boolean consistent
) {

    public static BalanceResponse of(UUID accountId, String currency, long derived, long cached) {
        return new BalanceResponse(accountId, currency, derived, cached, derived == cached);
    }
}
