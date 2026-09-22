package com.luislipinski.trucklife.subscription.api;

import com.luislipinski.trucklife.identity.application.AccountAuthorization;
import com.luislipinski.trucklife.identity.application.AuthenticatedAccount;
import com.luislipinski.trucklife.identity.config.AccessTokenAuthenticationFilter;
import com.luislipinski.trucklife.identity.domain.UserRole;
import com.luislipinski.trucklife.subscription.application.PaymentOperations;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1/subscriptions")
@Tag(name = "Subscriptions", description = "Premium subscription checkout operations")
@SecurityRequirement(name = "bearerAuth")
public class SubscriptionCheckoutController {

    private final AccountAuthorization authorization;
    private final PaymentOperations payments;

    public SubscriptionCheckoutController(AccountAuthorization authorization, PaymentOperations payments) {
        this.authorization = authorization;
        this.payments = payments;
    }

    @PostMapping(
            path = "/checkout",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(summary = "Create an idempotent Premium PIX checkout using the server-side plan price")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "PIX checkout created"),
            @ApiResponse(responseCode = "200", description = "Existing checkout replayed idempotently"),
            @ApiResponse(responseCode = "401", description = "Access token missing or invalid"),
            @ApiResponse(responseCode = "409", description = "Premium price or subscription state prevents checkout"),
            @ApiResponse(responseCode = "503", description = "Payment provider is not configured or unavailable")
    })
    public ResponseEntity<PremiumCheckoutResponse> checkout(
            @Valid @RequestBody PremiumCheckoutRequest request,
            HttpServletRequest servletRequest
    ) {
        AuthenticatedAccount account = authorizedAccount(servletRequest);
        PaymentOperations.CheckoutResult result = payments.checkoutPremium(
                account.userId(),
                account.email(),
                request.operationId()
        );
        PremiumCheckoutResponse response = new PremiumCheckoutResponse(
                result.idempotentReplay(),
                PaymentOrderResponse.from(result.order())
        );
        return ResponseEntity.status(result.idempotentReplay() ? HttpStatus.OK : HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(response);
    }

    private AuthenticatedAccount authorizedAccount(HttpServletRequest request) {
        AuthenticatedAccount account = (AuthenticatedAccount) request.getAttribute(
                AccessTokenAuthenticationFilter.AUTHENTICATED_ACCOUNT_ATTRIBUTE
        );
        authorization.requireAnyRole(account, UserRole.USER, UserRole.ADMIN);
        return account;
    }
}
