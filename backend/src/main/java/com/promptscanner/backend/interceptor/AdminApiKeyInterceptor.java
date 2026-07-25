package com.promptscanner.backend.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;

@Component
public class AdminApiKeyInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AdminApiKeyInterceptor.class);

    /**
     * Endpoints whose GET responses are sensitive. Scan history exposes filenames,
     * flagged excerpts, and AI analyses of documents submitted by other users, so
     * reads are credentialed too -- unlike the rule list, which stays public.
     */
    private static final Set<String> READ_PROTECTED_PATHS = Set.of("/api/history");

    @Value("${app.admin.api-key}")
    private String adminApiKey;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String method = request.getMethod();

        // CORS preflight must never be challenged
        if ("OPTIONS".equalsIgnoreCase(method)) {
            return true;
        }

        boolean isRead = "GET".equalsIgnoreCase(method);
        if (isRead && !READ_PROTECTED_PATHS.contains(request.getRequestURI())) {
            return true;
        }

        // Fail closed: an unconfigured key must never leave rule management open
        // to anonymous callers, since deleting every rule silently disables the
        // heuristic engine.
        if (adminApiKey == null || adminApiKey.trim().isEmpty() || "default-admin-key".equals(adminApiKey)) {
            log.error("Admin API Key is unset or still the default. Rejecting {} {}. Set ADMIN_API_KEY in the environment to enable rule management.",
                    method, request.getRequestURI());
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\": \"Rule management is disabled: the server has no ADMIN_API_KEY configured.\"}");
            return false;
        }

        String apiKeyHeader = request.getHeader("X-Admin-Api-Key");
        if (!matchesAdminKey(apiKeyHeader)) {
            log.warn("Unauthorized request to {}: API Key {}.", request.getRequestURI(),
                    apiKeyHeader == null ? "missing" : "mismatch");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\": \"Unauthorized. Valid X-Admin-Api-Key header is required.\"}");
            return false;
        }

        return true;
    }

    /** Constant-time comparison so response timing can't be used to recover the key. */
    private boolean matchesAdminKey(String provided) {
        if (provided == null) {
            return false;
        }
        return MessageDigest.isEqual(
                adminApiKey.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8));
    }
}
