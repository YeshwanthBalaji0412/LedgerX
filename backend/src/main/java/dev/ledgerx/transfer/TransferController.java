package dev.ledgerx.transfer;

import dev.ledgerx.transfer.dto.CreateTransferRequest;
import dev.ledgerx.transfer.dto.TransferResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/transfers")
public class TransferController {

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    /**
     * {@code Idempotency-Key} is optional but strongly advised: without it a
     * retried request is a second transfer. It is a header rather than a body
     * field because it describes the delivery of the request, not the money.
     */
    @PostMapping
    ResponseEntity<TransferResponse> create(
            @AuthenticationPrincipal UUID userId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateTransferRequest request) {

        TransferResponse response = transferService.createTransfer(userId, request, idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    Page<TransferResponse> list(@AuthenticationPrincipal UUID userId,
                                @RequestParam(defaultValue = "0") int page,
                                @RequestParam(defaultValue = "20") int size) {
        return transferService.listForUser(userId, page, size);
    }

    @GetMapping("/{transferId}")
    TransferResponse one(@AuthenticationPrincipal UUID userId, @PathVariable UUID transferId) {
        return transferService.requireVisibleTo(transferId, userId);
    }
}
