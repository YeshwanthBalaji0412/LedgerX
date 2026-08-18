package dev.ledgerx.ledger;

import dev.ledgerx.ledger.dto.AccountResponse;
import dev.ledgerx.ledger.dto.BalanceResponse;
import dev.ledgerx.ledger.dto.DepositRequest;
import dev.ledgerx.ledger.dto.OpenAccountRequest;
import dev.ledgerx.transfer.TransferService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService accountService;
    private final LedgerService ledgerService;
    private final TransferService transferService;

    public AccountController(AccountService accountService,
                             LedgerService ledgerService,
                             TransferService transferService) {
        this.accountService = accountService;
        this.ledgerService = ledgerService;
        this.transferService = transferService;
    }

    @GetMapping
    List<AccountResponse> myAccounts(@AuthenticationPrincipal UUID userId) {
        return accountService.accountsOf(userId).stream().map(AccountResponse::from).toList();
    }

    @PostMapping
    ResponseEntity<AccountResponse> openAccount(@AuthenticationPrincipal UUID userId,
                                                @Valid @RequestBody(required = false) OpenAccountRequest request) {
        String currency = request == null ? null : request.currency();
        Account account = accountService.openAccount(userId, currency);
        return ResponseEntity.status(HttpStatus.CREATED).body(AccountResponse.from(account));
    }

    /**
     * Ownership is resolved before anything is read, so one user cannot probe
     * another's balances by guessing account ids. An account belonging to
     * someone else is reported as not found rather than forbidden, which avoids
     * confirming that the id exists at all.
     */
    @GetMapping("/{accountId}/balance")
    BalanceResponse balance(@AuthenticationPrincipal UUID userId, @PathVariable UUID accountId) {
        Account account = accountService.requireOwnedBy(accountId, userId);
        return BalanceResponse.of(
                account.getId(),
                account.getCurrency(),
                ledgerService.derivedBalance(account.getId()),
                account.getCachedBalance());
    }

    /**
     * Funding routes through the transfer domain rather than writing entries
     * directly, so a deposit gets the same idempotency, rate limiting and
     * lifecycle as any other movement of money.
     */
    @PostMapping("/{accountId}/deposits")
    ResponseEntity<BalanceResponse> deposit(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID accountId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody DepositRequest request) {

        transferService.deposit(userId, accountId, request.amountMinorUnits(), idempotencyKey);

        Account refreshed = accountService.require(accountId);
        return ResponseEntity.status(HttpStatus.CREATED).body(BalanceResponse.of(
                refreshed.getId(),
                refreshed.getCurrency(),
                ledgerService.derivedBalance(accountId),
                refreshed.getCachedBalance()));
    }
}
