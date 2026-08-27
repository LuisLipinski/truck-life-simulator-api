package com.luislipinski.trucklife.qualification.application;

import com.luislipinski.trucklife.career.domain.CareerGame;
import com.luislipinski.trucklife.qualification.domain.AcademyModuleCode;
import com.luislipinski.trucklife.qualification.domain.QualificationType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ProgressionOperations {
    Status get(UUID userId, CareerGame game, UUID careerId);
    Status promote(UUID userId, CareerGame game, UUID careerId, int expectedOperationalWeek,
                   short expectedCurrentLevel, short targetLevel, boolean academyCompleted);
    Status acquireDangerousGoods(UUID userId, CareerGame game, UUID careerId, int expectedOperationalWeek,
                                 short expectedCurrentLevel);

    record Status(UUID careerId, CareerGame game, short currentLevel, BigDecimal balance, String displayCurrency,
                  BigDecimal totalDistance, boolean dangerousGoodsQualified,
                  List<AcademyCompletion> academyProgress, List<QualificationCompletion> qualifications,
                  List<PromotionOption> promotions, DangerousOption dangerousQualification) {}

    record AcademyCompletion(UUID id, short targetLevel, AcademyModuleCode moduleCode, String moduleName,
                             BigDecimal requiredDistance, BigDecimal distanceAtCompletion, BigDecimal feeAmount,
                             String displayCurrency, int operationalWeek, String policyVersion, Instant completedAt) {}

    record QualificationCompletion(UUID id, QualificationType type, String name, BigDecimal feeAmount,
                                   String displayCurrency, int operationalWeek, String policyVersion, Instant acquiredAt) {}

    record PromotionOption(short targetLevel, AcademyModuleCode moduleCode, String moduleName,
                           BigDecimal requiredDistance, BigDecimal currentDistance, BigDecimal remainingDistance,
                           BigDecimal feeAmount, boolean completed, boolean ready) {}

    record DangerousOption(QualificationType type, String name, short minimumLevel, BigDecimal feeAmount,
                           boolean acquired, boolean ready) {}
}
