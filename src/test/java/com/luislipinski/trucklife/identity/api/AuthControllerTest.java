package com.luislipinski.trucklife.identity.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.luislipinski.trucklife.identity.application.IdentityAccountOperations;
import com.luislipinski.trucklife.shared.error.ApiExceptionHandler;
import com.luislipinski.trucklife.shared.error.RateLimitExceededException;
import com.luislipinski.trucklife.shared.observability.CorrelationIdFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@Import({
        ApiExceptionHandler.class,
        CorrelationIdFilter.class,
        AuthControllerTest.AccountOperationsTestConfiguration.class
})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StubIdentityAccountOperations accountService;

    @BeforeEach
    void resetAccountOperations() {
        accountService.reset();
    }

    @Test
    void acceptsTrimmedRegistrationDataAndAWhitespacePassword() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": " Driver@Example.com ",
                                  "displayName": " Road Driver ",
                                  "password": "            "
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(content().string(""));

        assertThat(accountService.registration())
                .isEqualTo(new RegistrationCall(
                        "Driver@Example.com",
                        "Road Driver",
                        "            ",
                        "127.0.0.1"
                ));
    }

    @Test
    void rejectsPasswordsShorterThanTwelveUnicodeCharacters() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "driver@example.com",
                                  "displayName": "Road Driver",
                                  "password": "🚚🚚🚚🚚🚚🚚🚚🚚🚚🚚🚚"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.violations[0].field").value("password"));
    }

    @Test
    void mapsRateLimitToProblemDetailsAndRetryAfter() throws Exception {
        accountService.failWith(new RateLimitExceededException(42));

        mockMvc.perform(post("/api/v1/auth/resend-verification")
                        .header(CorrelationIdFilter.HEADER_NAME, "rate-limit-request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "driver@example.com"}
                                """))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string("Retry-After", "42"))
                .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.correlationId").value("rate-limit-request"));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class AccountOperationsTestConfiguration {

        @Bean
        StubIdentityAccountOperations stubIdentityAccountOperations() {
            return new StubIdentityAccountOperations();
        }
    }

    static final class StubIdentityAccountOperations implements IdentityAccountOperations {

        private RegistrationCall registration;
        private RuntimeException failure;

        @Override
        public void register(
                String email,
                String displayName,
                String rawPassword,
                String clientAddress
        ) {
            throwIfConfigured();
            registration = new RegistrationCall(email, displayName, rawPassword, clientAddress);
        }

        @Override
        public void verifyEmail(String rawToken, String clientAddress) {
            throwIfConfigured();
        }

        @Override
        public void resendVerification(String email, String clientAddress) {
            throwIfConfigured();
        }

        RegistrationCall registration() {
            return registration;
        }

        void failWith(RuntimeException exception) {
            failure = exception;
        }

        void reset() {
            registration = null;
            failure = null;
        }

        private void throwIfConfigured() {
            if (failure != null) {
                throw failure;
            }
        }
    }

    private record RegistrationCall(
            String email,
            String displayName,
            String rawPassword,
            String clientAddress
    ) {
    }
}
