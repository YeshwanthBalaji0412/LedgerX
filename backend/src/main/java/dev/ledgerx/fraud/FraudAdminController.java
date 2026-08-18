package dev.ledgerx.fraud;

import dev.ledgerx.fraud.dto.FraudFlagResponse;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Review is an operator function, so the role is enforced twice: by the URL rule
 * in the filter chain and again here, in case a path pattern is ever edited.
 */
@RestController
@RequestMapping("/api/admin/fraud-flags")
@PreAuthorize("hasRole('ADMIN')")
public class FraudAdminController {

    private final FraudReviewService reviewService;

    public FraudAdminController(FraudReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping
    Page<FraudFlagResponse> queue(@RequestParam(required = false) FraudFlagStatus status,
                                  @RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "20") int size) {
        return reviewService.queue(status, page, size);
    }

    @PostMapping("/{flagId}/clear")
    FraudFlagResponse clear(@AuthenticationPrincipal UUID reviewerId, @PathVariable UUID flagId) {
        return reviewService.review(flagId, FraudFlagStatus.CLEARED, reviewerId);
    }

    @PostMapping("/{flagId}/confirm")
    FraudFlagResponse confirm(@AuthenticationPrincipal UUID reviewerId, @PathVariable UUID flagId) {
        return reviewService.review(flagId, FraudFlagStatus.CONFIRMED, reviewerId);
    }
}
