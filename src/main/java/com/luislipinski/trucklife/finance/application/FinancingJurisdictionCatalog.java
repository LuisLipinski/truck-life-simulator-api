package com.luislipinski.trucklife.finance.application;

import com.luislipinski.trucklife.career.domain.CareerGame;
import com.luislipinski.trucklife.career.persistence.CareerEntity;
import com.luislipinski.trucklife.finance.domain.FinancialProductType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Versioned financing references and jurisdiction rules used by the roleplay economy.
 *
 * <p>The market reference and the legal/local rule are intentionally separate. A jurisdiction
 * never gets an artificial rate adjustment only to make it look different. When a researched
 * numeric ceiling exists, the offered rate is capped by it. Otherwise the researched market
 * reference is used and the local rule is still frozen in the contract snapshot.</p>
 */
@Component
public class FinancingJurisdictionCatalog {

    private static final String FED_PERSONAL_SOURCE = "https://fred.stlouisfed.org/series/TERMCBPER24NS";
    private static final String FED_AUTO_SOURCE = "https://fred.stlouisfed.org/series/RIFLPBCIANM60NM";
    private static final String NCLC_STATE_CAPS_SOURCE = "https://www.nclc.org/resources/predatory-installment-lending-in-the-states-2025/";
    private static final String CFPB_AUTO_SOURCE = "https://www.consumerfinance.gov/consumer-tools/auto-loans/";
    private static final String ECB_SOURCE = "https://data.ecb.europa.eu/data/datasets/MIR";
    private static final String EU_CONSUMER_CREDIT_SOURCE = "https://eur-lex.europa.eu/legal-content/EN/TXT/?uri=CELEX:32008L0048";
    private static final String BOE_SOURCE = "https://www.bankofengland.co.uk/statistics/effective-interest-rates/2026/may-2026";
    private static final String SWISS_CAP_SOURCE = "https://www.admin.ch/gov/en/start/documentation/media-releases.msg-id-103684.html";
    private static final String TURKEY_SOURCE = "https://www.tcmb.gov.tr/";
    private static final String SERBIA_SOURCE = "https://www.nbs.rs/en/finansijske-institucije/banke/kamatne-stope/";
    private static final String MONTENEGRO_SOURCE = "https://www.cbcg.me/en/core-functions/financial-stability/interest-rates";
    private static final String KOSOVO_SOURCE = "https://bqk-kos.org/";
    private static final String BOSNIA_SOURCE = "https://www.cbbh.ba/";
    private static final String ALBANIA_SOURCE = "https://www.bankofalbania.org/Monetary_Policy/";
    private static final String NORTH_MACEDONIA_SOURCE = "https://www.nbrm.mk/";
    private static final String NORWAY_SOURCE = "https://www.ssb.no/en/bank-og-finansmarked/finansielle-indikatorer";
    private static final String SWEDEN_SOURCE = "https://www.scb.se/en/finding-statistics/statistics-by-subject-area/financial-markets/";
    private static final String DENMARK_SOURCE = "https://www.nationalbanken.dk/en/what-we-do/stable-prices-monetary-policy-and-the-danish-economy";
    private static final String HUNGARY_SOURCE = "https://www.mnb.hu/en/statistics";

    private static final BigDecimal FED_PERSONAL_RATE = rate("0.1186000000");
    private static final BigDecimal FED_AUTO_RATE = rate("0.0714000000");

    private static final Set<String> EU_CONSUMER_CREDIT = Set.of(
            "AT","BE","BG","CZ","DE","DK","EE","ES","FI","FR","GR","HR","HU","IT","LT","LU",
            "LV","NL","PL","PT","RO","SE","SI","SK"
    );

