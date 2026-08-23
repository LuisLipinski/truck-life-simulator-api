package com.luislipinski.trucklife.identity.email;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

class ResendEmailConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(ResendEmailConfiguration.class)
            .withPropertyValues(
                    "identity.email.provider=resend",
                    "identity.email.resend-api-key=re_test_key",
                    "identity.email.test-mode=true"
            );

    @Test
    void startsResendContextWithoutRequiringExternalRestClientBuilderBean() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasBean("resendRestClient");
            assertThat(context).hasSingleBean(RestClient.class);
            assertThat(context).hasSingleBean(VerificationEmailDeliveryPort.class);
        });
    }
}
