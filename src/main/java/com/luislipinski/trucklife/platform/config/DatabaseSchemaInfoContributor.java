package com.luislipinski.trucklife.platform.config;

import java.util.LinkedHashMap;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationInfoService;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.stereotype.Component;

@Component
public class DatabaseSchemaInfoContributor implements InfoContributor {

    private final Flyway flyway;

    public DatabaseSchemaInfoContributor(Flyway flyway) {
        this.flyway = flyway;
    }

    @Override
    public void contribute(Info.Builder builder) {
        MigrationInfoService migrationInfo = flyway.info();
        MigrationInfo current = migrationInfo.current();

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("currentVersion", currentVersion(current));
        details.put("pendingMigrations", migrationInfo.pending().length);
        builder.withDetail("databaseSchema", details);
    }

    private String currentVersion(MigrationInfo current) {
        if (current == null || current.getVersion() == null) {
            return "none";
        }
        return current.getVersion().getVersion();
    }
}
