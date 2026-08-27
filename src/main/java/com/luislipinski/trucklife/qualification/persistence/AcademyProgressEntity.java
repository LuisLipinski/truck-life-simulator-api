package com.luislipinski.trucklife.qualification.persistence;

import com.luislipinski.trucklife.qualification.domain.AcademyModuleCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "academy_progress")
public class AcademyProgressEntity {
    @Id private UUID id;
    @Column(name = "career_id", nullable = false) private UUID careerId;
    @Column(name = "target_level", nullable = false) private short targetLevel;
    @Enumerated(EnumType.STRING)
    @Column(name = "module_code", nullable = false, length = 60) private AcademyModuleCode moduleCode;
    @Column(name = "module_name", nullable = false, length = 160) private String moduleName;
    @Column(name = "required_distance", nullable = false, precision = 14, scale = 2) private BigDecimal requiredDistance;
    @Column(name = "distance_at_completion", nullable = false, precision = 14, scale = 2) private BigDecimal distanceAtCompletion;
    @Column(name = "fee_amount", nullable = false, precision = 14, scale = 2) private BigDecimal feeAmount;
    @Column(name = "display_currency", nullable = false, length = 3) private String displayCurrency;
    @Column(name = "operational_week", nullable = false) private int operationalWeek;
    @Column(name = "policy_version", nullable = false, length = 60) private String policyVersion;
    @Column(name = "context_snapshot_json", nullable = false, columnDefinition = "text") private String contextSnapshotJson;
    @Column(name = "completed_at", nullable = false) private Instant completedAt;

    protected AcademyProgressEntity() {}

    public AcademyProgressEntity(UUID id, UUID careerId, short targetLevel, AcademyModuleCode moduleCode,
                                 String moduleName, BigDecimal requiredDistance, BigDecimal distanceAtCompletion,
                                 BigDecimal feeAmount, String displayCurrency, int operationalWeek,
                                 String policyVersion, String contextSnapshotJson, Instant completedAt) {
        this.id = id;
        this.careerId = careerId;
        this.targetLevel = targetLevel;
        this.moduleCode = moduleCode;
        this.moduleName = moduleName;
        this.requiredDistance = requiredDistance;
        this.distanceAtCompletion = distanceAtCompletion;
        this.feeAmount = feeAmount;
        this.displayCurrency = displayCurrency;
        this.operationalWeek = operationalWeek;
        this.policyVersion = policyVersion;
        this.contextSnapshotJson = contextSnapshotJson;
        this.completedAt = completedAt;
    }

    public UUID getId() { return id; }
    public UUID getCareerId() { return careerId; }
    public short getTargetLevel() { return targetLevel; }
    public AcademyModuleCode getModuleCode() { return moduleCode; }
    public String getModuleName() { return moduleName; }
    public BigDecimal getRequiredDistance() { return requiredDistance; }
    public BigDecimal getDistanceAtCompletion() { return distanceAtCompletion; }
    public BigDecimal getFeeAmount() { return feeAmount; }
    public String getDisplayCurrency() { return displayCurrency; }
    public int getOperationalWeek() { return operationalWeek; }
    public String getPolicyVersion() { return policyVersion; }
    public String getContextSnapshotJson() { return contextSnapshotJson; }
    public Instant getCompletedAt() { return completedAt; }
}
