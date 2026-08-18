package dev.ledgerx.ledger;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Re-deriving every balance is deliberately not something an ordinary user can
 * trigger: it is a full scan, and it reports on accounts that are not theirs.
 * <p>
 * The role is enforced twice, by the URL rule in the filter chain and by
 * {@code @PreAuthorize} here. The duplication is deliberate: a future edit to a
 * path pattern should not be able to quietly expose this.
 */
@RestController
@RequestMapping("/api/admin/ledger")
@PreAuthorize("hasRole('ADMIN')")
public class LedgerAdminController {

    private final LedgerService ledgerService;

    public LedgerAdminController(LedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    @GetMapping("/integrity")
    IntegrityReport integrity() {
        return ledgerService.checkIntegrity();
    }
}
