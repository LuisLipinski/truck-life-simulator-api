package com.luislipinski.trucklife.finance.application;

import com.luislipinski.trucklife.career.domain.CareerGame;
import com.luislipinski.trucklife.career.persistence.CareerEntity;
import com.luislipinski.trucklife.finance.domain.MonthlyExpenseCategory;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

final class FinancePolicyCatalog {
    static final String VERSION = "phase1-finance-2026-v1";
    static final BigDecimal EMERGENCY_RESERVE_ANNUAL_YIELD = bd("0.0325");
    private static final Map<MonthlyExpenseCategory, BigDecimal> ATS_BASE = expenses("1650","100","60","65","55","400","150","180","72","80","150");
    private static final Map<String, BigDecimal> ATS_STATE_FACTORS = Map.ofEntries(
            Map.entry("AZ",bd("0.78")),Map.entry("AR",bd("0.58")),Map.entry("CA",bd("1")),Map.entry("CO",bd("0.88")),Map.entry("ID",bd("0.72")),
            Map.entry("IL",bd("0.75")),Map.entry("IA",bd("0.62")),Map.entry("KS",bd("0.62")),Map.entry("LA",bd("0.65")),Map.entry("MO",bd("0.65")),
            Map.entry("MT",bd("0.72")),Map.entry("NE",bd("0.65")),Map.entry("NV",bd("0.82")),Map.entry("NM",bd("0.65")),Map.entry("OK",bd("0.60")),
            Map.entry("OR",bd("0.86")),Map.entry("TX",bd("0.70")),Map.entry("UT",bd("0.78")),Map.entry("WA",bd("0.95")),Map.entry("WY",bd("0.67")));
    private static final Map<MonthlyExpenseCategory, BigDecimal> EUR_BASE = expenses("950","90","35","40","30","300","120","80","65","70","120");
    private static final Map<MonthlyExpenseCategory, BigDecimal> GB_BASE = expenses("1000","110","40","35","25","320","140","0","90","70","130");
    private static final Map<MonthlyExpenseCategory, BigDecimal> PL_BASE = expenses("3000","350","150","80","70","1400","500","250","150","300","500");
    private static final Map<String, CountryProfile> ETS2 = Map.ofEntries(
            Map.entry("DE",direct("EUR",EUR_BASE)),Map.entry("GB",direct("GBP",GB_BASE)),Map.entry("PL",direct("PLN",PL_BASE)),
            Map.entry("FR",effective("EUR","1","0.98")),Map.entry("NL",effective("EUR","1","1.13")),Map.entry("BE",effective("EUR","1","1.05")),
            Map.entry("LU",effective("EUR","1","1.22")),Map.entry("CH",effective("CHF","0.9333","1.50")),Map.entry("AT",effective("EUR","1","1.00")),
            Map.entry("IT",effective("EUR","1","0.88")),Map.entry("PT",effective("EUR","1","0.72")),Map.entry("ES",effective("EUR","1","0.78")),
            Map.entry("CZ",effective("CZK","24.153","0.67")),Map.entry("SK",effective("EUR","1","0.64")),Map.entry("HU",effective("HUF","365.10","0.58")),
            Map.entry("DK",effective("DKK","7.4758","1.26")),Map.entry("NO",effective("NOK","10.9025","1.35")),Map.entry("SE",effective("SEK","11.0875","1.12")),
            Map.entry("FI",effective("EUR","1","1.08")),Map.entry("EE",effective("EUR","1","0.72")),Map.entry("LV",effective("EUR","1","0.63")),
            Map.entry("LT",effective("EUR","1","0.64")),Map.entry("RO",effective("RON","5.2515","0.50")),Map.entry("BG",effective("EUR","1","0.46")),
            Map.entry("TR",effective("TRY","56.0145","0.43")),Map.entry("SI",effective("EUR","1","0.76")),Map.entry("HR",effective("EUR","1","0.68")),
            Map.entry("BA",effective("BAM","1.95583","0.50")),Map.entry("RS",effective("RSD","117.3586","0.48")),Map.entry("ME",effective("EUR","1","0.55")),
            Map.entry("XK",effective("EUR","1","0.48")),Map.entry("MK",effective("MKD","61.5","0.44")),Map.entry("AL",effective("ALL","92.60","0.46")),Map.entry("GR",effective("EUR","1","0.70")));
    private FinancePolicyCatalog() {}

