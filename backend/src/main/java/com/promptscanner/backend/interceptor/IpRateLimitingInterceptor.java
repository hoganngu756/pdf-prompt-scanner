package com.promptscanner.backend.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class IpRateLimitingInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(IpRateLimitingInterceptor.class);

    /** Scans are expensive (render + OCR + LLM), so they get a tight budget. */
    @Value("${app.scan.rate-limit-rpm:10}")
    private int rateLimitRpm;

    /**
     * Cheap reads and rule edits share a separate, roomier budget -- browsing the
     * tabs issues several requests and must not burn the scan allowance.
     */
    @Value("${app.api.rate-limit-rpm:120}")
    private int apiRateLimitRpm;

    /**
     * Hops added by proxies in front of this app: 1 for a single Render/CDN layer,
     * 0 when the app is reached directly (local dev). Entries left of this depth
     * are client-supplied and are never trusted. Getting this wrong in the "too
     * high" direction is what makes X-Forwarded-For spoofable, so it is explicit.
     */
    @Value("${app.scan.trusted-proxy-count:1}")
    private int trustedProxyCount;

    /** Drop a bucket once it has been full and idle for this long. */
    private static final long BUCKET_TTL_MS = 10 * 60 * 1000L;
    private static final int MAX_TRACKED_CLIENTS = 50_000;

    /** Once over the ceiling, evict down to this so the sweep isn't re-triggered at once. */
    private static final int EVICT_DOWN_TO = 40_000;

    /**
     * Sweeping is O(n) over the bucket map, so it must not run per request -- that
     * turns a flood of distinct addresses into quadratic work on the very path
     * that is supposed to shed load.
     */
    private static final long SWEEP_INTERVAL_MS = 30_000L;

    private final AtomicLong lastSweepAt = new AtomicLong(0);

    private final Map<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        boolean isScan = "/api/scan".equals(request.getRequestURI()) && "POST".equalsIgnoreCase(request.getMethod());
        int limit = isScan ? rateLimitRpm : apiRateLimitRpm;

        String ip = getClientIp(request);
        evictStaleBuckets();
        // Separate buckets so browsing can't consume the scan allowance, or vice versa
        String bucketKey = (isScan ? "scan|" : "api|") + ip;
        TokenBucket bucket = buckets.computeIfAbsent(bucketKey, k -> new TokenBucket(limit, 60000)); // 60s window

        if (!bucket.tryConsume()) {
            log.warn("Rate limit exceeded for IP: {} on endpoint {}", ip, request.getRequestURI());
            response.setStatus(429); // Too Many Requests
            response.setContentType("application/json");
            response.getWriter().write(isScan
                    ? "{\"error\": \"Too many scan requests. Please wait before scanning again.\"}"
                    : "{\"error\": \"Too many requests. Please wait a moment.\"}");
            return false;
        }

        return true;
    }

    /**
     * Reads the client address from the right end of X-Forwarded-For.
     *
     * Proxies append, so the rightmost entries are the trustworthy ones; taking
     * the leftmost lets a caller spoof an unlimited number of identities simply by
     * sending their own X-Forwarded-For header, bypassing the limit entirely.
     */
    private String getClientIp(HttpServletRequest request) {
        // With no proxy in front, X-Forwarded-For is purely attacker-controlled and
        // must be ignored outright -- otherwise a direct-exposure deployment is
        // trivially bypassable.
        if (trustedProxyCount <= 0) {
            return request.getRemoteAddr();
        }

        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null || xfHeader.isBlank()) {
            return request.getRemoteAddr();
        }

        String[] hops = xfHeader.split(",");
        // Proxies append, so the rightmost entries are the trustworthy ones. Step
        // back over our own trusted layers to reach the address they observed;
        // anything further left was supplied by the caller and is ignored.
        int index = hops.length - trustedProxyCount;
        if (index < 0) {
            index = 0;
        }
        String candidate = hops[index].trim();
        return candidate.isEmpty() ? request.getRemoteAddr() : candidate;
    }

    /**
     * Without eviction the map grows once per distinct client address forever,
     * which is itself a memory-exhaustion vector.
     *
     * What this must never do is drop buckets that still hold spent tokens. An
     * earlier version called clear() at the ceiling, which made the limiter
     * self-defeating: flooding the map with distinct source addresses wiped every
     * counter, so an attacker could reset their own budget on demand and cost
     * everyone else their protection at the same time.
     */
    private void evictStaleBuckets() {
        long now = System.currentTimeMillis();
        long last = lastSweepAt.get();
        if (now - last < SWEEP_INTERVAL_MS) {
            return;
        }
        // Whoever wins the CAS does this sweep; everyone else carries on serving.
        if (!lastSweepAt.compareAndSet(last, now)) {
            return;
        }

        // Idle means fully refilled and untouched, so forgetting these changes
        // nobody's effective limit.
        buckets.values().removeIf(b -> b.isIdleSince(now, BUCKET_TTL_MS));

        int size = buckets.size();
        if (size < MAX_TRACKED_CLIENTS) {
            return;
        }

        // Still over the ceiling with nothing idle to reclaim. Drop the least
        // recently seen rather than everything: a flooder's own buckets are the
        // most recently seen, so this is the one policy they cannot use to clear
        // their own record.
        log.warn("Rate limiter tracking {} clients; evicting least-recently-seen down to {}.",
                size, EVICT_DOWN_TO);
        buckets.entrySet().stream()
                .sorted(Comparator.comparingLong(e -> e.getValue().lastSeen()))
                .limit((long) size - EVICT_DOWN_TO)
                .forEach(e -> buckets.remove(e.getKey(), e.getValue()));
    }

    private static class TokenBucket {
        private final double capacity;
        private final long refillIntervalMs;
        private double tokens;
        private long lastRefillTime;

        public TokenBucket(double capacity, long refillIntervalMs) {
            this.capacity = capacity;
            this.refillIntervalMs = refillIntervalMs;
            this.tokens = capacity;
            this.lastRefillTime = System.currentTimeMillis();
        }

        public synchronized boolean tryConsume() {
            refill();
            if (tokens >= 1.0) {
                tokens -= 1.0;
                return true;
            }
            return false;
        }

        synchronized long lastSeen() {
            return lastRefillTime;
        }

        /** Idle means fully refilled and untouched, so forgetting it changes nothing. */
        synchronized boolean isIdleSince(long now, long ttlMs) {
            return now - lastRefillTime > ttlMs && tokens >= capacity;
        }

        private void refill() {
            long now = System.currentTimeMillis();
            long duration = now - lastRefillTime;
            if (duration > 0) {
                double tokensToAdd = (duration * capacity) / refillIntervalMs;
                tokens = Math.min(capacity, tokens + tokensToAdd);
                lastRefillTime = now;
            }
        }
    }
}
