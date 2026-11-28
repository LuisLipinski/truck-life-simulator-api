package com.luislipinski.trucklife.phase2.application;

import com.luislipinski.trucklife.career.domain.CareerGame;
import com.luislipinski.trucklife.career.persistence.CareerEntity;
import com.luislipinski.trucklife.payroll.persistence.PayslipEntity;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class Phase2TerminationPolicyCatalog {
    private static final String POLICY_VERSION = "phase2-voluntary-exit-2026-v1";
    private static final String US_DOL_SOURCE = "https://www.dol.gov/general/topic/benefits-other/severancepay";
    private static final String ITALY_TFR_SOURCE = "https://www.lavoro.gov.it/temi-e-priorita-rapporti-di-lavoro-e-relazioni-industriali/focus/disciplina-del-rapporto-di-lavoro-triennio-2020-2022";
    private static final String TURKEY_SOURCE = "https://www.csgb.gov.tr/en/frequently-asked-questions/general-directorate-of-labor/business-rules/";
    private static final String UK_SOURCE = "https://www.gov.uk/handling-staff-resignations";
    private static final String EUROFOUND_SOURCE = "https://apps.eurofound.europa.eu/legislationdb/severance-payredundancy-compensation";

    public Settlement quote(
            CareerEntity career,
            int employmentStartOperationalWeek,
            List<PayslipEntity> payslips
    ) {
        if (career.getGame() == CareerGame.ATS) {
            return zero(
                    career,
                    US_DOL_SOURCE,
                    "Saída voluntária para a Fase 2. A FLSA não exige severance pay; acordos privados de indenização não são presumidos pelo simulador.",
                    "US_FEDERAL_VOLUNTARY_RESIGNATION"
            );
        }

        String country = normalize(career.getCountryCode());
        if ("IT".equals(country)) {
            BigDecimal eligibleGross = payslips.stream()
                    .filter(p -> p.getEndOperationalWeek() >= employmentStartOperationalWeek)
                    .map(PayslipEntity::getGrossAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal amount = eligibleGross.divide(new BigDecimal("13.5"), 2, RoundingMode.HALF_UP);
            Map<String,Object> snapshot = baseSnapshot(career, "ITALY_TFR_SIMPLIFIED");
            snapshot.put("eligibleGrossPaid", eligibleGross);
            snapshot.put("formula", "eligibleGrossPaid / 13.5");
            snapshot.put("annualRevaluationApplied", false);
            snapshot.put("calendarlessReason", "O simulador não usa calendário real de gameplay; a reavaliação anual do TFR não é aplicada nesta versão.");
            return new Settlement(
                    amount,
                    POLICY_VERSION,
                    ITALY_TFR_SOURCE,
                    "Itália: o TFR é devido no encerramento do vínculo independentemente da causa. O Truck Life usa o bruto histórico pago no vínculo atual dividido por 13,5 e não aplica reavaliação anual por ser calendarless.",
                    snapshot
            );
        }

        if ("TR".equals(country)) {
            return zero(
                    career,
                    TURKEY_SOURCE,
                    "Turquia: a saída voluntária comum, sem uma causa legal especial, não gera severance pay para o empregado.",
                    "TR_VOLUNTARY_RESIGNATION"
            );
        }

        if ("GB".equals(country)) {
            return zero(
                    career,
                    UK_SOURCE,
                    "Reino Unido: a transição é tratada como resignation comum; statutory redundancy pay não é aplicado a essa saída voluntária.",
                    "GB_VOLUNTARY_RESIGNATION"
            );
        }

        return zero(
                career,
                EUROFOUND_SOURCE,
                "Saída voluntária comum para a Fase 2. O Truck Life não aplica compensações de redundancy/dismissal a uma renúncia voluntária; benefícios específicos de demissão por iniciativa do empregador ficam fora deste motivo de saída.",
                "EUROPE_VOLUNTARY_RESIGNATION"
        );
    }

    private Settlement zero(CareerEntity career, String source, String summary, String rule) {
        return new Settlement(
                BigDecimal.ZERO.setScale(2),
                POLICY_VERSION,
                source,
                summary,
                baseSnapshot(career, rule)
        );
    }

    private Map<String,Object> baseSnapshot(CareerEntity career, String rule) {
        Map<String,Object> snapshot = new LinkedHashMap<>();
        snapshot.put("policyVersion", POLICY_VERSION);
        snapshot.put("terminationReason", "VOLUNTARY_PHASE2_TRANSITION");
        snapshot.put("noticeAssumption", "NOTICE_SATISFIED_OR_MUTUAL_RELEASE");
        snapshot.put("game", career.getGame().name());
        snapshot.put("countryCode", career.getCountryCode());
        snapshot.put("stateCode", career.getStateCode());
        snapshot.put("rule", rule);
        snapshot.put("ordinaryPayrollExcluded", true);
        snapshot.put("unusedLeaveExcluded", true);
        return snapshot;
    }

    private String normalize(String value) {
        return value == null ? "" : value.strip().toUpperCase(java.util.Locale.ROOT);
    }

    public record Settlement(
            BigDecimal amount,
            String policyVersion,
            String policySource,
            String policySummary,
            Map<String,Object> snapshot
    ) {}
}
