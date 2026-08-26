package com.luislipinski.trucklife.career.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.luislipinski.trucklife.career.domain.CareerGame;
import com.luislipinski.trucklife.career.persistence.CareerEntity;
import com.luislipinski.trucklife.career.persistence.CareerOwnerLock;
import com.luislipinski.trucklife.career.persistence.CareerRepository;
import com.luislipinski.trucklife.shared.error.ApiProblemException;
import com.luislipinski.trucklife.shared.error.ResourceNotFoundException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

class CareerServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-26T15:00:00Z");

    private CareerRepository careerRepository;
    private CareerOwnerLock ownerLock;
    private CareerService service;

    @BeforeEach
    void setUp() {
        careerRepository = mock(CareerRepository.class);
        ownerLock = mock(CareerOwnerLock.class);
        service = new CareerService(
                careerRepository,
                ownerLock,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void createsAnAtsCareerWithServerOwnedProgressionAndNormalizedSnapshots() {
        UUID userId = UUID.randomUUID();
        CreateCareerCommand command = command(CareerGame.ATS, " az ", null);
        when(careerRepository.saveAndFlush(any(CareerEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CareerEntity career = service.create(userId, command);

        assertThat(career.getUserId()).isEqualTo(userId);
        assertThat(career.getGame()).isEqualTo(CareerGame.ATS);
        assertThat(career.getDriverName()).isEqualTo("Alex Driver");
        assertThat(career.getCompanyName()).isEqualTo("Road Logistics");
        assertThat(career.getBiography()).isEqualTo("A new beginning");
        assertThat(career.getStateCode()).isEqualTo("AZ");
        assertThat(career.getCountryCode()).isNull();
        assertThat(career.getDefaultTruckMake()).isNull();
        assertThat(career.getDefaultTruckModel()).isEqualTo("T680");
        assertThat(career.getCurrentLevel()).isEqualTo((short) 1);
        assertThat(career.getCurrentOperationalWeek()).isEqualTo(1);
        assertThat(career.getCurrentPayrollMonth()).isNull();
        assertThat(career.getCreatedAt()).isEqualTo(NOW);
        assertThat(career.getUpdatedAt()).isEqualTo(NOW);
        verify(ownerLock).lock(userId);
        verify(careerRepository).countByUserIdAndGame(userId, CareerGame.ATS);
    }

    @Test
    void createsAnEts2CareerWithCountryAndInitialPayrollMonth() {
        UUID userId = UUID.randomUUID();
        CreateCareerCommand command = command(CareerGame.ETS2, "ignored", " de ");
        when(careerRepository.saveAndFlush(any(CareerEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CareerEntity career = service.create(userId, command);

        assertThat(career.getStateCode()).isNull();
        assertThat(career.getCountryCode()).isEqualTo("DE");
        assertThat(career.getCurrentPayrollMonth()).isEqualTo(1);
    }

    @Test
    void rejectsMissingGameSpecificLocationsBeforeAcquiringTheOwnerLock() {
        UUID userId = UUID.randomUUID();

        assertProblem(
                () -> service.create(userId, command(CareerGame.ATS, " ", null)),
                "CAREER_LOCATION_INVALID"
        );
        assertProblem(
                () -> service.create(userId, command(CareerGame.ETS2, null, null)),
                "CAREER_LOCATION_INVALID"
        );
        verifyNoInteractions(ownerLock, careerRepository);
    }

    @Test
    void enforcesTheFreeLimitAfterLockingTheOwner() {
        UUID userId = UUID.randomUUID();
        when(careerRepository.countByUserIdAndGame(userId, CareerGame.ATS)).thenReturn(2L);

        assertProblem(
                () -> service.create(userId, command(CareerGame.ATS, "AZ", null)),
                "CAREER_LIMIT_REACHED"
        );

        verify(ownerLock).lock(userId);
        verify(careerRepository).countByUserIdAndGame(userId, CareerGame.ATS);
    }

    @Test
    void listsAndGetsOnlyRepositoryResultsAlreadyScopedByOwnerAndGame() {
        UUID userId = UUID.randomUUID();
        CareerEntity career = career(userId, CareerGame.ATS);
        when(careerRepository.findAllByUserIdAndGameOrderByCreatedAtAscIdAsc(userId, CareerGame.ATS))
                .thenReturn(List.of(career));
        when(careerRepository.findByIdAndUserIdAndGame(career.getId(), userId, CareerGame.ATS))
                .thenReturn(Optional.of(career));

        assertThat(service.list(userId, CareerGame.ATS)).containsExactly(career);
        assertThat(service.get(userId, CareerGame.ATS, career.getId())).isSameAs(career);
    }

    @Test
    void hidesMissingWrongOwnerAndWrongGameCareersBehindTheSameNotFoundProblem() {
        UUID userId = UUID.randomUUID();
        UUID careerId = UUID.randomUUID();

        assertThatThrownBy(() -> service.get(userId, CareerGame.ETS2, careerId))
                .isInstanceOfSatisfying(ResourceNotFoundException.class, exception ->
                        assertThat(exception.code()).isEqualTo("CAREER_NOT_FOUND")
                );
    }

    @Test
    void updatesTheEditableProfileFieldsWhenTheVersionMatches() {
        UUID userId = UUID.randomUUID();
        CareerEntity career = career(userId, CareerGame.ATS);
        when(careerRepository.findByIdAndUserIdAndGame(career.getId(), userId, CareerGame.ATS))
                .thenReturn(Optional.of(career));

        CareerEntity updated = service.updateProfile(
                userId,
                CareerGame.ATS,
                career.getId(),
                new UpdateCareerProfileCommand(0, " Corrected Driver ", " ")
        );

        assertThat(updated.getDriverName()).isEqualTo("Corrected Driver");
        assertThat(updated.getBiography()).isNull();
        assertThat(updated.getUpdatedAt()).isEqualTo(NOW);
        verify(careerRepository).flush();
    }

    @Test
    void rejectsAStaleVersionBeforeChangingTheCareer() {
        UUID userId = UUID.randomUUID();
        CareerEntity career = career(userId, CareerGame.ATS);
        when(careerRepository.findByIdAndUserIdAndGame(career.getId(), userId, CareerGame.ATS))
                .thenReturn(Optional.of(career));

        assertProblem(
                () -> service.updateProfile(
                        userId,
                        CareerGame.ATS,
                        career.getId(),
                        new UpdateCareerProfileCommand(1, "Stale Driver", "Stale biography")
                ),
                "CAREER_VERSION_CONFLICT"
        );

        assertThat(career.getDriverName()).isEqualTo("Original Driver");
    }

    @Test
    void mapsADatabaseOptimisticLockRaceToThePublicConflictContract() {
        UUID userId = UUID.randomUUID();
        CareerEntity career = career(userId, CareerGame.ATS);
        when(careerRepository.findByIdAndUserIdAndGame(career.getId(), userId, CareerGame.ATS))
                .thenReturn(Optional.of(career));
        doThrow(new ObjectOptimisticLockingFailureException(CareerEntity.class, career.getId()))
                .when(careerRepository).flush();

        assertProblem(
                () -> service.updateProfile(
                        userId,
                        CareerGame.ATS,
                        career.getId(),
                        new UpdateCareerProfileCommand(0, "Concurrent Driver", null)
                ),
                "CAREER_VERSION_CONFLICT"
        );
    }

    private CreateCareerCommand command(CareerGame game, String stateCode, String countryCode) {
        return new CreateCareerCommand(
                game,
                " Alex Driver ",
                " Road Logistics ",
                " A new beginning ",
                new BigDecimal("-250.00"),
                game == CareerGame.ATS ? "USD" : "EUR",
                "BRL",
                new BigDecimal("5.25000000"),
                LocalDate.of(2026, 8, 26),
                stateCode,
                countryCode,
                game == CareerGame.ATS ? " Phoenix, AZ " : " Berlin, Germany ",
                " ",
                " T680 ",
                " market-v1 ",
                null,
                new BigDecimal("1.1000"),
                new BigDecimal("0.9500")
        );
    }

    private CareerEntity career(UUID userId, CareerGame game) {
        return new CareerEntity(
                UUID.randomUUID(),
                userId,
                game,
                "Original Driver",
                "Original Logistics",
                "Original biography",
                (short) 1,
                new BigDecimal("5000.00"),
                game == CareerGame.ATS ? "USD" : "EUR",
                "BRL",
                new BigDecimal("5.25000000"),
                LocalDate.of(2026, 8, 26),
                game == CareerGame.ATS ? "AZ" : null,
                game == CareerGame.ETS2 ? "DE" : null,
                game == CareerGame.ATS ? "Phoenix, AZ" : "Berlin, Germany",
                null,
                null,
                "market-v1",
                "Reference market",
                new BigDecimal("1.0000"),
                new BigDecimal("1.0000"),
                1,
                game == CareerGame.ETS2 ? 1 : null,
                NOW.minusSeconds(60),
                NOW.minusSeconds(60)
        );
    }

    private void assertProblem(Runnable operation, String expectedCode) {
        assertThatThrownBy(operation::run)
                .isInstanceOfSatisfying(ApiProblemException.class, exception ->
                        assertThat(exception.code()).isEqualTo(expectedCode)
                );
    }
}
