package com.luislipinski.trucklife.identity.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.luislipinski.trucklife.identity.email.VerificationEmailDeliveryPort;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

class IdentityAccountServiceTest {

    private IdentityAccountWriter accountWriter;
    private IdentityRateLimiter rateLimiter;
    private VerificationEmailDeliveryPort emailDelivery;
    private PasswordEncoder passwordEncoder;
    private IdentityAccountService service;

    @BeforeEach
    void setUp() {
        accountWriter = mock(IdentityAccountWriter.class);
        rateLimiter = mock(IdentityRateLimiter.class);
        emailDelivery = mock(VerificationEmailDeliveryPort.class);
        passwordEncoder = mock(PasswordEncoder.class);
        service = new IdentityAccountService(accountWriter, rateLimiter, emailDelivery, passwordEncoder);
    }

    @Test
    void normalizesRegistrationAndUsesUnknownForMissingClientAddress() {
        PendingVerificationDelivery delivery = new PendingVerificationDelivery(
                "Driver@Example.com",
                "Road Driver",
                "raw-token",
                Instant.parse("2026-08-25T10:00:00Z")
        );
        when(passwordEncoder.encode("valid password")).thenReturn("encoded-password");
        when(accountWriter.createPendingAccount(
                "Driver@Example.com",
                "driver@example.com",
                "Road Driver",
                "encoded-password"
        )).thenReturn(Optional.of(delivery));

        service.register(" Driver@Example.com ", " Road Driver ", "valid password", null);

        verify(rateLimiter).checkRegistration("unknown");
        verify(emailDelivery).sendVerificationEmail(
                delivery.recipient(),
                delivery.displayName(),
                delivery.rawToken(),
                delivery.expiresAt()
        );
    }

    @Test
    void treatsAUniqueConstraintRaceAsANeutralRegistration() {
        when(passwordEncoder.encode("valid password")).thenReturn("encoded-password");
        when(accountWriter.createPendingAccount(
                "driver@example.com",
                "driver@example.com",
                "Road Driver",
                "encoded-password"
        )).thenThrow(new DataIntegrityViolationException(
                "duplicate account",
                new SQLException("duplicate normalized email", "23505")
        ));

        assertThatCode(() -> service.register(
                "driver@example.com",
                "Road Driver",
                "valid password",
                "203.0.113.10"
        )).doesNotThrowAnyException();

        verify(emailDelivery, never()).sendVerificationEmail(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(Instant.class)
        );
    }

    @Test
    void rethrowsDatabaseIntegrityFailuresThatAreNotUniqueViolations() {
        DataIntegrityViolationException failure = new DataIntegrityViolationException(
                "unexpected integrity failure",
                new SQLException("check constraint", "23514")
        );
        when(passwordEncoder.encode("valid password")).thenReturn("encoded-password");
        when(accountWriter.createPendingAccount(
                "driver@example.com",
                "driver@example.com",
                "Road Driver",
                "encoded-password"
        )).thenThrow(failure);

        assertThatThrownBy(() -> service.register(
                "driver@example.com",
                "Road Driver",
                "valid password",
                "203.0.113.10"
        )).isSameAs(failure);
    }

    @Test
    void rethrowsIntegrityFailuresWithoutASqlCause() {
        DataIntegrityViolationException failure = new DataIntegrityViolationException("unexpected integrity failure");
        when(passwordEncoder.encode("valid password")).thenReturn("encoded-password");
        when(accountWriter.createPendingAccount(
                "driver@example.com",
                "driver@example.com",
                "Road Driver",
                "encoded-password"
        )).thenThrow(failure);

        assertThatThrownBy(() -> service.register(
                "driver@example.com",
                "Road Driver",
                "valid password",
                "203.0.113.10"
        )).isSameAs(failure);
    }
}
