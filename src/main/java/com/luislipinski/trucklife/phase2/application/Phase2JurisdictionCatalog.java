package com.luislipinski.trucklife.phase2.application;

import com.luislipinski.trucklife.career.domain.CareerGame;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class Phase2JurisdictionCatalog {
    private static final Set<String> ATS_STATES = Set.of(
            "AZ","AR","CA","CO","ID","IL","IA","KS","LA","MO","MT","NE","NV","NM","OK","OR","TX","UT","WA","WY"
    );
    private static final Set<String> ETS2_COUNTRIES = Set.of(
            "FR","NL","BE","LU","CH","AT","IT","PT","ES","CZ","SK","HU","DK","NO","SE","FI","EE","LV","LT",
            "RO","BG","TR","SI","HR","BA","RS","ME","XK","MK","AL","GR","DE","GB","PL"
    );

    public Jurisdiction normalizeAndValidate(
            CareerGame game,
            String countryCode,
            String stateCode,
            String fallbackCountryCode,
            String fallbackStateCode
    ) {
        if (game == CareerGame.ATS) {
            String state = normalize(stateCode == null || stateCode.isBlank() ? fallbackStateCode : stateCode);
            if (!ATS_STATES.contains(state)) {
                throw new IllegalArgumentException("Unsupported ATS carrier state: " + state);
            }
            String country = normalize(countryCode);
            if (!country.isBlank() && !"US".equals(country)) {
                throw new IllegalArgumentException("ATS carrier jurisdiction must use the United States");
            }
            return new Jurisdiction("US", state, "ATS:" + state);
        }

        String country = normalize(countryCode == null || countryCode.isBlank() ? fallbackCountryCode : countryCode);
        if (!ETS2_COUNTRIES.contains(country)) {
            throw new IllegalArgumentException("Unsupported ETS2 carrier country: " + country);
        }
        String state = normalize(stateCode);
        if (!state.isBlank()) {
            throw new IllegalArgumentException("ETS2 carrier jurisdiction uses country, not state");
        }
        return new Jurisdiction(country, null, "ETS2:" + country);
    }

    private String normalize(String value) {
        return value == null ? "" : value.strip().toUpperCase(Locale.ROOT);
    }

    public record Jurisdiction(String countryCode, String stateCode, String compensationRuleKey) {}
}
