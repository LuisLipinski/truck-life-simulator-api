package com.luislipinski.trucklife.identity.config;

import com.luislipinski.trucklife.identity.email.VerificationEmailDeliveryPort;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({
        IdentityProperties.class,
        IdentitySessionProperties.class,
        IdentityWebProperties.class
})
public class IdentitySecurityConfiguration {

    static final String PASSWORD_ENCODING_ID = "argon2id-v1";
    private static final int ARGON2_MEMORY_KIB = 19 * 1024;

    @Bean
    Clock identityClock() {
        return Clock.systemUTC();
    }

    @Bean
    SecureRandom identitySecureRandom() {
        return new SecureRandom();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        PasswordEncoder argon2id = new Argon2PasswordEncoder(
                16,
                32,
                1,
                ARGON2_MEMORY_KIB,
                2
        );
        return new DelegatingPasswordEncoder(
                PASSWORD_ENCODING_ID,
                Map.of(PASSWORD_ENCODING_ID, argon2id)
        );
    }

    @Bean
    @ConditionalOnMissingBean(VerificationEmailDeliveryPort.class)
    VerificationEmailDeliveryPort verificationEmailDeliveryPort() {
        return new DiscardingVerificationEmailAdapter();
    }

    private static final class DiscardingVerificationEmailAdapter
            implements VerificationEmailDeliveryPort {

        @Override
        public void sendVerificationEmail(
                String recipient,
                String displayName,
                String rawToken,
                java.time.Instant expiresAt
        ) {
            // The real provider is intentionally selected in a later task.
            // Never log or otherwise expose the raw token in this safe fallback.
        }
    }
}
