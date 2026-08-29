package com.luislipinski.trucklife.backup.application;

import com.luislipinski.trucklife.backup.api.CareerImportValidationRequest;
import com.luislipinski.trucklife.backup.api.CareerImportValidationResponse;
import com.luislipinski.trucklife.career.domain.CareerGame;
import com.luislipinski.trucklife.shared.error.ApiProblemException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class CareerImportValidationService {

    public CareerImportValidationResponse validate(CareerImportValidationRequest request) {
        Map<String, Object> career = request.career();
        Map<String, Object> state = request.state();

        String sourceId = requiredText(career, "id", "career.id");
        if (!request.sourceCareerId().equals(sourceId)) {
            throw invalid("sourceCareerId must match career.id");
        }

        String sourceGame = requiredText(career, "gameId", "career.gameId");
        if (!request.game().name().equalsIgnoreCase(sourceGame)) {
            throw invalid("The career game does not match the import game");
        }

        String driverName = requiredText(career, "driverName", "career.driverName");
        String baseCity = requiredText(career, "city", "career.city");
        String companyName = requiredText(career, "company", "career.company");

        if (request.game() == CareerGame.ATS) {
            requiredText(career, "stateCode", "career.stateCode");
        } else {
            requiredText(career, "countryCode", "career.countryCode");
        }

        int currentLevel = integer(firstNonNull(
                state.get("currentLevel"),
                state.get("careerLevel"),
                career.get("currentLevel")
        ), "state.currentLevel");
        if (currentLevel < 1 || currentLevel > 3) {
            throw invalid("state.currentLevel must be between 1 and 3");
        }

        Object careerLevel = career.get("currentLevel");
        if (careerLevel != null && integer(careerLevel, "career.currentLevel") != currentLevel) {
            throw invalid("career.currentLevel and state.currentLevel must match");
        }

        BigDecimal balance = decimal(state.get("balance"), "state.balance");
        Object careerBalance = career.get("currentBalance");
        if (careerBalance != null && decimal(careerBalance, "career.currentBalance").compareTo(balance) != 0) {
            throw invalid("career.currentBalance and state.balance must match");
        }

        Object emergencyReserve = state.get("emergencyReserve");
        if (emergencyReserve != null && decimal(emergencyReserve, "state.emergencyReserve").signum() < 0) {
            throw invalid("state.emergencyReserve cannot be negative");
        }

        int currentWeek = positiveInteger(state.get("currentWeek"), "state.currentWeek");
        Integer currentPayrollMonth = request.game() == CareerGame.ETS2
                ? positiveInteger(state.get("currentPayrollMonth"), "state.currentPayrollMonth")
                : null;

        List<?> trips = list(state, "trips");
        validateTrips(trips);
        List<?> closedWeeks = list(state, "closedWeeks");
        List<?> incidents = list(state, "incidents");
        List<?> history = list(state, "history");
        List<?> customExpenses = list(state, "customExpenses");
        validatePositiveIntegerList(state, "closedOperationalWeeks");
        validateObject(state, "expenses");
        validateObject(state, "academy");

        return new CareerImportValidationResponse(
                request.operationId(),
                request.sourceCareerId(),
                request.game(),
                request.sourceVersion(),
                true,
                false,
                new CareerImportValidationResponse.Summary(
                        driverName,
                        baseCity,
                        companyName,
                        (short) currentLevel,
                        balance,
                        currentWeek,
                        currentPayrollMonth,
                        trips.size(),
                        closedWeeks.size(),
                        incidents.size(),
                        history.size(),
                        customExpenses.size()
                )
        );
    }

    private void validateTrips(List<?> trips) {
        for (int index = 0; index < trips.size(); index++) {
            Object value = trips.get(index);
            if (!(value instanceof Map<?, ?> trip)) {
                throw invalid("state.trips[" + index + "] must be an object");
            }
            Object week = trip.get("week");
            if (week != null) {
                positiveInteger(week, "state.trips[" + index + "].week");
            }
        }
    }

    private void validatePositiveIntegerList(Map<String, Object> state, String key) {
        Object value = state.get(key);
        if (value == null) {
            return;
        }
        if (!(value instanceof List<?> values)) {
            throw invalid("state." + key + " must be an array");
        }
        for (int index = 0; index < values.size(); index++) {
            positiveInteger(values.get(index), "state." + key + "[" + index + "]");
        }
    }

    private void validateObject(Map<String, Object> state, String key) {
        Object value = state.get(key);
        if (value != null && !(value instanceof Map<?, ?>)) {
            throw invalid("state." + key + " must be an object");
        }
    }

    private List<?> list(Map<String, Object> state, String key) {
        Object value = state.get(key);
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> result)) {
            throw invalid("state." + key + " must be an array");
        }
        return result;
    }

    private String requiredText(Map<String, Object> source, String key, String label) {
        Object value = source.get(key);
        String text = value == null ? "" : String.valueOf(value).strip();
        if (text.isEmpty()) {
            throw invalid(label + " is required");
        }
        return text;
    }

    private int positiveInteger(Object value, String label) {
        int result = integer(value, label);
        if (result < 1) {
            throw invalid(label + " must be greater than zero");
        }
        return result;
    }

    private int integer(Object value, String label) {
        try {
            return decimal(value, label).intValueExact();
        } catch (ArithmeticException exception) {
            throw invalid(label + " must be an integer");
        }
    }

    private BigDecimal decimal(Object value, String label) {
        if (value == null) {
            throw invalid(label + " is required");
        }
        try {
            BigDecimal result = value instanceof BigDecimal bigDecimal
                    ? bigDecimal
                    : new BigDecimal(String.valueOf(value));
            if (result.scale() > 12) {
                result = result.stripTrailingZeros();
            }
            return result;
        } catch (NumberFormatException exception) {
            throw invalid(label + " must be numeric");
        }
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
}
