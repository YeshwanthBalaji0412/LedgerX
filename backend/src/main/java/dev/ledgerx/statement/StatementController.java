package dev.ledgerx.statement;

import dev.ledgerx.ledger.AccountService;
import dev.ledgerx.statement.dto.StatementResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/accounts/{accountId}/statements")
public class StatementController {

    private final StatementService statementService;
    private final AccountService accountService;

    public StatementController(StatementService statementService, AccountService accountService) {
        this.statementService = statementService;
        this.accountService = accountService;
    }

    @GetMapping
    List<StatementResponse> list(@AuthenticationPrincipal UUID userId, @PathVariable UUID accountId) {
        accountService.requireOwnedBy(accountId, userId);
        return statementService.listFor(accountId);
    }

    @GetMapping("/{period}")
    StatementResponse one(@AuthenticationPrincipal UUID userId,
                          @PathVariable UUID accountId,
                          @PathVariable String period) {
        accountService.requireOwnedBy(accountId, userId);
        return statementService.require(accountId, parse(period));
    }

    /**
     * Returns 200 rather than 201 when the statement already existed, so a
     * caller can tell whether this request generated it. The figures are
     * identical either way: regeneration reads, it does not recompute.
     */
    @PostMapping("/{period}")
    ResponseEntity<StatementResponse> generate(@AuthenticationPrincipal UUID userId,
                                               @PathVariable UUID accountId,
                                               @PathVariable String period) {
        accountService.requireOwnedBy(accountId, userId);
        YearMonth parsed = parse(period);

        boolean existed = statementService.exists(accountId, parsed);
        StatementResponse statement = statementService.generate(accountId, parsed);
        return ResponseEntity.status(existed ? HttpStatus.OK : HttpStatus.CREATED).body(statement);
    }

    private static YearMonth parse(String period) {
        try {
            return YearMonth.parse(period);
        } catch (DateTimeParseException e) {
            throw new InvalidPeriodException(period);
        }
    }
}
