package com.luislipinski.trucklife.finance.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.luislipinski.trucklife.career.domain.CareerGame;
import com.luislipinski.trucklife.career.persistence.CareerEntity;
import com.luislipinski.trucklife.finance.domain.MonthlyExpenseCategory;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FinancePolicyCatalogTest {
    @Test void appliesAtsStateCityAndDisplayCurrencyFactors(){
        CareerEntity az=career(CareerGame.ATS,"AZ",null,"USD","USD","1","1");
        Map<MonthlyExpenseCategory,BigDecimal> azValues=FinancePolicyCatalog.defaults(az);
        assertThat(azValues).hasSize(11);assertThat(azValues.get(MonthlyExpenseCategory.RENT)).isEqualByComparingTo("1287.00");assertThat(azValues.get(MonthlyExpenseCategory.INTERNET)).isEqualByComparingTo("50.70");
        CareerEntity ca=career(CareerGame.ATS,"CA",null,"USD","BRL","5.50","1.30");
        Map<MonthlyExpenseCategory,BigDecimal> caValues=FinancePolicyCatalog.defaults(ca);
        assertThat(caValues.get(MonthlyExpenseCategory.RENT)).isEqualByComparingTo("11797.50");assertThat(caValues.get(MonthlyExpenseCategory.INTERNET)).isEqualByComparingTo("357.50");
    }
    @Test void appliesEtsDetailedAndEffectiveCountryProfiles(){
        assertThat(FinancePolicyCatalog.defaults(career(CareerGame.ETS2,null,"GB","GBP","GBP","1","1")).get(MonthlyExpenseCategory.RENT)).isEqualByComparingTo("1000.00");
        assertThat(FinancePolicyCatalog.defaults(career(CareerGame.ETS2,null,"PL","PLN","EUR","0.23","1")).get(MonthlyExpenseCategory.GROCERIES)).isEqualByComparingTo("322.00");
        assertThat(FinancePolicyCatalog.defaults(career(CareerGame.ETS2,null,"CH","CHF","CHF","1","1")).get(MonthlyExpenseCategory.RENT)).isEqualByComparingTo("1329.95");
    }
    @Test void calculatesWeeklyAndMonthlyReserveYieldWithoutCalendarDates(){
        assertThat(FinancePolicyCatalog.reserveInterest(CareerGame.ATS,new BigDecimal("80"))).isEqualByComparingTo("0.05");
        assertThat(FinancePolicyCatalog.reserveInterest(CareerGame.ETS2,new BigDecimal("1200"))).isEqualByComparingTo("3.25");
        assertThat(FinancePolicyCatalog.reserveInterest(CareerGame.ATS,BigDecimal.ZERO)).isEqualByComparingTo("0.00");
    }
    private CareerEntity career(CareerGame game,String state,String country,String baseCurrency,String displayCurrency,String exchange,String cityFactor){Instant now=Instant.parse("2026-08-27T12:00:00Z");return new CareerEntity(UUID.randomUUID(),UUID.randomUUID(),game,"Driver","Company","",(short)1,new BigDecimal("5000"),baseCurrency,displayCurrency,new BigDecimal(exchange),LocalDate.of(2026,8,27),state,country,"Test city",null,null,"1","test",new BigDecimal(cityFactor),BigDecimal.ONE,1,game==CareerGame.ETS2?1:null,now,now);}
}
