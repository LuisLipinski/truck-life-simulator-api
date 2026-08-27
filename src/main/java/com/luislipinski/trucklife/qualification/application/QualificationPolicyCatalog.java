package com.luislipinski.trucklife.qualification.application;

import com.luislipinski.trucklife.career.domain.CareerGame;
import com.luislipinski.trucklife.qualification.domain.AcademyModuleCode;
import com.luislipinski.trucklife.qualification.domain.QualificationType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

final class QualificationPolicyCatalog {
    static final String POLICY_VERSION = "phase1-qualification-2026-v1";

    private static final Map<String, CountryFees> ETS2_COUNTRIES = Map.ofEntries(
            Map.entry("DE", fixed("EUR", "300", "60", "125")),
            Map.entry("GB", fixed("GBP", "260", "50", "110")),
            Map.entry("PL", fixed("PLN", "1200", "250", "500")),
            Map.entry("FR", effective("EUR", "1", "0.98")),
            Map.entry("NL", effective("EUR", "1", "1.13")),
            Map.entry("BE", effective("EUR", "1", "1.05")),
            Map.entry("LU", effective("EUR", "1", "1.22")),
            Map.entry("CH", effective("CHF", "0.9333", "1.50")),
            Map.entry("AT", effective("EUR", "1", "1.00")),
            Map.entry("IT", effective("EUR", "1", "0.88")),
            Map.entry("PT", effective("EUR", "1", "0.72")),
            Map.entry("ES", effective("EUR", "1", "0.78")),
            Map.entry("CZ", effective("CZK", "24.153", "0.67")),
            Map.entry("SK", effective("EUR", "1", "0.64")),
            Map.entry("HU", effective("HUF", "365.10", "0.58")),
            Map.entry("DK", effective("DKK", "7.4758", "1.26")),
            Map.entry("NO", effective("NOK", "10.9025", "1.35")),
            Map.entry("SE", effective("SEK", "11.0875", "1.12")),
            Map.entry("FI", effective("EUR", "1", "1.08")),
            Map.entry("EE", effective("EUR", "1", "0.72")),
            Map.entry("LV", effective("EUR", "1", "0.63")),
            Map.entry("LT", effective("EUR", "1", "0.64")),
            Map.entry("RO", effective("RON", "5.2515", "0.50")),
            Map.entry("BG", effective("EUR", "1", "0.46")),
            Map.entry("TR", effective("TRY", "56.0145", "0.43")),
            Map.entry("SI", effective("EUR", "1", "0.76")),
            Map.entry("HR", effective("EUR", "1", "0.68")),
            Map.entry("BA", effective("BAM", "1.95583", "0.50")),
            Map.entry("RS", effective("RSD", "117.3586", "0.48")),
            Map.entry("ME", effective("EUR", "1", "0.55")),
            Map.entry("XK", effective("EUR", "1", "0.48")),
            Map.entry("MK", effective("MKD", "61.5", "0.44")),
            Map.entry("AL", effective("ALL", "92.60", "0.46")),
            Map.entry("GR", effective("EUR", "1", "0.70"))
    );

    private QualificationPolicyCatalog() {}

    static Policy resolve(CareerGame game, String countryCode) {
        if (game == CareerGame.ATS) {
            return policy("USD", "300", "59", "144.25", QualificationType.HAZMAT, "HazMat", "10000", "50000");
        }
        CountryFees fees = ETS2_COUNTRIES.get(normalize(countryCode));
        if (fees == null) throw new IllegalArgumentException("Unsupported ETS2 country for qualification policy");
        return policy(fees.baseCurrency(), fees.level2Fee().toPlainString(), fees.level3Fee().toPlainString(),
                fees.dangerousFee().toPlainString(), QualificationType.ADR, "ADR", "16000", "80000");
    }

    static int supportedEts2CountryCount() { return ETS2_COUNTRIES.size(); }

    private static Policy policy(String baseCurrency, String level2Fee, String level3Fee, String dangerousFee,
                                 QualificationType dangerousType, String dangerousName,
                                 String level2Distance, String level3Distance) {
        return new Policy(
                baseCurrency,
                new Promotion((short) 2, AcademyModuleCode.TRUCK_DRIVING_PROFICIENCY,
                        "Truck Driving Proficiency", bd(level2Distance), bd(level2Fee)),
                new Promotion((short) 3, AcademyModuleCode.DOUBLE_TRAILER_HANDLING,
                        "Double Trailer Handling", bd(level3Distance), bd(level3Fee)),
                new Dangerous(dangerousType, dangerousName, (short) 2, bd(dangerousFee))
        );
    }

    private static CountryFees fixed(String currency, String level2, String level3, String dangerous) {
        return new CountryFees(currency, money(bd(level2)), money(bd(level3)), money(bd(dangerous)));
    }

    private static CountryFees effective(String currency, String perEuro, String costFactor) {
        BigDecimal exchange = bd(perEuro);
        BigDecimal factor = bd(costFactor);
        return new CountryFees(
                currency,
                money(bd("300").multiply(factor).multiply(exchange)),
                money(bd("60").multiply(factor).multiply(exchange)),
                money(bd("125").multiply(factor).multiply(exchange))
        );
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip().toUpperCase(java.util.Locale.ROOT);
    }

    private static BigDecimal bd(String value) { return new BigDecimal(value); }
    private static BigDecimal money(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP); }

    record Policy(String baseCurrency, Promotion level2, Promotion level3, Dangerous dangerous) {
        Promotion promotion(short targetLevel) {
            return targetLevel == 2 ? level2 : targetLevel == 3 ? level3 : null;
        }
    }

    record Promotion(short targetLevel, AcademyModuleCode moduleCode, String moduleName,
                     BigDecimal requiredDistance, BigDecimal baseFee) {}
    record Dangerous(QualificationType type, String name, short minimumLevel, BigDecimal baseFee) {}
    record CountryFees(String baseCurrency, BigDecimal level2Fee, BigDecimal level3Fee, BigDecimal dangerousFee) {}
}
