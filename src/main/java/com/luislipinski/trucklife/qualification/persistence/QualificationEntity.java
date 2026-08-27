package com.luislipinski.trucklife.qualification.persistence;

import com.luislipinski.trucklife.qualification.domain.QualificationType;
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
@Table(name = "qualifications")
public class QualificationEntity {
    @Id private UUID id;
    @Column(name = "career_id", nullable = false) private UUID careerId;
    @Enumerated(EnumType.STRING)
    @Column(name = "qualification_type", nullable = false, length = 20) private QualificationType type;
    @Column(name = "qualification_name", nullable = false, length = 80) private String name;
    @Column(name = "fee_amount", nullable = false, precision = 14, scale = 2) private BigDecimal feeAmount;
    @Column(name = "display_currency", nullable = false, length = 3) private String displayCurrency;
    @Column(name = "operational_week", nullable = false) private int operationalWeek;
    @Column(name = "policy_version", nullable = false, length = 60) private String policyVersion;
    @Column(name = "context_snapshot_json", nullable = false, columnDefinition = "text") private String contextSnapshotJson;
    @Column(name = "acquired_at", nullable = false) private Instant acquiredAt;

    protected QualificationEntity() {}

    public QualificationEntity(UUID id, UUID careerId, QualificationType type, String name, BigDecimal feeAmount,
                               String displayCurrency, int operationalWeek, String policyVersion,
                               String contextSnapshotJson, Instant acquiredAt) {
        this.id = id;
        this.careerId = careerId;
        this.type = type;
        this.name = name;
        this.feeAmount = feeAmount;
        this.displayCurrency = displayCurrency;
        this.operationalWeek = operationalWeek;
        this.policyVersion = policyVersion;
        this.contextSnapshotJson = contextSnapshotJson;
        this.acquiredAt = acquiredAt;
    }

    public UUID getId() { return id; }
    public UUID getCareerId() { return careerId; }
    public QualificationType getType() { return type; }
    public String getName() { return name; }
    public BigDecimal getFeeAmount() { return feeAmount; }
    public String getDisplayCurrency() { return displayCurrency; }
    public int getOperationalWeek() { return operationalWeek; }
    public String getPolicyVersion() { return policyVersion; }
    public String getContextSnapshotJson() { return contextSnapshotJson; }
    public Instant getAcquiredAt() { return acquiredAt; }
}
