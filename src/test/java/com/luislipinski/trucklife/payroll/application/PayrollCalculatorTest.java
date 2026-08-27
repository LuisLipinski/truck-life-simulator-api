package com.luislipinski.trucklife.payroll.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.luislipinski.trucklife.career.domain.CareerGame;
import com.luislipinski.trucklife.payroll.domain.PayslipLineType;
import com.luislipinski.trucklife.trip.domain.TripPaymentCategory;
import com.luislipinski.trucklife.trip.domain.TripSource;
import com.luislipinski.trucklife.trip.domain.TripType;
import com.luislipinski.trucklife.trip.persistence.TripEntity;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PayrollCalculatorTest {
    private final PayrollCalculator calculator = new PayrollCalculator();

    @Test
    void calculatesAtsLevelOneSalaryRouteOverrunBenefitsTaxesAndPerDiem() {
        var result = calculator.calculate(CareerGame.ATS, context((short)1,"AZ","","USD","USD","1","1"),
                List.of(trip(DayOfWeek.MONDAY,"08:00",DayOfWeek.TUESDAY,"08:00",TripPaymentCategory.NORMAL,"100",0)));
        assertThat(result.gross()).isEqualByComparingTo("1104.00");
        assertThat(result.benefits()).isEqualByComparingTo("36.00");
        assertThat(result.perDiem()).isEqualByComparingTo("160.00");
        assertThat(result.overrunMinutes()).isEqualTo(480);
        assertThat(result.deposit()).isEqualByComparingTo(result.netSalary().add(result.perDiem()));
        assertThat(result.lines()).extracting(PayrollCalculator.Line::code)
                .contains("BASE_SALARY","ROUTE_OVERRUN","PER_DIEM","FEDERAL_TAX","BENEFITS");
    }

    @Test
    void calculatesAtsMileageCategoriesWithRegionalAndCityFactors() {
        var result = calculator.calculate(CareerGame.ATS, context((short)3,"CA","","USD","USD","1","1.10"), List.of(
                trip(DayOfWeek.MONDAY,"08:00",DayOfWeek.MONDAY,"12:00",TripPaymentCategory.NORMAL,"100",0),
                trip(DayOfWeek.TUESDAY,"08:00",DayOfWeek.TUESDAY,"12:00",TripPaymentCategory.HAZMAT,"100",0),
                trip(DayOfWeek.WEDNESDAY,"08:00",DayOfWeek.WEDNESDAY,"12:00",TripPaymentCategory.DOUBLES,"100",0),
                trip(DayOfWeek.THURSDAY,"08:00",DayOfWeek.THURSDAY,"12:00",TripPaymentCategory.HAZMAT_DOUBLES,"100",0),
                trip(DayOfWeek.FRIDAY,"08:00",DayOfWeek.FRIDAY,"12:00",TripPaymentCategory.DEADHEAD,"100",0)));
        assertThat(result.totalDistance()).isEqualByComparingTo("500.00");
        assertThat(result.gross()).isPositive();
        assertThat(result.benefits()).isEqualByComparingTo("36.00");
        assertThat(result.lines()).filteredOn(line -> line.type() == PayslipLineType.EARNING)
                .extracting(PayrollCalculator.Line::code)
                .contains("MILEAGE_NORMAL","MILEAGE_HAZMAT","MILEAGE_DOUBLES","MILEAGE_HAZMAT_DOUBLES","MILEAGE_DEADHEAD");
    }

    @Test
    void usesCalendarlessScheduleAndSuggestedEts2Breaks() {
        var result = calculator.calculate(CareerGame.ETS2, context((short)1,"","DE","EUR","EUR","1","1"),
                List.of(trip(DayOfWeek.SUNDAY,"20:00",DayOfWeek.MONDAY,"07:00",TripPaymentCategory.NORMAL,"100",null)));
        assertThat(result.elapsedMinutes()).isEqualTo(660);
        assertThat(result.breakMinutes()).isEqualTo(90);
        assertThat(result.workedMinutes()).isEqualTo(570);
        assertThat(result.gross()).isPositive();
    }

    @Test
    void exercisesAllConfiguredAtsAndEts2TaxPolicies() {
        for (String state : List.of("AZ","AR","CA","CO","ID","IL","IA","KS","LA","MO","MT","NE","NV","NM","OK","OR","TX","UT","WA","WY")) {
            var result = calculator.calculate(CareerGame.ATS, context((short)1,state,"","USD","USD","1","1"), List.of());
            assertThat(result.gross()).isPositive(); assertThat(result.taxTotal()).isNotNegative();
        }
        for (Country country : List.of(
                new Country("DE","EUR"),new Country("GB","GBP"),new Country("PL","PLN"),new Country("FR","EUR"),
                new Country("NL","EUR"),new Country("BE","EUR"),new Country("LU","EUR"),new Country("CH","CHF"),
                new Country("AT","EUR"),new Country("IT","EUR"),new Country("PT","EUR"),new Country("ES","EUR"),
                new Country("CZ","CZK"),new Country("SK","EUR"),new Country("HU","HUF"),new Country("DK","DKK"),
                new Country("NO","NOK"),new Country("SE","SEK"),new Country("FI","EUR"),new Country("EE","EUR"),
                new Country("LV","EUR"),new Country("LT","EUR"),new Country("RO","RON"),new Country("BG","EUR"),
                new Country("TR","TRY"),new Country("SI","EUR"),new Country("HR","EUR"),new Country("BA","BAM"),
                new Country("RS","RSD"),new Country("ME","EUR"),new Country("XK","EUR"),new Country("MK","MKD"),
                new Country("AL","ALL"),new Country("GR","EUR"))) {
            var result = calculator.calculate(CareerGame.ETS2,
                    context((short)1,"",country.code(),country.currency(),country.currency(),"1","1"), List.of());
            assertThat(result.gross()).isPositive(); assertThat(result.taxTotal()).isNotNegative();
        }
    }

    @Test
    void convertsFromFiscalToDisplayCurrencyBeforeReturningLines() {
        var result = calculator.calculate(CareerGame.ETS2, context((short)2,"","DE","EUR","BRL","6.50","1"),
                List.of(trip(DayOfWeek.MONDAY,"08:00",DayOfWeek.MONDAY,"12:00",TripPaymentCategory.NORMAL,"100",0)));
        assertThat(result.gross()).isEqualByComparingTo("195.00");
        assertThat(result.lines()).filteredOn(line -> line.code().equals("MILEAGE_NORMAL")).singleElement()
                .satisfies(line -> assertThat(line.rate()).isEqualByComparingTo("1.9500"));
    }

    @Test
    void rejectsUnknownOrInconsistentFiscalContexts() {
        assertThatThrownBy(() -> calculator.calculate(CareerGame.ATS,
                context((short)1,"XX","","USD","USD","1","1"), List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> calculator.calculate(CareerGame.ETS2,
                context((short)1,"","DE","GBP","GBP","1","1"), List.of())).isInstanceOf(IllegalArgumentException.class);
    }

    private PayrollCalculator.Context context(short level,String state,String country,String base,String display,String rate,String factor) {
        return new PayrollCalculator.Context(level,state,country,base,display,new BigDecimal(rate),new BigDecimal(factor));
    }
    private TripEntity trip(DayOfWeek departureDay,String departureTime,DayOfWeek arrivalDay,String arrivalTime,
                            TripPaymentCategory category,String distance,Integer breakMinutes) {
        Instant now=Instant.parse("2026-08-26T12:00:00Z");
        return new TripEntity(UUID.randomUUID(),UUID.randomUUID(),1,departureDay,LocalTime.parse(departureTime),arrivalDay,
                LocalTime.parse(arrivalTime),"Origin","Employer","Destination","Customer","Cargo",
                category == TripPaymentCategory.DEADHEAD ? TripType.DEADHEAD : TripType.LOADED, category,
                new BigDecimal(distance),breakMinutes,null,null,null,null,TripSource.MANUAL,"{}","{}",now,now);
    }
    private record Country(String code,String currency) {}
}
