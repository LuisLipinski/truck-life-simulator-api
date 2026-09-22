package com.luislipinski.trucklife.subscription.api;

import com.luislipinski.trucklife.identity.application.AccountAuthorization;
import com.luislipinski.trucklife.identity.application.AuthenticatedAccount;
import com.luislipinski.trucklife.identity.config.AccessTokenAuthenticationFilter;
import com.luislipinski.trucklife.identity.domain.UserRole;
import com.luislipinski.trucklife.subscription.application.EntitlementOperations;
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
@RequestMapping(path = "/api/v1/me/entitlements")
@Tag(name = "Entitlements", description = "Current account plan and server-authoritative feature access")
@SecurityRequirement(name = "bearerAuth")
public class EntitlementsController {
    private final AccountAuthorization authorization;
    private final EntitlementOperations entitlements;

    public EntitlementsController(AccountAuthorization authorization, EntitlementOperations entitlements) {
        this.authorization = authorization;
        this.entitlements = entitlements;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Return the current Free or Premium entitlements")
    public ResponseEntity<EntitlementsResponse> current(HttpServletRequest request) {
        AuthenticatedAccount account = (AuthenticatedAccount) request.getAttribute(
                AccessTokenAuthenticationFilter.AUTHENTICATED_ACCOUNT_ATTRIBUTE
        );
        authorization.requireAnyRole(account, UserRole.USER, UserRole.ADMIN);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(EntitlementsResponse.from(entitlements.entitlements(account.userId())));
    }
}
