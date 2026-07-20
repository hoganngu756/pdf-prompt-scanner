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
                .allowCredentials(true);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(adminApiKeyInterceptor)
                .addPathPatterns("/api/rules", "/api/rules/**");
        
        registry.addInterceptor(ipRateLimitingInterceptor)
                .addPathPatterns("/api/scan");
    }
}
