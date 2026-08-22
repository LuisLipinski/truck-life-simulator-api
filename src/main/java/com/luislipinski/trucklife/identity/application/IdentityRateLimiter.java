package com.luislipinski.trucklife.identity.application;

import com.luislipinski.trucklife.identity.config.IdentityProperties;
import com.luislipinski.trucklife.shared.error.RateLimitExceededException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

@Component
public class IdentityRateLimiter {

    private static final long CLEANUP_INTERVAL = 256;

    private final IdentityProperties properties;
    private final Clock clock;
    private final ConcurrentHashMap<BucketKey, Window> windows = new ConcurrentHashMap<>();
    private final AtomicLong checks = new AtomicLong();

    public IdentityRateLimiter(IdentityProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public void checkRegistration(String clientAddress) {
        check(
                Scope.REGISTRATION,
                clientAddress,
                properties.rateLimit().registration()
        );
    }

    public void checkEmailVerification(String tokenHash, String clientAddress) {
        check(
                Scope.EMAIL_VERIFICATION,
                tokenHash + ':' + clientAddress,
                properties.rateLimit().emailVerification()
        );
    }

    public void checkResendVerification(String normalizedEmailHash, String clientAddress) {
        check(
                Scope.RESEND_VERIFICATION,
                normalizedEmailHash + ':' + clientAddress,
                properties.rateLimit().resendVerification()
        );
    }

    private void check(Scope scope, String subject, IdentityProperties.Policy policy) {
        Instant now = clock.instant();
        AtomicReference<Decision> decision = new AtomicReference<>();
        BucketKey key = new BucketKey(scope, subject);

        windows.compute(key, (ignored, current) -> {
            if (current == null || !now.isBefore(current.resetAt())) {
                Window created = new Window(1, now.plus(policy.window()));
                decision.set(new Decision(true, created.resetAt()));
                return created;
            }
            if (current.attempts() >= policy.maxAttempts()) {
                decision.set(new Decision(false, current.resetAt()));
                return current;
            }

            Window incremented = new Window(current.attempts() + 1, current.resetAt());
            decision.set(new Decision(true, incremented.resetAt()));
            return incremented;
        });

        cleanupExpiredWindows(now);
        Decision result = decision.get();
        if (!result.allowed()) {
            throw new RateLimitExceededException(retryAfterSeconds(now, result.resetAt()));
        }
    }

    private void cleanupExpiredWindows(Instant now) {
        if (checks.incrementAndGet() % CLEANUP_INTERVAL == 0) {
            windows.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().resetAt()));
        }
    }

    private long retryAfterSeconds(Instant now, Instant resetAt) {
        long millis = Math.max(1, Duration.between(now, resetAt).toMillis());
        return Math.max(1, (millis + 999) / 1000);
    }

    private enum Scope {
        REGISTRATION,
        EMAIL_VERIFICATION,
        RESEND_VERIFICATION
    }

    private record BucketKey(Scope scope, String subject) {
    }

    private record Window(int attempts, Instant resetAt) {
    }

    private record Decision(boolean allowed, Instant resetAt) {
    }
}
