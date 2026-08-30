package com.luislipinski.trucklife.backup.api;

import com.luislipinski.trucklife.backup.application.CareerImportRecoveryService;
import com.luislipinski.trucklife.backup.application.CareerImportService;
import com.luislipinski.trucklife.backup.application.CareerImportValidationService;
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
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1/careers/imports")
@Tag(name = "Career imports", description = "Migration of local ATS and ETS2 careers into the authenticated account")
@SecurityRequirement(name = "bearerAuth")
public class CareerImportController {

    private final AccountAuthorization authorization;
    private final CareerImportValidationService validationService;
    private final CareerImportService importService;
    private final CareerImportRecoveryService recoveryService;

    public CareerImportController(
            AccountAuthorization authorization,
            CareerImportValidationService validationService,
            CareerImportService importService,
            CareerImportRecoveryService recoveryService
    ) {
        this.authorization = authorization;
        this.validationService = validationService;
        this.importService = importService;
        this.recoveryService = recoveryService;
    }

    @PostMapping(
            path = "/validate",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(summary = "Validate a normalized local career snapshot before migration")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Snapshot accepted for the supported import contract"),
            @ApiResponse(responseCode = "400", description = "Snapshot is malformed, mixed between games or unsupported", content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)
            )),
            @ApiResponse(responseCode = "401", description = "Access token missing or invalid")
    })
    public ResponseEntity<CareerImportValidationResponse> validate(
            @Valid @RequestBody CareerImportValidationRequest request,
            HttpServletRequest servletRequest
    ) {
        authorizedAccount(servletRequest);
        CareerImportValidationResponse response = validationService.validate(request);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(response);
    }

    @PostMapping(
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(summary = "Import a supported local career snapshot transactionally and idempotently")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Local career imported into a new server-side career UUID"),
            @ApiResponse(responseCode = "200", description = "The same completed idempotent operation was replayed"),
            @ApiResponse(responseCode = "400", description = "Snapshot is invalid or unsupported", content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)
            )),
            @ApiResponse(responseCode = "401", description = "Access token missing or invalid"),
            @ApiResponse(responseCode = "409", description = "The operation id or local career identity conflicts with a prior import", content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)
            ))
    })
    public ResponseEntity<CareerImportResponse> importCareer(
            @Valid @RequestBody CareerImportValidationRequest request,
            HttpServletRequest servletRequest
    ) {
        AuthenticatedAccount account = authorizedAccount(servletRequest);
        CareerImportResponse response = importService.importCareer(account.userId(), request);
        HttpStatus status = response.idempotentReplay() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status)
                .cacheControl(CacheControl.noStore())
                .body(response);
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Recover an existing local-to-server career association without reimporting data")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Completed import association recovered for the authenticated owner"),
            @ApiResponse(responseCode = "400", description = "Lookup parameters are invalid", content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)
            )),
            @ApiResponse(responseCode = "401", description = "Access token missing or invalid"),
            @ApiResponse(responseCode = "404", description = "No import association exists for this owner, game and local career id", content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)
            )),
            @ApiResponse(responseCode = "409", description = "The matching import operation exists but is not completed", content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)
            ))
    })
    public ResponseEntity<CareerImportResponse> recover(
            @RequestParam CareerGame game,
            @RequestParam String sourceCareerId,
            HttpServletRequest servletRequest
    ) {
        AuthenticatedAccount account = authorizedAccount(servletRequest);
        CareerImportResponse response = recoveryService.recover(account.userId(), game, sourceCareerId);
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
