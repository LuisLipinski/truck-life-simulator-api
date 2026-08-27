package com.luislipinski.trucklife.qualification.api;

import com.luislipinski.trucklife.career.domain.CareerGame;
import com.luislipinski.trucklife.qualification.application.ProgressionOperations;
import com.luislipinski.trucklife.qualification.domain.AcademyModuleCode;
import com.luislipinski.trucklife.qualification.domain.QualificationType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ProgressionResponse(UUID careerId, CareerGame game, short currentLevel, BigDecimal balance,
                                  String displayCurrency, BigDecimal totalDistance, boolean dangerousGoodsQualified,
                                  List<AcademyCompletionResponse> academyProgress,
                                  List<QualificationCompletionResponse> qualifications,
                                  List<PromotionOptionResponse> promotions,
                                  DangerousOptionResponse dangerousQualification) {
    static ProgressionResponse from(ProgressionOperations.Status status) {
        return new ProgressionResponse(status.careerId(), status.game(), status.currentLevel(), status.balance(),
                status.displayCurrency(), status.totalDistance(), status.dangerousGoodsQualified(),
                status.academyProgress().stream().map(AcademyCompletionResponse::from).toList(),
                status.qualifications().stream().map(QualificationCompletionResponse::from).toList(),
                status.promotions().stream().map(PromotionOptionResponse::from).toList(),
                DangerousOptionResponse.from(status.dangerousQualification()));
    }

    public record AcademyCompletionResponse(UUID id, short targetLevel, AcademyModuleCode moduleCode, String moduleName,
                                             BigDecimal requiredDistance, BigDecimal distanceAtCompletion,
                                             BigDecimal feeAmount, String displayCurrency, int operationalWeek,
                                             String policyVersion, Instant completedAt) {
        static AcademyCompletionResponse from(ProgressionOperations.AcademyCompletion value) {
            return new AcademyCompletionResponse(value.id(), value.targetLevel(), value.moduleCode(), value.moduleName(),
                    value.requiredDistance(), value.distanceAtCompletion(), value.feeAmount(), value.displayCurrency(),
                    value.operationalWeek(), value.policyVersion(), value.completedAt());
        }
    }

    public record QualificationCompletionResponse(UUID id, QualificationType type, String name, BigDecimal feeAmount,
                                                   String displayCurrency, int operationalWeek, String policyVersion,
                                                   Instant acquiredAt) {
        static QualificationCompletionResponse from(ProgressionOperations.QualificationCompletion value) {
            return new QualificationCompletionResponse(value.id(), value.type(), value.name(), value.feeAmount(),
                    value.displayCurrency(), value.operationalWeek(), value.policyVersion(), value.acquiredAt());
        }
    }

    public record PromotionOptionResponse(short targetLevel, AcademyModuleCode moduleCode, String moduleName,
                                           BigDecimal requiredDistance, BigDecimal currentDistance,
                                           BigDecimal remainingDistance, BigDecimal feeAmount,
                                           boolean completed, boolean ready) {
        static PromotionOptionResponse from(ProgressionOperations.PromotionOption value) {
            return new PromotionOptionResponse(value.targetLevel(), value.moduleCode(), value.moduleName(),
                    value.requiredDistance(), value.currentDistance(), value.remainingDistance(), value.feeAmount(),
                    value.completed(), value.ready());
        }
    }

    public record DangerousOptionResponse(QualificationType type, String name, short minimumLevel,
                                          BigDecimal feeAmount, boolean acquired, boolean ready) {
        static DangerousOptionResponse from(ProgressionOperations.DangerousOption value) {
            return new DangerousOptionResponse(value.type(), value.name(), value.minimumLevel(), value.feeAmount(),
                    value.acquired(), value.ready());
        }
    }
}
