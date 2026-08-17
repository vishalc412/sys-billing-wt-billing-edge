
package com.westfield.api.billing.platform.observability;

import com.westfield.api.billing.platform.observability.MigratedFrom;

/**
 * The legacy structured-logging trace-point taxonomy (km:node/N-0009, CAP-003). Reproduced exactly:
 * downstream log analytics groups on these values.
 */
@MigratedFrom(value = "km:node/N-0009", note = "JSON logger trace points; see ADR-0017")
public enum TracePoint {
    START,
    BEFORE_REQUEST,
    AFTER_REQUEST,
    EXCEPTION,
    END
}
