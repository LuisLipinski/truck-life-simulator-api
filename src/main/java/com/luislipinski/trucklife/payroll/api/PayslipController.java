package com.luislipinski.trucklife.payroll.api;

import com.luislipinski.trucklife.career.domain.CareerGame;
import com.luislipinski.trucklife.identity.application.AccountAuthorization;
import com.luislipinski.trucklife.identity.application.AuthenticatedAccount;
import com.luislipinski.trucklife.identity.config.AccessTokenAuthenticationFilter;
import com.luislipinski.trucklife.identity.domain.UserRole;
import com.luislipinski.trucklife.payroll.application.PayslipOperations;
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
@RequestMapping(path = "/api/v1/careers/{careerId}/payslips")
@Tag(name = "Payslips", description = "Authenticated server-side payroll generation and history")
@SecurityRequirement(name = "bearerAuth")
public class PayslipController {
    private final AccountAuthorization authorization;
    private final PayslipOperations payslipOperations;
    private final ObjectMapper objectMapper;

    public PayslipController(AccountAuthorization authorization, PayslipOperations payslipOperations, ObjectMapper objectMapper) {
        this.authorization = authorization;
        this.payslipOperations = payslipOperations;
        this.objectMapper = objectMapper;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Generate the current ATS weekly or ETS2 monthly payslip")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Payslip generated and deposited"),
            @ApiResponse(responseCode = "400", description = "Generation request is invalid", content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "Access token missing or invalid"),
            @ApiResponse(responseCode = "404", description = "Career not found for this owner and game"),
            @ApiResponse(responseCode = "409", description = "Operational period changed or is not ready")
    })
    public ResponseEntity<PayslipResponse> generate(@PathVariable("careerId") UUID careerId,
                                                     @RequestParam(name = "game") CareerGame game,
                                                     @Valid @RequestBody GeneratePayslipRequest request,
                                                     HttpServletRequest servletRequest) {
        AuthenticatedAccount account = authorizedAccount(servletRequest);
        PayslipResponse response = PayslipResponse.from(payslipOperations.generate(account.userId(), game, careerId,
                request.expectedOperationalWeek(), request.expectedPayrollMonth()), objectMapper);
        URI location = URI.create("/api/v1/careers/" + careerId + "/payslips/" + response.id() + "?game=" + game);
        return ResponseEntity.created(location).cacheControl(CacheControl.noStore()).body(response);
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "List immutable payslips for the owner career")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Payslip history"),
            @ApiResponse(responseCode = "401", description = "Access token missing or invalid"),
            @ApiResponse(responseCode = "404", description = "Career not found for this owner and game")
    })
    public ResponseEntity<List<PayslipResponse>> list(@PathVariable("careerId") UUID careerId,
                                                       @RequestParam(name = "game") CareerGame game,
                                                       HttpServletRequest servletRequest) {
        AuthenticatedAccount account = authorizedAccount(servletRequest);
        List<PayslipResponse> response = payslipOperations.list(account.userId(), game, careerId).stream()
                .map(result -> PayslipResponse.from(result, objectMapper)).toList();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(response);
    }

    @GetMapping(path = "/{payslipId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get an immutable payslip for the owner career")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Payslip"),
            @ApiResponse(responseCode = "401", description = "Access token missing or invalid"),
            @ApiResponse(responseCode = "404", description = "Career or payslip not found")
    })
    public ResponseEntity<PayslipResponse> get(@PathVariable("careerId") UUID careerId,
                                                @PathVariable("payslipId") UUID payslipId,
                                                @RequestParam(name = "game") CareerGame game,
                                                HttpServletRequest servletRequest) {
        AuthenticatedAccount account = authorizedAccount(servletRequest);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(PayslipResponse.from(payslipOperations.get(account.userId(), game, careerId, payslipId), objectMapper));
    }

    private AuthenticatedAccount authorizedAccount(HttpServletRequest request) {
        AuthenticatedAccount account = (AuthenticatedAccount) request.getAttribute(
                AccessTokenAuthenticationFilter.AUTHENTICATED_ACCOUNT_ATTRIBUTE);
        authorization.requireAnyRole(account, UserRole.USER, UserRole.ADMIN);
        return account;
    }
}
