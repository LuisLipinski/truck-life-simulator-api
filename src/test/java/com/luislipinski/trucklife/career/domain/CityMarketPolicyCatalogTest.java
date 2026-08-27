package com.luislipinski.trucklife.career.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CityMarketPolicyCatalogTest {

    @Test
    void resolvesAtsTierOverridesAndSmallerMarkets() {
        CityMarketPolicyCatalog.Profile phoenix = CityMarketPolicyCatalog.resolve(
                CareerGame.ATS,
                "AZ",
                null,
                "Phoenix, AZ"
        );
        assertThat(phoenix.key()).isEqualTo("major");
        assertThat(phoenix.costFactor()).isEqualByComparingTo("1.10");
        assertThat(phoenix.salaryFactor()).isEqualByComparingTo("1.05");
        assertThat(phoenix.known()).isTrue();

        CityMarketPolicyCatalog.Profile sanFrancisco = CityMarketPolicyCatalog.resolve(
                CareerGame.ATS,
                "CA",
                null,
                "San Francisco, CA"
        );
        assertThat(sanFrancisco.key()).isEqualTo("custom");
        assertThat(sanFrancisco.costFactor()).isEqualByComparingTo("1.30");
        assertThat(sanFrancisco.salaryFactor()).isEqualByComparingTo("1.10");

        CityMarketPolicyCatalog.Profile campVerde = CityMarketPolicyCatalog.resolve(
                CareerGame.ATS,
                "AZ",
                null,
                "Camp Verde, AZ"
        );
        assertThat(campVerde.key()).isEqualTo("smaller");
        assertThat(campVerde.costFactor()).isEqualByComparingTo("0.88");
        assertThat(campVerde.salaryFactor()).isEqualByComparingTo("0.96");
    }

    @Test
    void resolvesEts2TierOverridesAndSmallerMarkets() {
        CityMarketPolicyCatalog.Profile berlin = CityMarketPolicyCatalog.resolve(
                CareerGame.ETS2,
                null,
                "DE",
                "Berlin, Alemanha"
        );
        assertThat(berlin.key()).isEqualTo("major");
        assertThat(berlin.costFactor()).isEqualByComparingTo("1.10");
        assertThat(berlin.salaryFactor()).isEqualByComparingTo("1.05");
        assertThat(berlin.known()).isTrue();

        CityMarketPolicyCatalog.Profile london = CityMarketPolicyCatalog.resolve(
                CareerGame.ETS2,
                null,
                "GB",
                "Londres, Reino Unido"
        );
        assertThat(london.key()).isEqualTo("custom");
        assertThat(london.costFactor()).isEqualByComparingTo("1.28");
        assertThat(london.salaryFactor()).isEqualByComparingTo("1.10");

        CityMarketPolicyCatalog.Profile carlisle = CityMarketPolicyCatalog.resolve(
                CareerGame.ETS2,
                null,
                "GB",
                "Carlisle, Reino Unido"
        );
        assertThat(carlisle.key()).isEqualTo("smaller");
        assertThat(carlisle.costFactor()).isEqualByComparingTo("0.90");
        assertThat(carlisle.salaryFactor()).isEqualByComparingTo("0.97");
    }

    @Test
    void usesNeutralReferenceForModCitiesInsteadOfExternalFactors() {
        CityMarketPolicyCatalog.Profile modCity = CityMarketPolicyCatalog.resolve(
                CareerGame.ATS,
                "AZ",
                null,
                "Modville, AZ"
        );

        assertThat(modCity.key()).isEqualTo("reference");
        assertThat(modCity.label()).isEqualTo("Referência da sede para cidade de mod");
        assertThat(modCity.costFactor()).isEqualByComparingTo("1");
        assertThat(modCity.salaryFactor()).isEqualByComparingTo("1");
        assertThat(modCity.known()).isFalse();
    }

    @Test
    void rejectsKnownCityThatDoesNotBelongToCareerRegion() {
        assertThatThrownBy(() -> CityMarketPolicyCatalog.resolve(
                CareerGame.ATS,
                "CA",
                null,
                "Phoenix, AZ"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("career state");

        assertThatThrownBy(() -> CityMarketPolicyCatalog.resolve(
                CareerGame.ETS2,
                null,
                "FR",
                "Berlin, Alemanha"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("career country");
    }
}
