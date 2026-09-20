package com.luislipinski.trucklife.finance.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.luislipinski.trucklife.career.domain.CareerGame;
import com.luislipinski.trucklife.career.persistence.CareerEntity;
import com.luislipinski.trucklife.finance.domain.FinancialPaymentFrequency;
import com.luislipinski.trucklife.finance.domain.FinancialProductType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FinancingPolicyCatalogTest {
    private static final List<String> ATS_STATES=List.of(
            "AZ","AR","CA","CO","ID","IL","IA","KS","LA","MO","MT","NE","NV","NM","OK","OR","TX","UT","WA","WY"
    );
    private static final List<String> ETS2_COUNTRIES=List.of(
            "DE","GB","PL","FR","NL","BE","LU","CH","AT","IT","PT","ES","CZ","SK","HU","DK","NO","SE",
            "FI","EE","LV","LT","RO","BG","TR","SI","HR","BA","RS","ME","XK","MK","AL","GR"
    );

    private final FinancingPolicyCatalog catalog=new FinancingPolicyCatalog(new FinancingJurisdictionCatalog());

    @Test void usesFedMarketReferenceAndFreezesTheAtsStateRule(){
        CareerEntity career=career(CareerGame.ATS,"USD","US","AR","Little Rock, AR");
        var personal=catalog.offers(career,FinancialProductType.PERSONAL_LOAN,new BigDecimal("2000.00"));
        var vehicle=catalog.offers(career,FinancialProductType.VEHICLE_FINANCING,new BigDecimal("100000.00"));

        assertThat(personal).hasSize(2);
        assertThat(personal.getFirst().annualInterestRate()).isEqualByComparingTo("0.1186000000");
        assertThat(personal.getFirst().legalAprCap()).isEqualByComparingTo("0.17");
        assertThat(personal.getFirst().jurisdictionStateCode()).isEqualTo("AR");
        assertThat(personal.getFirst().jurisdictionRuleSource()).contains("nclc.org");
        assertThat(personal.getFirst().paymentFrequency()).isEqualTo(FinancialPaymentFrequency.WEEKLY);
        assertThat(personal.getFirst().prepaymentFeeRate()).isZero();
        assertThat(personal.getFirst().maxMissedInstallments()).isEqualTo(3);

        assertThat(vehicle).hasSize(3);
        assertThat(vehicle.getFirst().annualInterestRate()).isEqualByComparingTo("0.0714000000");
        assertThat(vehicle.getFirst().principal()).isEqualByComparingTo("80000.00");
        assertThat(vehicle.getFirst().downPayment()).isEqualByComparingTo("20000.00");
        assertThat(vehicle.getFirst().downPaymentRate()).isEqualByComparingTo("0.2000000000");
        assertThat(vehicle.getFirst().policySource()).isEqualTo("https://fred.stlouisfed.org/series/RIFLPBCIANM60NM");
    }

    @Test void resolvesEverySupportedAtsStateWithoutInventingAnUnknownState(){
        for(String state:ATS_STATES){
            var offers=catalog.offers(career(CareerGame.ATS,"USD","US",state,"City, "+state),FinancialProductType.PERSONAL_LOAN,new BigDecimal("5000.00"));
            assertThat(offers).hasSize(2);
            assertThat(offers.getFirst().jurisdictionStateCode()).isEqualTo(state);
            assertThat(offers.getFirst().jurisdictionRuleSummary()).isNotBlank();
        }
        assertThatThrownBy(()->catalog.offers(career(CareerGame.ATS,"USD","US","XX","Unknown, XX"),FinancialProductType.PERSONAL_LOAN,new BigDecimal("5000.00")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No researched ATS financing rule");
    }

    @Test void resolvesEverySupportedEts2CountryAndUsesCountrySpecificReferences(){
        for(String country:ETS2_COUNTRIES){
            var offers=catalog.offers(career(CareerGame.ETS2,"EUR",country,null,"Capital"),FinancialProductType.PERSONAL_LOAN,new BigDecimal("5000.00"));
            assertThat(offers).hasSize(3);
            assertThat(offers.getFirst().jurisdictionCountryCode()).isEqualTo(country);
            assertThat(offers.getFirst().annualInterestRate()).isPositive();
            assertThat(offers.getFirst().jurisdictionRuleSummary()).isNotBlank();
        }

        var uk=catalog.offers(career(CareerGame.ETS2,"GBP","GB",null,"London"),FinancialProductType.PERSONAL_LOAN,new BigDecimal("5000.00")).getFirst();
        assertThat(uk.annualInterestRate()).isEqualByComparingTo("0.0967000000");
        assertThat(uk.rateBasis()).isEqualTo("BOE_NEW_PERSONAL_LOANS_TO_INDIVIDUALS");

        var poland=catalog.offers(career(CareerGame.ETS2,"PLN","PL",null,"Warsaw"),FinancialProductType.PERSONAL_LOAN,new BigDecimal("5000.00")).getFirst();
        assertThat(poland.annualInterestRate()).isEqualByComparingTo("0.1027000000");
        assertThat(poland.jurisdictionRuleSource()).contains("eur-lex.europa.eu");

        var switzerland=catalog.offers(career(CareerGame.ETS2,"CHF","CH",null,"Zürich"),FinancialProductType.PERSONAL_LOAN,new BigDecimal("5000.00")).getFirst();
        assertThat(switzerland.legalAprCap()).isEqualByComparingTo("0.1000000000");
        assertThat(switzerland.annualInterestRate()).isLessThanOrEqualTo(switzerland.legalAprCap());

        CareerEntity turkey=career(CareerGame.ETS2,"TRY","TR",null,"İstanbul");
        assertThat(catalog.offers(turkey,FinancialProductType.PERSONAL_LOAN,new BigDecimal("5000.00")).getFirst().annualInterestRate()).isEqualByComparingTo("0.6300000000");
        assertThat(catalog.offers(turkey,FinancialProductType.VEHICLE_FINANCING,new BigDecimal("5000.00")).getFirst().annualInterestRate()).isEqualByComparingTo("0.3760000000");
    }

    @Test void buildsFullyAmortizingSchedulesAndRejectsArbitraryTerms(){
        FinancingPolicyCatalog.Plan plan=catalog.plan(new BigDecimal("10000.00"),new BigDecimal("0.1186000000"),FinancialPaymentFrequency.WEEKLY,52);
        assertThat(plan.periods()).hasSize(52);
        assertThat(plan.periods().stream().map(FinancingPolicyCatalog.PeriodAmount::principal).reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo("10000.00");

        CareerEntity career=career(CareerGame.ATS,"USD","US","CA","Los Angeles, CA");
        assertThatThrownBy(()->catalog.offer(career,FinancialProductType.PERSONAL_LOAN,new BigDecimal("1000.00"),12)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->catalog.offers(career,FinancialProductType.PERSONAL_LOAN,new BigDecimal("0.50"))).isInstanceOf(IllegalArgumentException.class);
    }

    private CareerEntity career(CareerGame game,String currency,String country,String state,String city){
        Instant now=Instant.parse("2026-08-28T12:00:00Z");
        return new CareerEntity(
                UUID.randomUUID(),UUID.randomUUID(),game,"Driver","Company",null,(short)1,new BigDecimal("10000.00"),
                currency,currency,BigDecimal.ONE.setScale(8),LocalDate.of(2026,8,28),state,country,city,null,null,
                "test-v1","Test",BigDecimal.ONE.setScale(4),BigDecimal.ONE.setScale(4),1,game==CareerGame.ETS2?1:null,now,now
        );
    }
}
