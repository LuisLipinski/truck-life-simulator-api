package com.luislipinski.trucklife.qualification.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.luislipinski.trucklife.career.domain.CareerGame;
import com.luislipinski.trucklife.qualification.domain.AcademyModuleCode;
import com.luislipinski.trucklife.qualification.domain.QualificationType;
import java.util.List;
import org.junit.jupiter.api.Test;

class QualificationPolicyCatalogTest {

    @Test
    void resolvesAuthoritativeAtsProgressionPolicy() {
        var policy = QualificationPolicyCatalog.resolve(CareerGame.ATS, null);
        assertThat(policy.baseCurrency()).isEqualTo("USD");
        assertThat(policy.level2().targetLevel()).isEqualTo((short) 2);
        assertThat(policy.level2().moduleCode()).isEqualTo(AcademyModuleCode.TRUCK_DRIVING_PROFICIENCY);
        assertThat(policy.level2().requiredDistance()).isEqualByComparingTo("10000");
        assertThat(policy.level2().baseFee()).isEqualByComparingTo("300.00");
        assertThat(policy.level3().requiredDistance()).isEqualByComparingTo("50000");
        assertThat(policy.level3().baseFee()).isEqualByComparingTo("59.00");
        assertThat(policy.dangerous().type()).isEqualTo(QualificationType.HAZMAT);
        assertThat(policy.dangerous().baseFee()).isEqualByComparingTo("144.25");
    }

    @Test
    void resolvesEverySupportedEts2CountryAndCountrySpecificFees() {
        List<String> countries = List.of(
                "DE","GB","PL","FR","NL","BE","LU","CH","AT","IT","PT","ES","CZ","SK","HU","DK","NO",
                "SE","FI","EE","LV","LT","RO","BG","TR","SI","HR","BA","RS","ME","XK","MK","AL","GR"
        );
        assertThat(QualificationPolicyCatalog.supportedEts2CountryCount()).isEqualTo(34);
        assertThat(countries).allSatisfy(code -> {
            var policy = QualificationPolicyCatalog.resolve(CareerGame.ETS2, code);
            assertThat(policy.baseCurrency()).isNotBlank();
            assertThat(policy.level2().baseFee()).isPositive();
            assertThat(policy.level3().baseFee()).isPositive();
            assertThat(policy.dangerous().type()).isEqualTo(QualificationType.ADR);
            assertThat(policy.dangerous().baseFee()).isPositive();
        });

        var gb = QualificationPolicyCatalog.resolve(CareerGame.ETS2, "GB");
        assertThat(gb.baseCurrency()).isEqualTo("GBP");
        assertThat(gb.level2().baseFee()).isEqualByComparingTo("260.00");
        assertThat(gb.level3().baseFee()).isEqualByComparingTo("50.00");
        assertThat(gb.dangerous().baseFee()).isEqualByComparingTo("110.00");

        var pl = QualificationPolicyCatalog.resolve(CareerGame.ETS2, "PL");
        assertThat(pl.baseCurrency()).isEqualTo("PLN");
        assertThat(pl.level2().baseFee()).isEqualByComparingTo("1200.00");
        assertThat(pl.dangerous().baseFee()).isEqualByComparingTo("500.00");

        var ch = QualificationPolicyCatalog.resolve(CareerGame.ETS2, "CH");
        assertThat(ch.baseCurrency()).isEqualTo("CHF");
        assertThat(ch.level2().baseFee()).isEqualByComparingTo("419.99");
        assertThat(ch.level3().baseFee()).isEqualByComparingTo("84.00");
        assertThat(ch.dangerous().baseFee()).isEqualByComparingTo("174.99");
    }
}
