package dev.ledgerx.api;

import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * @param allowedOrigins exact origins the browser may call from. A list rather
 *                       than a wildcard on purpose: credentials cannot be sent
 *                       to a wildcard origin, and enumerating them keeps an
 *                       unexpected origin a deployment decision rather than a
 *                       default. Validated non-empty so a misconfiguration
 *                       fails at startup instead of at the first request.
 */
@Validated
@ConfigurationProperties(prefix = "ledgerx.web")
public record WebProperties(
        @NotEmpty List<String> allowedOrigins
) {
}
