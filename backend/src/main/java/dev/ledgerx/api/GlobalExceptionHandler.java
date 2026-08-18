package dev.ledgerx.api;

import dev.ledgerx.auth.EmailAlreadyExistsException;
import dev.ledgerx.auth.InvalidCredentialsException;
import dev.ledgerx.auth.InvalidRefreshTokenException;
import dev.ledgerx.fraud.FraudFlagAlreadyReviewedException;
import dev.ledgerx.fraud.FraudFlagNotFoundException;
import dev.ledgerx.ledger.AccountNotFoundException;
import dev.ledgerx.ledger.InsufficientFundsException;
import dev.ledgerx.ledger.LedgerException;
import dev.ledgerx.transfer.IdempotencyConflictException;
import dev.ledgerx.transfer.RateLimitExceededException;
import dev.ledgerx.transfer.RequestInProgressException;
import dev.ledgerx.transfer.TransferNotFoundException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private final Clock clock;

    public GlobalExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    ResponseEntity<ErrorResponse> handleEmailAlreadyExists(EmailAlreadyExistsException e) {
        return build(HttpStatus.CONFLICT, "EMAIL_ALREADY_EXISTS", e.getMessage());
    }

    /**
     * The message comes from the exception unchanged. It is identical for an
     * unknown email and a wrong password, which is the whole point: the response
     * must not tell a caller which addresses are registered.
     */
    @ExceptionHandler(InvalidCredentialsException.class)
    ResponseEntity<ErrorResponse> handleInvalidCredentials(InvalidCredentialsException e) {
        return build(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", e.getMessage());
    }

    /**
     * Covers reuse as well, since RefreshTokenReuseException is a subtype. A
     * client learns its token was rejected but not that theft was suspected;
     * the family revocation has already happened server side.
     */
    @ExceptionHandler(InvalidRefreshTokenException.class)
    ResponseEntity<ErrorResponse> handleInvalidRefreshToken(InvalidRefreshTokenException e) {
        return build(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", e.getMessage());
    }

    /**
     * Not found rather than forbidden, and the same answer whether the account
     * is missing or simply someone else's, so account ids cannot be probed.
     */
    @ExceptionHandler(AccountNotFoundException.class)
    ResponseEntity<ErrorResponse> handleAccountNotFound(AccountNotFoundException e) {
        return build(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND", "No such account");
    }

    /**
     * 422 rather than 400: the request was well formed and understood, it just
     * cannot be satisfied against current state.
     */
    @ExceptionHandler(InsufficientFundsException.class)
    ResponseEntity<ErrorResponse> handleInsufficientFunds(InsufficientFundsException e) {
        return build(HttpStatus.UNPROCESSABLE_CONTENT, "INSUFFICIENT_FUNDS", e.getMessage());
    }

    @ExceptionHandler(LedgerException.class)
    ResponseEntity<ErrorResponse> handleLedgerRule(LedgerException e) {
        return build(HttpStatus.BAD_REQUEST, "LEDGER_RULE_VIOLATION", e.getMessage());
    }

    /**
     * A concurrent write beat this one. Retrying is the caller's decision at
     * this layer; the transfer path retries internally before it ever gets here.
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<ErrorResponse> handleOptimisticLock(OptimisticLockingFailureException e) {
        return build(HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION",
                "The account changed while this request was in flight; retry");
    }

    @ExceptionHandler(TransferNotFoundException.class)
    ResponseEntity<ErrorResponse> handleTransferNotFound(TransferNotFoundException e) {
        return build(HttpStatus.NOT_FOUND, "TRANSFER_NOT_FOUND", e.getMessage());
    }

    /**
     * 422 rather than 409: the key is not merely contended, it has been bound
     * to a different request and can never be satisfied as sent.
     */
    @ExceptionHandler(IdempotencyConflictException.class)
    ResponseEntity<ErrorResponse> handleIdempotencyConflict(IdempotencyConflictException e) {
        return build(HttpStatus.UNPROCESSABLE_CONTENT, "IDEMPOTENCY_KEY_REUSED", e.getMessage());
    }

    /** 409 with a retry invited: the same request is simply still running. */
    @ExceptionHandler(RequestInProgressException.class)
    ResponseEntity<ErrorResponse> handleRequestInProgress(RequestInProgressException e) {
        return build(HttpStatus.CONFLICT, "REQUEST_IN_PROGRESS", e.getMessage());
    }

    @ExceptionHandler(RateLimitExceededException.class)
    ResponseEntity<ErrorResponse> handleRateLimit(RateLimitExceededException e) {
        return build(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMIT_EXCEEDED", e.getMessage());
    }

    @ExceptionHandler(FraudFlagNotFoundException.class)
    ResponseEntity<ErrorResponse> handleFlagNotFound(FraudFlagNotFoundException e) {
        return build(HttpStatus.NOT_FOUND, "FRAUD_FLAG_NOT_FOUND", e.getMessage());
    }

    @ExceptionHandler(FraudFlagAlreadyReviewedException.class)
    ResponseEntity<ErrorResponse> handleFlagAlreadyReviewed(FraudFlagAlreadyReviewedException e) {
        return build(HttpStatus.CONFLICT, "FRAUD_FLAG_ALREADY_REVIEWED", e.getMessage());
    }

    /**
     * A body that could not be parsed at all. The exception's own message can
     * carry parser internals and fragments of the payload, so a fixed string is
     * returned instead of {@code e.getMessage()}.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException e) {
        return build(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST_BODY",
                "The request body could not be parsed as JSON");
    }

    /** A path or query value of the wrong type, such as a malformed UUID. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        ErrorResponse body = new ErrorResponse(
                clock.instant(),
                HttpStatus.BAD_REQUEST.value(),
                "INVALID_PARAMETER",
                "One or more parameters are the wrong type",
                Map.of(e.getName(), "is not a valid value"));
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fieldError : e.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }

        ErrorResponse body = new ErrorResponse(
                clock.instant(),
                HttpStatus.BAD_REQUEST.value(),
                "VALIDATION_FAILED",
                "One or more fields are invalid",
                fieldErrors);

        return ResponseEntity.badRequest().body(body);
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status)
                .body(ErrorResponse.of(clock.instant(), status.value(), code, message));
    }
}
