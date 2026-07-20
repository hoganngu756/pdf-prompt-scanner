package com.promptscanner.backend.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AdminApiKeyInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AdminApiKeyInterceptor.class);

    @Value("${app.admin.api-key}")
    private String adminApiKey;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String method = request.getMethod();
        
        // Only protect write operations (POST, PUT, DELETE)
        if ("GET".equalsIgnoreCase(method) || "OPTIONS".equalsIgnoreCase(method)) {
            return true;
        }

        String apiKeyHeader = request.getHeader("X-Admin-Api-Key");
        
        if (adminApiKey == null || adminApiKey.trim().isEmpty() || "default-admin-key".equals(adminApiKey)) {
            log.warn("Admin API Key is default or empty. Allowing request. Please set ADMIN_API_KEY in environment.");
            return true;
        }

        if (apiKeyHeader == null || !adminApiKey.equals(apiKeyHeader)) {
            log.warn("Unauthorized request to {}: API Key mismatch or missing. Header: {}", request.getRequestURI(), apiKeyHeader);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\": \"Unauthorized. Valid X-Admin-Api-Key header is required.\"}");
            return false;
        }

        return true;
    }
}
