package com.luislipinski.trucklife.identity.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.luislipinski.trucklife.identity.domain.UserRole;
import com.luislipinski.trucklife.identity.domain.UserStatus;
import com.luislipinski.trucklife.shared.error.ApiProblemException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class AccountAuthorizationTest {

    private final AccountAuthorization authorization = new AccountAuthorization();

    @Test
    void allowsAnAccountWhenAnyRequiredRoleMatches() {
        AuthenticatedAccount account = account(UserRole.ADMIN);

        assertThatCode(() -> authorization.requireAnyRole(account, UserRole.USER, UserRole.ADMIN))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsAnAuthenticatedAccountWithoutAnyRequiredRole() {
        AuthenticatedAccount account = account(UserRole.USER);

        assertThatThrownBy(() -> authorization.requireAnyRole(account, UserRole.ADMIN))
                .isInstanceOf(ApiProblemException.class)
                .satisfies(exception -> {
                    ApiProblemException problem = (ApiProblemException) exception;
                    org.assertj.core.api.Assertions.assertThat(problem.status()).isEqualTo(HttpStatus.FORBIDDEN);
                    org.assertj.core.api.Assertions.assertThat(problem.code()).isEqualTo("FORBIDDEN");
                });
    }

    @Test
    void rejectsMissingAuthenticationWithoutEvaluatingRoles() {
        assertThatThrownBy(() -> authorization.requireAnyRole(null, UserRole.USER, UserRole.ADMIN))
                .isInstanceOf(ApiProblemException.class)
                .hasMessageContaining("does not have permission");
    }

    private AuthenticatedAccount account(UserRole role) {
        return new AuthenticatedAccount(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "driver@example.com",
                "Road Driver",
                role,
                UserStatus.ACTIVE,
                true,
                Instant.now(),
                Instant.now()
        );
    }
}
