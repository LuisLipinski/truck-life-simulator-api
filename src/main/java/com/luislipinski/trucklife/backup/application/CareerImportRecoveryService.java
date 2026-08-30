package com.luislipinski.trucklife.backup.application;

import com.luislipinski.trucklife.backup.api.CareerImportResponse;
import com.luislipinski.trucklife.backup.domain.CareerImportStatus;
import com.luislipinski.trucklife.backup.persistence.CareerImportOperationEntity;
import com.luislipinski.trucklife.backup.persistence.CareerImportOperationRepository;
import com.luislipinski.trucklife.career.domain.CareerGame;
import com.luislipinski.trucklife.shared.error.ApiProblemException;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class CareerImportRecoveryService {

    private final CareerImportOperationRepository importRepository;
    private final ObjectMapper objectMapper;

    public CareerImportRecoveryService(
            CareerImportOperationRepository importRepository,
            ObjectMapper objectMapper
    ) {
        this.importRepository = importRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public CareerImportResponse recover(UUID userId, CareerGame game, String sourceCareerId) {
        UUID ownerId = Objects.requireNonNull(userId, "userId");
        CareerGame requestedGame = Objects.requireNonNull(game, "game");
        String normalizedSourceId = normalizeSourceCareerId(sourceCareerId);

        CareerImportOperationEntity operation = importRepository
                .findByUserIdAndGameAndSourceCareerId(ownerId, requestedGame, normalizedSourceId)
                .orElseThrow(() -> new ApiProblemException(
                        HttpStatus.NOT_FOUND,
                        "CAREER_IMPORT_NOT_FOUND",
                        "Career import was not found",
                        "No import association exists for this local career and authenticated owner"
                ));

        if (operation.getStatus() != CareerImportStatus.COMPLETED
                || operation.getImportedCareerId() == null
                || operation.getResultSummaryJson() == null) {
            throw new ApiProblemException(
                    HttpStatus.CONFLICT,
                    "CAREER_IMPORT_IN_PROGRESS",
                    "Career import is still processing",
                    "The local career association exists but is not completed yet"
            );
        }

        return new CareerImportResponse(
                operation.getOperationId(),
                operation.getSourceCareerId(),
                operation.getGame(),
                operation.getSourceVersion(),
                operation.getImportedCareerId(),
                true,
                true,
                readSummary(operation)
        );
    }

    private CareerImportResponse.Summary readSummary(CareerImportOperationEntity operation) {
        try {
            return objectMapper.readValue(
                    operation.getResultSummaryJson(),
                    CareerImportResponse.Summary.class
            );
        } catch (JacksonException exception) {
            throw new IllegalStateException("Stored career import summary could not be read", exception);
        }
    }

    private String normalizeSourceCareerId(String sourceCareerId) {
        if (sourceCareerId == null || sourceCareerId.isBlank()) {
            throw invalid("sourceCareerId is required");
        }
        String normalized = sourceCareerId.strip();
        if (normalized.length() > 200) {
            throw invalid("sourceCareerId must contain at most 200 characters");
        }
        return normalized;
    }

    private ApiProblemException invalid(String detail) {
        return new ApiProblemException(
                HttpStatus.BAD_REQUEST,
                "CAREER_IMPORT_INVALID",
                "Career import lookup is invalid",
                detail
        );
    }
}
