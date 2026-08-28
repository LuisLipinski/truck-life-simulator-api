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
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FinancingPolicyCatalogTest {
    private final FinancingPolicyCatalog catalog=new FinancingPolicyCatalog();

    @Test void usesFedProductSpecificReferencesForAtsAndBuildsFullyAmortizingSchedules(){
        CareerEntity career=career(CareerGame.ATS,"USD","US","AZ","Phoenix, AZ");
        var personal=catalog.offers(career,FinancialProductType.PERSONAL_LOAN,new BigDecimal("10000.00"));
        var vehicle=catalog.offers(career,FinancialProductType.VEHICLE_FINANCING,new BigDecimal("100000.00"));
        assertThat(personal).hasSize(2);assertThat(personal.getFirst().annualInterestRate()).isEqualByComparingTo("0.1186000000");assertThat(personal.getFirst().paymentFrequency()).isEqualTo(FinancialPaymentFrequency.WEEKLY);assertThat(personal.getFirst().downPayment()).isEqualByComparingTo("0.00");
        assertThat(vehicle).hasSize(3);assertThat(vehicle.getFirst().annualInterestRate()).isEqualByComparingTo("0.0714000000");assertThat(vehicle.getFirst().principal()).isEqualByComparingTo("80000.00");assertThat(vehicle.getFirst().downPayment()).isEqualByComparingTo("20000.00");
        FinancingPolicyCatalog.Plan plan=catalog.plan(new BigDecimal("10000.00"),new BigDecimal("0.1186000000"),FinancialPaymentFrequency.WEEKLY,52);assertThat(plan.periods()).hasSize(52);assertThat(plan.periods().stream().map(FinancingPolicyCatalog.PeriodAmount::principal).reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo("10000.00");
    }

    @Test void usesCountrySpecificEcbReferencesAndRefusesUnresearchedJurisdictions(){
        CareerEntity germany=career(CareerGame.ETS2,"EUR","DE",null,"Berlin");var offer=catalog.offers(germany,FinancialProductType.PERSONAL_LOAN,new BigDecimal("5000.00")).getFirst();assertThat(offer.annualInterestRate()).isEqualByComparingTo("0.0812000000");assertThat(offer.paymentFrequency()).isEqualTo(FinancialPaymentFrequency.MONTHLY);assertThat(offer.policySource()).contains("ecb.europa.eu");
        CareerEntity uk=career(CareerGame.ETS2,"GBP","GB",null,"London");assertThat(catalog.offers(uk,FinancialProductType.VEHICLE_FINANCING,new BigDecimal("25000.00")).getFirst().annualInterestRate()).isEqualByComparingTo("0.0535000000");
        CareerEntity poland=career(CareerGame.ETS2,"PLN","PL",null,"Warsaw");assertThatThrownBy(()->catalog.offers(poland,FinancialProductType.PERSONAL_LOAN,new BigDecimal("5000.00"))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("refuses to invent");
    }

    @Test void rejectsArbitraryTermsAndSubCurrencyAmounts(){CareerEntity career=career(CareerGame.ATS,"USD","US","CA","Los Angeles, CA");assertThatThrownBy(()->catalog.offer(career,FinancialProductType.PERSONAL_LOAN,new BigDecimal("1000.00"),12)).isInstanceOf(IllegalArgumentException.class);assertThatThrownBy(()->catalog.offers(career,FinancialProductType.PERSONAL_LOAN,new BigDecimal("0.50"))).isInstanceOf(IllegalArgumentException.class);}

    private CareerEntity career(CareerGame game,String currency,String country,String state,String city){Instant now=Instant.parse("2026-08-28T12:00:00Z");return new CareerEntity(UUID.randomUUID(),UUID.randomUUID(),game,"Driver","Company",null,(short)1,new BigDecimal("10000.00"),currency,currency,BigDecimal.ONE.setScale(8),LocalDate.of(2026,8,28),state,country,city,null,null,"test-v1","Test",BigDecimal.ONE.setScale(4),BigDecimal.ONE.setScale(4),1,game==CareerGame.ETS2?1:null,now,now);}
}
