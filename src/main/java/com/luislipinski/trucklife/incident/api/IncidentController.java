package com.luislipinski.trucklife.incident.api;

import com.luislipinski.trucklife.career.domain.CareerGame;
import com.luislipinski.trucklife.identity.application.AccountAuthorization;
import com.luislipinski.trucklife.identity.application.AuthenticatedAccount;
import com.luislipinski.trucklife.identity.config.AccessTokenAuthenticationFilter;
import com.luislipinski.trucklife.identity.domain.UserRole;
import com.luislipinski.trucklife.incident.application.IncidentOperations;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
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
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1/careers/{careerId}/incidents")
@Tag(name = "Incidents", description = "Authenticated career infractions, accidents and financial occurrences")
@SecurityRequirement(name = "bearerAuth")
public class IncidentController {

    private final AccountAuthorization authorization;
    private final IncidentOperations incidentOperations;

    public IncidentController(
            AccountAuthorization authorization,
            IncidentOperations incidentOperations
    ) {
        this.authorization = authorization;
        this.incidentOperations = incidentOperations;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Register a career incident without inventing gameplay calendar dates")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Incident registered"),
            @ApiResponse(responseCode = "400", description = "Incident request is invalid", content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)
            )),
            @ApiResponse(responseCode = "401", description = "Access token missing or invalid"),
            @ApiResponse(responseCode = "404", description = "Career or related trip not found"),
            @ApiResponse(responseCode = "409", description = "Operational week changed")
    })
    public ResponseEntity<IncidentResponse> create(
            @PathVariable("careerId") UUID careerId,
            @RequestParam(name = "game") CareerGame game,
            @Valid @RequestBody CreateIncidentRequest request,
            HttpServletRequest servletRequest
    ) {
        AuthenticatedAccount account = authorizedAccount(servletRequest);
        IncidentResponse response = IncidentResponse.from(
                incidentOperations.create(
                        account.userId(),
                        game,
                        careerId,
                        request.expectedOperationalWeek(),
                        request.type(),
                        request.amount(),
                        request.relatedTripId(),
                        request.route(),
                        request.description(),
                        request.chargeMethod()
                ),
                game
        );
        URI location = URI.create(
                "/api/v1/careers/" + careerId + "/incidents/" + response.id() + "?game=" + game
        );
        return ResponseEntity.created(location)
                .cacheControl(CacheControl.noStore())
                .body(response);
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "List incidents for the owner career")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Incident history"),
            @ApiResponse(responseCode = "401", description = "Access token missing or invalid"),
            @ApiResponse(responseCode = "404", description = "Career not found for this owner and game")
    })
    public ResponseEntity<List<IncidentResponse>> list(
            @PathVariable("careerId") UUID careerId,
            @RequestParam(name = "game") CareerGame game,
            HttpServletRequest servletRequest
    ) {
        AuthenticatedAccount account = authorizedAccount(servletRequest);
        List<IncidentResponse> response = incidentOperations.list(account.userId(), game, careerId)
                .stream()
                .map(result -> IncidentResponse.from(result, game))
                .toList();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(response);
    }

    @GetMapping(path = "/{incidentId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get one incident and its payslip deductions")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Incident"),
            @ApiResponse(responseCode = "401", description = "Access token missing or invalid"),
            @ApiResponse(responseCode = "404", description = "Career or incident not found")
    })
    public ResponseEntity<IncidentResponse> get(
            @PathVariable("careerId") UUID careerId,
            @PathVariable("incidentId") UUID incidentId,
            @RequestParam(name = "game") CareerGame game,
            HttpServletRequest servletRequest
    ) {
        AuthenticatedAccount account = authorizedAccount(servletRequest);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(IncidentResponse.from(
                        incidentOperations.get(account.userId(), game, careerId, incidentId),
                        game
                ));
    }

    @DeleteMapping(path = "/{incidentId}")
    @Operation(summary = "Cancel an untouched incident that is still pending for a future payslip")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Pending incident cancelled"),
            @ApiResponse(responseCode = "401", description = "Access token missing or invalid"),
            @ApiResponse(responseCode = "404", description = "Career or incident not found"),
            @ApiResponse(responseCode = "409", description = "Incident was already charged or partially deducted")
    })
    public ResponseEntity<Void> cancel(
            @PathVariable("careerId") UUID careerId,
            @PathVariable("incidentId") UUID incidentId,
            @RequestParam(name = "game") CareerGame game,
            HttpServletRequest servletRequest
    ) {
        AuthenticatedAccount account = authorizedAccount(servletRequest);
        incidentOperations.cancel(account.userId(), game, careerId, incidentId);
        return ResponseEntity.noContent()
                .cacheControl(CacheControl.noStore())
                .build();
    }

    private AuthenticatedAccount authorizedAccount(HttpServletRequest request) {
        AuthenticatedAccount account = (AuthenticatedAccount) request.getAttribute(
                AccessTokenAuthenticationFilter.AUTHENTICATED_ACCOUNT_ATTRIBUTE
        );
        authorization.requireAnyRole(account, UserRole.USER, UserRole.ADMIN);
        return account;
    }
}
