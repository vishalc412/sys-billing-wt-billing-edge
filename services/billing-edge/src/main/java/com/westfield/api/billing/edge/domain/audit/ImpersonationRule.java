package com.westfield.api.billing.edge.domain.audit;

import com.westfield.api.billing.platform.observability.MigratedFrom;
import com.westfield.api.billing.platform.spi.CallerContext;

/**
 * The two definitions of "impersonated" that this application has always had, each implemented once.
 *
 * <p>They DISAGREE, and the disagreement ships (ADR-0036). For a token using the flat {@code actSub}
 * representation the request/response audit stream says impersonated and the per-endpoint audit
 * stream says not impersonated, for the same request. Reconciling them changes a compliance-relevant
 * count in the direction of "more impersonation than we thought"; ADR-0036 gates that on R-021
 * producing a named compliance owner, which has not happened. Do not "fix" this.
 */
@MigratedFrom(value = "km:node/N-0052",
        note = "isImpersonated (complete) vs the inline !isEmpty(act) test at N-0036; ADR-0036 preserves both")
public final class ImpersonationRule {

    private ImpersonationRule() {
    }

    /**
     * The complete rule, used by the request/response audit record (N-0052).
     *
     * <p>PingFederate encodes the actor in two ways depending on the issuing channel — a nested
     * {@code act} object for some sources, a flat {@code actSub} claim for others — so both count.
     * The four-character STRING {@code "null"} is some issuers' "no actor" marker and must not be
     * mistaken for an actor.
     *
     * <p>Asymmetry preserved deliberately (N-0052 ec 1): a nested {@code act.sub} carrying the text
     * {@code "null"} IS reported as impersonated, while a flat {@code actSub} carrying the same text
     * is not. Two encodings of one concept, handled differently, exactly as today.
     */
    @MigratedFrom(value = "km:node/N-0052", note = "isImpersonated; accepts either actor representation")
    public static boolean complete(CallerContext caller) {
        if (caller == null) {
            return false;
        }
        if (caller.actorFromNestedAct()) {
            return true;
        }
        String flatActorSubject = caller.actorSubject();
        return flatActorSubject != null
                && !CallerContext.EMPTY.equals(flatActorSubject)
                && !CallerContext.NULL_SENTINEL.equals(flatActorSubject);
    }

    /**
     * The incomplete rule, used by the per-endpoint audit record (N-0036 ec 4): {@code !isEmpty(act)}
     * only. A token carrying only the flat claim is reported as NOT impersonated here.
     */
    @MigratedFrom(value = "km:node/N-0036", note = "inline !isEmpty(act) test, five verbatim copies collapsed to one")
    public static boolean actObjectOnly(CallerContext caller) {
        return caller != null && caller.actorFromNestedAct();
    }
}
