package com.luislipinski.trucklife.phase2.api;

import com.luislipinski.trucklife.phase2.application.Phase2TransitionOperations;
import com.luislipinski.trucklife.phase2.domain.CarrierContractStatus;
import com.luislipinski.trucklife.phase2.domain.Phase2AffiliationChoice;
import com.luislipinski.trucklife.phase2.domain.Phase2TransitionStatus;
import com.luislipinski.trucklife.phase2.persistence.CarrierContractEntity;
import com.luislipinski.trucklife.phase2.persistence.Phase2TransitionEntity;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.util.UUID;

public record Phase2TransitionResponse(
        UUID id,
        Phase2TransitionStatus status,
        String previousCompanyName,
        String terminationReason,
        String terminationCountryCode,
        String terminationStateCode,
        int employmentStartOperationalWeek,
        int terminationOperationalWeek,
        DayOfWeek terminationEffectiveDay,
        BigDecimal statutorySettlementAmount,
        String displayCurrency,
        String policyVersion,
        String policySource,
        String policySummary,
        Phase2AffiliationChoice selectedAffiliation,
        Instant completedAt,
        CarrierContract carrierContract,
        BigDecimal currentBalance,
        boolean replay
) {
    static Phase2TransitionResponse from(Phase2TransitionOperations.Details details) {
        Phase2TransitionEntity transition = details.transition();
        CarrierContractEntity contract = details.carrierContract();
        return new Phase2TransitionResponse(
                transition.getId(),
                transition.getStatus(),
                transition.getPreviousCompanyName(),
                transition.getTerminationReason(),
                transition.getTerminationCountryCode(),
                transition.getTerminationStateCode(),
                transition.getEmploymentStartOperationalWeek(),
                transition.getTerminationOperationalWeek(),
                transition.getTerminationEffectiveDay(),
                transition.getStatutorySettlementAmount(),
                transition.getDisplayCurrency(),
                transition.getPolicyVersion(),
                transition.getPolicySource(),
                transition.getPolicySummary(),
                transition.getSelectedAffiliation(),
                transition.getCompletedAt(),
                contract == null ? null : CarrierContract.from(contract),
                details.currentBalance(),
                details.replay()
        );
    }

    public record CarrierContract(
            UUID id,
            CarrierContractStatus status,
            String carrierName,
            Phase2AffiliationChoice affiliationOrigin,
            String jurisdictionCountryCode,
            String jurisdictionStateCode,
            String compensationRuleKey,
            String compensationPolicyStatus,
            int startedOperationalWeek,
            DayOfWeek startedEffectiveDay,
            Instant createdAt
    ) {
        static CarrierContract from(CarrierContractEntity contract) {
            return new CarrierContract(
                    contract.getId(),
                    contract.getStatus(),
                    contract.getCarrierName(),
                    contract.getAffiliationOrigin(),
                    contract.getJurisdictionCountryCode(),
                    contract.getJurisdictionStateCode(),
                    contract.getCompensationRuleKey(),
                    contract.getCompensationPolicyStatus(),
                    contract.getStartedOperationalWeek(),
                    contract.getStartedEffectiveDay(),
                    contract.getCreatedAt()
            );
        }
    }
}
