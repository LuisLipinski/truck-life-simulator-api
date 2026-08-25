package com.luislipinski.trucklife.career.persistence;

import com.luislipinski.trucklife.career.domain.CareerGame;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "careers")
public class CareerEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "game_id", nullable = false, length = 10)
    private CareerGame game;

    @Column(name = "driver_name", nullable = false, length = 120)
    private String driverName;

    @Column(name = "company_name", length = 160)
    private String companyName;

    @Column(columnDefinition = "text")
    private String biography;

    @Column(name = "current_level", nullable = false)
    private short currentLevel;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal balance;

    @Column(name = "base_currency", nullable = false, length = 3)
    private String baseCurrency;

    @Column(name = "display_currency", nullable = false, length = 3)
    private String displayCurrency;

    @Column(name = "exchange_rate", nullable = false, precision = 18, scale = 8)
    private BigDecimal exchangeRate;

    @Column(name = "exchange_rate_as_of")
    private LocalDate exchangeRateAsOf;

    @Column(name = "state_code", length = 10)
    private String stateCode;

    @Column(name = "country_code", length = 10)
    private String countryCode;

    @Column(name = "base_city", nullable = false, length = 160)
    private String baseCity;

    @Column(name = "city_market_version", length = 40)
    private String cityMarketVersion;

    @Column(name = "city_market_label", length = 100)
    private String cityMarketLabel;

    @Column(name = "city_cost_factor", precision = 8, scale = 4)
    private BigDecimal cityCostFactor;

    @Column(name = "city_salary_factor", precision = 8, scale = 4)
    private BigDecimal citySalaryFactor;

    @Column(name = "current_operational_week", nullable = false)
    private int currentOperationalWeek;

    @Column(name = "current_payroll_month")
    private Integer currentPayrollMonth;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected CareerEntity() {
    }

    public CareerEntity(
            UUID id,
            UUID userId,
            CareerGame game,
            String driverName,
            String companyName,
            String biography,
            short currentLevel,
            BigDecimal balance,
            String baseCurrency,
            String displayCurrency,
            BigDecimal exchangeRate,
            LocalDate exchangeRateAsOf,
            String stateCode,
            String countryCode,
            String baseCity,
            String cityMarketVersion,
            String cityMarketLabel,
            BigDecimal cityCostFactor,
            BigDecimal citySalaryFactor,
            int currentOperationalWeek,
            Integer currentPayrollMonth,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.userId = userId;
        this.game = game;
        this.driverName = driverName;
        this.companyName = companyName;
        this.biography = biography;
        this.currentLevel = currentLevel;
        this.balance = balance;
        this.baseCurrency = baseCurrency;
        this.displayCurrency = displayCurrency;
        this.exchangeRate = exchangeRate;
        this.exchangeRateAsOf = exchangeRateAsOf;
        this.stateCode = stateCode;
        this.countryCode = countryCode;
        this.baseCity = baseCity;
        this.cityMarketVersion = cityMarketVersion;
        this.cityMarketLabel = cityMarketLabel;
        this.cityCostFactor = cityCostFactor;
        this.citySalaryFactor = citySalaryFactor;
        this.currentOperationalWeek = currentOperationalWeek;
        this.currentPayrollMonth = currentPayrollMonth;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public CareerGame getGame() {
        return game;
    }

    public String getDriverName() {
        return driverName;
    }

    public String getCompanyName() {
        return companyName;
    }

    public String getBiography() {
        return biography;
    }

    public short getCurrentLevel() {
        return currentLevel;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public String getBaseCurrency() {
        return baseCurrency;
    }

    public String getDisplayCurrency() {
        return displayCurrency;
    }

    public BigDecimal getExchangeRate() {
        return exchangeRate;
    }

    public LocalDate getExchangeRateAsOf() {
        return exchangeRateAsOf;
    }

    public String getStateCode() {
        return stateCode;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public String getBaseCity() {
        return baseCity;
    }

    public String getCityMarketVersion() {
        return cityMarketVersion;
    }

    public String getCityMarketLabel() {
        return cityMarketLabel;
    }

    public BigDecimal getCityCostFactor() {
        return cityCostFactor;
    }

    public BigDecimal getCitySalaryFactor() {
        return citySalaryFactor;
    }

    public int getCurrentOperationalWeek() {
        return currentOperationalWeek;
    }

    public Integer getCurrentPayrollMonth() {
        return currentPayrollMonth;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }
}
