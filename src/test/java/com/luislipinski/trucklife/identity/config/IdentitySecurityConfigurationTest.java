package com.luislipinski.trucklife.identity.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class IdentitySecurityConfigurationTest {

    @Test
    void encodesNewPasswordsWithVersionedArgon2idParameters() {
        PasswordEncoder encoder = new IdentitySecurityConfiguration().passwordEncoder();
        String rawPassword = "correct horse battery staple";

        String encoded = encoder.encode(rawPassword);

        assertThat(encoded)
                .startsWith("{" + IdentitySecurityConfiguration.PASSWORD_ENCODING_ID + "}")
                .contains("$argon2id$v=19$m=19456,t=2,p=1$")
                .doesNotContain(rawPassword);
        assertThat(encoder.matches(rawPassword, encoded)).isTrue();
        assertThat(encoder.matches("incorrect password", encoded)).isFalse();
        assertThat(encoder.upgradeEncoding(encoded)).isFalse();
    }
}
