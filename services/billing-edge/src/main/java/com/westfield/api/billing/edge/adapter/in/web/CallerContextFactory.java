package com.westfield.api.billing.edge.adapter.in.web;

import com.westfield.api.billing.platform.observability.MigratedFrom;
import com.westfield.api.billing.platform.spi.CallerContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Turns the validated bearer token into the typed {@link CallerContext} every audit projection and
 * the entitlement rule read (N-0021, ADR-0013).
 *
 * <p>The legacy reads {@code authentication.properties.userProperties.*}, a binding that exists only
 * because an API Manager policy populated it — a dependency that is invisible in the flow XML
 * (N-0004 ec 2). The policy set was never exported (R-003), so the claim NAMES below are the ones
 * the legacy expressions demonstrably read, and nothing has been invented around them: no scope
 * check, no client-id allowlist, no audience rule beyond what the resource server does.
 *
 * <p>Claims are read from the TOKEN, never from injected headers. A service that trusts headers is
 * bypassable the moment it is reachable directly, and today it is: the listener binds 0.0.0.0:8081
 * with no authentication of its own (N-0013 ec 2, ADR-0013).
 */
@MigratedFrom(value = "km:node/N-0021", note = "authentication.properties.userProperties.* as a typed object")
public class CallerContextFactory {

    /** The nested actor object PingFederate uses for some issuing channels (N-0052). */
    public static final String ACT_CLAIM = "act";

    public CallerContext fromSecurityContext() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            // No decoded token context at all. Every consumer of CallerContext must cope with this,
            // because the legacy does: its audit record is emitted with a null clientId and EMPTY
            // everywhere else (N-0051 ec 1).
            return null;
        }
        return from(jwt);
    }

    @MigratedFrom(value = "km:node/N-0051", note = "both actor representations; raw values, sentinels applied later")
    public CallerContext from(Jwt jwt) {
        Map<String, Object> act = nestedActor(jwt);
        boolean actorFromNestedAct = act != null;
        String actorSubject = actorFromNestedAct
                // A nested act object that omits sub or email does NOT fall back to the flat claims;
                // the missing value is EMPTY and the actor information is lost silently (N-0052 ec 3).
                // Preserved: the fallback would report actors the legacy never reported.
                ? asString(act.get("sub"))
                : jwt.getClaimAsString("actSub");
        String actorEmail = actorFromNestedAct
                ? asString(act.get("email"))
                : jwt.getClaimAsString("actEmail");
        return new CallerContext(
                jwt.getClaimAsString("sub"),
                jwt.getClaimAsString("email"),
                clientId(jwt),
                actorSubject,
                actorEmail,
                actorFromNestedAct,
                agencyCodes(jwt));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> nestedActor(Jwt jwt) {
        Object claim = jwt.getClaim(ACT_CLAIM);
        if (claim instanceof Map<?, ?> map && !map.isEmpty()) {
            return (Map<String, Object>) map;
        }
        return null;
    }

    private static String clientId(Jwt jwt) {
        // clientId is the ONE field the legacy leaves without a default (N-0051 ec 1). The raw value
        // is carried through here; the audit projections are where that asymmetry becomes visible.
        String claim = jwt.getClaimAsString("clientId");
        return claim != null ? claim : jwt.getClaimAsString("client_id");
    }

    private static List<String> agencyCodes(Jwt jwt) {
        Object claim = jwt.getClaim("agencyCodes");
        if (claim instanceof List<?> values) {
            List<String> codes = new ArrayList<>();
            for (Object value : values) {
                if (value != null) {
                    codes.add(value.toString());
                }
            }
            return List.copyOf(codes);
        }
        if (claim instanceof String single && !single.isBlank()) {
            return List.of(single);
        }
        // Absent claim is an EMPTY LIST on the audit record, never the EMPTY sentinel (N-0051 rule 5)
        // — and an empty list is a DENY on the worklist endpoints (ADR-0037).
        return List.of();
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }
}
