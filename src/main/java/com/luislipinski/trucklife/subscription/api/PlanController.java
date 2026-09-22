package com.luislipinski.trucklife.subscription.api;

import com.luislipinski.trucklife.subscription.application.EntitlementOperations;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1/plans")
@Tag(name = "Plans", description = "Public Free and Premium plan capabilities")
public class PlanController {
    private final EntitlementOperations entitlements;

    public PlanController(EntitlementOperations entitlements) {
        this.entitlements = entitlements;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "List active plans and their server-authoritative features")
    public ResponseEntity<List<PlanResponse>> plans() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(entitlements.plans().stream().map(PlanResponse::from).toList());
    }
}
