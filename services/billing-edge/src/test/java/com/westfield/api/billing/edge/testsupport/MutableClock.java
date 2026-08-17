package com.westfield.api.billing.edge.testsupport;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/**
 * A clock the test moves by hand.
 *
 * <p>Both caches in this packet — the SAML assertion cache (ADR-0007) and the credential cache
 * (ADR-0008) — are defined by a TTL, and a TTL is only testable if time can be advanced. Sleeping for
 * the real duration would make the suite slow and flaky, and shortening the TTL to milliseconds would
 * test a configuration nobody ships.
 */
public final class MutableClock extends Clock {

    private final ZoneId zone;
    private Instant instant;

    public MutableClock(Instant start) {
        this(start, ZoneId.of("UTC"));
    }

    private MutableClock(Instant start, ZoneId zone) {
        this.instant = start;
        this.zone = zone;
    }

    public void advance(Duration amount) {
        instant = instant.plus(amount);
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId newZone) {
        return new MutableClock(instant, newZone);
    }

    @Override
    public Instant instant() {
        return instant;
    }
}
