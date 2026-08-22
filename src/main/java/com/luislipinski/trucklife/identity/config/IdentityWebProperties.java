package com.luislipinski.trucklife.identity.config;

import java.net.URI;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("identity.web")
public record IdentityWebProperties(
        List<String> allowedOrigins,
        String csrfCookieName
) {

    public IdentityWebProperties {
        if (allowedOrigins == null || allowedOrigins.isEmpty()) {
            throw new IllegalArgumentException("identity.web.allowed-origins must be configured");
        }
        allowedOrigins = allowedOrigins.stream()
                .map(String::strip)
                .peek(IdentityWebProperties::validateOrigin)
                .distinct()
                .toList();
        if (csrfCookieName == null || csrfCookieName.isBlank()) {
            throw new IllegalArgumentException("identity.web.csrf-cookie-name must be configured");
        }
        csrfCookieName = csrfCookieName.strip();
    }

    private static void validateOrigin(String origin) {
        if (origin.isBlank() || origin.contains("*")) {
            throw new IllegalArgumentException("allowed origins must be explicit and non-blank");
        }
        URI uri;
        try {
            uri = URI.create(origin);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("allowed origin must be a valid URI", exception);
        }
        boolean localhost = "localhost".equalsIgnoreCase(uri.getHost())
                || "127.0.0.1".equals(uri.getHost());
        boolean validScheme = "https".equalsIgnoreCase(uri.getScheme())
                || (localhost && "http".equalsIgnoreCase(uri.getScheme()));
        if (!validScheme || uri.getHost() == null || uri.getRawQuery() != null
                || uri.getRawFragment() != null || !uri.getPath().isEmpty()) {
            throw new IllegalArgumentException(
                    "allowed origins must use HTTPS, except HTTP localhost, and contain no path"
            );
        }
    }
}
