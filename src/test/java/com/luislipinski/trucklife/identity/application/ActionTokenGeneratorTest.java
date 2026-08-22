package com.luislipinski.trucklife.identity.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.SecureRandom;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class ActionTokenGeneratorTest {

    @Test
    void generatesIndependentUrlSafe256BitTokensAndTheirSha256Hashes() {
        ActionTokenGenerator generator = new ActionTokenGenerator(new SecureRandom());

        ActionTokenGenerator.GeneratedActionToken first = generator.generate();
        ActionTokenGenerator.GeneratedActionToken second = generator.generate();

        assertThat(first.rawToken()).matches("[A-Za-z0-9_-]{43}");
        assertThat(Base64.getUrlDecoder().decode(first.rawToken())).hasSize(32);
        assertThat(first.rawToken()).isNotEqualTo(second.rawToken());
        assertThat(first.tokenHash())
                .isEqualTo(TokenDigests.sha256(first.rawToken()))
                .matches("[0-9a-f]{64}");
        assertThat(first.toString()).doesNotContain(first.rawToken(), first.tokenHash());
    }
}
