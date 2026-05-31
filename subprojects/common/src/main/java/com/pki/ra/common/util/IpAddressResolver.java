package com.pki.ra.common.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Utility for resolving the real client IP address from an HTTP request.
 *
 * <h3>Problems solved</h3>
 * <ol>
 *   <li><b>Proxy / load balancer</b> — real client IP is in the first entry of
 *       the {@code X-Forwarded-For} header, not in {@code RemoteAddr}.</li>
 *   <li><b>IPv6 loopback</b> — localhost connections return {@code "0:0:0:0:0:0:0:1"}
 *       (or {@code "::1"}) on dual-stack JVMs instead of the familiar
 *       {@code "127.0.0.1"}. Normalised here for readable logs and consistent
 *       audit entries.</li>
 * </ol>
 *
 * <h3>Resolution order</h3>
 * <ol>
 *   <li>{@code X-Forwarded-For} header (first entry) — set by reverse proxies
 *       and load balancers.</li>
 *   <li>{@link HttpServletRequest#getRemoteAddr()} — direct connection fallback.</li>
 * </ol>
 *
 * <h3>Reusability</h3>
 * Used by every abstract controller that needs client IP:
 * {@link com.pki.ra.common.web.AbstractUserController} and any future
 * refresh / cert-operation controllers.
 * Defined once here — never duplicated.
 *
 * @author pki-ra
 * @since  1.0.0
 */
public final class IpAddressResolver {

    /** IPv6 loopback — full form returned by JVM on dual-stack hosts. */
    private static final String IPV6_LOOPBACK_FULL  = "0:0:0:0:0:0:0:1";

    /** IPv6 loopback — compact form (equivalent to the above). */
    private static final String IPV6_LOOPBACK_SHORT = "::1";

    /** Canonical IPv4 loopback — used as the normalised output. */
    private static final String IPV4_LOOPBACK       = "127.0.0.1";

    private IpAddressResolver() {
        // static utility — no instances
    }

    /**
     * Resolves the real client IP from the given request.
     *
     * @param request the inbound HTTP request — never null
     * @return resolved and normalised IP string — never null
     */
    public static String resolve(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            // X-Forwarded-For: client, proxy1, proxy2 — first entry is the real client
            return normalize(forwarded.split(",")[0].trim());
        }
        return normalize(request.getRemoteAddr());
    }

    /**
     * Normalises an IPv6 loopback address to the readable IPv4 equivalent.
     *
     * <p>{@code "0:0:0:0:0:0:0:1"} and {@code "::1"} → {@code "127.0.0.1"}.
     * All other addresses are returned unchanged.
     *
     * @param ip raw IP string from the request — never null
     * @return normalised IP string — never null
     */
    private static String normalize(String ip) {
        if (IPV6_LOOPBACK_FULL.equals(ip) || IPV6_LOOPBACK_SHORT.equals(ip)) {
            return IPV4_LOOPBACK;
        }
        return ip;
    }
}
