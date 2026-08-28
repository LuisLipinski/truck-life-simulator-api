package com.luislipinski.trucklife.finance.api;

import com.luislipinski.trucklife.career.domain.CareerGame;
import com.luislipinski.trucklife.finance.application.FinancialLedgerOperations;
import com.luislipinski.trucklife.identity.application.AccountAuthorization;
import com.luislipinski.trucklife.identity.application.AuthenticatedAccount;
import com.luislipinski.trucklife.identity.config.AccessTokenAuthenticationFilter;
import com.luislipinski.trucklife.identity.domain.UserRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

@RestController @Validated
@RequestMapping(path="/api/v1/careers/{careerId}/finances")
@Tag(name="Financial ledger",description="Immutable authenticated financial movement history")
@SecurityRequirement(name="bearerAuth")
public class FinancialLedgerController {
    private final AccountAuthorization authorization;private final FinancialLedgerOperations operations;private final ObjectMapper objectMapper;
    public FinancialLedgerController(AccountAuthorization authorization,FinancialLedgerOperations operations,ObjectMapper objectMapper){this.authorization=authorization;this.operations=operations;this.objectMapper=objectMapper;}

    @GetMapping(path="/ledger",produces=MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary="List immutable financial ledger entries")
    @ApiResponses({@ApiResponse(responseCode="200",description="Ledger history"),@ApiResponse(responseCode="401",description="Authentication required"),@ApiResponse(responseCode="404",description="Career not found")})
    public ResponseEntity<List<LedgerEntryResponse>> list(@PathVariable UUID careerId,@RequestParam CareerGame game,
            @RequestParam(defaultValue="100") @Min(1) @Max(500) int limit,HttpServletRequest request){AuthenticatedAccount a=account(request);List<LedgerEntryResponse> body=operations.list(a.userId(),game,careerId,limit).stream().map(e->LedgerEntryResponse.from(e,objectMapper)).toList();return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);}

    @PostMapping(path="/balance-adjustments",consumes=MediaType.APPLICATION_JSON_VALUE,produces=MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary="Record an idempotent audited manual balance correction")
    @ApiResponses({@ApiResponse(responseCode="201",description="Balance adjusted"),@ApiResponse(responseCode="400",description="Invalid request"),@ApiResponse(responseCode="401",description="Authentication required"),@ApiResponse(responseCode="404",description="Career not found"),@ApiResponse(responseCode="409",description="Stale balance or operational context")})
    public ResponseEntity<LedgerEntryResponse> adjust(@PathVariable UUID careerId,@RequestParam CareerGame game,@Valid @RequestBody BalanceAdjustmentRequest body,HttpServletRequest request){AuthenticatedAccount a=account(request);LedgerEntryResponse response=LedgerEntryResponse.from(operations.adjustBalance(a.userId(),game,careerId,body.operationId(),body.expectedOperationalWeek(),body.expectedPayrollMonth(),body.expectedBalance(),body.newBalance(),body.note()),objectMapper);return ResponseEntity.created(java.net.URI.create("/api/v1/careers/"+careerId+"/finances/ledger?game="+game)).cacheControl(CacheControl.noStore()).body(response);}

    private AuthenticatedAccount account(HttpServletRequest request){AuthenticatedAccount a=(AuthenticatedAccount)request.getAttribute(AccessTokenAuthenticationFilter.AUTHENTICATED_ACCOUNT_ATTRIBUTE);authorization.requireAnyRole(a,UserRole.USER,UserRole.ADMIN);return a;}
}