    private static final Map<String, MarketReference> ETS2_REFERENCES = Map.ofEntries(
            entry("DE", "0.0812000000", ECB_SOURCE, "ECB_MFI_NEW_CONSUMER_CREDIT_REFERENCE_DE", "2026-04-01"),
            entry("GB", "0.0967000000", BOE_SOURCE, "BOE_NEW_PERSONAL_LOANS_TO_INDIVIDUALS", "2026-05-01"),
            entry("PL", "0.1027000000", ECB_SOURCE, "ECB_PURE_NEW_CONSUMER_CREDIT_REFERENCE_PL", "2026-03-01"),
            entry("FR", "0.0629000000", ECB_SOURCE, "ECB_MFI_NEW_CONSUMER_CREDIT_REFERENCE_FR", "2026-04-01"),
            entry("NL", "0.0759000000", ECB_SOURCE, "ECB_EURO_AREA_CONSUMER_CREDIT_REFERENCE_FALLBACK_NL", "2026-04-01"),
            entry("BE", "0.0595000000", ECB_SOURCE, "ECB_MFI_NEW_CONSUMER_CREDIT_REFERENCE_BE", "2026-04-01"),
            entry("LU", "0.0449000000", ECB_SOURCE, "ECB_MFI_NEW_CONSUMER_CREDIT_REFERENCE_LU", "2026-04-01"),
            entry("CH", "0.0790000000", SWISS_CAP_SOURCE, "SWISS_RESEARCHED_CONSUMER_LOAN_REFERENCE", "2026-01-01"),
            entry("AT", "0.0816000000", ECB_SOURCE, "ECB_MFI_NEW_CONSUMER_CREDIT_REFERENCE_AT", "2026-04-01"),
            entry("IT", "0.0869000000", ECB_SOURCE, "ECB_MFI_NEW_CONSUMER_CREDIT_REFERENCE_IT", "2026-04-01"),
            entry("PT", "0.0898000000", ECB_SOURCE, "ECB_MFI_NEW_CONSUMER_CREDIT_REFERENCE_PT", "2026-04-01"),
            entry("ES", "0.0759000000", ECB_SOURCE, "ECB_EURO_AREA_CONSUMER_CREDIT_REFERENCE_FALLBACK_ES", "2026-04-01"),
            entry("CZ", "0.0723000000", ECB_SOURCE, "ECB_APRC_NEW_CONSUMER_CREDIT_REFERENCE_CZ", "2026-03-01"),
            entry("SK", "0.0892000000", ECB_SOURCE, "ECB_MFI_NEW_CONSUMER_CREDIT_REFERENCE_SK", "2026-04-01"),
            entry("HU", "0.1350000000", HUNGARY_SOURCE, "MNB_RESEARCHED_PERSONAL_LOAN_REFERENCE", "2026-06-01"),
            entry("DK", "0.0800000000", DENMARK_SOURCE, "DANISH_RESEARCHED_CONSUMER_CREDIT_REFERENCE", "2026-06-01"),
            entry("NO", "0.1290000000", NORWAY_SOURCE, "NORWAY_RESEARCHED_CONSUMER_CREDIT_REFERENCE", "2026-07-01"),
            entry("SE", "0.0800000000", SWEDEN_SOURCE, "SWEDEN_RESEARCHED_CONSUMER_CREDIT_REFERENCE", "2026-06-01"),
            entry("FI", "0.0534000000", ECB_SOURCE, "ECB_MFI_NEW_CONSUMER_CREDIT_REFERENCE_FI", "2026-04-01"),
            entry("EE", "0.1295000000", ECB_SOURCE, "ECB_MFI_NEW_CONSUMER_CREDIT_REFERENCE_EE", "2026-04-01"),
            entry("LV", "0.1179000000", ECB_SOURCE, "ECB_MFI_NEW_CONSUMER_CREDIT_REFERENCE_LV", "2026-04-01"),
            entry("LT", "0.0826000000", ECB_SOURCE, "ECB_MFI_NEW_CONSUMER_CREDIT_REFERENCE_LT", "2026-04-01"),
            entry("RO", "0.1037000000", ECB_SOURCE, "ECB_APRC_NEW_CONSUMER_CREDIT_REFERENCE_RO", "2026-04-01"),
            entry("BG", "0.0880000000", ECB_SOURCE, "ECB_MFI_NEW_CONSUMER_CREDIT_REFERENCE_BG", "2026-04-01"),
            entry("SI", "0.0574000000", ECB_SOURCE, "ECB_MFI_NEW_CONSUMER_CREDIT_REFERENCE_SI", "2026-04-01"),
            entry("HR", "0.0469000000", ECB_SOURCE, "ECB_MFI_NEW_CONSUMER_CREDIT_REFERENCE_HR", "2026-04-01"),
            entry("BA", "0.0570000000", BOSNIA_SOURCE, "CBBH_NON_PURPOSE_CONSUMER_LOAN_REFERENCE", "2025-12-01"),
            entry("RS", "0.0906000000", SERBIA_SOURCE, "NBS_RSD_OTHER_LOANS_WEIGHTED_AVERAGE", "2026-06-01"),
            entry("ME", "0.0596000000", MONTENEGRO_SOURCE, "CBCG_NEW_LOANS_WEIGHTED_EFFECTIVE_RATE", "2026-07-01"),
            entry("XK", "0.0660000000", KOSOVO_SOURCE, "CBK_CONSUMER_LOAN_EFFECTIVE_RATE", "2025-12-01"),
            entry("MK", "0.1100000000", NORTH_MACEDONIA_SOURCE, "NBRNM_RESEARCHED_CONSUMER_LOAN_REFERENCE", "2024-12-01"),
            entry("AL", "0.0870000000", ALBANIA_SOURCE, "BOA_AVERAGE_CONSUMER_LOAN_REFERENCE", "2026-01-01"),
            entry("GR", "0.1037000000", ECB_SOURCE, "ECB_MFI_NEW_CONSUMER_CREDIT_REFERENCE_GR", "2026-04-01"),
            entry("TR", "0.6300000000", TURKEY_SOURCE, "TCMB_PERSONAL_NEEDS_LOAN_RATE", "2026-09-04")
    );

