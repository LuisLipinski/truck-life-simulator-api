package com.luislipinski.trucklife.finance.api;

import com.luislipinski.trucklife.career.domain.CareerGame;
import com.luislipinski.trucklife.finance.application.FinancingOperations;
import com.luislipinski.trucklife.finance.domain.FinancialProductType;
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
import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;
import java.net.URI;
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

@RestController @Validated
@RequestMapping(path="/api/v1/careers/{careerId}/financing")
@Tag(name="Financing",description="Server-side financing contracts, operational schedules and immutable payment history")
@SecurityRequirement(name="bearerAuth")
public class FinancingController {
    private final AccountAuthorization authorization;private final FinancingOperations operations;
    public FinancingController(AccountAuthorization authorization,FinancingOperations operations){this.authorization=authorization;this.operations=operations;}

    @GetMapping(path="/offers",produces=MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary="Calculate server-authoritative financing offers")
    @ApiResponses({@ApiResponse(responseCode="200",description="Available offers"),@ApiResponse(responseCode="401",description="Authentication required"),@ApiResponse(responseCode="404",description="Career not found"),@ApiResponse(responseCode="409",description="No researched policy for jurisdiction")})
    public ResponseEntity<List<FinancingOfferResponse>> offers(@PathVariable UUID careerId,@RequestParam CareerGame game,@RequestParam FinancialProductType productType,@RequestParam @DecimalMin("1.00") BigDecimal requestedAmount,HttpServletRequest request){AuthenticatedAccount a=account(request);List<FinancingOfferResponse> body=operations.offers(a.userId(),game,careerId,productType,requestedAmount).stream().map(FinancingOfferResponse::from).toList();return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);}

    @PostMapping(path="/contracts",consumes=MediaType.APPLICATION_JSON_VALUE,produces=MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary="Create an idempotent financing or personal-loan contract using server policy")
    @ApiResponses({@ApiResponse(responseCode="201",description="Contract created"),@ApiResponse(responseCode="400",description="Invalid request"),@ApiResponse(responseCode="401",description="Authentication required"),@ApiResponse(responseCode="404",description="Career not found"),@ApiResponse(responseCode="409",description="Stale context, balance or unavailable policy")})
    public ResponseEntity<FinancialContractResponse> create(@PathVariable UUID careerId,@RequestParam CareerGame game,@Valid @RequestBody CreateFinancingContractRequest body,HttpServletRequest request){AuthenticatedAccount a=account(request);FinancialContractResponse response=FinancialContractResponse.from(operations.create(a.userId(),game,careerId,new FinancingOperations.CreateContractCommand(body.operationId(),body.productType(),body.requestedAmount(),body.termPeriods(),body.expectedOperationalWeek(),body.expectedPayrollMonth(),body.expectedBalance())));return ResponseEntity.created(URI.create("/api/v1/careers/"+careerId+"/financing/contracts/"+response.id()+"?game="+game)).cacheControl(CacheControl.noStore()).body(response);}

    @GetMapping(path="/contracts",produces=MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary="List financing contracts for the authenticated career")
    public ResponseEntity<List<FinancialContractResponse>> list(@PathVariable UUID careerId,@RequestParam CareerGame game,HttpServletRequest request){AuthenticatedAccount a=account(request);return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(operations.list(a.userId(),game,careerId).stream().map(FinancialContractResponse::from).toList());}

    @GetMapping(path="/contracts/{contractId}",produces=MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary="Read one financing contract with schedule, payments and contract events")
    public ResponseEntity<FinancialContractResponse> get(@PathVariable UUID careerId,@PathVariable UUID contractId,@RequestParam CareerGame game,HttpServletRequest request){AuthenticatedAccount a=account(request);return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(FinancialContractResponse.from(operations.get(a.userId(),game,careerId,contractId)));}

    @PostMapping(path="/contracts/{contractId}/payments",consumes=MediaType.APPLICATION_JSON_VALUE,produces=MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary="Record an idempotent manual, extra-principal or payoff payment")
    @ApiResponses({@ApiResponse(responseCode="200",description="Payment applied"),@ApiResponse(responseCode="400",description="Invalid payment"),@ApiResponse(responseCode="401",description="Authentication required"),@ApiResponse(responseCode="404",description="Career or contract not found"),@ApiResponse(responseCode="409",description="Stale context, balance or debt state")})
    public ResponseEntity<FinancialContractResponse> pay(@PathVariable UUID careerId,@PathVariable UUID contractId,@RequestParam CareerGame game,@Valid @RequestBody FinancingPaymentRequest body,HttpServletRequest request){AuthenticatedAccount a=account(request);return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(FinancialContractResponse.from(operations.pay(a.userId(),game,careerId,contractId,new FinancingOperations.PaymentCommand(body.operationId(),body.paymentType(),body.amount(),body.expectedOperationalWeek(),body.expectedPayrollMonth(),body.expectedBalance()))));}

    private AuthenticatedAccount account(HttpServletRequest request){AuthenticatedAccount a=(AuthenticatedAccount)request.getAttribute(AccessTokenAuthenticationFilter.AUTHENTICATED_ACCOUNT_ATTRIBUTE);authorization.requireAnyRole(a,UserRole.USER,UserRole.ADMIN);return a;}
}
