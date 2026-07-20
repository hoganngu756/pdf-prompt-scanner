package com.promptscanner.backend.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class IpRateLimitingInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(IpRateLimitingInterceptor.class);

    @Value("${app.scan.rate-limit-rpm:10}")
    private int rateLimitRpm;

    private final Map<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!"/api/scan".equals(request.getRequestURI()) || !"POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String ip = getClientIp(request);
        TokenBucket bucket = buckets.computeIfAbsent(ip, k -> new TokenBucket(rateLimitRpm, 60000)); // 60s window

        if (!bucket.tryConsume()) {
            log.warn("Rate limit exceeded for IP: {} on endpoint {}", ip, request.getRequestURI());
            response.setStatus(429); // Too Many Requests
            response.setContentType("application/json");
            response.getWriter().write("{\"error\": \"Too many scan requests. Please wait before scanning again.\"}");
            return false;
        }

        return true;
    }

    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0].trim();
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
