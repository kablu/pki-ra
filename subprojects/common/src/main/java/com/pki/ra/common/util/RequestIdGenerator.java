package com.pki.ra.common.util;

import java.time.Year;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Generates unique request IDs in the format: RA-REQ-{YYYY}-{6-digit-sequence}
 * Example: RA-REQ-2026-000042
 *
 * <p>Thread-safe via AtomicLong. Sequence resets on application restart —
 * uniqueness is ultimately enforced by the database UNIQUE constraint.
 */
public final class RequestIdGenerator {

    private static final String PREFIX = "RA-REQ";
    private static final AtomicLong SEQUENCE = new AtomicLong(0);

    private RequestIdGenerator() {}

    public static String generate() {
        long seq = SEQUENCE.incrementAndGet();
        return String.format("%s-%d-%06d", PREFIX, Year.now().getValue(), seq);
    }
}
