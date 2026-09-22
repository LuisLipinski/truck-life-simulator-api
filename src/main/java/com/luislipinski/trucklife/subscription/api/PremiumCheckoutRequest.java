package com.luislipinski.trucklife.subscription.api;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record PremiumCheckoutRequest(
        @NotNull UUID operationId
) {}
