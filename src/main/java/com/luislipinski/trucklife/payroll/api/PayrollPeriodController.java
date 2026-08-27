package com.luislipinski.trucklife.payroll.api;

import com.luislipinski.trucklife.career.domain.CareerGame;
import com.luislipinski.trucklife.identity.application.AccountAuthorization;
import com.luislipinski.trucklife.identity.application.AuthenticatedAccount;
import com.luislipinski.trucklife.identity.config.AccessTokenAuthenticationFilter;
import com.luislipinski.trucklife.identity.domain.UserRole;
import com.luislipinski.trucklife.payroll.application.PayrollPeriodOperations;
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
@RequestMapping(path = "/api/v1/careers/{careerId}/payroll-periods")
@Tag(name = "Payroll periods", description = "Authenticated operational payroll period closing")
@SecurityRequirement(name = "bearerAuth")
public class PayrollPeriodController {

    private final AccountAuthorization authorization;
    private final PayrollPeriodOperations payrollPeriodOperations;
    private final ObjectMapper objectMapper;

    public PayrollPeriodController(
            AccountAuthorization authorization,
            PayrollPeriodOperations payrollPeriodOperations,
            ObjectMapper objectMapper
    ) {
        this.authorization = authorization;
        this.payrollPeriodOperations = payrollPeriodOperations;
        this.objectMapper = objectMapper;
    }

    @PostMapping(
            path = "/close",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(summary = "Close the current ETS2 operational week without issuing a payslip")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Operational week closed"),
            @ApiResponse(responseCode = "400", description = "Close request is invalid", content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ProblemDetail.class)
            )),
            @ApiResponse(responseCode = "401", description = "Access token missing or invalid"),
            @ApiResponse(responseCode = "404", description = "Career not found for this owner and game"),
            @ApiResponse(responseCode = "409", description = "Week changed, ATS close requested, or month limit reached")
    })
    public ResponseEntity<PayrollPeriodResponse> close(
            @PathVariable("careerId") UUID careerId,
            @RequestParam(name = "game") CareerGame game,
            @Valid @RequestBody ClosePayrollPeriodRequest request,
            HttpServletRequest servletRequest
    ) {
        AuthenticatedAccount account = authorizedAccount(servletRequest);
        PayrollPeriodResponse response = PayrollPeriodResponse.from(
                payrollPeriodOperations.close(
                        account.userId(),
                        game,
                        careerId,
                        request.expectedOperationalWeek()
                ),
                game,
                objectMapper
        );
        URI location = URI.create(
                "/api/v1/careers/" + careerId + "/payroll-periods/" + response.id() + "?game=" + game
        );
        return ResponseEntity.created(location)
                .cacheControl(CacheControl.noStore())
                .body(response);
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "List closed operational payroll periods for the owner career")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Closed payroll periods"),
            @ApiResponse(responseCode = "401", description = "Access token missing or invalid"),
            @ApiResponse(responseCode = "404", description = "Career not found for this owner and game")
    })
    public ResponseEntity<List<PayrollPeriodResponse>> list(
            @PathVariable("careerId") UUID careerId,
            @RequestParam(name = "game") CareerGame game,
            HttpServletRequest servletRequest
    ) {
        AuthenticatedAccount account = authorizedAccount(servletRequest);
        List<PayrollPeriodResponse> response = payrollPeriodOperations.list(
                        account.userId(),
                        game,
                        careerId
                ).stream()
                .map(period -> PayrollPeriodResponse.from(period, game, objectMapper))
                .toList();
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
