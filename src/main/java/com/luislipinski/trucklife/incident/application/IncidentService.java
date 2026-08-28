package com.luislipinski.trucklife.incident.application;

import com.luislipinski.trucklife.career.domain.CareerGame;
import com.luislipinski.trucklife.career.persistence.CareerEntity;
import com.luislipinski.trucklife.career.persistence.CareerRepository;
import com.luislipinski.trucklife.incident.domain.IncidentChargeMethod;
import com.luislipinski.trucklife.incident.domain.IncidentStatus;
import com.luislipinski.trucklife.incident.domain.IncidentType;
import com.luislipinski.trucklife.incident.persistence.IncidentEntity;
import com.luislipinski.trucklife.incident.persistence.IncidentPayslipDeductionRepository;
import com.luislipinski.trucklife.incident.persistence.IncidentRepository;
import com.luislipinski.trucklife.ledger.application.LedgerEntryDraft;
import com.luislipinski.trucklife.ledger.application.LedgerWriter;
import com.luislipinski.trucklife.ledger.domain.LedgerEntryType;
import com.luislipinski.trucklife.ledger.domain.LedgerSourceType;
import com.luislipinski.trucklife.shared.error.ApiProblemException;
import com.luislipinski.trucklife.shared.error.ResourceNotFoundException;
import com.luislipinski.trucklife.trip.persistence.TripEntity;
import com.luislipinski.trucklife.trip.persistence.TripRepository;
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
public class IncidentService implements IncidentOperations {
    private final CareerRepository careerRepository; private final TripRepository tripRepository;
    private final IncidentRepository incidentRepository; private final IncidentPayslipDeductionRepository deductionRepository;
    private final LedgerWriter ledgerWriter; private final Clock clock;

    public IncidentService(CareerRepository careerRepository,TripRepository tripRepository,IncidentRepository incidentRepository,
                           IncidentPayslipDeductionRepository deductionRepository,LedgerWriter ledgerWriter,Clock clock){
        this.careerRepository=careerRepository;this.tripRepository=tripRepository;this.incidentRepository=incidentRepository;
        this.deductionRepository=deductionRepository;this.ledgerWriter=ledgerWriter;this.clock=clock;
    }

    @Override @Transactional
    public Result create(UUID userId,CareerGame game,UUID careerId,int expectedOperationalWeek,IncidentType type,BigDecimal amount,
                         UUID relatedTripId,String route,String description,IncidentChargeMethod chargeMethod){
        CareerEntity career=lockedOwnedCareer(userId,game,careerId);
        if(expectedOperationalWeek!=career.getCurrentOperationalWeek())throw conflict("INCIDENT_WEEK_CONFLICT","Operational week changed","The requested week is no longer the career current operational week");
        TripEntity trip=relatedTripId==null?null:tripRepository.findByIdAndCareerId(relatedTripId,career.getId()).orElseThrow(()->new ResourceNotFoundException("TRIP_NOT_FOUND","The related trip does not exist for this career"));
        int incidentWeek=trip==null?career.getCurrentOperationalWeek():trip.getOperationalWeek();String routeLabel=trip==null?routeLabel(route):trip.getOriginCity()+" → "+trip.getDestinationCity();
        BigDecimal normalizedAmount=amount.setScale(2,RoundingMode.UNNECESSARY);Instant now=clock.instant();IncidentStatus status=chargeMethod==IncidentChargeMethod.BALANCE?IncidentStatus.PAID_BALANCE:IncidentStatus.PENDING_PAYSLIP;
        BigDecimal remaining=chargeMethod==IncidentChargeMethod.BALANCE?BigDecimal.ZERO.setScale(2):normalizedAmount;
        IncidentEntity incident=new IncidentEntity(UUID.randomUUID(),career.getId(),relatedTripId,incidentWeek,type,normalizedAmount,remaining,routeLabel,description.strip(),chargeMethod,status,now,now);
        BigDecimal balanceBefore=null;
        if(chargeMethod==IncidentChargeMethod.BALANCE){ledgerWriter.ensureOpeningBalance(career.getId(),career.getBalance(),career.getDisplayCurrency(),career.getGame()==CareerGame.ETS2?1:null,career.getCreatedAt());balanceBefore=career.getBalance().setScale(2,RoundingMode.UNNECESSARY);career.debitBalance(normalizedAmount,now);}
        incidentRepository.saveAndFlush(incident);
        if(chargeMethod==IncidentChargeMethod.BALANCE){ledgerWriter.record(new LedgerEntryDraft(career.getId(),LedgerEntryType.INCIDENT_CHARGE,LedgerSourceType.INCIDENT,incident.getId(),10,incidentWeek,career.getGame()==CareerGame.ETS2?career.getCurrentPayrollMonth():null,normalizedAmount.negate(),normalizedAmount.negate(),BigDecimal.ZERO.setScale(2),balanceBefore,career.getBalance().setScale(2,RoundingMode.UNNECESSARY),null,null,career.getDisplayCurrency(),"Ocorrência: "+incident.getDescription(),Map.of("incidentType",type.name(),"route",routeLabel,"chargeMethod",chargeMethod.name()),now));}
        careerRepository.flush();return result(incident);
    }

    @Override @Transactional(readOnly=true) public List<Result> list(UUID userId,CareerGame game,UUID careerId){CareerEntity career=ownedCareer(userId,game,careerId);return incidentRepository.findAllByCareerIdOrderByRecordedAtDescIdDesc(career.getId()).stream().map(this::result).toList();}
    @Override @Transactional(readOnly=true) public Result get(UUID userId,CareerGame game,UUID careerId,UUID incidentId){CareerEntity career=ownedCareer(userId,game,careerId);IncidentEntity incident=incidentRepository.findByIdAndCareerId(incidentId,career.getId()).orElseThrow(()->new ResourceNotFoundException("INCIDENT_NOT_FOUND","The requested incident does not exist"));return result(incident);}
    @Override @Transactional public void cancel(UUID userId,CareerGame game,UUID careerId,UUID incidentId){CareerEntity career=lockedOwnedCareer(userId,game,careerId);IncidentEntity incident=incidentRepository.findByIdAndCareerId(incidentId,career.getId()).orElseThrow(()->new ResourceNotFoundException("INCIDENT_NOT_FOUND","The requested incident does not exist"));if(incident.getStatus()==IncidentStatus.CANCELLED)return;if(!incident.canCancel())throw conflict("INCIDENT_NOT_CANCELLABLE","Incident cannot be cancelled","Only an untouched incident pending for a future payslip can be cancelled");incident.cancel(clock.instant());incidentRepository.flush();}
    private Result result(IncidentEntity incident){return new Result(incident,deductionRepository.findAllByIncidentIdOrderByRecordedAtAscIdAsc(incident.getId()));}
    private String routeLabel(String route){return route==null||route.isBlank()?"Manual route":route.strip();}
    private CareerEntity lockedOwnedCareer(UUID userId,CareerGame game,UUID careerId){return careerRepository.findForUpdateByIdAndUserIdAndGame(careerId,userId,game).orElseThrow(()->new ResourceNotFoundException("CAREER_NOT_FOUND","The requested career does not exist"));}
    private CareerEntity ownedCareer(UUID userId,CareerGame game,UUID careerId){return careerRepository.findByIdAndUserIdAndGame(careerId,userId,game).orElseThrow(()->new ResourceNotFoundException("CAREER_NOT_FOUND","The requested career does not exist"));}
    private ApiProblemException conflict(String code,String title,String detail){return new ApiProblemException(HttpStatus.CONFLICT,code,title,detail);}
}
