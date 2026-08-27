package com.luislipinski.trucklife.payroll.application;

import com.luislipinski.trucklife.trip.domain.TripPaymentCategory;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

final class PayrollPolicyCatalog {

    static final BigDecimal ATS_BENEFITS = bd("36");
    static final Map<TripPaymentCategory, BigDecimal> ATS_BASE_PAY_RATES = rates(
            "0.60", "0.63", "0.64", "0.67", "0.50"
    );

    private static final Map<String, StatePolicy> ATS_STATES = Map.ofEntries(
            Map.entry("AZ", state("920", "80", flat("0.025", "8350", "0"), null)),
            Map.entry("AR", state("810", "80", tax("2470", "0", "0", brackets(
                    bracket("4600", "0.02"), open("0.039")
            )), null)),
            Map.entry("CA", state("1080", "86", tax("5540", "0", "153", brackets(
                    bracket("11079", "0.01"), bracket("26264", "0.02"), bracket("41452", "0.04"),
                    bracket("57542", "0.06"), bracket("72724", "0.08"), bracket("371479", "0.093"),
                    bracket("445771", "0.103"), bracket("742953", "0.113"), bracket("1000000", "0.123"),
                    open("0.133")
            )), bd("0.013"))),
            Map.entry("CO", state("1040", "80", flat("0.044", "16100", "0"), null)),
            Map.entry("ID", state("900", "80", tax("16100", "0", "0", brackets(
                    bracket("4811", "0"), open("0.053")
            )), null)),
            Map.entry("IL", state("990", "80", flat("0.0495", "0", "2925"), null)),
            Map.entry("IA", state("900", "80", flat("0.038", "16100", "0"), null)),
            Map.entry("KS", state("880", "80", tax("3605", "9160", "0", brackets(
                    bracket("23000", "0.052"), open("0.0558")
            )), null)),
            Map.entry("LA", state("880", "80", flat("0.03", "12875", "0"), null)),
            Map.entry("MO", state("910", "80", tax("16100", "0", "0", brackets(
                    bracket("1348", "0"), bracket("2696", "0.02"), bracket("4044", "0.025"),
                    bracket("5392", "0.03"), bracket("6740", "0.035"), bracket("8088", "0.04"),
                    bracket("9436", "0.045"), open("0.047")
            )), null)),
            Map.entry("MT", state("940", "80", tax("16100", "0", "0", brackets(
                    bracket("47500", "0.047"), open("0.0565")
            )), null)),
            Map.entry("NE", state("920", "80", tax("8850", "0", "176", brackets(
                    bracket("4130", "0.0246"), bracket("24760", "0.0351"), open("0.0455")
            )), null)),
            Map.entry("NV", state("990", "80", null, null)),
            Map.entry("NM", state("870", "80", tax("16100", "0", "0", brackets(
                    bracket("5500", "0.015"), bracket("16500", "0.032"), bracket("33500", "0.043"),
                    bracket("66500", "0.047"), bracket("210000", "0.049"), open("0.059")
            )), null)),
            Map.entry("OK", state("860", "80", tax("6350", "1000", "0", brackets(
                    bracket("3750", "0"), bracket("4900", "0.025"), bracket("7200", "0.035"), open("0.045")
            )), null)),
            Map.entry("OR", state("1050", "86", tax("2910", "0", "256", brackets(
                    bracket("4550", "0.0475"), bracket("11400", "0.0675"), bracket("125000", "0.0875"),
                    open("0.099")
            )), null)),
            Map.entry("TX", state("960", "80", null, null)),
            Map.entry("UT", state("960", "80", flat("0.045", "16100", "0"), null)),
            Map.entry("WA", state("1100", "86", null, bd("0.0058"))),
            Map.entry("WY", state("950", "80", null, null))
    );

