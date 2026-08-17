package com.westfield.api.billing.edge.config;

import com.westfield.api.billing.platform.observability.MigratedFrom;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Inbound authentication (ADR-0013, N-0004, N-0021, N-0035).
 *
 * <p>The service validates the bearer token <b>itself</b>, as an OAuth2 resource server. It does not
 * assume a gateway did it and it does not trust an injected header. The legacy relies entirely on an
 * API Manager policy whose configuration was never exported (R-003) while its own listener binds
 * {@code 0.0.0.0} over plain HTTP with no authentication of its own (N-0013 ec 2) — so anything that
 * can reach the pod today is unauthenticated. That is the gap ADR-0013 closes, and it is why the
 * {@link JwtDecoder} is a REQUIRED constructor dependency: with no issuer configured the application
 * refuses to start rather than serving traffic it cannot authenticate.
 *
 * <p><b>Why authorization is {@code permitAll} and the token requirement lives elsewhere.</b> Two
 * different conditions produce two different legacy responses, and collapsing them would change what
 * callers see:
 * <ul>
 *   <li>an <b>absent</b> Authorization header is a CONTRACT violation. The RAML declares the header
 *       required, so APIkit answers 400 with the one-field body (ADM-003-a). That check lives in
 *       {@code InboundAdmissionRule}, inside the audit funnel, so the rejection is audited;</li>
 *   <li>a <b>present but invalid</b> token is an authentication failure and is answered 401 by the
 *       resource server. The legacy's equivalent response came from a gateway policy nobody has
 *       exported, so its exact shape is an assumption recorded under R-003 (ADR-0013).</li>
 * </ul>
 * Requiring authentication at this layer would turn every missing-header request into a 401 and
 * silently retire the 400 that consumers see today. The admission filter is therefore the mandatory
 * gate for the header, and {@code SecurityRequiresTokenTest} asserts that no request without one ever
 * reaches an implementation.
 *
 * <p>The console is deliberately reachable without a token, as it is today (N-0031); ADR-0038 gates it
 * by configuration instead, and disables it in production.
 */
@Configuration(proxyBeanMethods = false)
@MigratedFrom(value = "km:node/N-0004",
        note = "the API Manager token binding, relocated into the service per ADR-0013")
public class SecurityConfiguration {

    private final JwtDecoder jwtDecoder;

    public SecurityConfiguration(JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    @Bean
    @MigratedFrom(value = "km:node/N-0021", note = "decoded claims made available to the audit projection")
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                // No browser session and no form login: this is a machine-to-machine read API, and a
                // session would be a second, weaker credential nobody asked for.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
                // Validates the token when one is presented; 401 when it is present and bad.
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.decoder(jwtDecoder)))
                .build();
    }
}
