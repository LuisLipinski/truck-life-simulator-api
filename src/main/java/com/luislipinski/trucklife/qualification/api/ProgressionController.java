package com.luislipinski.trucklife.qualification.api;

import com.luislipinski.trucklife.career.domain.CareerGame;
import com.luislipinski.trucklife.identity.application.AccountAuthorization;
import com.luislipinski.trucklife.identity.application.AuthenticatedAccount;
import com.luislipinski.trucklife.identity.config.AccessTokenAuthenticationFilter;
import com.luislipinski.trucklife.identity.domain.UserRole;
import com.luislipinski.trucklife.qualification.application.ProgressionOperations;
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

@RestController
@RequestMapping(path = "/api/v1/careers/{careerId}/progression")
@Tag(name = "Progression", description = "Authenticated career levels, Driving Academy and dangerous-goods qualifications")
@SecurityRequirement(name = "bearerAuth")
public class ProgressionController {
    private final AccountAuthorization authorization;
    private final ProgressionOperations operations;

    public ProgressionController(AccountAuthorization authorization, ProgressionOperations operations) {
        this.authorization = authorization;
        this.operations = operations;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get authoritative career progression and qualification status")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Progression status"),
            @ApiResponse(responseCode = "401", description = "Access token missing or invalid"),
            @ApiResponse(responseCode = "404", description = "Career not found for this owner and game")
    })
    public ResponseEntity<ProgressionResponse> get(@PathVariable UUID careerId, @RequestParam CareerGame game,
                                                    HttpServletRequest request) {
        AuthenticatedAccount account = authorizedAccount(request);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ProgressionResponse.from(operations.get(account.userId(), game, careerId)));
    }

    @PostMapping(path = "/promotions", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Confirm the required Driving Academy module, pay the server-side fee and promote one level")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Promotion completed"),
            @ApiResponse(responseCode = "400", description = "Request validation failed", content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "Access token missing or invalid"),
            @ApiResponse(responseCode = "404", description = "Career not found for this owner and game"),
            @ApiResponse(responseCode = "409", description = "Distance, level, balance or policy requirement not satisfied")
    })
    public ResponseEntity<ProgressionResponse> promote(@PathVariable UUID careerId, @RequestParam CareerGame game,
                                                        @Valid @RequestBody PromotionRequest body,
                                                        HttpServletRequest request) {
        AuthenticatedAccount account = authorizedAccount(request);
        ProgressionResponse response = ProgressionResponse.from(operations.promote(account.userId(), game, careerId,
                body.expectedOperationalWeek(), body.expectedCurrentLevel(), body.targetLevel(), body.academyCompleted()));
        return ResponseEntity.created(URI.create("/api/v1/careers/" + careerId + "/progression?game=" + game))
                .cacheControl(CacheControl.noStore()).body(response);
    }

    @PostMapping(path = "/dangerous-goods", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Acquire the game-specific HazMat or ADR qualification using the server-side fee")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Qualification acquired"),
            @ApiResponse(responseCode = "400", description = "Request validation failed", content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "Access token missing or invalid"),
            @ApiResponse(responseCode = "404", description = "Career not found for this owner and game"),
            @ApiResponse(responseCode = "409", description = "Level, balance or policy requirement not satisfied")
    })
    public ResponseEntity<ProgressionResponse> acquireDangerousGoods(@PathVariable UUID careerId,
                                                                      @RequestParam CareerGame game,
                                                                      @Valid @RequestBody ProgressionActionRequest body,
                                                                      HttpServletRequest request) {
        AuthenticatedAccount account = authorizedAccount(request);
        ProgressionResponse response = ProgressionResponse.from(operations.acquireDangerousGoods(account.userId(), game,
                careerId, body.expectedOperationalWeek(), body.expectedCurrentLevel()));
        return ResponseEntity.created(URI.create("/api/v1/careers/" + careerId + "/progression?game=" + game))
                .cacheControl(CacheControl.noStore()).body(response);
    }

    private AuthenticatedAccount authorizedAccount(HttpServletRequest request) {
        AuthenticatedAccount account = (AuthenticatedAccount) request.getAttribute(
                AccessTokenAuthenticationFilter.AUTHENTICATED_ACCOUNT_ATTRIBUTE);
        authorization.requireAnyRole(account, UserRole.USER, UserRole.ADMIN);
        return account;
    }
}
