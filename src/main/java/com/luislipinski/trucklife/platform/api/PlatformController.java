package com.luislipinski.trucklife.platform.api;

import com.luislipinski.trucklife.platform.domain.ModuleCatalog;
import com.luislipinski.trucklife.shared.error.ResourceNotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1/platform", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Platform", description = "Backend capability and module discovery")
public class PlatformController {

    @GetMapping
    @Operation(summary = "Describe the API platform")
    public PlatformInfoResponse platform() {
        return new PlatformInfoResponse(
                "truck-life-simulator-api",
                "UP",
                "v1",
                ModuleCatalog.all().size(),
                Instant.now()
        );
    }

    @GetMapping("/modules")
    @Operation(summary = "List the bounded modules planned for the modular monolith")
    public List<ModuleInfoResponse> modules() {
        return ModuleCatalog.all().stream()
                .map(ModuleInfoResponse::from)
                .toList();
    }

    @GetMapping("/modules/{name}")
    @Operation(summary = "Describe one bounded module")
    public ModuleInfoResponse module(@PathVariable String name) {
        return ModuleCatalog.findByName(name)
                .map(ModuleInfoResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "MODULE_NOT_FOUND",
                        "Module '%s' was not found".formatted(name)
                ));
    }
}
