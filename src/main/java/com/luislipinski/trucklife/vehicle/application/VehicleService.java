package com.luislipinski.trucklife.vehicle.application;

import com.luislipinski.trucklife.career.domain.CareerGame;
import com.luislipinski.trucklife.career.persistence.CareerEntity;
import com.luislipinski.trucklife.career.persistence.CareerRepository;
import com.luislipinski.trucklife.finance.application.FinancingJurisdictionCatalog;
import com.luislipinski.trucklife.finance.application.FinancingOperations;
import com.luislipinski.trucklife.finance.domain.FinancialProductType;
import com.luislipinski.trucklife.finance.persistence.FinancialContractEntity;
import com.luislipinski.trucklife.finance.persistence.FinancialContractRepository;
import com.luislipinski.trucklife.ledger.application.LedgerEntryDraft;
import com.luislipinski.trucklife.ledger.application.LedgerWriter;
import com.luislipinski.trucklife.ledger.domain.LedgerEntryType;
import com.luislipinski.trucklife.ledger.domain.LedgerSourceType;
import com.luislipinski.trucklife.shared.error.ApiProblemException;
import com.luislipinski.trucklife.shared.error.ResourceNotFoundException;
import com.luislipinski.trucklife.vehicle.domain.VehicleAcquisitionType;
import com.luislipinski.trucklife.vehicle.domain.VehicleCondition;
import com.luislipinski.trucklife.vehicle.domain.VehicleStatus;
import com.luislipinski.trucklife.vehicle.persistence.CareerVehicleEntity;
import com.luislipinski.trucklife.vehicle.persistence.CareerVehicleRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VehicleService implements VehicleOperations {
    private final CareerRepository careerRepository;
    private final CareerVehicleRepository vehicleRepository;
    private final FinancingOperations financingOperations;
    private final FinancingJurisdictionCatalog financingJurisdictionCatalog;
    private final FinancialContractRepository contractRepository;
    private final LedgerWriter ledgerWriter;
    private final Clock clock;

    public VehicleService(
            CareerRepository careerRepository,
            CareerVehicleRepository vehicleRepository,
            FinancingOperations financingOperations,
            FinancingJurisdictionCatalog financingJurisdictionCatalog,
            FinancialContractRepository contractRepository,
            LedgerWriter ledgerWriter,
            Clock clock
    ) {
        this.careerRepository = careerRepository;
        this.vehicleRepository = vehicleRepository;
        this.financingOperations = financingOperations;
        this.financingJurisdictionCatalog = financingJurisdictionCatalog;
        this.contractRepository = contractRepository;
        this.ledgerWriter = ledgerWriter;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public List<VehicleDetails> list(UUID userId, CareerGame game, UUID careerId) {
        CareerEntity career = ownedCareer(userId, game, careerId);
        return vehicleRepository.findAllByCareerIdOrderByCreatedAtAscIdAsc(careerId).stream()
                .map(vehicle -> details(vehicle, career, false))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<FinancingOperations.Offer> financingOffers(
            UUID userId,
            CareerGame game,
            UUID careerId,
            VehicleCondition condition,
            BigDecimal purchasePrice,
            String purchaseCountryCode,
            String purchaseStateCode
    ) {
        CareerEntity career = ownedCareer(userId, game, careerId);
        BigDecimal amount = positiveMoney(purchasePrice, "Purchase price");
        PurchaseJurisdiction jurisdiction = resolvePurchaseJurisdiction(
                career, condition, purchaseCountryCode, purchaseStateCode);
        validatePurchaseJurisdiction(game, jurisdiction);
        return financingOperations.offers(
                userId,
                game,
                careerId,
                FinancialProductType.VEHICLE_FINANCING,
                amount,
                jurisdiction.countryCode(),
                jurisdiction.stateCode()
        );
    }

    @Override
    @Transactional
    public VehicleDetails acquire(UUID userId, CareerGame game, UUID careerId, AcquireVehicleCommand command) {
        CareerVehicleEntity replay = vehicleRepository.findByAcquisitionOperationId(command.operationId()).orElse(null);
        if (replay != null) {
            if (!replay.getCareerId().equals(careerId)) {
                throw conflict(
                        "VEHICLE_OPERATION_ID_CONFLICT",
                        "Operation identifier conflict",
                        "The supplied operationId already belongs to another career"
                );
            }
            CareerEntity replayCareer = ownedCareer(userId, game, careerId);
            return details(replay, replayCareer, true);
        }

        CareerEntity career = lockedCareer(userId, game, careerId);
        if (vehicleRepository.existsByCareerIdAndStatus(careerId, VehicleStatus.ACTIVE)) {
            throw conflict(
                    "FIRST_TRUCK_ALREADY_ACQUIRED",
                    "Active owned truck already exists",
                    "Truck replacement and multiple owned vehicles are not enabled in this phase yet"
            );
        }
        validateContext(career, command.expectedOperationalWeek(), command.expectedPayrollMonth());
        BigDecimal expectedBalance = money(command.expectedBalance(), "Expected balance");
        BigDecimal currentBalance = career.getBalance().setScale(2, RoundingMode.UNNECESSARY);
        if (currentBalance.compareTo(expectedBalance) != 0) {
            throw conflict(
                    "VEHICLE_BALANCE_CONFLICT",
                    "Career balance changed",
                    "The expected balance no longer matches the current career balance"
            );
        }

        String make = requiredText(command.make(), 80, "Truck make");
        String model = requiredText(command.model(), 120, "Truck model");
        Short modelYear = validateYear(command.modelYear());
        BigDecimal odometer = nonNegative(command.initialOdometer(), 3, "Initial odometer");
        BigDecimal purchasePrice = positiveMoney(command.purchasePrice(), "Purchase price");
        PurchaseJurisdiction purchaseJurisdiction = resolvePurchaseJurisdiction(
                career,
                command.vehicleCondition(),
                command.purchaseCountryCode(),
                command.purchaseStateCode()
        );
        validatePurchaseJurisdiction(game, purchaseJurisdiction);
        Instant now = clock.instant();
        UUID vehicleId = UUID.randomUUID();
        Integer payrollMonth = game == CareerGame.ETS2 ? career.getCurrentPayrollMonth() : null;
        UUID financingContractId = null;

        if (command.acquisitionType() == VehicleAcquisitionType.CASH) {
            if (currentBalance.compareTo(purchasePrice) < 0) {
                throw conflict(
                        "VEHICLE_CASH_BALANCE_INSUFFICIENT",
                        "Insufficient balance for truck purchase",
                        "The current career balance cannot cover the cash purchase price"
                );
            }
            ledgerWriter.ensureOpeningBalance(
                    careerId,
                    currentBalance,
                    career.getDisplayCurrency(),
                    game == CareerGame.ETS2 ? 1 : null,
                    career.getCreatedAt()
            );
            career.debitBalance(purchasePrice, now);
        } else if (command.acquisitionType() == VehicleAcquisitionType.FINANCED) {
            if (command.financingTermPeriods() == null || command.financingTermPeriods() < 1) {
                throw badRequest(
                        "VEHICLE_FINANCING_TERM_REQUIRED",
                        "Financing term required",
                        "A server-supported financing term is required for financed acquisition"
                );
            }
            FinancingOperations.ContractDetails contractDetails = financingOperations.create(
                    userId,
                    game,
                    careerId,
                    new FinancingOperations.CreateContractCommand(
                            command.operationId(),
                            FinancialProductType.VEHICLE_FINANCING,
                            purchasePrice,
                            command.financingTermPeriods(),
                            command.expectedOperationalWeek(),
                            command.expectedPayrollMonth(),
                            expectedBalance,
                            purchaseJurisdiction.countryCode(),
                            purchaseJurisdiction.stateCode()
                    )
            );
            FinancialContractEntity financingContract = contractDetails.contract();
            if (financingContract.getProductType() != FinancialProductType.VEHICLE_FINANCING
                    || financingContract.getRequestedAmount().compareTo(purchasePrice) != 0) {
                throw conflict(
                        "VEHICLE_FINANCING_OPERATION_CONFLICT",
                        "Financing operation conflict",
                        "The supplied operationId already represents a different financial contract"
                );
            }
            financingContractId = financingContract.getId();
        } else {
            throw badRequest(
                    "VEHICLE_ACQUISITION_TYPE_INVALID",
                    "Acquisition type invalid",
                    "Truck acquisition must be CASH or FINANCED"
            );
        }

        CareerVehicleEntity vehicle = new CareerVehicleEntity(
                vehicleId,
                careerId,
                command.operationId(),
                make,
                model,
                modelYear,
                odometer,
                purchasePrice,
                career.getDisplayCurrency(),
                command.vehicleCondition(),
                purchaseJurisdiction.countryCode(),
                purchaseJurisdiction.stateCode(),
                command.acquisitionType(),
                VehicleStatus.ACTIVE,
                career.getCurrentOperationalWeek(),
                payrollMonth,
                now,
                now
        );
        vehicleRepository.saveAndFlush(vehicle);

        if (financingContractId != null) {
            FinancialContractEntity contract = contractRepository.findById(financingContractId).orElseThrow();
            contract.linkVehicle(vehicleId, now);
            contractRepository.saveAndFlush(contract);
        } else {
            BigDecimal after = career.getBalance().setScale(2, RoundingMode.UNNECESSARY);
            ledgerWriter.record(new LedgerEntryDraft(
                    careerId,
                    LedgerEntryType.BALANCE_ADJUSTMENT,
                    LedgerSourceType.CAREER,
                    vehicleId,
                    16,
                    career.getCurrentOperationalWeek(),
                    payrollMonth,
                    purchasePrice.negate(),
                    purchasePrice.negate(),
                    BigDecimal.ZERO.setScale(2),
                    currentBalance,
                    after,
                    null,
                    null,
                    career.getDisplayCurrency(),
                    "Compra à vista de caminhão — " + make + " " + model,
                    Map.of(
                            "vehicleId", vehicleId.toString(),
                            "acquisitionType", VehicleAcquisitionType.CASH.name(),
                            "vehicleCondition", command.vehicleCondition().name(),
                            "purchaseCountryCode", purchaseJurisdiction.countryCode(),
                            "purchaseStateCode", purchaseJurisdiction.stateCode() == null ? "" : purchaseJurisdiction.stateCode(),
                            "purchasePrice", purchasePrice
                    ),
                    now
            ));
        }

        career.unlockPhase2(now);
        career.updateDefaultTruck(make, model, now);
        careerRepository.flush();
        return new VehicleDetails(vehicle, financingContractId, career.getCurrentPhase(), career.getBalance(), false);
    }

    private VehicleDetails details(CareerVehicleEntity vehicle, CareerEntity career, boolean replay) {
        UUID contractId = contractRepository.findByVehicleId(vehicle.getId())
                .map(FinancialContractEntity::getId)
                .orElse(null);
        return new VehicleDetails(vehicle, contractId, career.getCurrentPhase(), career.getBalance(), replay);
    }

    private CareerEntity ownedCareer(UUID userId, CareerGame game, UUID careerId) {
        return careerRepository.findByIdAndUserIdAndGame(careerId, userId, game)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "CAREER_NOT_FOUND",
                        "The requested career does not exist"
                ));
    }

    private CareerEntity lockedCareer(UUID userId, CareerGame game, UUID careerId) {
        return careerRepository.findForUpdateByIdAndUserIdAndGame(careerId, userId, game)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "CAREER_NOT_FOUND",
                        "The requested career does not exist"
                ));
    }

    private PurchaseJurisdiction resolvePurchaseJurisdiction(
            CareerEntity career,
            VehicleCondition condition,
            String requestedCountryCode,
            String requestedStateCode
    ) {
        if (condition == null) {
            throw badRequest("VEHICLE_CONDITION_REQUIRED", "Truck condition required", "Truck condition must be NEW or USED");
        }
        String requestedCountry = normalizeCode(requestedCountryCode);
        String requestedState = normalizeCode(requestedStateCode);

        if (career.getGame() == CareerGame.ATS) {
            String headquartersState = normalizeCode(career.getStateCode());
            if (condition == VehicleCondition.USED) {
                if ((!requestedCountry.isBlank() && !"US".equals(requestedCountry))
                        || (!requestedState.isBlank() && !headquartersState.equals(requestedState))) {
                    throw badRequest(
                            "USED_TRUCK_JURISDICTION_FIXED",
                            "Used truck jurisdiction is fixed",
                            "Used trucks use the career headquarters state for purchase and financing rules"
                    );
                }
                return new PurchaseJurisdiction("US", headquartersState);
            }
            if (requestedState.isBlank()) {
                throw badRequest(
                        "NEW_TRUCK_PURCHASE_STATE_REQUIRED",
                        "Purchase state required",
                        "Select the ATS state where the new truck was purchased"
                );
            }
            if (!requestedCountry.isBlank() && !"US".equals(requestedCountry)) {
                throw badRequest(
                        "NEW_TRUCK_PURCHASE_COUNTRY_INVALID",
                        "Purchase country invalid",
                        "ATS truck purchases must use the United States"
                );
            }
            return new PurchaseJurisdiction("US", requestedState);
        }

        String headquartersCountry = normalizeCode(career.getCountryCode());
        if (condition == VehicleCondition.USED) {
            if ((!requestedCountry.isBlank() && !headquartersCountry.equals(requestedCountry))
                    || !requestedState.isBlank()) {
                throw badRequest(
                        "USED_TRUCK_JURISDICTION_FIXED",
                        "Used truck jurisdiction is fixed",
                        "Used trucks use the career headquarters country for purchase and financing rules"
                );
            }
            return new PurchaseJurisdiction(headquartersCountry, null);
        }
        if (requestedCountry.isBlank()) {
            throw badRequest(
                    "NEW_TRUCK_PURCHASE_COUNTRY_REQUIRED",
                    "Purchase country required",
                    "Select the ETS2 country where the new truck was purchased"
            );
        }
        if (!requestedState.isBlank()) {
            throw badRequest(
                    "NEW_TRUCK_PURCHASE_STATE_INVALID",
                    "Purchase state invalid",
                    "ETS2 truck purchases use country jurisdiction, not a state"
            );
        }
        return new PurchaseJurisdiction(requestedCountry, null);
    }

    private void validatePurchaseJurisdiction(CareerGame game, PurchaseJurisdiction jurisdiction) {
        try {
            financingJurisdictionCatalog.validateVehiclePurchaseJurisdiction(
                    game,
                    jurisdiction.countryCode(),
                    jurisdiction.stateCode()
            );
        } catch (IllegalArgumentException ex) {
            throw badRequest(
                    "VEHICLE_PURCHASE_JURISDICTION_UNAVAILABLE",
                    "Purchase jurisdiction unavailable",
                    ex.getMessage()
            );
        }
    }

    private String normalizeCode(String value) {
        return value == null ? "" : value.strip().toUpperCase(java.util.Locale.ROOT);
    }

    private record PurchaseJurisdiction(String countryCode, String stateCode) {}

    private void validateContext(CareerEntity career, Integer expectedWeek, Integer expectedMonth) {
        if (expectedWeek == null || expectedWeek != career.getCurrentOperationalWeek()) {
            throw conflict(
                    "VEHICLE_WEEK_CONFLICT",
                    "Operational week changed",
                    "The requested operational week is no longer current"
            );
        }
        if (career.getGame() == CareerGame.ETS2
                && (expectedMonth == null || !expectedMonth.equals(career.getCurrentPayrollMonth()))) {
            throw conflict(
                    "VEHICLE_MONTH_CONFLICT",
                    "Payroll month changed",
                    "The requested payroll month is no longer current"
            );
        }
    }

    private String requiredText(String value, int maxLength, String label) {
        String result = value == null ? "" : value.trim();
        if (result.isEmpty() || result.length() > maxLength) {
            throw badRequest(
                    "VEHICLE_FIELD_INVALID",
                    "Truck data invalid",
                    label + " is required and must not exceed " + maxLength + " characters"
            );
        }
        return result;
    }

    private Short validateYear(Short value) {
        if (value == null) return null;
        if (value < 1950 || value > 2100) {
            throw badRequest(
                    "VEHICLE_YEAR_INVALID",
                    "Truck year invalid",
                    "Truck model year must be between 1950 and 2100"
            );
        }
        return value;
    }

    private BigDecimal positiveMoney(BigDecimal value, String label) {
        BigDecimal result = scaled(value, 2, label);
        if (result.signum() <= 0) {
            throw badRequest("VEHICLE_AMOUNT_INVALID", "Amount invalid", label + " must be greater than zero");
        }
        return result;
    }

    private BigDecimal nonNegative(BigDecimal value, int scale, String label) {
        BigDecimal result = scaled(value, scale, label);
        if (result.signum() < 0) {
            throw badRequest("VEHICLE_ODOMETER_INVALID", "Odometer invalid", label + " must be zero or greater");
        }
        return result;
    }

    private BigDecimal money(BigDecimal value, String label) {
        return scaled(value, 2, label);
    }

    private BigDecimal scaled(BigDecimal value, int scale, String label) {
        if (value == null) {
            throw badRequest("VEHICLE_VALUE_REQUIRED", "Value required", label + " is required");
        }
        try {
            return value.setScale(scale, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw badRequest(
                    "VEHICLE_VALUE_INVALID",
                    "Value invalid",
                    label + " supports at most " + scale + " decimal places"
            );
        }
    }

    private ApiProblemException conflict(String code, String title, String detail) {
        return new ApiProblemException(HttpStatus.CONFLICT, code, title, detail);
    }

    private ApiProblemException badRequest(String code, String title, String detail) {
        return new ApiProblemException(HttpStatus.BAD_REQUEST, code, title, detail);
    }
}
