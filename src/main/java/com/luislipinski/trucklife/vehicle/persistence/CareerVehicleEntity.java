package com.luislipinski.trucklife.vehicle.persistence;

import com.luislipinski.trucklife.vehicle.domain.VehicleAcquisitionType;
import com.luislipinski.trucklife.vehicle.domain.VehicleStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "career_vehicles")
public class CareerVehicleEntity {
    @Id private UUID id;
    @Column(name = "career_id", nullable = false) private UUID careerId;
    @Column(name = "acquisition_operation_id", nullable = false, unique = true) private UUID acquisitionOperationId;
    @Column(nullable = false, length = 80) private String make;
    @Column(nullable = false, length = 120) private String model;
    @Column(name = "model_year") private Short modelYear;
    @Column(name = "initial_odometer", nullable = false, precision = 16, scale = 3) private BigDecimal initialOdometer;
    @Column(name = "purchase_price", nullable = false, precision = 14, scale = 2) private BigDecimal purchasePrice;
    @Column(name = "display_currency", nullable = false, length = 3) private String displayCurrency;
    @Enumerated(EnumType.STRING) @Column(name = "acquisition_type", nullable = false, length = 20) private VehicleAcquisitionType acquisitionType;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private VehicleStatus status;
    @Column(name = "acquired_operational_week", nullable = false) private int acquiredOperationalWeek;
    @Column(name = "acquired_payroll_month") private Integer acquiredPayrollMonth;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version @Column(nullable = false) private long version;

    protected CareerVehicleEntity() {}

    public CareerVehicleEntity(
            UUID id,
            UUID careerId,
            UUID acquisitionOperationId,
            String make,
            String model,
            Short modelYear,
            BigDecimal initialOdometer,
            BigDecimal purchasePrice,
            String displayCurrency,
            VehicleAcquisitionType acquisitionType,
            VehicleStatus status,
            int acquiredOperationalWeek,
            Integer acquiredPayrollMonth,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.careerId = careerId;
        this.acquisitionOperationId = acquisitionOperationId;
        this.make = make;
        this.model = model;
        this.modelYear = modelYear;
        this.initialOdometer = initialOdometer;
        this.purchasePrice = purchasePrice;
        this.displayCurrency = displayCurrency;
        this.acquisitionType = acquisitionType;
        this.status = status;
        this.acquiredOperationalWeek = acquiredOperationalWeek;
        this.acquiredPayrollMonth = acquiredPayrollMonth;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId(){return id;}
    public UUID getCareerId(){return careerId;}
    public UUID getAcquisitionOperationId(){return acquisitionOperationId;}
    public String getMake(){return make;}
    public String getModel(){return model;}
    public Short getModelYear(){return modelYear;}
    public BigDecimal getInitialOdometer(){return initialOdometer;}
    public BigDecimal getPurchasePrice(){return purchasePrice;}
    public String getDisplayCurrency(){return displayCurrency;}
    public VehicleAcquisitionType getAcquisitionType(){return acquisitionType;}
    public VehicleStatus getStatus(){return status;}
    public int getAcquiredOperationalWeek(){return acquiredOperationalWeek;}
    public Integer getAcquiredPayrollMonth(){return acquiredPayrollMonth;}
    public Instant getCreatedAt(){return createdAt;}
    public Instant getUpdatedAt(){return updatedAt;}
    public long getVersion(){return version;}
}