    private static final Map<String, StateRule> ATS_STATE_RULES = Map.ofEntries(
            state("AZ","0.41","0.30"), state("AR","0.17","0.17"), state("CA","0.25",null),
            state("CO","0.31","0.21"), state("ID",null,null), state("IL","0.36","0.36"),
            state("IA","0.36","0.32"), state("KS","0.38","0.23"), state("LA","0.38","0.27"),
            state("MO",null,null), state("MT","0.36","0.36"), state("NE","0.30","0.24"),
            state("NV","0.40","0.40"), state("NM","0.36","0.36"), state("OK","0.56","0.36"),
            state("OR","0.36","0.36"), state("TX","0.37","0.27"), state("UT",null,null),
            state("WA","0.29","0.27"), state("WY","0.31","0.23")
    );

    public ResolvedPolicy resolve(CareerEntity career, FinancialProductType productType, BigDecimal requestedAmount) {
        if (career.getGame() == CareerGame.ATS) {
            return resolveAts(career, productType, requestedAmount);
        }
        return resolveEts2(career, productType);
    }

    private ResolvedPolicy resolveAts(CareerEntity career, FinancialProductType productType, BigDecimal requestedAmount) {
        String stateCode = normalize(career.getStateCode());
        StateRule stateRule = ATS_STATE_RULES.get(stateCode);
        if (stateRule == null) {
            throw new IllegalArgumentException("No researched ATS financing rule is available for state " + stateCode);
        }

        BigDecimal marketRate = productType == FinancialProductType.VEHICLE_FINANCING ? FED_AUTO_RATE : FED_PERSONAL_RATE;
        String marketSource = productType == FinancialProductType.VEHICLE_FINANCING ? FED_AUTO_SOURCE : FED_PERSONAL_SOURCE;
        String marketBasis = productType == FinancialProductType.VEHICLE_FINANCING
                ? "FED_G19_60_MONTH_NEW_AUTO_FINANCE_RATE"
                : "FED_G19_24_MONTH_PERSONAL_LOAN_RATE";

        BigDecimal cap = null;
        String summary;
        String ruleSource;
        if (productType == FinancialProductType.PERSONAL_LOAN) {
            cap = stateRule.capFor(requestedAmount);
            ruleSource = NCLC_STATE_CAPS_SOURCE;
            summary = cap == null
                    ? "Empréstimo pessoal: a política registra a jurisdição " + stateCode + " e usa a referência Fed porque a fonte estadual pesquisada não fornece um teto numérico universal para este valor."
                    : "Empréstimo pessoal: referência de mercado limitada ao teto estadual pesquisado para a faixa de valor da simulação (" + percent(cap) + " a.a.).";
        } else {
            ruleSource = CFPB_AUTO_SOURCE;
            summary = "Financiamento de veículo: usa a referência Fed para financiamento automotivo e congela o estado " + stateCode + "; a política não inventa um ajuste estadual numérico quando não há teto pesquisado aplicável ao produto.";
        }

        BigDecimal effectiveRate = capped(marketRate, cap);
        return new ResolvedPolicy(
                "phase1-financing-ats-state-2026-v2-" + stateCode,
                marketSource,
                LocalDate.of(2026, 5, 1),
                marketBasis,
                effectiveRate,
                ruleSource,
                summary,
                cap,
                "Quitação antecipada é permitida pela política da simulação sem tarifa; qualquer custo legal não pesquisado não é inventado.",
                "Atrasos são acompanhados por parcela/período operacional; a política V2 não inventa multa monetária estadual e usa o ciclo de inadimplência do contrato.",
                BigDecimal.ZERO.setScale(10),
                BigDecimal.ZERO.setScale(10),
                3
        );
    }

