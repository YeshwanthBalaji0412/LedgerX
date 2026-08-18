package dev.ledgerx.transfer;

import dev.ledgerx.ledger.Account;
import dev.ledgerx.ledger.AccountService;
import dev.ledgerx.transfer.dto.CreateTransferRequest;
import dev.ledgerx.transfer.dto.TransferResponse;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * Orchestration only. The single ACID unit lives in {@link TransferWriter}; the
 * retry loop, the rate limit and the idempotency handshake all sit outside it,
 * because each of them has to be able to observe or survive a failed attempt.
 */
@Service
public class TransferService {

    private final TransferWriter writer;
    private final TransferRepository transferRepository;
    private final IdempotencyStore idempotencyStore;
    private final AccountService accountService;
    private final TransferRateLimiter rateLimiter;
    private final TransferProperties properties;
    private final ObjectMapper objectMapper;

    public TransferService(TransferWriter writer,
                           TransferRepository transferRepository,
                           IdempotencyStore idempotencyStore,
                           AccountService accountService,
                           TransferRateLimiter rateLimiter,
                           TransferProperties properties,
                           ObjectMapper objectMapper) {
        this.writer = writer;
        this.transferRepository = transferRepository;
        this.idempotencyStore = idempotencyStore;
        this.accountService = accountService;
        this.rateLimiter = rateLimiter;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public TransferResponse createTransfer(UUID userId, CreateTransferRequest request, String idempotencyKey) {
        // Ownership first, so a caller cannot even discover whether an account
        // exists by watching which error a spent rate limit or a reused key gives.
        accountService.requireOwnedBy(request.sourceAccountId(), userId);

        return execute(
                userId,
                idempotencyKey,
                hashRequest(request.sourceAccountId(), request.destinationAccountId(), request.amountMinorUnits()),
                () -> writeWithRetry(
                        request.sourceAccountId(),
                        request.destinationAccountId(),
                        request.amountMinorUnits()));
    }

    /** Money entering the platform: a transfer whose source is the treasury. */
    public TransferResponse deposit(UUID userId, UUID accountId, long amount, String idempotencyKey) {
        accountService.requireOwnedBy(accountId, userId);
        return execute(
                userId,
                idempotencyKey,
                hashRequest(Account.TREASURY_ID, accountId, amount),
                () -> writeWithRetry(Account.TREASURY_ID, accountId, amount));
    }

    /** Money leaving the platform, the mirror of {@link #deposit}. */
    public TransferResponse withdraw(UUID userId, UUID accountId, long amount, String idempotencyKey) {
        accountService.requireOwnedBy(accountId, userId);
        return execute(
                userId,
                idempotencyKey,
                hashRequest(accountId, Account.TREASURY_ID, amount),
                () -> writeWithRetry(accountId, Account.TREASURY_ID, amount));
    }

    @Transactional(readOnly = true)
    public Page<TransferResponse> listForUser(UUID userId, int page, int size) {
        List<UUID> accountIds = accountService.accountsOf(userId).stream().map(Account::getId).toList();
        if (accountIds.isEmpty()) {
            return Page.empty();
        }
        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        return transferRepository.findForAccounts(accountIds, pageable).map(TransferResponse::from);
    }

    @Transactional(readOnly = true)
    public TransferResponse requireVisibleTo(UUID transferId, UUID userId) {
        Transfer transfer = transferRepository.findById(transferId)
                .orElseThrow(TransferNotFoundException::new);

        List<UUID> accountIds = accountService.accountsOf(userId).stream().map(Account::getId).toList();
        boolean involvesCaller = accountIds.contains(transfer.getSourceAccount().getId())
                || accountIds.contains(transfer.getDestinationAccount().getId());
        if (!involvesCaller) {
            throw new TransferNotFoundException();
        }
        return TransferResponse.from(transfer);
    }

    /**
     * The idempotency handshake. A replay short circuits before the rate limit
     * is touched: a retry of an answered request is not new work and should not
     * spend the caller's allowance.
     */
    private TransferResponse execute(UUID userId, String idempotencyKey, String requestHash,
                                     java.util.function.Supplier<Transfer> work) {

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            rateLimiter.requireSlot(userId);
            return TransferResponse.from(work.get());
        }

        IdempotencyClaim claim = idempotencyStore.claim(idempotencyKey, userId, requestHash);
        if (claim.isReplay()) {
            return objectMapper.readValue(claim.replayBody(), TransferResponse.class);
        }

        rateLimiter.requireSlot(userId);
        try {
            Transfer transfer = work.get();
            TransferResponse response = TransferResponse.from(transfer);
            idempotencyStore.complete(claim.recordId(), 201,
                    objectMapper.writeValueAsString(response), transfer.getId());
            return response;
        } catch (RuntimeException e) {
            // Release rather than store the failure, so the caller can retry the
            // same key. A claim abandoned after a failure would lock the key out
            // permanently with nothing to replay.
            idempotencyStore.release(claim.recordId());
            throw e;
        }
    }

    /**
     * Bounded retry on a lost version race. Bounded because contention is not
     * an error worth hiding forever: past a few attempts the honest answer is a
     * conflict, not a request that never returns.
     */
    private Transfer writeWithRetry(UUID sourceAccountId, UUID destinationAccountId, long amount) {
        OptimisticLockingFailureException lastFailure = null;

        for (int attempt = 0; attempt <= properties.maxOptimisticRetries(); attempt++) {
            try {
                return writer.write(sourceAccountId, destinationAccountId, amount);
            } catch (OptimisticLockingFailureException e) {
                lastFailure = e;
            }
        }
        throw lastFailure;
    }

    /**
     * Binds a key to the exact request it answered. Two different requests
     * carrying one key must be distinguishable, or a client bug turns into a
     * wrong answer served with confidence.
     */
    static String hashRequest(UUID sourceAccountId, UUID destinationAccountId, long amount) {
        String canonical = sourceAccountId + "|" + destinationAccountId + "|" + amount;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required but unavailable", e);
        }
    }
}
