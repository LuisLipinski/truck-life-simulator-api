package com.luislipinski.trucklife.career.api;

import com.luislipinski.trucklife.career.application.CareerOperations;
import com.luislipinski.trucklife.career.domain.CareerGame;
import com.luislipinski.trucklife.identity.application.AccountAuthorization;
import com.luislipinski.trucklife.identity.application.AuthenticatedAccount;
import com.luislipinski.trucklife.identity.config.AccessTokenAuthenticationFilter;
import com.luislipinski.trucklife.identity.domain.UserRole;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1/careers")
@Tag(name = "Careers", description = "Authenticated ATS and ETS2 careers")
@SecurityRequirement(name = "bearerAuth")
public class CareerController {

    private final AccountAuthorization authorization;
    private final CareerOperations careerOperations;

    public CareerController(AccountAuthorization authorization, CareerOperations careerOperations) {
        this.authorization = authorization;
        this.careerOperations = careerOperations;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create a career owned by the authenticated account")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Career created"),
            @ApiResponse(responseCode = "400", description = "Career data is invalid", content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)
            )),
            @ApiResponse(responseCode = "401", description = "Access token missing or invalid"),
            @ApiResponse(responseCode = "409", description = "Free career limit reached")
    })
    public ResponseEntity<CareerResponse> create(
            @Valid @RequestBody CreateCareerRequest request,
            HttpServletRequest servletRequest
    ) {
        AuthenticatedAccount account = authorizedAccount(servletRequest);
        CareerResponse response = CareerResponse.from(
                careerOperations.create(account.userId(), request.toCommand())
        );
        URI location = URI.create(
                "/api/v1/careers/" + response.id() + "?game=" + response.game()
        );
        return ResponseEntity.created(location)
                .cacheControl(CacheControl.noStore())
                .body(response);
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "List the authenticated account careers for one game")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Owner careers for the selected game"),
            @ApiResponse(responseCode = "400", description = "Game context missing or invalid"),
            @ApiResponse(responseCode = "401", description = "Access token missing or invalid")
    })
    public ResponseEntity<List<CareerResponse>> list(
            @RequestParam(name = "game") CareerGame game,
            HttpServletRequest servletRequest
    ) {
        AuthenticatedAccount account = authorizedAccount(servletRequest);
        List<CareerResponse> response = careerOperations.list(account.userId(), game).stream()
                .map(CareerResponse::from)
                .toList();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(response);
    }

    @GetMapping(path = "/{careerId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Return one owner career in the selected game")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Owner career"),
            @ApiResponse(responseCode = "401", description = "Access token missing or invalid"),
            @ApiResponse(responseCode = "404", description = "Career not found for this owner and game")
    })
    public ResponseEntity<CareerResponse> get(
            @PathVariable("careerId") UUID careerId,
            @RequestParam(name = "game") CareerGame game,
            HttpServletRequest servletRequest
    ) {
        AuthenticatedAccount account = authorizedAccount(servletRequest);
        CareerResponse response = CareerResponse.from(
                careerOperations.get(account.userId(), game, careerId)
        );
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(response);
    }

    @PatchMapping(
            path = "/{careerId}",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(summary = "Update the owner career profile with optimistic locking")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Career profile updated"),
            @ApiResponse(responseCode = "400", description = "Profile data is invalid"),
            @ApiResponse(responseCode = "401", description = "Access token missing or invalid"),
            @ApiResponse(responseCode = "404", description = "Career not found for this owner and game"),
            @ApiResponse(responseCode = "409", description = "Career version is stale")
    })
    public ResponseEntity<CareerResponse> updateProfile(
            @PathVariable("careerId") UUID careerId,
            @RequestParam(name = "game") CareerGame game,
            @Valid @RequestBody UpdateCareerProfileRequest request,
            HttpServletRequest servletRequest
    ) {
        AuthenticatedAccount account = authorizedAccount(servletRequest);
        CareerResponse response = CareerResponse.from(careerOperations.updateProfile(
                account.userId(),
                game,
                careerId,
                request.toCommand()
        ));
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
