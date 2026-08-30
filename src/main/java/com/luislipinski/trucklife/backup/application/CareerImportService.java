package com.luislipinski.trucklife.backup.application;

import com.luislipinski.trucklife.backup.api.CareerImportResponse;
import com.luislipinski.trucklife.backup.api.CareerImportValidationRequest;
import com.luislipinski.trucklife.backup.api.CareerImportValidationResponse;
import com.luislipinski.trucklife.backup.domain.CareerImportStatus;
import com.luislipinski.trucklife.backup.persistence.CareerImportOperationEntity;
import com.luislipinski.trucklife.backup.persistence.CareerImportOperationRepository;
import com.luislipinski.trucklife.career.domain.CareerGame;
import com.luislipinski.trucklife.career.persistence.CareerEntity;
import com.luislipinski.trucklife.career.persistence.CareerOwnerLock;
import com.luislipinski.trucklife.career.persistence.CareerRepository;
import com.luislipinski.trucklife.shared.error.ApiProblemException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class CareerImportService {

    private final CareerImportValidationService validationService;
    private final CareerImportOperationRepository importRepository;
    private final CareerRepository careerRepository;
    private final CareerOwnerLock ownerLock;
    private final CareerImportAggregateMaterializer aggregateMaterializer;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public CareerImportService(
            CareerImportValidationService validationService,
            CareerImportOperationRepository importRepository,
            CareerRepository careerRepository,
            CareerOwnerLock ownerLock,
            CareerImportAggregateMaterializer aggregateMaterializer,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.validationService = validationService;
        this.importRepository = importRepository;
        this.careerRepository = careerRepository;
        this.ownerLock = ownerLock;
        this.aggregateMaterializer = aggregateMaterializer;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public CareerImportResponse importCareer(UUID userId, CareerImportValidationRequest request) {
        CareerImportValidationResponse validation = validationService.validate(request);
        CoreSnapshot snapshot = coreSnapshot(request, validation);
        String snapshotHash = snapshotHash(request);

        ownerLock.lock(userId);

        CareerImportOperationEntity sameOperation = importRepository
                .findByUserIdAndOperationId(userId, request.operationId())
                .orElse(null);
        if (sameOperation != null) {
            requireSameOperation(sameOperation, request, snapshotHash);
            if (sameOperation.getStatus() != CareerImportStatus.COMPLETED) {
                throw conflict(
                        "CAREER_IMPORT_IN_PROGRESS",
                        "Career import is still processing",
                        "The same import operation is already processing"
                );
            }
            return replay(sameOperation);
        }

        CareerImportOperationEntity sameSource = importRepository
                .findByUserIdAndGameAndSourceCareerId(userId, request.game(), request.sourceCareerId())
                .orElse(null);
        if (sameSource != null) {
            throw conflict(
                    "CAREER_IMPORT_ALREADY_EXISTS",
                    "Career was already imported",
                    "This local career is already associated with another import operation"
            );
        }

        Instant now = clock.instant();
        CareerImportOperationEntity operation = new CareerImportOperationEntity(
                UUID.randomUUID(),
                userId,
                request.operationId(),
                request.sourceCareerId(),
                request.game(),
                request.sourceVersion(),
                snapshotHash,
                now
        );
        importRepository.saveAndFlush(operation);

        UUID careerId = UUID.randomUUID();
        CareerEntity career = new CareerEntity(
                careerId,
                userId,
                request.game(),
                snapshot.driverName(),
                snapshot.companyName(),
                snapshot.biography(),
                snapshot.currentLevel(),
                snapshot.balance(),
                snapshot.baseCurrency(),
                snapshot.displayCurrency(),
                snapshot.exchangeRate(),
                snapshot.exchangeRateAsOf(),
                snapshot.stateCode(),
                snapshot.countryCode(),
                snapshot.baseCity(),
                snapshot.defaultTruckMake(),
                snapshot.defaultTruckModel(),
                snapshot.cityMarketVersion(),
                snapshot.cityMarketLabel(),
                snapshot.cityCostFactor(),
                snapshot.citySalaryFactor(),
                snapshot.currentOperationalWeek(),
                snapshot.currentPayrollMonth(),
                now,
                now
        );
        if (qualified(request.state())) {
            career.qualifyDangerousGoods(now);
        }
        careerRepository.saveAndFlush(career);
        aggregateMaterializer.materialize(operation, career, request, now);

        CareerImportResponse.Summary summary = summary(snapshot);
        operation.complete(careerId, serializeSummary(summary), now);
        importRepository.saveAndFlush(operation);

        return response(operation, summary, false);
    }

    private CoreSnapshot coreSnapshot(
            CareerImportValidationRequest request,
            CareerImportValidationResponse validation
    ) {
        Map<String, Object> career = request.career();
        CareerImportValidationResponse.Summary summary = validation.summary();
        String baseCurrency = currency(career.get("baseCurrency"), "career.baseCurrency");
        String displayCurrency = currency(firstNonNull(career.get("currency"), career.get("displayCurrency")), "career.currency");
        BigDecimal exchangeRate = positiveDecimal(career.get("exchangeRate"), "career.exchangeRate");
        LocalDate exchangeRateAsOf = localDate(career.get("exchangeRateAsOf"), "career.exchangeRateAsOf");
        BigDecimal cityCostFactor = positiveDecimal(career.get("cityCostFactor"), "career.cityCostFactor");
        BigDecimal citySalaryFactor = positiveDecimal(career.get("citySalaryFactor"), "career.citySalaryFactor");

        String stateCode = request.game() == CareerGame.ATS
                ? requiredText(career.get("stateCode"), "career.stateCode").toUpperCase(Locale.ROOT)
                : null;
        String countryCode = request.game() == CareerGame.ETS2
                ? requiredText(career.get("countryCode"), "career.countryCode").toUpperCase(Locale.ROOT)
                : null;

        return new CoreSnapshot(
                summary.driverName(),
                summary.companyName(),
                optionalText(firstNonNull(career.get("bio"), career.get("biography"))),
                summary.currentLevel(),
                summary.balance(),
                baseCurrency,
                displayCurrency,
                exchangeRate,
                exchangeRateAsOf,
                stateCode,
                countryCode,
                summary.baseCity(),
                optionalText(firstNonNull(career.get("defaultTruckMake"), career.get("truckMake"))),
                optionalText(firstNonNull(career.get("defaultTruckModel"), career.get("truckModel"))),
                requiredText(career.get("cityMarketVersion"), "career.cityMarketVersion"),
                requiredText(career.get("cityMarketLabel"), "career.cityMarketLabel"),
                cityCostFactor,
                citySalaryFactor,
                summary.currentOperationalWeek(),
                summary.currentPayrollMonth()
        );
    }

    private void requireCoreOnlySnapshot(
            CareerImportValidationRequest request,
            CareerImportValidationResponse validation
    ) {
        Map<String, Object> state = request.state();
        List<String> pendingSections = new ArrayList<>();
        for (String key : List.of("trips", "closedWeeks", "history", "customExpenses", "incidents", "closedOperationalWeeks")) {
            Object value = state.get(key);
            if (value instanceof List<?> list && !list.isEmpty()) {
                pendingSections.add(key);
            }
        }
        Object expenses = state.get("expenses");
        if (expenses instanceof Map<?, ?> map && !map.isEmpty()) {
            pendingSections.add("expenses");
        }
        Object reserve = state.get("emergencyReserve");
        if (reserve != null && decimal(reserve, "state.emergencyReserve").signum() != 0) {
            pendingSections.add("emergencyReserve");
        }
        if (validation.summary().currentLevel() != 1 || qualified(state) || academyProgressed(state)) {
            pendingSections.add("progression");
        }
        if (validation.summary().currentOperationalWeek() != 1
                || (request.game() == CareerGame.ETS2 && !Objects.equals(validation.summary().currentPayrollMonth(), 1))) {
            pendingSections.add("operationalPeriods");
        }
        if (!pendingSections.isEmpty()) {
            throw new ApiProblemException(
                    HttpStatus.BAD_REQUEST,
                    "CAREER_IMPORT_AGGREGATE_PENDING",
                    "Career import aggregate is not supported yet",
                    "This migration slice only materializes a fresh core career; pending sections: "
                            + String.join(", ", pendingSections)
            );
        }
    }

    private boolean qualified(Map<String, Object> state) {
        return Boolean.TRUE.equals(booleanValue(firstNonNull(
                state.get("dangerousGoodsQualified"),
                state.get("hazmatQualified")
        )));
    }

    private boolean academyProgressed(Map<String, Object> state) {
        Object value = state.get("academy");
        if (!(value instanceof Map<?, ?> academy)) {
            return false;
        }
        return Boolean.TRUE.equals(booleanValue(academy.get("level2")))
                || Boolean.TRUE.equals(booleanValue(academy.get("level3")));
    }

    private Boolean booleanValue(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private void requireSameOperation(
            CareerImportOperationEntity operation,
            CareerImportValidationRequest request,
            String snapshotHash
    ) {
        boolean same = operation.getGame() == request.game()
                && operation.getSourceVersion() == request.sourceVersion()
                && operation.getSourceCareerId().equals(request.sourceCareerId())
                && operation.getSnapshotSha256().equals(snapshotHash);
        if (!same) {
            throw conflict(
                    "CAREER_IMPORT_IDEMPOTENCY_CONFLICT",
                    "Career import idempotency conflict",
                    "operationId was already used with a different local snapshot"
            );
        }
    }

    private CareerImportResponse replay(CareerImportOperationEntity operation) {
        try {
            CareerImportResponse.Summary summary = objectMapper.readValue(
                    operation.getResultSummaryJson(),
                    CareerImportResponse.Summary.class
            );
            return response(operation, summary, true);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Stored career import summary could not be read", exception);
        }
    }

    private CareerImportResponse response(
            CareerImportOperationEntity operation,
            CareerImportResponse.Summary summary,
            boolean replay
    ) {
        return new CareerImportResponse(
                operation.getOperationId(),
                operation.getSourceCareerId(),
                operation.getGame(),
                operation.getSourceVersion(),
                operation.getImportedCareerId(),
                true,
                replay,
                summary
        );
    }

    private CareerImportResponse.Summary summary(CoreSnapshot snapshot) {
        return new CareerImportResponse.Summary(
                snapshot.driverName(),
                snapshot.baseCity(),
                snapshot.companyName(),
                snapshot.currentLevel(),
                snapshot.balance(),
                snapshot.baseCurrency(),
                snapshot.displayCurrency(),
                snapshot.currentOperationalWeek(),
                snapshot.currentPayrollMonth()
        );
    }

    private String serializeSummary(CareerImportResponse.Summary summary) {
        try {
            return objectMapper.writeValueAsString(summary);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Career import summary could not be serialized", exception);
        }
    }

    private String snapshotHash(CareerImportValidationRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sourceCareerId", request.sourceCareerId());
        payload.put("game", request.game().name());
        payload.put("sourceVersion", request.sourceVersion());
        payload.put("career", request.career());
        payload.put("state", request.state());
        try {
            byte[] canonical = objectMapper.writeValueAsString(canonicalize(payload)).getBytes(StandardCharsets.UTF_8);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical);
            return HexFormat.of().formatHex(digest);
        } catch (JacksonException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Career import snapshot could not be hashed", exception);
        }
    }

    private Object canonicalize(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sorted = new TreeMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                sorted.put(String.valueOf(entry.getKey()), canonicalize(entry.getValue()));
            }
            return sorted;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(this::canonicalize).toList();
        }
        if (value instanceof Number number) {
            try {
                return new BigDecimal(number.toString()).stripTrailingZeros();
            } catch (NumberFormatException ignored) {
                return number.toString();
            }
        }
        return value;
    }

    private String currency(Object value, String label) {
        String result = requiredText(value, label).toUpperCase(Locale.ROOT);
        if (!result.matches("[A-Z]{3}")) {
            throw invalid(label + " must be a 3-letter currency code");
        }
        return result;
    }

    private LocalDate localDate(Object value, String label) {
        String text = requiredText(value, label);
        try {
            return LocalDate.parse(text);
        } catch (DateTimeParseException exception) {
            throw invalid(label + " must be an ISO date");
        }
    }

    private BigDecimal positiveDecimal(Object value, String label) {
        BigDecimal result = decimal(value, label);
        if (result.signum() <= 0) {
            throw invalid(label + " must be greater than zero");
        }
        return result;
    }

    private BigDecimal decimal(Object value, String label) {
        if (value == null) {
            throw invalid(label + " is required");
        }
        try {
            return value instanceof BigDecimal bigDecimal ? bigDecimal : new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException exception) {
            throw invalid(label + " must be numeric");
        }
    }

    private String requiredText(Object value, String label) {
        String text = value == null ? "" : String.valueOf(value).strip();
        if (text.isEmpty()) {
            throw invalid(label + " is required");
        }
        return text;
    }

    private String optionalText(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).strip();
        return text.isEmpty() ? null : text;
    }

    private Object firstNonNull(Object... values) {
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private ApiProblemException invalid(String detail) {
        return new ApiProblemException(
                HttpStatus.BAD_REQUEST,
                "CAREER_IMPORT_INVALID",
                "Career import snapshot is invalid",
                detail
        );
    }

    private ApiProblemException conflict(String code, String title, String detail) {
        return new ApiProblemException(HttpStatus.CONFLICT, code, title, detail);
    }

    private record CoreSnapshot(
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
            String defaultTruckMake,
            String defaultTruckModel,
            String cityMarketVersion,
            String cityMarketLabel,
            BigDecimal cityCostFactor,
            BigDecimal citySalaryFactor,
            int currentOperationalWeek,
            Integer currentPayrollMonth
    ) {
    }
}
