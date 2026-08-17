
package com.westfield.api.billing.platform.observability;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares which MuleSoft artefact a type or method reproduces.
 *
 * <p>Source retention: this is documentation and a CI input, not runtime metadata. The annotation
 * check in CI resolves every {@code value()} against the knowledge map, which is what makes the
 * gatekeeper's backward-traceability check possible.
 *
 * <p>Examples of a well-formed value: {@code "mule:flow/primaryAccount-implementation"},
 * {@code "km:node/N-0077"}.
 */
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD})
public @interface MigratedFrom {

    /** The Mule flow or knowledge-map node this element reproduces. */
    String value();

    /** Why this element looks the way it does; name the ADR when behaviour is deliberately odd. */
    String note() default "";
}
