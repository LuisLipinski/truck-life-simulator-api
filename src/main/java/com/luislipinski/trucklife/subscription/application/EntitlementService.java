package com.luislipinski.trucklife.subscription.application;

import com.luislipinski.trucklife.career.domain.CareerGame;
import com.luislipinski.trucklife.subscription.domain.PlanCode;
import com.luislipinski.trucklife.subscription.domain.PlanFeatureCode;
import com.luislipinski.trucklife.subscription.domain.SubscriptionStatus;
import com.luislipinski.trucklife.subscription.persistence.PlanEntity;
import com.luislipinski.trucklife.subscription.persistence.PlanFeatureEntity;
import com.luislipinski.trucklife.subscription.persistence.PlanFeatureRepository;
import com.luislipinski.trucklife.subscription.persistence.PlanRepository;
import com.luislipinski.trucklife.subscription.persistence.SubscriptionEntity;
import com.luislipinski.trucklife.subscription.persistence.SubscriptionRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EntitlementService implements EntitlementOperations {
    private final PlanRepository planRepository;
    private final PlanFeatureRepository featureRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final Clock clock;

    public EntitlementService(
            PlanRepository planRepository,
            PlanFeatureRepository featureRepository,
            SubscriptionRepository subscriptionRepository,
            Clock clock
    ) {
        this.planRepository = planRepository;
        this.featureRepository = featureRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public EntitlementSnapshot entitlements(UUID userId) {
        Instant now = clock.instant();
        List<SubscriptionEntity> subscriptions = subscriptionRepository.findAllByUserIdOrderByUpdatedAtDescIdDesc(userId);
        SubscriptionEntity valid = subscriptions.stream()
                .filter(subscription -> subscription.getStatus() == SubscriptionStatus.ACTIVE)
                .filter(subscription -> isCurrent(subscription, now))
                .filter(subscription -> planRepository.findById(subscription.getPlanId())
                        .filter(PlanEntity::isActive)
                        .isPresent())
                .findFirst()
                .orElse(null);

        PlanEntity plan = valid == null
                ? freePlan()
                : planRepository.findById(valid.getPlanId()).filter(PlanEntity::isActive).orElseGet(this::freePlan);

        SubscriptionEntity latest = subscriptions.isEmpty() ? null : subscriptions.getFirst();
        SubscriptionStatus subscriptionStatus = valid != null
                ? valid.getStatus()
                : latest == null ? null : latest.getStatus();
        Instant currentPeriodEnd = valid != null
                ? valid.getCurrentPeriodEnd()
                : latest == null ? null : latest.getCurrentPeriodEnd();

        Map<PlanFeatureCode, FeatureAccess> features = featureMap(plan.getId());
        return new EntitlementSnapshot(
                plan.getCode(),
                plan.getCode() == PlanCode.PREMIUM,
                subscriptionStatus,
                currentPeriodEnd,
                Map.copyOf(features)
        );
    }

    @Override
    @Transactional(readOnly = true)
    public FeatureAccess careerLimit(UUID userId, CareerGame game) {
        PlanFeatureCode feature = game == CareerGame.ATS
                ? PlanFeatureCode.MAX_ATS_CAREERS
                : PlanFeatureCode.MAX_ETS2_CAREERS;
        return entitlements(userId).feature(feature);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PlanSnapshot> plans() {
        return planRepository.findAllByActiveTrueOrderByPriceCentsAscCodeAsc().stream()
                .map(plan -> new PlanSnapshot(
                        plan.getCode(),
                        plan.getName(),
                        plan.isActive(),
                        plan.getPriceCents(),
                        plan.getCurrency(),
                        plan.getBillingPeriod(),
                        Map.copyOf(featureMap(plan.getId()))
                ))
                .toList();
    }

    private boolean isCurrent(SubscriptionEntity subscription, Instant now) {
        Instant start = subscription.getCurrentPeriodStart();
        Instant end = subscription.getCurrentPeriodEnd();
        return (start == null || !start.isAfter(now))
                && (end == null || end.isAfter(now));
    }

    private PlanEntity freePlan() {
        return planRepository.findByCodeAndActiveTrue(PlanCode.FREE)
                .orElseThrow(() -> new IllegalStateException("The active FREE plan seed is missing"));
    }

    private EnumMap<PlanFeatureCode, FeatureAccess> featureMap(UUID planId) {
        EnumMap<PlanFeatureCode, FeatureAccess> result = new EnumMap<>(PlanFeatureCode.class);
        for (PlanFeatureEntity feature : featureRepository.findAllByPlanIdOrderByFeatureCodeAsc(planId)) {
            result.put(feature.getFeatureCode(), new FeatureAccess(feature.isEnabled(), feature.getLimitValue()));
        }
        return result;
    }
}
