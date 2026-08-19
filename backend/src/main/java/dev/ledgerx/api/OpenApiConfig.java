package dev.ledgerx.api;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    static final String BEARER_SCHEME = "bearerAuth";

    /**
     * Declaring the scheme here is what makes the Authorize button in the UI
     * work: paste an access token once and every documented call carries it,
     * so the docs are usable rather than merely readable.
     */
    @Bean
    OpenAPI ledgerxOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("LedgerX API")
                        .version("v1")
                        .description("""
                                Double-entry payments ledger.

                                Every movement of money is a balanced pair of ledger entries, so
                                balances are derived from primary records rather than stored as
                                editable state. Amounts are always integer minor units (cents):
                                `25000` is $250.00. Never parse them as a decimal.

                                Obtain a token from `POST /api/auth/login`, then use Authorize
                                above. Access tokens are short lived; use `POST /api/auth/refresh`
                                to rotate.

                                This deployment has no external funding source, so deposits mint
                                money against a system treasury account. It is a sandbox.""")
                        .license(new License().name("MIT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Access token from /api/auth/login or /api/auth/refresh")));
    }
}
