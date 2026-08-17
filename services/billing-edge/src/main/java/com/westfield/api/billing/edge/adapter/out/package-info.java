/**
 * adapter/out
 *
 * <p>Backend clients and wire mapping. Holds no business rule (ADR-0006).
 *
 * <p>Module scope: CAP-001, 002, 003, 004, 011, 012, 013, 014 - admission, identity, audit, errors, config, /info, console. Owned by exactly one work packet (ADR-0001); no other agent writes here.
 */
package com.westfield.api.billing.edge.adapter.out;
