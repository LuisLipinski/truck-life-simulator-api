package com.luislipinski.trucklife.platform.domain;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public enum ModuleCatalog {
    IDENTITY("identity", "Identity, authentication and user access"),
    SUBSCRIPTION("subscription", "Plans, entitlements and subscription lifecycle"),
    CAREER("career", "Truck-driving careers and progression"),
    TRIP("trip", "Trips, routes and working-time records"),
    PAYROLL("payroll", "Payslips, earnings, taxes and deductions"),
    FINANCE("finance", "Expenses, reserves and financial balances"),
    QUALIFICATION("qualification", "Licences, training and qualifications"),
    INCIDENT("incident", "Incidents and operational consequences"),
    BACKUP("backup", "Career export, import and recovery"),
    AUDIT("audit", "Security and business audit trail");

    private final String name;
    private final String description;

    ModuleCatalog(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public String moduleName() {
        return name;
    }

    public String description() {
        return description;
    }

    public static List<ModuleCatalog> all() {
        return List.of(values());
    }

    public static Optional<ModuleCatalog> findByName(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }

        String normalizedName = name.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(module -> module.name.equals(normalizedName))
                .findFirst();
    }
}
