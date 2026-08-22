package com.luislipinski.trucklife.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.luislipinski.trucklife.identity.config.IdentitySessionProperties;
import com.luislipinski.trucklife.identity.domain.UserRole;
import com.luislipinski.trucklife.identity.domain.UserStatus;
import com.luislipinski.trucklife.identity.persistence.UserEntity;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class JwtAccessTokenIssuerTest {

    private static final String SECRET =
            "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

    @Test
    void signsAndValidatesTheMinimalHs256Claims() {
        Instant now = Instant.parse("2026-08-22T18:00:00Z");
        MutableClock clock = new MutableClock(now);
        JwtAccessTokenIssuer issuer = issuer(clock, "truck-life-test");
        UserEntity user = activeUser(now);
        UUID sessionId = UUID.randomUUID();

        JwtAccessTokenIssuer.IssuedAccessToken issued = issuer.issue(user, sessionId);
        JwtAccessTokenIssuer.DecodedAccessToken decoded = issuer.decodeAndValidate(
                issued.token()
        );

        assertThat(issued.token().split("\\.")).hasSize(3);
        assertThat(issued.expiresAt()).isEqualTo(now.plus(Duration.ofMinutes(10)));
        assertThat(decoded.userId()).isEqualTo(user.getId());
        assertThat(decoded.sessionId()).isEqualTo(sessionId);
        assertThat(decoded.role()).isEqualTo(UserRole.USER);
        assertThat(decoded.emailVerified()).isTrue();
        assertThat(decoded.issuer()).isEqualTo("https://api.test.example");
        assertThat(decoded.audience()).isEqualTo("truck-life-test");
        assertThat(decoded.issuedAt()).isEqualTo(now);
        assertThat(decoded.expiresAt()).isEqualTo(now.plus(Duration.ofMinutes(10)));
        assertThat(decoded.tokenId()).isNotNull();
        assertThat(issued.toString()).doesNotContain(issued.token());
    }

    @Test
    void rejectsTamperingWrongAudienceAndExpiration() {
        Instant now = Instant.parse("2026-08-22T18:00:00Z");
        MutableClock clock = new MutableClock(now);
        JwtAccessTokenIssuer issuer = issuer(clock, "truck-life-test");
        String token = issuer.issue(activeUser(now), UUID.randomUUID()).token();
        String[] parts = token.split("\\.");
        char replacement = parts[2].charAt(0) == 'A' ? 'B' : 'A';
        String tampered = parts[0] + '.' + parts[1] + '.'
                + replacement + parts[2].substring(1);

        assertThatThrownBy(() -> issuer.decodeAndValidate(tampered))
                .isInstanceOf(JwtAccessTokenIssuer.InvalidAccessTokenException.class);
        assertThatThrownBy(() -> issuer(clock, "another-audience").decodeAndValidate(token))
                .isInstanceOf(JwtAccessTokenIssuer.InvalidAccessTokenException.class);

        clock.advance(Duration.ofMinutes(11));
        assertThatThrownBy(() -> issuer.decodeAndValidate(token))
                .isInstanceOf(JwtAccessTokenIssuer.InvalidAccessTokenException.class);
    }

    private JwtAccessTokenIssuer issuer(Clock clock, String audience) {
        return new JwtAccessTokenIssuer(
                new ObjectMapper(),
                new IdentitySessionProperties(
                        Duration.ofMinutes(10),
                        Duration.ofDays(30),
                        SECRET,
                        "https://api.test.example",
                        audience,
                        "TLS_REFRESH_TOKEN",
                        "/api/v1/auth"
                ),
                clock
        );
    }

    private UserEntity activeUser(Instant now) {
        return new UserEntity(
                UUID.randomUUID(),
                "driver@example.com",
                "driver@example.com",
                "{argon2id-v1}test",
                "Road Driver",
                UserStatus.ACTIVE,
                UserRole.USER,
                true,
                now,
                now,
                now,
                null
        );
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
