package com.luislipinski.trucklife.trip.api;

import com.luislipinski.trucklife.career.domain.CareerGame;
import com.luislipinski.trucklife.trip.domain.TripPaymentCategory;
import com.luislipinski.trucklife.trip.domain.TripSource;
import com.luislipinski.trucklife.trip.domain.TripType;
import com.luislipinski.trucklife.trip.persistence.TripEntity;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

public record TripResponse(
        UUID id,
        UUID careerId,
        CareerGame game,
        int operationalWeek,
        String departureDay,
        LocalTime departureTime,
        String arrivalDay,
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
        BigDecimal odometerDistance,
        TripSource source,
        Map<String, Object> employerSnapshot,
        Map<String, Object> baseSnapshot,
        Instant createdAt,
        Instant updatedAt,
        long version
) {

    private static final TypeReference<Map<String, Object>> SNAPSHOT_TYPE = new TypeReference<>() {
    };

    static TripResponse from(TripEntity trip, CareerGame game, ObjectMapper objectMapper) {
        return new TripResponse(
                trip.getId(),
                trip.getCareerId(),
                game,
                trip.getOperationalWeek(),
                trip.getDepartureDay().name().toLowerCase(Locale.ROOT),
                trip.getDepartureTime(),
                trip.getArrivalDay().name().toLowerCase(Locale.ROOT),
                trip.getArrivalTime(),
                trip.getOriginCity(),
                trip.getOriginCompany(),
                trip.getDestinationCity(),
                trip.getDestinationCompany(),
                trip.getCargo(),
                trip.getType(),
                trip.getPaymentCategory(),
                trip.getOfficialDistance(),
                trip.getBreakMinutes(),
                trip.getTruckMake(),
                trip.getTruckModel(),
                trip.getOdometerStart(),
                trip.getOdometerEnd(),
                odometerDistance(trip),
                trip.getSource(),
                snapshot(trip.getEmployerSnapshotJson(), objectMapper),
                snapshot(trip.getBaseSnapshotJson(), objectMapper),
                trip.getCreatedAt(),
                trip.getUpdatedAt(),
                trip.getVersion()
        );
    }

    private static BigDecimal odometerDistance(TripEntity trip) {
        if (trip.getOdometerStart() == null || trip.getOdometerEnd() == null) {
            return null;
        }
        return trip.getOdometerEnd().subtract(trip.getOdometerStart());
    }

    private static Map<String, Object> snapshot(String json, ObjectMapper objectMapper) {
        try {
            return objectMapper.readValue(json.getBytes(java.nio.charset.StandardCharsets.UTF_8), SNAPSHOT_TYPE);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Trip snapshot could not be deserialized", exception);
        }
    }
}
