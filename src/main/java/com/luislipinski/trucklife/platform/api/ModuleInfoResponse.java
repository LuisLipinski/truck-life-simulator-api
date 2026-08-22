package com.luislipinski.trucklife.platform.api;

import com.luislipinski.trucklife.platform.domain.ModuleCatalog;

public record ModuleInfoResponse(String name, String description) {

    static ModuleInfoResponse from(ModuleCatalog module) {
        return new ModuleInfoResponse(module.moduleName(), module.description());
    }
}
