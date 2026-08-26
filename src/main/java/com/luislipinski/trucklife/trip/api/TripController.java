package com.luislipinski.trucklife.trip.api;

import com.luislipinski.trucklife.career.domain.CareerGame;
import com.luislipinski.trucklife.identity.application.AccountAuthorization;
import com.luislipinski.trucklife.identity.application.AuthenticatedAccount;
import com.luislipinski.trucklife.identity.config.AccessTokenAuthenticationFilter;
import com.luislipinski.trucklife.identity.domain.UserRole;
import com.luislipinski.trucklife.trip.application.TripOperations;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

@RestController
@RequestMapping(path = "/api/v1/careers/{careerId}/trips")
@Tag(name = "Trips", description = "Authenticated career trips")
@SecurityRequirement(name = "bearerAuth")
public class TripController {

    private final AccountAuthorization authorization;
    private final TripOperations tripOperations;
    private final ObjectMapper objectMapper;

    public TripController(
            AccountAuthorization authorization,
            TripOperations tripOperations,
            ObjectMapper objectMapper
    ) {
        this.authorization = authorization;
        this.tripOperations = tripOperations;
        this.objectMapper = objectMapper;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create a manual trip in the career current operational week")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Trip created"),
            @ApiResponse(responseCode = "400", description = "Trip data is invalid", content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)
            )),
            @ApiResponse(responseCode = "401", description = "Access token missing or invalid"),
            @ApiResponse(responseCode = "404", description = "Career not found for this owner and game")
    })
    public ResponseEntity<TripResponse> create(
            @PathVariable("careerId") UUID careerId,
            @RequestParam(name = "game") CareerGame game,
            @Valid @RequestBody CreateTripRequest request,
            HttpServletRequest servletRequest
    ) {
        AuthenticatedAccount account = authorizedAccount(servletRequest);
        TripResponse response = TripResponse.from(
                tripOperations.create(account.userId(), game, careerId, request.toCommand()),
                game,
                objectMapper
        );
        URI location = URI.create(
                "/api/v1/careers/" + careerId + "/trips/" + response.id() + "?game=" + game
        );
        return ResponseEntity.created(location)
                .cacheControl(CacheControl.noStore())
                .body(response);
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "List owner trips, optionally filtered by operational week")
    public ResponseEntity<List<TripResponse>> list(
            @PathVariable("careerId") UUID careerId,
            @RequestParam(name = "game") CareerGame game,
            @RequestParam(name = "operationalWeek", required = false) Integer operationalWeek,
            HttpServletRequest servletRequest
    ) {
        AuthenticatedAccount account = authorizedAccount(servletRequest);
        List<TripResponse> response = tripOperations.list(
                        account.userId(), game, careerId, operationalWeek
                ).stream()
                .map(trip -> TripResponse.from(trip, game, objectMapper))
                .toList();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(response);
    }

    @GetMapping(path = "/{tripId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Return one owner trip in the selected game")
    public ResponseEntity<TripResponse> get(
            @PathVariable("careerId") UUID careerId,
            @PathVariable("tripId") UUID tripId,
            @RequestParam(name = "game") CareerGame game,
            HttpServletRequest servletRequest
    ) {
        AuthenticatedAccount account = authorizedAccount(servletRequest);
        TripResponse response = TripResponse.from(
                tripOperations.get(account.userId(), game, careerId, tripId),
                game,
                objectMapper
        );
        return ResponseEntity.ok()
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
