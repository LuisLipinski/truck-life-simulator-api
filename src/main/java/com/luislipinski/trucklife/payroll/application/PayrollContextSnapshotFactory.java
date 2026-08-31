package com.luislipinski.trucklife.payroll.application;

import com.luislipinski.trucklife.career.domain.CityMarketPolicyCatalog;
import com.luislipinski.trucklife.career.persistence.CareerEntity;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
final class PayrollContextSnapshotFactory {

    Map<String, Object> from(CareerEntity career) {
        CityMarketPolicyCatalog.Profile cityMarket = CityMarketPolicyCatalog.resolve(
                career.getGame(),
                career.getStateCode(),
                career.getCountryCode(),
                career.getBaseCity()
        );

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("currentLevel", career.getCurrentLevel());
        snapshot.put("companyName", textOrEmpty(career.getCompanyName()));
        snapshot.put("stateCode", textOrEmpty(career.getStateCode()));
        snapshot.put("countryCode", textOrEmpty(career.getCountryCode()));
        snapshot.put("baseCity", career.getBaseCity());
        snapshot.put("baseCurrency", career.getBaseCurrency());
        snapshot.put("displayCurrency", career.getDisplayCurrency());
        snapshot.put("exchangeRate", career.getExchangeRate());
        snapshot.put(
                "exchangeRateAsOf",
                career.getExchangeRateAsOf() == null ? "" : career.getExchangeRateAsOf().toString()
        );
        snapshot.put("cityMarketVersion", CityMarketPolicyCatalog.VERSION);
        snapshot.put("cityMarketKey", cityMarket.key());
        snapshot.put("cityMarketLabel", cityMarket.label());
        snapshot.put("cityMarketKnown", cityMarket.known());
        snapshot.put("cityCostFactor", cityMarket.costFactor());
        snapshot.put("citySalaryFactor", cityMarket.salaryFactor());
        snapshot.put("payrollLevel1GrossOverride", career.getPayrollLevel1GrossOverride());
        snapshot.put("payrollRouteOverrunRateOverride", career.getPayrollRouteOverrunRateOverride());
        snapshot.put("payrollBenefitsOverride", career.getPayrollBenefitsOverride());
        snapshot.put("payrollPerDiemRateOverride", career.getPayrollPerDiemRateOverride());
        return snapshot;
    }

    private String textOrEmpty(String value) {
        return value == null ? "" : value;
    }
}
