package com.luislipinski.trucklife.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.luislipinski.trucklife.identity.config.IdentityProperties;
import com.luislipinski.trucklife.shared.error.RateLimitExceededException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class IdentityRateLimiterTest {

    @Test
    void limitsEachSubjectAndAllowsRequestsAfterTheWindowResets() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-22T18:00:00Z"));
        IdentityProperties.Policy policy = new IdentityProperties.Policy(
                2,
                Duration.ofMinutes(1)
        );
        IdentityProperties properties = new IdentityProperties(
                Duration.ofHours(24),
                new IdentityProperties.RateLimits(policy, policy, policy, policy, policy)
        );
        IdentityRateLimiter limiter = new IdentityRateLimiter(properties, clock);

        limiter.checkRegistration("203.0.113.10");
        limiter.checkRegistration("203.0.113.10");

        assertThatThrownBy(() -> limiter.checkRegistration("203.0.113.10"))
                .isInstanceOfSatisfying(RateLimitExceededException.class, exception ->
                        assertThat(exception.retryAfterSeconds()).isEqualTo(60)
                );

        limiter.checkRegistration("203.0.113.11");
        clock.advance(Duration.ofSeconds(61));
        limiter.checkRegistration("203.0.113.10");
    }

    @Test
    void limitsLoginByBothAddressAndHashedEmailAndRefreshBySession() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-22T18:00:00Z"));
        IdentityProperties.Policy policy = new IdentityProperties.Policy(
                2,
                Duration.ofMinutes(1)
        );
        IdentityRateLimiter limiter = new IdentityRateLimiter(
                new IdentityProperties(
                        Duration.ofHours(24),
                        new IdentityProperties.RateLimits(
                                policy,
                                policy,
                                policy,
                                policy,
                                policy
                        )
                ),
                clock
        );

        limiter.checkLogin("email-hash-a", "203.0.113.10");
        limiter.checkLogin("email-hash-b", "203.0.113.10");
        assertThatThrownBy(() -> limiter.checkLogin("email-hash-c", "203.0.113.10"))
                .isInstanceOf(RateLimitExceededException.class);

        limiter.checkRefresh("family-a", "203.0.113.20");
        limiter.checkRefresh("family-a", "203.0.113.21");
        assertThatThrownBy(() -> limiter.checkRefresh("family-a", "203.0.113.22"))
                .isInstanceOf(RateLimitExceededException.class);
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