    static Map<MonthlyExpenseCategory, BigDecimal> defaults(CareerEntity career) {
        BigDecimal cityFactor=positiveOrOne(career.getCityCostFactor()), displayExchange=positiveOrOne(career.getExchangeRate());
        Map<MonthlyExpenseCategory,BigDecimal> result=new LinkedHashMap<>();
        if(career.getGame()==CareerGame.ATS){
            BigDecimal stateFactor=ATS_STATE_FACTORS.get(normalize(career.getStateCode()));
            if(stateFactor==null||!"USD".equals(career.getBaseCurrency()))throw new IllegalArgumentException("ATS finance policy unavailable for career state/currency");
            for(MonthlyExpenseCategory category:MonthlyExpenseCategory.values()){BigDecimal amount=ATS_BASE.get(category).multiply(stateFactor);if(category.citySensitive())amount=amount.multiply(cityFactor);result.put(category,money(amount.multiply(displayExchange)));}return result;
        }
        CountryProfile country=ETS2.get(normalize(career.getCountryCode()));
        if(country==null||!country.currency().equals(career.getBaseCurrency()))throw new IllegalArgumentException("ETS2 finance policy unavailable for career country/currency");
        for(MonthlyExpenseCategory category:MonthlyExpenseCategory.values()){BigDecimal amount=country.amount(category);if(category.citySensitive())amount=amount.multiply(cityFactor);result.put(category,money(amount.multiply(displayExchange)));}return result;
    }

    static BigDecimal reserveInterest(CareerGame game,BigDecimal reserveBalance){
        BigDecimal balance=reserveBalance==null?BigDecimal.ZERO:reserveBalance.max(BigDecimal.ZERO);int periods=game==CareerGame.ATS?52:12;
        return money(balance.multiply(EMERGENCY_RESERVE_ANNUAL_YIELD).divide(BigDecimal.valueOf(periods),12,RoundingMode.HALF_UP));
    }
    private static CountryProfile direct(String currency,Map<MonthlyExpenseCategory,BigDecimal> amounts){return new CountryProfile(currency,Map.copyOf(amounts));}
    private static CountryProfile effective(String currency,String perEuro,String costFactor){Map<MonthlyExpenseCategory,BigDecimal> values=new EnumMap<>(MonthlyExpenseCategory.class);BigDecimal exchange=bd(perEuro),factor=bd(costFactor);EUR_BASE.forEach((category,amount)->values.put(category,money(amount.multiply(factor).multiply(exchange))));return direct(currency,values);}
    private static Map<MonthlyExpenseCategory,BigDecimal> expenses(String rent,String electricity,String water,String internet,String phone,String groceries,String eatingOut,String health,String publicTransport,String household,String leisure){Map<MonthlyExpenseCategory,BigDecimal> map=new EnumMap<>(MonthlyExpenseCategory.class);map.put(MonthlyExpenseCategory.RENT,bd(rent));map.put(MonthlyExpenseCategory.ELECTRICITY,bd(electricity));map.put(MonthlyExpenseCategory.WATER,bd(water));map.put(MonthlyExpenseCategory.INTERNET,bd(internet));map.put(MonthlyExpenseCategory.PHONE,bd(phone));map.put(MonthlyExpenseCategory.GROCERIES,bd(groceries));map.put(MonthlyExpenseCategory.EATING_OUT,bd(eatingOut));map.put(MonthlyExpenseCategory.HEALTH,bd(health));map.put(MonthlyExpenseCategory.PUBLIC_TRANSPORT,bd(publicTransport));map.put(MonthlyExpenseCategory.HOUSEHOLD,bd(household));map.put(MonthlyExpenseCategory.LEISURE,bd(leisure));return Map.copyOf(map);}
    private static BigDecimal positiveOrOne(BigDecimal value){return value==null||value.signum()<=0?BigDecimal.ONE:value;}
    private static String normalize(String value){return value==null?"":value.strip().toUpperCase(java.util.Locale.ROOT);}
    private static BigDecimal bd(String value){return new BigDecimal(value);} static BigDecimal money(BigDecimal value){return value.setScale(2,RoundingMode.HALF_UP);}
    private record CountryProfile(String currency,Map<MonthlyExpenseCategory,BigDecimal> amounts){BigDecimal amount(MonthlyExpenseCategory category){return amounts.get(category);}}
}
