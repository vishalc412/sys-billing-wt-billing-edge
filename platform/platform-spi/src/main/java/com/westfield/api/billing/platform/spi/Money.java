
package com.westfield.api.billing.platform.spi;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The one monetary normalisation in the system (ADR-0004).
 *
 * <p>The legacy expression is {@code x as Number as String {format:"0.00"} as Number default null}.
 * It ROUNDS, it does not truncate, and the rounding mode is DataWeave's default. HALF_UP is
 * therefore INHERITED RUNTIME BEHAVIOUR, not a stated business rule (R-018). If the billing business
 * owner states HALF_EVEN, ADR-0004 is superseded and every money criterion is re-baselined.
 *
 * <p>Unknown is null, never ZERO: a consumer must be able to tell "nothing owed" from "not known".
 */
public final class Money {

    private Money() {
    }

    /** @return the value at exactly two decimals, or null if the value was not supplied. */
    public static BigDecimal normalise(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }
}
