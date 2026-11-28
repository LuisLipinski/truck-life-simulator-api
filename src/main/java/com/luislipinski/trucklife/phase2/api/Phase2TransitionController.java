package com.luislipinski.trucklife.phase2.api;

import com.luislipinski.trucklife.career.domain.CareerGame;
import com.luislipinski.trucklife.identity.application.AccountAuthorization;
import com.luislipinski.trucklife.identity.application.AuthenticatedAccount;
import com.luislipinski.trucklife.identity.config.AccessTokenAuthenticationFilter;
import com.luislipinski.trucklife.identity.domain.UserRole;
import com.luislipinski.trucklife.phase2.application.Phase2TransitionOperations;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1/careers/{careerId}/phase2-transition")
@Tag(name = "Phase 2 transition", description = "Mandatory employment exit and first carrier affiliation when entering Phase 2")
@SecurityRequirement(name = "bearerAuth")
public class Phase2TransitionController {
    private final AccountAuthorization authorization;
    private final Phase2TransitionOperations operations;

    public Phase2TransitionController(
            AccountAuthorization authorization,
            Phase2TransitionOperations operations
    ) {
        this.authorization = authorization;
        this.operations = operations;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Load or create the mandatory Phase 2 transition for an unlocked career")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Phase 2 transition state"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "404", description = "Career not found"),
            @ApiResponse(responseCode = "409", description = "Phase 2 is not unlocked")
    })
    public ResponseEntity<Phase2TransitionResponse> get(
            @PathVariable UUID careerId,
            @RequestParam CareerGame game,
            HttpServletRequest request
    ) {
        AuthenticatedAccount account = account(request);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(Phase2TransitionResponse.from(
                        operations.getOrCreate(account.userId(), game, careerId)
                ));
    }

    @PostMapping(
            path = "/complete",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(summary = "Complete the Phase 2 onboarding, settle the employee exit and create the first carrier contract")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Phase 2 transition completed"),
            @ApiResponse(responseCode = "400", description = "Transition or carrier data is invalid"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "404", description = "Career not found"),
            @ApiResponse(responseCode = "409", description = "Stale balance, locked Phase 2 or transition already completed")
    })
    public ResponseEntity<Phase2TransitionResponse> complete(
            @PathVariable UUID careerId,
            @RequestParam CareerGame game,
            @Valid @RequestBody CompletePhase2TransitionRequest body,
            HttpServletRequest request
    ) {
        AuthenticatedAccount account = account(request);
        var details = operations.complete(
                account.userId(),
                game,
                careerId,
                new Phase2TransitionOperations.CompleteCommand(
                        body.operationId(),
                        body.affiliation(),
                        body.carrierName(),
                        body.carrierCountryCode(),
                        body.carrierStateCode(),
                        body.effectiveDay(),
                        body.expectedBalance()
                )
        );
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(Phase2TransitionResponse.from(details));
    }

    private AuthenticatedAccount account(HttpServletRequest request) {
        AuthenticatedAccount account =
                (AuthenticatedAccount) request.getAttribute(AccessTokenAuthenticationFilter.AUTHENTICATED_ACCOUNT_ATTRIBUTE);
        authorization.requireAnyRole(account, UserRole.USER, UserRole.ADMIN);
        return account;
    }
}
