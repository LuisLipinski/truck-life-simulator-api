package com.luislipinski.trucklife.phase2.application;

import com.luislipinski.trucklife.career.domain.CareerEventType;
import com.luislipinski.trucklife.career.domain.CareerGame;
import com.luislipinski.trucklife.career.persistence.CareerEntity;
import com.luislipinski.trucklife.career.persistence.CareerEventRepository;
import com.luislipinski.trucklife.career.persistence.CareerRepository;
import com.luislipinski.trucklife.ledger.application.LedgerEntryDraft;
import com.luislipinski.trucklife.ledger.application.LedgerWriter;
import com.luislipinski.trucklife.ledger.domain.LedgerEntryType;
import com.luislipinski.trucklife.ledger.domain.LedgerSourceType;
import com.luislipinski.trucklife.payroll.persistence.PayslipEntity;
import com.luislipinski.trucklife.payroll.persistence.PayslipRepository;
import com.luislipinski.trucklife.phase2.domain.Phase2AffiliationChoice;
import com.luislipinski.trucklife.phase2.domain.Phase2TransitionStatus;
import com.luislipinski.trucklife.phase2.persistence.CarrierContractEntity;
import com.luislipinski.trucklife.phase2.persistence.CarrierContractRepository;
import com.luislipinski.trucklife.phase2.persistence.Phase2TransitionEntity;
import com.luislipinski.trucklife.phase2.persistence.Phase2TransitionRepository;
import com.luislipinski.trucklife.shared.error.ApiProblemException;
import com.luislipinski.trucklife.shared.error.ResourceNotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class Phase2TransitionService implements Phase2TransitionOperations {
    private final CareerRepository careerRepository;
    private final CareerEventRepository eventRepository;
    private final PayslipRepository payslipRepository;
    private final Phase2TransitionRepository transitionRepository;
    private final CarrierContractRepository carrierContractRepository;
    private final Phase2TerminationPolicyCatalog terminationPolicyCatalog;
    private final Phase2JurisdictionCatalog jurisdictionCatalog;
    private final LedgerWriter ledgerWriter;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public Phase2TransitionService(
            CareerRepository careerRepository,
            CareerEventRepository eventRepository,
            PayslipRepository payslipRepository,
            Phase2TransitionRepository transitionRepository,
            CarrierContractRepository carrierContractRepository,
            Phase2TerminationPolicyCatalog terminationPolicyCatalog,
            Phase2JurisdictionCatalog jurisdictionCatalog,
            LedgerWriter ledgerWriter,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.careerRepository = careerRepository;
        this.eventRepository = eventRepository;
        this.payslipRepository = payslipRepository;
        this.transitionRepository = transitionRepository;
        this.carrierContractRepository = carrierContractRepository;
        this.terminationPolicyCatalog = terminationPolicyCatalog;
        this.jurisdictionCatalog = jurisdictionCatalog;
        this.ledgerWriter = ledgerWriter;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Details getOrCreate(UUID userId, CareerGame game, UUID careerId) {
        CareerEntity career = lockedCareer(userId, game, careerId);
        Phase2TransitionEntity transition = ensureTransition(career);
        CarrierContractEntity contract = carrierContractRepository.findByTransitionId(transition.getId()).orElse(null);
        return new Details(transition, contract, money(career.getBalance()), false);
    }

    @Override
    @Transactional
    public Details complete(UUID userId, CareerGame game, UUID careerId, CompleteCommand command) {
        if (command.operationId() == null) {
            throw badRequest("PHASE2_OPERATION_REQUIRED", "Operation required", "operationId is required");
        }

        Phase2TransitionEntity replay = transitionRepository.findByCompletionOperationId(command.operationId()).orElse(null);
        if (replay != null) {
            if (!replay.getCareerId().equals(careerId)) {
                throw conflict("PHASE2_OPERATION_ID_CONFLICT", "Operation identifier conflict", "The supplied operationId belongs to another career");
            }
            CareerEntity replayCareer = ownedCareer(userId, game, careerId);
            CarrierContractEntity contract = carrierContractRepository.findByTransitionId(replay.getId()).orElse(null);
            return new Details(replay, contract, money(replayCareer.getBalance()), true);
        }

        CareerEntity career = lockedCareer(userId, game, careerId);
        Phase2TransitionEntity transition = ensureTransition(career);
        if (transition.getStatus() == Phase2TransitionStatus.COMPLETED) {
            throw conflict("PHASE2_TRANSITION_COMPLETED", "Phase 2 transition already completed", "This career already completed the Phase 2 onboarding");
        }

        BigDecimal expectedBalance = requiredMoney(command.expectedBalance(), "Expected balance");
        BigDecimal currentBalance = money(career.getBalance());
        if (currentBalance.compareTo(expectedBalance) != 0) {
            throw conflict("PHASE2_BALANCE_CONFLICT", "Career balance changed", "The expected balance no longer matches the current career balance");
        }
        if (command.affiliation() == null) {
            throw badRequest("PHASE2_AFFILIATION_REQUIRED", "Carrier choice required", "Choose whether to remain with the current company or join another carrier");
        }
        if (command.effectiveDay() == null) {
            throw badRequest("PHASE2_EFFECTIVE_DAY_REQUIRED", "Effective day required", "Select the operational weekday for the transition");
        }

        String carrierName;
        String countryCandidate;
        String stateCandidate;
        if (command.affiliation() == Phase2AffiliationChoice.CURRENT_COMPANY) {
            carrierName = transition.getPreviousCompanyName();
            countryCandidate = career.getCountryCode();
            stateCandidate = career.getStateCode();
        } else {
            carrierName = requiredText(command.carrierName(), 160, "Carrier name");
            countryCandidate = command.carrierCountryCode();
            stateCandidate = command.carrierStateCode();
        }

        Phase2JurisdictionCatalog.Jurisdiction jurisdiction;
        try {
            jurisdiction = jurisdictionCatalog.normalizeAndValidate(
                    game,
                    countryCandidate,
                    stateCandidate,
                    career.getCountryCode(),
                    career.getStateCode()
            );
        } catch (IllegalArgumentException ex) {
            throw badRequest("PHASE2_CARRIER_JURISDICTION_INVALID", "Carrier jurisdiction invalid", ex.getMessage());
        }

        Instant now = clock.instant();
        CarrierContractEntity contract = new CarrierContractEntity(
                UUID.randomUUID(),
                careerId,
                transition.getId(),
                carrierName,
                command.affiliation(),
                jurisdiction.countryCode(),
                jurisdiction.stateCode(),
                jurisdiction.compensationRuleKey(),
                career.getCurrentOperationalWeek(),
                command.effectiveDay(),
                now,
                now
        );
        carrierContractRepository.saveAndFlush(contract);

        BigDecimal settlement = transition.getStatutorySettlementAmount().setScale(2, RoundingMode.UNNECESSARY);
        if (settlement.signum() > 0) {
            ledgerWriter.ensureOpeningBalance(
                    careerId,
                    currentBalance,
                    career.getDisplayCurrency(),
                    game == CareerGame.ETS2 ? 1 : null,
                    career.getCreatedAt()
            );
            Map<String,Object> metadata = new LinkedHashMap<>();
            metadata.put("phase2TransitionId", transition.getId().toString());
            metadata.put("previousCompanyName", transition.getPreviousCompanyName());
            metadata.put("terminationReason", transition.getTerminationReason());
            metadata.put("policyVersion", transition.getPolicyVersion());
            metadata.put("terminationCountryCode", transition.getTerminationCountryCode());
            metadata.put("terminationStateCode", transition.getTerminationStateCode());
            ledgerWriter.record(new LedgerEntryDraft(
                    careerId,
                    LedgerEntryType.BALANCE_ADJUSTMENT,
                    LedgerSourceType.BALANCE_ADJUSTMENT,
                    transition.getId(),
                    18,
                    career.getCurrentOperationalWeek(),
                    game == CareerGame.ETS2 ? career.getCurrentPayrollMonth() : null,
                    settlement,
                    settlement,
                    BigDecimal.ZERO.setScale(2),
                    currentBalance,
                    currentBalance.add(settlement),
                    null,
                    null,
                    career.getDisplayCurrency(),
                    "Acerto legal de desligamento — " + transition.getPreviousCompanyName(),
                    metadata,
                    now
            ));
            career.creditBalance(settlement, now);
        }

        career.changeEmployer(carrierName, now);
        transition.complete(command.operationId(), command.effectiveDay(), command.affiliation(), now);
        transitionRepository.saveAndFlush(transition);
        careerRepository.flush();

        return new Details(transition, contract, money(career.getBalance()), false);
    }

    private Phase2TransitionEntity ensureTransition(CareerEntity career) {
        if (career.getCurrentPhase() < 2) {
            throw conflict(
                    "PHASE2_NOT_UNLOCKED",
                    "Phase 2 is not unlocked",
                    "Acquire the first owned truck before starting the Phase 2 transition"
            );
        }

        Phase2TransitionEntity existing = transitionRepository.findByCareerId(career.getId()).orElse(null);
        if (existing != null) {
            return existing;
        }

        int employmentStartWeek = eventRepository.findAllByCareerIdOrderByOperationalWeekAscRecordedAtAscIdAsc(career.getId()).stream()
                .filter(event -> event.getType() == CareerEventType.EMPLOYER_CHANGED)
                .mapToInt(event -> event.getOperationalWeek())
                .reduce((first, second) -> second)
                .orElse(1);

        List<PayslipEntity> payslips = payslipRepository.findAllByCareerIdOrderByGeneratedAtDescIdDesc(career.getId());
        Phase2TerminationPolicyCatalog.Settlement settlement =
                terminationPolicyCatalog.quote(career, employmentStartWeek, payslips);

        Instant now = clock.instant();
        String previousCompany = optionalCompany(career.getCompanyName());
        Phase2TransitionEntity transition = new Phase2TransitionEntity(
                UUID.randomUUID(),
                career.getId(),
                previousCompany,
                career.getGame() == CareerGame.ETS2 ? normalize(career.getCountryCode()) : "US",
                career.getGame() == CareerGame.ATS ? normalize(career.getStateCode()) : null,
                employmentStartWeek,
                career.getCurrentOperationalWeek(),
                settlement.amount(),
                career.getDisplayCurrency(),
                settlement.policyVersion(),
                settlement.policySource(),
                settlement.policySummary(),
                json(settlement.snapshot()),
                now,
                now
        );
        return transitionRepository.saveAndFlush(transition);
    }

    private String optionalCompany(String value) {
        String company = value == null ? "" : value.strip();
        return company.isBlank() ? "Transportadora da Fase 1" : company;
    }

    private String requiredText(String value, int maxLength, String label) {
        String result = value == null ? "" : value.strip();
        if (result.isBlank() || result.length() > maxLength) {
            throw badRequest("PHASE2_FIELD_INVALID", "Phase 2 data invalid", label + " is required and must not exceed " + maxLength + " characters");
        }
        return result;
    }

    private BigDecimal requiredMoney(BigDecimal value, String label) {
        if (value == null) {
            throw badRequest("PHASE2_VALUE_REQUIRED", "Value required", label + " is required");
        }
        try {
            return value.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw badRequest("PHASE2_VALUE_INVALID", "Value invalid", label + " supports at most two decimal places");
        }
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.UNNECESSARY);
    }

    private String normalize(String value) {
        return value == null ? null : value.strip().toUpperCase(java.util.Locale.ROOT);
    }

    private String json(Map<String,Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException ex) {
            throw new IllegalStateException("Phase 2 policy snapshot could not be serialized", ex);
        }
    }

    private CareerEntity lockedCareer(UUID userId, CareerGame game, UUID careerId) {
        return careerRepository.findForUpdateByIdAndUserIdAndGame(careerId, userId, game)
                .orElseThrow(() -> new ResourceNotFoundException("CAREER_NOT_FOUND", "The requested career does not exist"));
    }

    private CareerEntity ownedCareer(UUID userId, CareerGame game, UUID careerId) {
        return careerRepository.findByIdAndUserIdAndGame(careerId, userId, game)
                .orElseThrow(() -> new ResourceNotFoundException("CAREER_NOT_FOUND", "The requested career does not exist"));
    }

    private ApiProblemException badRequest(String code, String title, String detail) {
        return new ApiProblemException(HttpStatus.BAD_REQUEST, code, title, detail);
    }

    private ApiProblemException conflict(String code, String title, String detail) {
        return new ApiProblemException(HttpStatus.CONFLICT, code, title, detail);
    }
}