    private ResolvedPolicy resolveEts2(CareerEntity career, FinancialProductType productType) {
        String country = normalize(career.getCountryCode());
        MarketReference reference = ETS2_REFERENCES.get(country);
        if (reference == null) {
            throw new IllegalArgumentException("No researched ETS2 financing reference is available for country " + country);
        }

        if ("TR".equals(country) && productType == FinancialProductType.VEHICLE_FINANCING) {
            reference = new MarketReference(rate("0.3760000000"), TURKEY_SOURCE, LocalDate.of(2026, 9, 4), "TCMB_VEHICLE_LOAN_RATE");
        }

        Rule rule = countryRule(country, productType);
        BigDecimal effectiveRate = capped(reference.annualRate(), rule.legalAprCap());
        return new ResolvedPolicy(
                "phase1-financing-ets2-country-2026-v2-" + country,
                reference.source(),
                reference.referenceAsOf(),
                reference.rateBasis(),
                effectiveRate,
                rule.source(),
                rule.summary(),
                rule.legalAprCap(),
                rule.prepaymentSummary(),
                rule.lateSummary(),
                BigDecimal.ZERO.setScale(10),
                BigDecimal.ZERO.setScale(10),
                3
        );
    }

    private Rule countryRule(String country, FinancialProductType productType) {
        if (EU_CONSUMER_CREDIT.contains(country)) {
            return new Rule(
                    EU_CONSUMER_CREDIT_SOURCE,
                    "Crédito ao consumidor sujeito às regras harmonizadas de informação, custo total e direito de reembolso antecipado aplicáveis na jurisdição da UE; a implementação nacional permanece congelada pelo país da carreira.",
                    null,
                    "A simulação permite quitação antecipada. A diretiva europeia prevê redução dos juros/custos futuros e limita compensação em certas situações; o credor da simulação adota tarifa zero.",
                    "A política V2 não inventa multa nacional de atraso; parcelas vencidas alteram o status do contrato e três parcelas não regularizadas levam ao default na simulação."
            );
        }
        return switch (country) {
            case "GB" -> new Rule(
                    "https://www.fca.org.uk/consumers/personal-loans",
                    "Crédito ao consumidor usa a referência de novos empréstimos pessoais do Bank of England e regras de transparência/affordability do mercado britânico.",
                    null,
                    "Quitação antecipada é permitida pela política da simulação sem tarifa adicional.",
                    "A política não inventa encargos de atraso; o atraso é representado pelo status operacional do contrato."
            );
            case "CH" -> new Rule(
                    SWISS_CAP_SOURCE,
                    "A Suíça reduziu para 10% o teto anual pesquisado para crédito em dinheiro ao consumidor a partir de 2026; a oferta nunca pode ultrapassar esse teto.",
                    rate("0.1000000000"),
                    "Quitação antecipada é permitida; a simulação não cobra tarifa de antecipação.",
                    "A política usa o teto legal pesquisado para juros e não inventa multa monetária de atraso."
            );
            case "RS" -> new Rule(
                    SERBIA_SOURCE,
                    "A Sérvia publica limites máximos por tipo/moeda. Para a referência RSD de outros créditos, a política registra o limite efetivo pesquisado e mantém a taxa de mercado abaixo dele.",
                    rate("0.1575000000"),
                    "Quitação antecipada é permitida sem tarifa adicional na política da simulação.",
                    "Atraso/default seguem o cronograma operacional; encargos adicionais não pesquisados não são adicionados."
            );
            case "ME" -> new Rule(
                    MONTENEGRO_SOURCE,
                    "Montenegro limita a taxa efetiva máxima de crédito ao consumidor por fórmula ligada à média ponderada do mercado; a política V2 aplica o teto pesquisado da referência vigente.",
                    rate("0.1224000000"),
                    "Quitação antecipada reduz juros futuros; a simulação não cobra tarifa de antecipação.",
                    "A política não cria encargo adicional além do status de atraso/default."
            );
            case "TR" -> new Rule(
                    TURKEY_SOURCE,
                    productType == FinancialProductType.VEHICLE_FINANCING
                            ? "A Turquia possui referência específica pesquisada para financiamento de veículo; ela é usada separadamente da taxa de empréstimo pessoal."
                            : "A Turquia possui referência específica pesquisada para empréstimos pessoais/necessidades; ela é usada diretamente na oferta.",
                    null,
                    "Quitação antecipada é suportada pela simulação e elimina juros futuros do cronograma.",
                    "Sem multa inventada: atraso é representado por parcela vencida, inadimplência e histórico."
            );
            case "BA" -> simpleNationalRule(BOSNIA_SOURCE, "Bósnia e Herzegovina", "empréstimos não destinados e crédito para compra de bens/veículos são tratados como produtos distintos; a política usa a referência bancária pesquisada.");
            case "XK" -> simpleNationalRule(KOSOVO_SOURCE, "Kosovo", "a política usa a taxa efetiva pesquisada de crédito ao consumidor do sistema bancário.");
            case "MK" -> simpleNationalRule(NORTH_MACEDONIA_SOURCE, "Macedônia do Norte", "a política usa a última referência de crédito ao consumidor pesquisada e mantém sua data explícita até nova atualização.");
            case "AL" -> simpleNationalRule(ALBANIA_SOURCE, "Albânia", "a política usa a média pesquisada de crédito ao consumidor divulgada pelo banco central.");
            case "NO" -> simpleNationalRule(NORWAY_SOURCE, "Noruega", "a política usa uma referência pesquisada de crédito ao consumidor e mantém a fonte/data congeladas no contrato.");
            default -> throw new IllegalArgumentException("No researched ETS2 jurisdiction rule is available for country " + country);
        };
    }

