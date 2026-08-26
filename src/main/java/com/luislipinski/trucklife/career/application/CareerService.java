package com.luislipinski.trucklife.career.application;

import com.luislipinski.trucklife.career.domain.CareerGame;
import com.luislipinski.trucklife.career.persistence.CareerEntity;
import com.luislipinski.trucklife.career.persistence.CareerOwnerLock;
import com.luislipinski.trucklife.career.persistence.CareerRepository;
import com.luislipinski.trucklife.shared.error.ApiProblemException;
import com.luislipinski.trucklife.shared.error.ResourceNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CareerService implements CareerOperations {

    private static final long FREE_CAREERS_PER_GAME = 2;

    private final CareerRepository careerRepository;
    private final CareerOwnerLock ownerLock;
    private final Clock clock;

    public CareerService(CareerRepository careerRepository, CareerOwnerLock ownerLock, Clock clock) {
        this.careerRepository = careerRepository;
        this.ownerLock = ownerLock;
        this.clock = clock;
    }

    @Override
    @Transactional
    public CareerEntity create(UUID userId, CreateCareerCommand command) {
        String stateCode = null;
        String countryCode = null;
        if (command.game() == CareerGame.ATS) {
            stateCode = requiredLocation(command.stateCode(), "stateCode", CareerGame.ATS);
        } else {
            countryCode = requiredLocation(command.countryCode(), "countryCode", CareerGame.ETS2);
        }

        ownerLock.lock(userId);
        if (careerRepository.countByUserIdAndGame(userId, command.game()) >= FREE_CAREERS_PER_GAME) {
            throw new ApiProblemException(
                    HttpStatus.CONFLICT,
                    "CAREER_LIMIT_REACHED",
                    "Career limit reached",
                    "The Free account limit is two careers per game"
            );
        }

        Instant now = clock.instant();
        CareerEntity career = new CareerEntity(
                UUID.randomUUID(),
                userId,
                command.game(),
                command.driverName().strip(),
                command.companyName().strip(),
                optional(command.biography()),
                (short) 1,
                command.initialBalance(),
                command.baseCurrency().strip(),
                command.displayCurrency().strip(),
                command.exchangeRate(),
                command.exchangeRateAsOf(),
                stateCode,
                countryCode,
                command.baseCity().strip(),
                optional(command.defaultTruckMake()),
                optional(command.defaultTruckModel()),
                optional(command.cityMarketVersion()),
                optional(command.cityMarketLabel()),
                command.cityCostFactor(),
                command.citySalaryFactor(),
                1,
                command.game() == CareerGame.ETS2 ? 1 : null,
                now,
                now
        );
        return careerRepository.saveAndFlush(career);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CareerEntity> list(UUID userId, CareerGame game) {
        return careerRepository.findAllByUserIdAndGameOrderByCreatedAtAscIdAsc(userId, game);
    }

    @Override
    @Transactional(readOnly = true)
    public CareerEntity get(UUID userId, CareerGame game, UUID careerId) {
        return ownedCareer(userId, game, careerId);
    }

    @Override
    @Transactional
    public CareerEntity updateProfile(
            UUID userId,
            CareerGame game,
            UUID careerId,
            UpdateCareerProfileCommand command
    ) {
        CareerEntity career = ownedCareer(userId, game, careerId);
        if (career.getVersion() != command.version()) {
            throw versionConflict();
        }

        career.updateProfile(
                command.driverName().strip(),
                optional(command.biography()),
                clock.instant()
        );
        try {
            careerRepository.flush();
        } catch (ObjectOptimisticLockingFailureException exception) {
            throw versionConflict();
        }
        return career;
    }

    private CareerEntity ownedCareer(UUID userId, CareerGame game, UUID careerId) {
        return careerRepository.findByIdAndUserIdAndGame(careerId, userId, game)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "CAREER_NOT_FOUND",
                        "The requested career does not exist"
                ));
    }

    private String requiredLocation(String value, String field, CareerGame game) {
        if (value == null || value.isBlank()) {
            throw new ApiProblemException(
                    HttpStatus.BAD_REQUEST,
                    "CAREER_LOCATION_INVALID",
                    "Career location invalid",
                    field + " is required for " + game
            );
        }
        return value.strip().toUpperCase(Locale.ROOT);
    }

    private String optional(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private ApiProblemException versionConflict() {
        return new ApiProblemException(
                HttpStatus.CONFLICT,
                "CAREER_VERSION_CONFLICT",
                "Career version conflict",
                "The career changed since it was loaded; reload it before retrying"
        );
    }
}
