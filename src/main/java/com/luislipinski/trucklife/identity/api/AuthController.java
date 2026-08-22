package com.luislipinski.trucklife.identity.api;

import com.luislipinski.trucklife.identity.application.IdentityAccountOperations;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1/auth")
@Tag(name = "Identity", description = "Account registration and e-mail verification")
public class AuthController {

    private final IdentityAccountOperations accountService;

    public AuthController(IdentityAccountOperations accountService) {
        this.accountService = accountService;
    }

    @PostMapping(
            path = "/register",
            consumes = MediaType.APPLICATION_JSON_VALUE
    )
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Register a pending account")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Registration request accepted"),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid registration data",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "429",
                    description = "Registration rate limit exceeded",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)
                    )
            )
    })
    public void register(
            @Valid @RequestBody RegistrationRequest request,
            HttpServletRequest servletRequest
    ) {
        accountService.register(
                request.email(),
                request.displayName(),
                request.password(),
                servletRequest.getRemoteAddr()
        );
    }

    @PostMapping(
            path = "/verify-email",
            consumes = MediaType.APPLICATION_JSON_VALUE
    )
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Verify an e-mail address with a one-time token")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "E-mail verified or already verified"),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid or expired verification token",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "429",
                    description = "Verification rate limit exceeded",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)
                    )
            )
    })
    public void verifyEmail(
            @Valid @RequestBody VerificationTokenRequest request,
            HttpServletRequest servletRequest
    ) {
        accountService.verifyEmail(request.token(), servletRequest.getRemoteAddr());
    }

    @PostMapping(
            path = "/resend-verification",
            consumes = MediaType.APPLICATION_JSON_VALUE
    )
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Request another e-mail verification message")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Resend request accepted"),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request data",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "429",
                    description = "Resend rate limit exceeded",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)
                    )
            )
    })
    public void resendVerification(
            @Valid @RequestBody ResendVerificationRequest request,
            HttpServletRequest servletRequest
    ) {
        accountService.resendVerification(request.email(), servletRequest.getRemoteAddr());
    }
}
