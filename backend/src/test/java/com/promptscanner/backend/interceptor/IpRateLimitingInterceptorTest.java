package com.promptscanner.backend.interceptor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class IpRateLimitingInterceptorTest {

    private static final int SCAN_LIMIT = 3;
    /** Mirrors MAX_TRACKED_CLIENTS in the interceptor. */
    private static final int MAX_TRACKED_CLIENTS = 50_000;

    private IpRateLimitingInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new IpRateLimitingInterceptor();
        ReflectionTestUtils.setField(interceptor, "rateLimitRpm", SCAN_LIMIT);
        ReflectionTestUtils.setField(interceptor, "apiRateLimitRpm", 120);
        ReflectionTestUtils.setField(interceptor, "trustedProxyCount", 1);
    }

    private boolean request(String method, String uri, String ip) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest(method, uri);
        req.setRequestURI(uri);
        req.addHeader("X-Forwarded-For", ip);
        return interceptor.preHandle(req, new MockHttpServletResponse(), new Object());
    }

    private boolean scan(String ip) throws Exception {
        return request("POST", "/api/scan", ip);
    }

    private boolean read(String ip) throws Exception {
        return request("GET", "/api/rules", ip);
    }

    /** The sweep is throttled to once per 30s, so tests ask for one explicitly. */
    private void allowSweep() {
        ((AtomicLong) ReflectionTestUtils.getField(interceptor, "lastSweepAt")).set(0);
    }

    @Test
    void refusesOnceTheBudgetIsSpent() throws Exception {
        for (int i = 0; i < SCAN_LIMIT; i++) {
            assertTrue(scan("1.2.3.4"), "request " + i + " should be within budget");
        }
        assertFalse(scan("1.2.3.4"));
    }

    @Test
    void budgetsAreSeparatePerClient() throws Exception {
        for (int i = 0; i < SCAN_LIMIT; i++) {
            scan("1.2.3.4");
        }
        assertFalse(scan("1.2.3.4"));
        assertTrue(scan("5.6.7.8"), "one client's spending must not consume another's");
    }

    @Test
    void scanAndReadBudgetsDoNotShare() throws Exception {
        for (int i = 0; i < SCAN_LIMIT; i++) {
            scan("1.2.3.4");
        }
        assertFalse(scan("1.2.3.4"));
        assertTrue(read("1.2.3.4"), "browsing must not be blocked by the scan budget");
    }

    @Test
    void floodingDistinctAddressesDoesNotForgiveActiveSpend() throws Exception {
        // Regression: eviction at the ceiling used to call buckets.clear(), so
        // anyone able to push the map past its limit wiped every counter — their
        // own included — and got their budget back on demand.

        // 1. Fill the map to its ceiling from throwaway addresses.
        for (int i = 0; i < MAX_TRACKED_CLIENTS; i++) {
            read("10." + (i >> 16 & 0xFF) + "." + (i >> 8 & 0xFF) + "." + (i & 0xFF));
        }

        // 2. Spend a real client's scan budget. Its bucket is now the most recently
        //    seen, which is exactly what eviction must never discard.
        for (int i = 0; i < SCAN_LIMIT; i++) {
            scan("203.0.113.9");
        }
        assertFalse(scan("203.0.113.9"), "precondition: the budget is spent");

        // 3. Force the sweep the flood was meant to provoke.
        allowSweep();
        read("198.51.100.1");

        // 4. The spend must survive it.
        assertFalse(scan("203.0.113.9"),
                "a flood of distinct addresses must not restore an exhausted budget");
    }

    @Test
    void ignoresForwardedHeaderWhenNoProxyIsTrusted() throws Exception {
        // With nothing in front of the app, X-Forwarded-For is purely caller-supplied;
        // honouring it would let one client claim unlimited identities.
        ReflectionTestUtils.setField(interceptor, "trustedProxyCount", 0);

        for (int i = 0; i < SCAN_LIMIT; i++) {
            scan("1.1.1." + i);
        }
        assertFalse(scan("9.9.9.9"), "all of these are the same client");
    }
}