    private static final Map<String, CountryPolicy> ETS2_COUNTRIES = Map.ofEntries(
            Map.entry("DE", detailed("EUR", "2800", "18", "48", rates("0.30", "0.33", "0.35", "0.38", "0.25"), EtsTaxModel.DE, null, null)),
            Map.entry("GB", detailed("GBP", "2600", "16", "45", rates("0.27", "0.30", "0.32", "0.35", "0.22"), EtsTaxModel.GB, null, null)),
            Map.entry("PL", detailed("PLN", "10000", "60", "180", rates("1.15", "1.25", "1.30", "1.40", "0.95"), EtsTaxModel.PL, null, null)),
            Map.entry("FR", effective("EUR", "1", "0.98", "2550", "0.167", "0.113")),
            Map.entry("NL", effective("EUR", "1", "1.13", "3000", "0.178", "0.10")),
            Map.entry("BE", effective("EUR", "1", "1.05", "2850", "0.256", "0.14")),
            Map.entry("LU", effective("EUR", "1", "1.22", "3300", "0.197", "0.123")),
            Map.entry("CH", effective("CHF", "0.9333", "1.50", "5200", "0.117", "0.064")),
            Map.entry("AT", effective("EUR", "1", "1.00", "2850", "0.146", "0.179")),
            Map.entry("IT", effective("EUR", "1", "0.88", "2300", "0.191", "0.095")),
            Map.entry("PT", effective("EUR", "1", "0.72", "1650", "0.139", "0.11")),
            Map.entry("ES", effective("EUR", "1", "0.78", "2050", "0.171", "0.065")),
            Map.entry("CZ", effective("CZK", "24.153", "0.67", "1700", "0.097", "0.116")),
            Map.entry("SK", effective("EUR", "1", "0.64", "1500", "0.109", "0.134")),
            Map.entry("HU", effective("HUF", "365.10", "0.58", "1350", "0.15", "0.185")),
            Map.entry("DK", effective("DKK", "7.4758", "1.26", "4100", "0.353", "0")),
            Map.entry("NO", effective("NOK", "10.9025", "1.35", "4200", "0.204", "0.077")),
            Map.entry("SE", effective("SEK", "11.0875", "1.12", "3300", "0.156", "0.07")),
            Map.entry("FI", effective("EUR", "1", "1.08", "3200", "0.212", "0.095")),
            Map.entry("EE", effective("EUR", "1", "0.72", "1900", "0.216", "0.016")),
            Map.entry("LV", effective("EUR", "1", "0.63", "1550", "0.155", "0.105")),
            Map.entry("LT", effective("EUR", "1", "0.64", "1700", "0.192", "0.195")),
            Map.entry("RO", effective("RON", "5.2515", "0.50", "1200", "0.10", "0.35")),
            Map.entry("BG", effective("EUR", "1", "0.46", "1050", "0.10", "0.1378")),
            Map.entry("TR", effective("TRY", "56.0145", "0.43", "1050", "0.143", "0.15")),
            Map.entry("SI", effective("EUR", "1", "0.76", "2200", "0.121", "0.241")),
            Map.entry("HR", effective("EUR", "1", "0.68", "1800", "0.15", "0.20")),
            Map.entry("BA", effective("BAM", "1.95583", "0.50", "1200", "0.10", "0.31")),
            Map.entry("RS", effective("RSD", "117.3586", "0.48", "1100", "0.10", "0.199")),
            Map.entry("ME", effective("EUR", "1", "0.55", "1200", "0.09", "0.15")),
            Map.entry("XK", effective("EUR", "1", "0.48", "1050", "0.10", "0.05")),
            Map.entry("MK", effective("MKD", "61.5", "0.44", "950", "0.10", "0.28")),
            Map.entry("AL", effective("ALL", "92.60", "0.46", "1000", "0.13", "0.112")),
            Map.entry("GR", effective("EUR", "1", "0.70", "1700", "0.127", "0.134"))
    );

    private PayrollPolicyCatalog() {
    }

    static StatePolicy ats(String stateCode) {
        return ATS_STATES.get(normalize(stateCode));
    }

    static CountryPolicy ets2(String countryCode) {
        return ETS2_COUNTRIES.get(normalize(countryCode));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip().toUpperCase(java.util.Locale.ROOT);
    }

    private static StatePolicy state(
            String weeklyGross,
            String perDiemRate,
            StateIncomeTax incomeTax,
            BigDecimal payrollTaxRate
    ) {
        return new StatePolicy(bd(weeklyGross), bd(perDiemRate), incomeTax, payrollTaxRate);
    }

    private static StateIncomeTax flat(String rate, String standardDeduction, String personalExemption) {
        return tax(standardDeduction, personalExemption, "0", brackets(open(rate)));
    }

