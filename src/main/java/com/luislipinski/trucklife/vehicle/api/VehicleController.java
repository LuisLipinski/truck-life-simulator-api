package com.luislipinski.trucklife.vehicle.api;

import com.luislipinski.trucklife.career.domain.CareerGame;
import com.luislipinski.trucklife.identity.application.AccountAuthorization;
import com.luislipinski.trucklife.identity.application.AuthenticatedAccount;
import com.luislipinski.trucklife.identity.config.AccessTokenAuthenticationFilter;
import com.luislipinski.trucklife.identity.domain.UserRole;
import com.luislipinski.trucklife.vehicle.application.VehicleOperations;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
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
@RequestMapping(path = "/api/v1/careers/{careerId}/vehicles")
@Tag(name = "Career vehicles", description = "Owned truck acquisition and career phase progression")
@SecurityRequirement(name = "bearerAuth")
public class VehicleController {
    private final AccountAuthorization authorization;
    private final VehicleOperations operations;

    public VehicleController(AccountAuthorization authorization, VehicleOperations operations) {
        this.authorization = authorization;
        this.operations = operations;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "List owned trucks for the authenticated career")
    public ResponseEntity<List<VehicleResponse>> list(
            @PathVariable UUID careerId,
            @RequestParam CareerGame game,
            HttpServletRequest request
    ) {
        AuthenticatedAccount account = account(request);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(operations.list(account.userId(), game, careerId).stream().map(VehicleResponse::from).toList());
    }

    @PostMapping(path = "/acquisitions", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Acquire the first owned truck by cash purchase or server-authoritative financing")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Truck acquired and Phase 2 unlocked"),
            @ApiResponse(responseCode = "200", description = "Idempotent replay of an existing acquisition"),
            @ApiResponse(responseCode = "400", description = "Invalid truck or acquisition data"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "404", description = "Career not found"),
            @ApiResponse(responseCode = "409", description = "Stale career context, insufficient balance or truck already acquired")
    })
    public ResponseEntity<VehicleResponse> acquire(
            @PathVariable UUID careerId,
            @RequestParam CareerGame game,
            @Valid @RequestBody AcquireVehicleRequest body,
            HttpServletRequest request
    ) {
        AuthenticatedAccount account = account(request);
        var replay = operations.list(account.userId(), game, careerId).stream()
                .filter(item -> item.vehicle().getAcquisitionOperationId().equals(body.operationId()))
                .findFirst();
        var details = operations.acquire(
                account.userId(),
                game,
                careerId,
                new VehicleOperations.AcquireVehicleCommand(
                        body.operationId(),
                        body.acquisitionType(),
                        body.make(),
                        body.model(),
                        body.modelYear(),
                        body.initialOdometer(),
                        body.purchasePrice(),
                        body.financingTermPeriods(),
                        body.expectedOperationalWeek(),
                        body.expectedPayrollMonth(),
                        body.expectedBalance()
                )
        );
        VehicleResponse response = VehicleResponse.from(details);
        if (replay.isPresent()) {
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(response);
        }
        return ResponseEntity.created(
                        URI.create("/api/v1/careers/" + careerId + "/vehicles/" + response.id() + "?game=" + game)
                )
                .cacheControl(CacheControl.noStore())
                .body(response);
    }

    private AuthenticatedAccount account(HttpServletRequest request) {
        AuthenticatedAccount account =
                (AuthenticatedAccount) request.getAttribute(AccessTokenAuthenticationFilter.AUTHENTICATED_ACCOUNT_ATTRIBUTE);
        authorization.requireAnyRole(account, UserRole.USER, UserRole.ADMIN);
        return account;
    }
}
