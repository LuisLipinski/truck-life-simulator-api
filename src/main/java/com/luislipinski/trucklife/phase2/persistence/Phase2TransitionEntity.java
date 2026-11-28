package com.luislipinski.trucklife.phase2.persistence;

import com.luislipinski.trucklife.phase2.domain.Phase2AffiliationChoice;
import com.luislipinski.trucklife.phase2.domain.Phase2TransitionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "phase2_transitions")
public class Phase2TransitionEntity {
    @Id private UUID id;
    @Column(name = "career_id", nullable = false, unique = true) private UUID careerId;
    @Column(name = "completion_operation_id", unique = true) private UUID completionOperationId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Phase2TransitionStatus status;
    @Column(name = "previous_company_name", nullable = false, length = 160) private String previousCompanyName;
    @Column(name = "termination_reason", nullable = false, length = 40) private String terminationReason;
    @Column(name = "termination_country_code", length = 10) private String terminationCountryCode;
    @Column(name = "termination_state_code", length = 10) private String terminationStateCode;
    @Column(name = "employment_start_operational_week", nullable = false) private int employmentStartOperationalWeek;
    @Column(name = "termination_operational_week", nullable = false) private int terminationOperationalWeek;
    @Enumerated(EnumType.STRING) @Column(name = "termination_effective_day", length = 9) private DayOfWeek terminationEffectiveDay;
    @Column(name = "statutory_settlement_amount", nullable = false, precision = 14, scale = 2) private BigDecimal statutorySettlementAmount;
    @Column(name = "display_currency", nullable = false, length = 3) private String displayCurrency;
    @Column(name = "policy_version", nullable = false, length = 100) private String policyVersion;
    @Column(name = "policy_source", nullable = false, length = 500) private String policySource;
    @Column(name = "policy_summary", nullable = false, columnDefinition = "text") private String policySummary;
    @Column(name = "policy_snapshot_json", nullable = false, columnDefinition = "text") private String policySnapshotJson;
    @Enumerated(EnumType.STRING) @Column(name = "selected_affiliation", length = 30) private Phase2AffiliationChoice selectedAffiliation;
    @Column(name = "completed_at") private Instant completedAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version @Column(nullable = false) private long version;

    protected Phase2TransitionEntity() {}

    public Phase2TransitionEntity(
            UUID id,
            UUID careerId,
            String previousCompanyName,
            String terminationCountryCode,
            String terminationStateCode,
            int employmentStartOperationalWeek,
            int terminationOperationalWeek,
            BigDecimal statutorySettlementAmount,
            String displayCurrency,
            String policyVersion,
            String policySource,
            String policySummary,
            String policySnapshotJson,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.careerId = careerId;
        this.status = Phase2TransitionStatus.PENDING;
        this.previousCompanyName = previousCompanyName;
        this.terminationReason = "VOLUNTARY_PHASE2_TRANSITION";
        this.terminationCountryCode = terminationCountryCode;
        this.terminationStateCode = terminationStateCode;
        this.employmentStartOperationalWeek = employmentStartOperationalWeek;
        this.terminationOperationalWeek = terminationOperationalWeek;
        this.statutorySettlementAmount = statutorySettlementAmount;
        this.displayCurrency = displayCurrency;
        this.policyVersion = policyVersion;
        this.policySource = policySource;
        this.policySummary = policySummary;
        this.policySnapshotJson = policySnapshotJson;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public void refreshSettlement(
            BigDecimal amount,
            String policyVersion,
            String policySource,
            String policySummary,
            String policySnapshotJson,
            Instant now
    ) {
        if (status == Phase2TransitionStatus.COMPLETED) return;
        this.statutorySettlementAmount = amount;
        this.policyVersion = policyVersion;
        this.policySource = policySource;
        this.policySummary = policySummary;
        this.policySnapshotJson = policySnapshotJson;
        this.updatedAt = now;
    }

    public void complete(UUID operationId, DayOfWeek effectiveDay, Phase2AffiliationChoice affiliation, Instant now) {
        if (status == Phase2TransitionStatus.COMPLETED) {
            if (!operationId.equals(completionOperationId)) {
                throw new IllegalStateException("Phase 2 transition is already completed");
            }
            return;
        }
        this.completionOperationId = operationId;
        this.terminationEffectiveDay = effectiveDay;
        this.selectedAffiliation = affiliation;
        this.status = Phase2TransitionStatus.COMPLETED;
        this.completedAt = now;
        this.updatedAt = now;
    }

    public UUID getId(){return id;}
    public UUID getCareerId(){return careerId;}
    public UUID getCompletionOperationId(){return completionOperationId;}
    public Phase2TransitionStatus getStatus(){return status;}
    public String getPreviousCompanyName(){return previousCompanyName;}
    public String getTerminationReason(){return terminationReason;}
    public String getTerminationCountryCode(){return terminationCountryCode;}
    public String getTerminationStateCode(){return terminationStateCode;}
    public int getEmploymentStartOperationalWeek(){return employmentStartOperationalWeek;}
    public int getTerminationOperationalWeek(){return terminationOperationalWeek;}
    public DayOfWeek getTerminationEffectiveDay(){return terminationEffectiveDay;}
    public BigDecimal getStatutorySettlementAmount(){return statutorySettlementAmount;}
    public String getDisplayCurrency(){return displayCurrency;}
    public String getPolicyVersion(){return policyVersion;}
    public String getPolicySource(){return policySource;}
    public String getPolicySummary(){return policySummary;}
    public String getPolicySnapshotJson(){return policySnapshotJson;}
    public Phase2AffiliationChoice getSelectedAffiliation(){return selectedAffiliation;}
    public Instant getCompletedAt(){return completedAt;}
    public Instant getCreatedAt(){return createdAt;}
    public Instant getUpdatedAt(){return updatedAt;}
    public long getVersion(){return version;}
}
