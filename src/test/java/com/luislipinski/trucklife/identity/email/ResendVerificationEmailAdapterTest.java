package com.luislipinski.trucklife.identity.email;

import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ResendVerificationEmailAdapterTest {

    private static final String API_KEY = "re_test_key_not_secret";
    private static final String TEST_RECIPIENT = "delivered@resend.dev";
    private static final String FRONTEND = "https://luislipinski.github.io/truck-life-simulator";

    @Test
    void sendsVerificationToResendTestRecipientWithoutExposingRealRecipient() {
        TestContext context = context(true);
        String token = "a".repeat(43);

        context.server().expect(once(), requestTo("https://api.resend.com/emails"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + API_KEY))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"to\":[\"delivered@resend.dev\"]")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("real@example.com"))))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/#/verify-email?token=" + token)))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("email-verification")))
                .andRespond(withSuccess("{\"id\":\"test-email-id\"}", MediaType.APPLICATION_JSON));

        context.adapter().sendVerificationEmail(
                "real@example.com",
                "Driver <One>",
                token,
                Instant.parse("2026-08-24T12:00:00Z")
        );

        context.server().verify();
    }

    @Test
    void sendsPasswordResetToRealRecipientWhenTestModeIsDisabled() {
        TestContext context = context(false);
        String token = "b".repeat(43);

        context.server().expect(once(), requestTo("https://api.resend.com/emails"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"to\":[\"real@example.com\"]")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/#/reset-password?token=" + token)))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("password-reset")))
                .andRespond(withSuccess("{\"id\":\"test-email-id\"}", MediaType.APPLICATION_JSON));

        context.adapter().sendPasswordResetEmail(
                "real@example.com",
                "Driver Two",
                token,
                Instant.parse("2026-08-23T13:00:00Z")
        );

        context.server().verify();
    }

    @Test
    void requiresApiKeyWhenResendIsEnabled() {
        ResendEmailProperties properties = new ResendEmailProperties(
                "resend",
                " ",
                null,
                null,
                true,
                null
        );

        org.assertj.core.api.Assertions.assertThatThrownBy(properties::requiredApiKey)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RESEND_API_KEY");
    }

    private TestContext context(boolean testMode) {
        ResendEmailProperties properties = new ResendEmailProperties(
                "resend",
                API_KEY,
                "Truck Life Simulator <onboarding@resend.dev>",
                FRONTEND,
                testMode,
                TEST_RECIPIENT
        );
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://api.resend.com")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.requiredApiKey());
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        return new TestContext(
                server,
                new ResendVerificationEmailAdapter(builder.build(), properties)
        );
    }

    private record TestContext(
            MockRestServiceServer server,
            ResendVerificationEmailAdapter adapter
    ) {
    }
}
