package com.luislipinski.trucklife.subscription.api;

import com.luislipinski.trucklife.identity.application.AccountAuthorization;
import com.luislipinski.trucklife.identity.application.AuthenticatedAccount;
import com.luislipinski.trucklife.identity.config.AccessTokenAuthenticationFilter;
import com.luislipinski.trucklife.identity.domain.UserRole;
import com.luislipinski.trucklife.subscription.application.PaymentOperations;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1/payments")
@Tag(name = "Payments", description = "Owner-scoped payment status")
@SecurityRequirement(name = "bearerAuth")
public class PaymentController {

    private final AccountAuthorization authorization;
    private final PaymentOperations payments;

    public PaymentController(AccountAuthorization authorization, PaymentOperations payments) {
        this.authorization = authorization;
        this.payments = payments;
    }

    @GetMapping(path = "/{paymentOrderId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Return an authenticated account payment order")
    public ResponseEntity<PaymentOrderResponse> payment(
            @PathVariable UUID paymentOrderId,
            HttpServletRequest request
    ) {
        AuthenticatedAccount account = authorizedAccount(request);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(PaymentOrderResponse.from(payments.payment(account.userId(), paymentOrderId)));
    }

    private AuthenticatedAccount authorizedAccount(HttpServletRequest request) {
        AuthenticatedAccount account = (AuthenticatedAccount) request.getAttribute(
                AccessTokenAuthenticationFilter.AUTHENTICATED_ACCOUNT_ATTRIBUTE
        );
        authorization.requireAnyRole(account, UserRole.USER, UserRole.ADMIN);
        return account;
    }
}
