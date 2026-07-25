package com.promptscanner.backend.config;

import com.promptscanner.backend.interceptor.AdminApiKeyInterceptor;
import com.promptscanner.backend.interceptor.IpRateLimitingInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    private final AdminApiKeyInterceptor adminApiKeyInterceptor;
    private final IpRateLimitingInterceptor ipRateLimitingInterceptor;

    public WebConfig(AdminApiKeyInterceptor adminApiKeyInterceptor,
                     IpRateLimitingInterceptor ipRateLimitingInterceptor) {
        this.adminApiKeyInterceptor = adminApiKeyInterceptor;
        this.ipRateLimitingInterceptor = ipRateLimitingInterceptor;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] origins = allowedOrigins.split(",");
        registry.addMapping("/**")
                .allowedOrigins(origins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                // Auth is a bearer-style header, not a cookie, so credentialed
                // cross-origin requests are never needed.
                .allowCredentials(false);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(adminApiKeyInterceptor)
                .addPathPatterns("/api/rules", "/api/rules/**", "/api/history");

        // Every endpoint is rate limited, not just scanning, so rule and history
        // traffic can't be used to hammer the instance for free.
        registry.addInterceptor(ipRateLimitingInterceptor)
                .addPathPatterns("/api/**");
    }
}
