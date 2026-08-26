package com.luislipinski.trucklife.trip.persistence;

import com.luislipinski.trucklife.trip.domain.TripPaymentCategory;
import com.luislipinski.trucklife.trip.domain.TripSource;
import com.luislipinski.trucklife.trip.domain.TripType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "trips")
public class TripEntity {

    @Id
    private UUID id;

    @Column(name = "career_id", nullable = false)
    private UUID careerId;

    @Column(name = "operational_week", nullable = false)
    private int operationalWeek;

    @Enumerated(EnumType.STRING)
    @Column(name = "departure_day", nullable = false, length = 10)
    private DayOfWeek departureDay;

    @Column(name = "departure_time", nullable = false)
    private LocalTime departureTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "arrival_day", nullable = false, length = 10)
    private DayOfWeek arrivalDay;

    @Column(name = "arrival_time", nullable = false)
    private LocalTime arrivalTime;

    @Column(name = "origin_city", nullable = false, length = 160)
    private String originCity;

    @Column(name = "origin_company", length = 160)
    private String originCompany;

    @Column(name = "destination_city", nullable = false, length = 160)
    private String destinationCity;

    @Column(name = "destination_company", length = 160)
    private String destinationCompany;

    @Column(length = 200)
    private String cargo;

    @Enumerated(EnumType.STRING)
    @Column(name = "trip_type", nullable = false, length = 20)
    private TripType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_category", nullable = false, length = 30)
    private TripPaymentCategory paymentCategory;

    @Column(name = "official_distance", nullable = false, precision = 12, scale = 2)
    private BigDecimal officialDistance;

    @Column(name = "break_minutes")
    private Integer breakMinutes;

    @Column(name = "truck_make", length = 80)
    private String truckMake;

    @Column(name = "truck_model", length = 120)
    private String truckModel;

    @Column(name = "odometer_start", precision = 14, scale = 1)
    private BigDecimal odometerStart;

    @Column(name = "odometer_end", precision = 14, scale = 1)
    private BigDecimal odometerEnd;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TripSource source;

    @Column(name = "employer_snapshot_json", nullable = false, columnDefinition = "text")
    private String employerSnapshotJson;

    @Column(name = "base_snapshot_json", nullable = false, columnDefinition = "text")
    private String baseSnapshotJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected TripEntity() {
    }

    public TripEntity(
            UUID id,
            UUID careerId,
            int operationalWeek,
            DayOfWeek departureDay,
            LocalTime departureTime,
            DayOfWeek arrivalDay,
            LocalTime arrivalTime,
            String originCity,
            String originCompany,
            String destinationCity,
            String destinationCompany,
            String cargo,
            TripType type,
            TripPaymentCategory paymentCategory,
            BigDecimal officialDistance,
            Integer breakMinutes,
            String truckMake,
            String truckModel,
            BigDecimal odometerStart,
            BigDecimal odometerEnd,
            TripSource source,
            String employerSnapshotJson,
            String baseSnapshotJson,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.careerId = careerId;
        this.operationalWeek = operationalWeek;
        this.departureDay = departureDay;
        this.departureTime = departureTime;
        this.arrivalDay = arrivalDay;
        this.arrivalTime = arrivalTime;
        this.originCity = originCity;
        this.originCompany = originCompany;
        this.destinationCity = destinationCity;
        this.destinationCompany = destinationCompany;
        this.cargo = cargo;
        this.type = type;
        this.paymentCategory = paymentCategory;
        this.officialDistance = officialDistance;
        this.breakMinutes = breakMinutes;
        this.truckMake = truckMake;
        this.truckModel = truckModel;
        this.odometerStart = odometerStart;
        this.odometerEnd = odometerEnd;
        this.source = source;
        this.employerSnapshotJson = employerSnapshotJson;
        this.baseSnapshotJson = baseSnapshotJson;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() { return id; }
    public UUID getCareerId() { return careerId; }
    public int getOperationalWeek() { return operationalWeek; }
    public DayOfWeek getDepartureDay() { return departureDay; }
    public LocalTime getDepartureTime() { return departureTime; }
    public DayOfWeek getArrivalDay() { return arrivalDay; }
    public LocalTime getArrivalTime() { return arrivalTime; }
    public String getOriginCity() { return originCity; }
    public String getOriginCompany() { return originCompany; }
    public String getDestinationCity() { return destinationCity; }
    public String getDestinationCompany() { return destinationCompany; }
    public String getCargo() { return cargo; }
    public TripType getType() { return type; }
    public TripPaymentCategory getPaymentCategory() { return paymentCategory; }
    public BigDecimal getOfficialDistance() { return officialDistance; }
    public Integer getBreakMinutes() { return breakMinutes; }
    public String getTruckMake() { return truckMake; }
    public String getTruckModel() { return truckModel; }
    public BigDecimal getOdometerStart() { return odometerStart; }
    public BigDecimal getOdometerEnd() { return odometerEnd; }
    public TripSource getSource() { return source; }
    public String getEmployerSnapshotJson() { return employerSnapshotJson; }
    public String getBaseSnapshotJson() { return baseSnapshotJson; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