    private Rule simpleNationalRule(String source, String countryName, String marketDetail) {
        return new Rule(
                source,
                countryName + ": " + marketDetail,
                null,
                "Quitação antecipada é suportada pela simulação sem tarifa adicional.",
                "A política não inventa multa monetária local; atraso/default são controlados pelo cronograma operacional."
        );
    }

    private BigDecimal capped(BigDecimal marketRate, BigDecimal cap) {
        return cap == null || marketRate.compareTo(cap) <= 0 ? marketRate : cap;
    }

    private String normalize(String value) {
        return value == null ? "" : value.strip().toUpperCase(Locale.ROOT);
    }

    private String percent(BigDecimal value) {
        return value.multiply(BigDecimal.valueOf(100)).stripTrailingZeros().toPlainString() + "%";
    }

    private static BigDecimal rate(String value) {
        return new BigDecimal(value);
    }

    private static Map.Entry<String, MarketReference> entry(String country, String rate, String source, String basis, String asOf) {
        return Map.entry(country, new MarketReference(rate(rate), source, LocalDate.parse(asOf), basis));
    }

    private static Map.Entry<String, StateRule> state(String code, String smallCap, String mediumCap) {
        return Map.entry(code, new StateRule(smallCap == null ? null : rate(smallCap), mediumCap == null ? null : rate(mediumCap)));
    }

    record ResolvedPolicy(
            String version,
            String marketSource,
            LocalDate referenceAsOf,
            String rateBasis,
            BigDecimal annualRate,
            String jurisdictionRuleSource,
            String jurisdictionRuleSummary,
            BigDecimal legalAprCap,
            String prepaymentRuleSummary,
            String latePaymentRuleSummary,
            BigDecimal prepaymentFeeRate,
            BigDecimal lateFeeRate,
            int maxMissedInstallments
    ) {}

    private record MarketReference(BigDecimal annualRate, String source, LocalDate referenceAsOf, String rateBasis) {}

    private record Rule(
            String source,
            String summary,
            BigDecimal legalAprCap,
            String prepaymentSummary,
            String lateSummary
    ) {}

    private record StateRule(BigDecimal capAtTwoThousand, BigDecimal capAtTenThousand) {
        BigDecimal capFor(BigDecimal amount) {
            return amount.compareTo(BigDecimal.valueOf(2000)) <= 0 ? capAtTwoThousand : capAtTenThousand;
        }
    }
}
