package com.luislipinski.trucklife.identity.config;

import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("identity")
public record IdentityProperties(
        Duration emailVerificationTtl,
        RateLimits rateLimit
) {

    public IdentityProperties {
        requirePositive(emailVerificationTtl, "identity.email-verification-ttl");
        Objects.requireNonNull(rateLimit, "identity.rate-limit must be configured");
    }

    public record RateLimits(
            Policy registration,
            Policy emailVerification,
            Policy resendVerification,
            Policy login,
            Policy refresh
    ) {

        public RateLimits {
            Objects.requireNonNull(registration, "registration rate limit must be configured");
            Objects.requireNonNull(emailVerification, "email verification rate limit must be configured");
            Objects.requireNonNull(resendVerification, "resend rate limit must be configured");
            Objects.requireNonNull(login, "login rate limit must be configured");
            Objects.requireNonNull(refresh, "refresh rate limit must be configured");
        }
    }

    public record Policy(int maxAttempts, Duration window) {

        public Policy {
            if (maxAttempts < 1) {
                throw new IllegalArgumentException("rate limit max-attempts must be positive");
            }
            requirePositive(window, "rate limit window");
        }
    }

    private static void requirePositive(Duration duration, String propertyName) {
        Objects.requireNonNull(duration, propertyName + " must be configured");
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(propertyName + " must be positive");
        }
    }
}
