package com.luislipinski.trucklife.payroll.api;

import com.luislipinski.trucklife.career.domain.CareerGame;
import com.luislipinski.trucklife.identity.application.AccountAuthorization;
import com.luislipinski.trucklife.identity.application.AuthenticatedAccount;
import com.luislipinski.trucklife.identity.config.AccessTokenAuthenticationFilter;
import com.luislipinski.trucklife.identity.domain.UserRole;
import com.luislipinski.trucklife.payroll.application.PayrollPreferencesService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1/careers/{careerId}/payslips")
@Tag(name = "Payslips", description = "Authenticated server-side payroll generation and history")
@SecurityRequirement(name = "bearerAuth")
public class PayrollPreferencesController {
    private final AccountAuthorization authorization;
    private final PayrollPreferencesService service;

    public PayrollPreferencesController(AccountAuthorization authorization, PayrollPreferencesService service) {
        this.authorization = authorization;
        this.service = service;
    }

    @GetMapping(path = "/settings", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get editable payroll preferences and policy defaults for the current operational period")
    public ResponseEntity<PayrollSettingsResponse> settings(@PathVariable UUID careerId,
                                                            @RequestParam CareerGame game,
                                                            HttpServletRequest request) {
        AuthenticatedAccount account = account(request);
        return ok(PayrollSettingsResponse.from(service.getSettings(account.userId(), game, careerId)));
    }

    @PatchMapping(path = "/settings", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Persist payroll preferences for future server-side calculations")
    public ResponseEntity<PayrollSettingsResponse> update(@PathVariable UUID careerId,
                                                          @RequestParam CareerGame game,
                                                          @Valid @RequestBody UpdatePayrollSettingsRequest body,
                                                          HttpServletRequest request) {
        AuthenticatedAccount account = account(request);
        return ok(PayrollSettingsResponse.from(service.updateSettings(
                account.userId(), game, careerId, body.expectedOperationalWeek(), body.expectedPayrollMonth(),
                body.level1Gross(), body.routeOverrunRate(), body.benefits(), body.perDiemRate()
        )));
    }

    @GetMapping(path = "/preview", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Preview the current payslip using persisted server-side data and payroll preferences")
    public ResponseEntity<PayrollPreviewResponse> preview(@PathVariable UUID careerId,
                                                          @RequestParam CareerGame game,
                                                          HttpServletRequest request) {
        AuthenticatedAccount account = account(request);
        return ok(PayrollPreviewResponse.from(service.preview(account.userId(), game, careerId)));
    }

    private AuthenticatedAccount account(HttpServletRequest request) {
        AuthenticatedAccount account = (AuthenticatedAccount) request.getAttribute(
                AccessTokenAuthenticationFilter.AUTHENTICATED_ACCOUNT_ATTRIBUTE);
        authorization.requireAnyRole(account, UserRole.USER, UserRole.ADMIN);
        return account;
    }

    private <T> ResponseEntity<T> ok(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
