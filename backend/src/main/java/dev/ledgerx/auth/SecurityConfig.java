package dev.ledgerx.auth;

import dev.ledgerx.api.ErrorResponse;
import dev.ledgerx.api.WebProperties;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Clock;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            JwtAuthenticationFilter jwtAuthenticationFilter,
                                            AuthenticationEntryPoint authenticationEntryPoint,
                                            AccessDeniedHandler accessDeniedHandler,
                                            CorsConfigurationSource corsConfigurationSource) throws Exception {
        return http
                // Nothing is carried in a cookie or a session, so a forged
                // cross-site request has no ambient credential to ride on.
                .csrf(csrf -> csrf.disable())
                // Registered before authorization so a preflight OPTIONS is
                // answered by the CORS filter rather than rejected as an
                // unauthenticated request, which is what browsers require.
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .logout(logout -> logout.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()
                        // Spring forwards unhandled exceptions to /error. Without
                        // this the forward is itself an unauthenticated request,
                        // so a malformed body came back as 401 instead of 400 and
                        // every unmapped failure was masked as an auth problem.
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    /**
     * Scoped to {@code /api/**} so the actuator surface is not made
     * browser-reachable as a side effect. Origins are enumerated rather than
     * wildcarded, and the Authorization header is exposed only for the methods
     * the API actually serves.
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource(WebProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key"));
        // No cookies are used, so credentials stay off: the bearer token is an
        // explicit header the client chooses to send, not ambient authority.
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    /**
     * Security rejections are emitted in the same body shape as
     * {@code GlobalExceptionHandler} produces. They cannot go through that
     * handler because they happen in the filter chain, before any controller
     * exists to advise.
     */
    @Bean
    AuthenticationEntryPoint authenticationEntryPoint(ObjectMapper objectMapper, Clock clock) {
        return (request, response, authException) ->
                writeError(response, objectMapper, clock, HttpStatus.UNAUTHORIZED,
                        "UNAUTHENTICATED", "Authentication is required to access this resource");
    }

    @Bean
    AccessDeniedHandler accessDeniedHandler(ObjectMapper objectMapper, Clock clock) {
        return (request, response, accessDeniedException) ->
                writeError(response, objectMapper, clock, HttpStatus.FORBIDDEN,
                        "ACCESS_DENIED", "Your role does not permit this action");
    }

    private static void writeError(HttpServletResponse response,
                                   ObjectMapper objectMapper,
                                   Clock clock,
                                   HttpStatus status,
                                   String code,
                                   String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(),
                ErrorResponse.of(clock.instant(), status.value(), code, message));
    }
}
