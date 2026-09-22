package com.luislipinski.trucklife.subscription.api;

import com.luislipinski.trucklife.identity.application.AccountAuthorization;
import com.luislipinski.trucklife.identity.application.AuthenticatedAccount;
import com.luislipinski.trucklife.identity.config.AccessTokenAuthenticationFilter;
import com.luislipinski.trucklife.identity.domain.UserRole;
import com.luislipinski.trucklife.subscription.application.EntitlementOperations;
import com.luislipinski.trucklife.subscription.application.PaymentOperations;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1/me/subscription")
@Tag(name = "Subscriptions", description = "Current account subscription and payment history")
@SecurityRequirement(name = "bearerAuth")
public class AccountSubscriptionController {

    private final AccountAuthorization authorization;
    private final EntitlementOperations entitlements;
    private final PaymentOperations payments;

    public AccountSubscriptionController(
            AccountAuthorization authorization,
            EntitlementOperations entitlements,
            PaymentOperations payments
    ) {
        this.authorization = authorization;
        this.entitlements = entitlements;
        this.payments = payments;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Return current subscription state and owner payment history")
    public ResponseEntity<AccountSubscriptionResponse> current(HttpServletRequest request) {
        AuthenticatedAccount account = authorizedAccount(request);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(AccountSubscriptionResponse.from(
                        entitlements.entitlements(account.userId()),
                        payments.currentSubscription(account.userId()),
                        payments.payments(account.userId())
                ));
    }

    private AuthenticatedAccount authorizedAccount(HttpServletRequest request) {
        AuthenticatedAccount account = (AuthenticatedAccount) request.getAttribute(
                AccessTokenAuthenticationFilter.AUTHENTICATED_ACCOUNT_ATTRIBUTE
        );
        authorization.requireAnyRole(account, UserRole.USER, UserRole.ADMIN);
        return account;
    }
}
