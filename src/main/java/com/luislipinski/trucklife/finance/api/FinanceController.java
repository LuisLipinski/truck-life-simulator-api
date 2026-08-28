package com.luislipinski.trucklife.finance.api;

import com.luislipinski.trucklife.career.domain.CareerGame;
import com.luislipinski.trucklife.finance.application.FinanceOperations;
import com.luislipinski.trucklife.identity.application.AccountAuthorization;
import com.luislipinski.trucklife.identity.application.AuthenticatedAccount;
import com.luislipinski.trucklife.identity.config.AccessTokenAuthenticationFilter;
import com.luislipinski.trucklife.identity.domain.UserRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path="/api/v1/careers/{careerId}/finances")
@Tag(name="Finances", description="Authenticated monthly expenses and emergency reserve")
@SecurityRequirement(name="bearerAuth")
public class FinanceController {
    private final AccountAuthorization authorization; private final FinanceOperations operations;
    public FinanceController(AccountAuthorization authorization,FinanceOperations operations){this.authorization=authorization;this.operations=operations;}

    @GetMapping(produces=MediaType.APPLICATION_JSON_VALUE) @Operation(summary="Get monthly expenses and emergency reserve")
    public ResponseEntity<FinanceResponse> get(@PathVariable UUID careerId,@RequestParam CareerGame game,HttpServletRequest request){var a=account(request);return ok(FinanceResponse.from(operations.get(a.userId(),game,careerId)));}

    @PostMapping(path="/monthly-expenses",consumes=MediaType.APPLICATION_JSON_VALUE,produces=MediaType.APPLICATION_JSON_VALUE) @Operation(summary="Create a custom monthly expense")
    public ResponseEntity<FinanceResponse> createExpense(@PathVariable UUID careerId,@RequestParam CareerGame game,@Valid @RequestBody FinanceRequests.CreateExpense body,HttpServletRequest request){var a=account(request);return created(FinanceResponse.from(operations.createCustomExpense(a.userId(),game,careerId,body.expectedOperationalWeek(),body.expectedPayrollMonth(),body.name(),body.amount(),body.included())),careerId,game);}

    @PatchMapping(path="/monthly-expenses/{expenseId}",consumes=MediaType.APPLICATION_JSON_VALUE,produces=MediaType.APPLICATION_JSON_VALUE) @Operation(summary="Edit or include/exclude a monthly expense")
    public ResponseEntity<FinanceResponse> updateExpense(@PathVariable UUID careerId,@PathVariable UUID expenseId,@RequestParam CareerGame game,@Valid @RequestBody FinanceRequests.UpdateExpense body,HttpServletRequest request){var a=account(request);return ok(FinanceResponse.from(operations.updateExpense(a.userId(),game,careerId,expenseId,body.expectedOperationalWeek(),body.expectedPayrollMonth(),body.name(),body.amount(),body.included())));}

    @DeleteMapping(path="/monthly-expenses/{expenseId}",produces=MediaType.APPLICATION_JSON_VALUE) @Operation(summary="Delete a custom monthly expense")
    public ResponseEntity<FinanceResponse> deleteExpense(@PathVariable UUID careerId,@PathVariable UUID expenseId,@RequestParam CareerGame game,@RequestParam Integer expectedOperationalWeek,@RequestParam(required=false) Integer expectedPayrollMonth,HttpServletRequest request){var a=account(request);return ok(FinanceResponse.from(operations.deleteExpense(a.userId(),game,careerId,expenseId,expectedOperationalWeek,expectedPayrollMonth)));}

    @PostMapping(path="/monthly-expense-applications",consumes=MediaType.APPLICATION_JSON_VALUE,produces=MediaType.APPLICATION_JSON_VALUE) @Operation(summary="Apply the currently included monthly expenses idempotently")
    public ResponseEntity<FinanceResponse> applyExpenses(@PathVariable UUID careerId,@RequestParam CareerGame game,@Valid @RequestBody FinanceRequests.ContextOperation body,HttpServletRequest request){var a=account(request);return created(FinanceResponse.from(operations.applyExpenses(a.userId(),game,careerId,body.operationId(),body.expectedOperationalWeek(),body.expectedPayrollMonth())),careerId,game);}

    @PostMapping(path="/emergency-reserve/deposits",consumes=MediaType.APPLICATION_JSON_VALUE,produces=MediaType.APPLICATION_JSON_VALUE) @Operation(summary="Transfer career balance into the emergency reserve")
    public ResponseEntity<FinanceResponse> deposit(@PathVariable UUID careerId,@RequestParam CareerGame game,@Valid @RequestBody FinanceRequests.ReserveTransfer body,HttpServletRequest request){var a=account(request);return created(FinanceResponse.from(operations.depositReserve(a.userId(),game,careerId,body.operationId(),body.expectedOperationalWeek(),body.expectedPayrollMonth(),body.amount())),careerId,game);}

    @PostMapping(path="/emergency-reserve/withdrawals",consumes=MediaType.APPLICATION_JSON_VALUE,produces=MediaType.APPLICATION_JSON_VALUE) @Operation(summary="Withdraw from the emergency reserve back to career balance")
    public ResponseEntity<FinanceResponse> withdraw(@PathVariable UUID careerId,@RequestParam CareerGame game,@Valid @RequestBody FinanceRequests.ReserveWithdrawal body,HttpServletRequest request){var a=account(request);return created(FinanceResponse.from(operations.withdrawReserve(a.userId(),game,careerId,body.operationId(),body.expectedOperationalWeek(),body.expectedPayrollMonth(),body.amount(),body.reason())),careerId,game);}

    @PatchMapping(path="/emergency-reserve/auto-contribution",consumes=MediaType.APPLICATION_JSON_VALUE,produces=MediaType.APPLICATION_JSON_VALUE) @Operation(summary="Configure the automatic reserve contribution applied by payslip generation")
    public ResponseEntity<FinanceResponse> configure(@PathVariable UUID careerId,@RequestParam CareerGame game,@Valid @RequestBody FinanceRequests.ReserveConfiguration body,HttpServletRequest request){var a=account(request);return ok(FinanceResponse.from(operations.configureAutoReserve(a.userId(),game,careerId,body.expectedOperationalWeek(),body.expectedPayrollMonth(),body.enabled(),body.amount())));}

    private AuthenticatedAccount account(HttpServletRequest request){AuthenticatedAccount account=(AuthenticatedAccount)request.getAttribute(AccessTokenAuthenticationFilter.AUTHENTICATED_ACCOUNT_ATTRIBUTE);authorization.requireAnyRole(account,UserRole.USER,UserRole.ADMIN);return account;}
    private ResponseEntity<FinanceResponse> ok(FinanceResponse body){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);}
    private ResponseEntity<FinanceResponse> created(FinanceResponse body,UUID careerId,CareerGame game){return ResponseEntity.created(java.net.URI.create("/api/v1/careers/"+careerId+"/finances?game="+game)).cacheControl(CacheControl.noStore()).body(body);}
}