    private static StateIncomeTax tax(
            String standardDeduction,
            String personalExemption,
            String credit,
            List<TaxBracket> brackets
    ) {
        return new StateIncomeTax(
                bd(standardDeduction),
                bd(personalExemption),
                bd(credit),
                List.copyOf(brackets)
        );
    }

    private static List<TaxBracket> brackets(TaxBracket... brackets) {
        return List.of(brackets);
    }

    private static TaxBracket bracket(String upperLimit, String rate) {
        return new TaxBracket(bd(upperLimit), bd(rate));
    }

    private static TaxBracket open(String rate) {
        return new TaxBracket(null, bd(rate));
    }

    private static CountryPolicy detailed(
            String baseCurrency,
            String level1Gross,
            String routeOverrunRate,
            String perDiemRate,
            Map<TripPaymentCategory, BigDecimal> payRates,
            EtsTaxModel taxModel,
            BigDecimal incomeTaxRate,
            BigDecimal socialRate
    ) {
        return new CountryPolicy(
                baseCurrency,
                bd(level1Gross),
                bd(routeOverrunRate),
                bd(perDiemRate),
                payRates,
                taxModel,
                incomeTaxRate,
                socialRate
        );
    }

    private static CountryPolicy effective(
            String baseCurrency,
            String perEuro,
            String costFactor,
            String grossEur,
            String incomeTaxRate,
            String socialRate
    ) {
        BigDecimal exchange = bd(perEuro);
        BigDecimal gross = bd(grossEur);
        BigDecimal payFactor = gross.divide(bd("2800"), 12, RoundingMode.HALF_UP)
                .max(bd("0.65"))
                .min(bd("1.35"));
        Map<TripPaymentCategory, BigDecimal> payRates = Map.of(
                TripPaymentCategory.NORMAL, rate4(bd("0.30").multiply(payFactor).multiply(exchange)),
                TripPaymentCategory.HAZMAT, rate4(bd("0.33").multiply(payFactor).multiply(exchange)),
                TripPaymentCategory.DOUBLES, rate4(bd("0.35").multiply(payFactor).multiply(exchange)),
                TripPaymentCategory.HAZMAT_DOUBLES, rate4(bd("0.38").multiply(payFactor).multiply(exchange)),
                TripPaymentCategory.DEADHEAD, rate4(bd("0.25").multiply(payFactor).multiply(exchange))
        );
        return detailed(
                baseCurrency,
                money(gross.multiply(exchange)).toPlainString(),
                money(gross.divide(bd("160"), 12, RoundingMode.HALF_UP).multiply(exchange)).toPlainString(),
                money(bd("48").multiply(bd(costFactor)).multiply(exchange)).toPlainString(),
                payRates,
                EtsTaxModel.EFFECTIVE,
                bd(incomeTaxRate),
                bd(socialRate)
        );
    }

    private static Map<TripPaymentCategory, BigDecimal> rates(
            String normal,
            String hazmat,
            String doubles,
            String hazmatDoubles,
            String deadhead
    ) {
        return Map.of(
                TripPaymentCategory.NORMAL, bd(normal),
                TripPaymentCategory.HAZMAT, bd(hazmat),
                TripPaymentCategory.DOUBLES, bd(doubles),
                TripPaymentCategory.HAZMAT_DOUBLES, bd(hazmatDoubles),
                TripPaymentCategory.DEADHEAD, bd(deadhead)
        );
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal rate4(BigDecimal value) {
        return value.setScale(4, RoundingMode.HALF_UP);
    }

    record StatePolicy(
            BigDecimal weeklyGross,
            BigDecimal perDiemRate,
            StateIncomeTax incomeTax,
            BigDecimal payrollTaxRate
    ) {
    }

    record StateIncomeTax(
            BigDecimal standardDeduction,
            BigDecimal personalExemption,
            BigDecimal credit,
            List<TaxBracket> brackets
    ) {
    }

    record TaxBracket(BigDecimal upperLimit, BigDecimal rate) {
    }

    enum EtsTaxModel {
        DE,
        GB,
        PL,
        EFFECTIVE
    }

    record CountryPolicy(
            String baseCurrency,
            BigDecimal level1Gross,
            BigDecimal routeOverrunRate,
            BigDecimal perDiemRate,
            Map<TripPaymentCategory, BigDecimal> payRates,
            EtsTaxModel taxModel,
            BigDecimal incomeTaxRate,
            BigDecimal socialRate
    ) {
    }
}
