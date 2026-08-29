package com.luislipinski.trucklife.backup.api;

import com.luislipinski.trucklife.backup.application.CareerImportValidationService;
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
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1/careers/imports")
@Tag(name = "Career imports", description = "Migration of local ATS and ETS2 careers into the authenticated account")
@SecurityRequirement(name = "bearerAuth")
public class CareerImportController {

    private final AccountAuthorization authorization;
    private final CareerImportValidationService validationService;

    public CareerImportController(
            AccountAuthorization authorization,
            CareerImportValidationService validationService
    ) {
        this.authorization = authorization;
        this.validationService = validationService;
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

    private AuthenticatedAccount authorizedAccount(HttpServletRequest request) {
        AuthenticatedAccount account = (AuthenticatedAccount) request.getAttribute(
                AccessTokenAuthenticationFilter.AUTHENTICATED_ACCOUNT_ATTRIBUTE
        );
        authorization.requireAnyRole(account, UserRole.USER, UserRole.ADMIN);
        return account;
    }
}
