package com.luislipinski.trucklife.payroll.application;

import com.luislipinski.trucklife.career.domain.CareerGame;
import com.luislipinski.trucklife.career.persistence.CareerEntity;
import com.luislipinski.trucklife.career.persistence.CareerRepository;
import com.luislipinski.trucklife.finance.application.FinancePayrollOperations;
import com.luislipinski.trucklife.incident.domain.IncidentChargeMethod;
import com.luislipinski.trucklife.incident.persistence.IncidentEntity;
import com.luislipinski.trucklife.incident.persistence.IncidentPayslipDeductionEntity;
import com.luislipinski.trucklife.incident.persistence.IncidentPayslipDeductionRepository;
import com.luislipinski.trucklife.incident.persistence.IncidentRepository;
import com.luislipinski.trucklife.ledger.application.LedgerEntryDraft;
import com.luislipinski.trucklife.ledger.application.LedgerWriter;
import com.luislipinski.trucklife.ledger.domain.LedgerEntryType;
import com.luislipinski.trucklife.ledger.domain.LedgerSourceType;
import com.luislipinski.trucklife.payroll.domain.PayslipLineType;
import com.luislipinski.trucklife.payroll.persistence.PayrollPeriodEntity;
import com.luislipinski.trucklife.payroll.persistence.PayrollPeriodRepository;
import com.luislipinski.trucklife.payroll.persistence.PayslipEntity;
import com.luislipinski.trucklife.payroll.persistence.PayslipLineEntity;
import com.luislipinski.trucklife.payroll.persistence.PayslipLineRepository;
import com.luislipinski.trucklife.payroll.persistence.PayslipRepository;
import com.luislipinski.trucklife.shared.error.ApiProblemException;
import com.luislipinski.trucklife.shared.error.ResourceNotFoundException;
import com.luislipinski.trucklife.trip.persistence.TripEntity;
import com.luislipinski.trucklife.trip.persistence.TripRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
public class PayslipService implements PayslipOperations {
    private static final int ETS2_MIN_WEEKS_PER_PAYROLL_MONTH=4;
    private static final int ETS2_MAX_WEEKS_PER_PAYROLL_MONTH=5;
    private static final String POLICY_VERSION="phase1-payroll-2026-v2";
    private static final TypeReference<Map<String,Object>> SNAPSHOT_TYPE=new TypeReference<>(){};
    private final CareerRepository careerRepository; private final PayrollPeriodRepository payrollPeriodRepository;
    private final PayslipRepository payslipRepository; private final PayslipLineRepository payslipLineRepository;
    private final TripRepository tripRepository; private final IncidentRepository incidentRepository;
    private final IncidentPayslipDeductionRepository incidentDeductionRepository; private final PayrollCalculator calculator;
    private final PayrollContextSnapshotFactory contextSnapshotFactory; private final FinancePayrollOperations financePayrollOperations;
    private final LedgerWriter ledgerWriter; private final ObjectMapper objectMapper; private final Clock clock;

    public PayslipService(CareerRepository careerRepository,PayrollPeriodRepository payrollPeriodRepository,PayslipRepository payslipRepository,
                          PayslipLineRepository payslipLineRepository,TripRepository tripRepository,IncidentRepository incidentRepository,
                          IncidentPayslipDeductionRepository incidentDeductionRepository,PayrollCalculator calculator,
                          PayrollContextSnapshotFactory contextSnapshotFactory,FinancePayrollOperations financePayrollOperations,
                          LedgerWriter ledgerWriter,ObjectMapper objectMapper,Clock clock){
        this.careerRepository=careerRepository;this.payrollPeriodRepository=payrollPeriodRepository;this.payslipRepository=payslipRepository;
        this.payslipLineRepository=payslipLineRepository;this.tripRepository=tripRepository;this.incidentRepository=incidentRepository;
        this.incidentDeductionRepository=incidentDeductionRepository;this.calculator=calculator;this.contextSnapshotFactory=contextSnapshotFactory;
        this.financePayrollOperations=financePayrollOperations;this.ledgerWriter=ledgerWriter;this.objectMapper=objectMapper;this.clock=clock;
    }

    @Override @Transactional
    public Result generate(UUID userId,CareerGame game,UUID careerId,Integer expectedOperationalWeek,Integer expectedPayrollMonth){CareerEntity career=lockedOwnedCareer(userId,game,careerId);try{return game==CareerGame.ATS?generateAts(career,expectedOperationalWeek):generateEts2(career,expectedPayrollMonth);}catch(IllegalArgumentException ex){throw conflict("PAYSLIP_POLICY_UNAVAILABLE","Payroll policy unavailable",ex.getMessage());}}
    @Override @Transactional(readOnly=true) public List<Result> list(UUID userId,CareerGame game,UUID careerId){CareerEntity career=ownedCareer(userId,game,careerId);return payslipRepository.findAllByCareerIdOrderByGeneratedAtDescIdDesc(career.getId()).stream().map(this::result).toList();}
    @Override @Transactional(readOnly=true) public Result get(UUID userId,CareerGame game,UUID careerId,UUID payslipId){CareerEntity career=ownedCareer(userId,game,careerId);PayslipEntity payslip=payslipRepository.findByIdAndCareerId(payslipId,career.getId()).orElseThrow(()->new ResourceNotFoundException("PAYSLIP_NOT_FOUND","The requested payslip does not exist"));return result(payslip);}

    private Result generateAts(CareerEntity career,Integer expectedOperationalWeek){
        if(expectedOperationalWeek==null)throw badRequest("PAYSLIP_WEEK_REQUIRED","Operational week required","ATS payslip generation requires expectedOperationalWeek");int currentWeek=career.getCurrentOperationalWeek();
        if(expectedOperationalWeek!=currentWeek)throw conflict("PAYSLIP_WEEK_CONFLICT","Operational week changed","The requested week is no longer the career current operational week");
        if(payslipRepository.existsByCareerIdAndOperationalWeek(career.getId(),currentWeek))throw conflict("PAYSLIP_ALREADY_GENERATED","Payslip already generated","The ATS operational week already has a payslip");
        Map<String,Object> periodContext=contextSnapshotFactory.from(career);PayrollCalculator.Context calculationContext=contextFrom(periodContext);List<TripEntity> trips=tripRepository.findAllByCareerIdAndOperationalWeekOrderByCreatedAtAscIdAsc(career.getId(),currentWeek);
        PayrollCalculator.Calculation calculation=calculator.calculate(CareerGame.ATS,calculationContext,trips);IncidentDeductionPlan incidentPlan=planIncidentDeductions(career.getId(),currentWeek,calculation.deposit());
        BigDecimal finalDeposit=calculation.deposit().subtract(incidentPlan.total());Instant now=clock.instant();UUID payslipId=UUID.randomUUID();
        PayslipEntity payslip=new PayslipEntity(payslipId,career.getId(),CareerGame.ATS,currentWeek,null,currentWeek,currentWeek,calculation.level(),calculationContext.displayCurrency(),calculation.gross(),calculation.taxTotal(),calculation.benefits(),calculation.perDiem(),calculation.netSalary(),incidentPlan.total(),finalDeposit,zero(),zero(),finalDeposit,calculation.totalDistance(),calculation.elapsedMinutes(),calculation.breakMinutes(),calculation.workedMinutes(),calculation.overrunMinutes(),json(payslipSnapshot(periodContext,List.of(currentWeek),List.of(),trips,calculation,incidentPlan,null)),now);
        payslipRepository.saveAndFlush(payslip);FinancePayrollOperations.PayrollReserveResult reserve=financePayrollOperations.applyPayslipReserve(career,finalDeposit,payslipId,now);
        payslip.applyEmergencyReserve(reserve.interestAmount(),reserve.contributionAmount(),reserve.balanceCreditAmount(),json(payslipSnapshot(periodContext,List.of(currentWeek),List.of(),trips,calculation,incidentPlan,reserve)));payslipRepository.flush();
        PayrollPeriodEntity period=new PayrollPeriodEntity(UUID.randomUUID(),career.getId(),currentWeek,null,json(periodContext),now);period.assignPayslip(payslipId);payrollPeriodRepository.saveAndFlush(period);
        applyIncidentDeductions(payslipId,incidentPlan,now);List<PayslipLineEntity> lines=saveLines(payslipId,calculation.lines(),incidentPlan);
        recordPayslipLedger(career,payslip,reserve,currentWeek,null,now);career.creditBalance(reserve.balanceCreditAmount(),now);career.advanceOperationalWeek(now);careerRepository.flush();return new Result(payslip,lines);
    }

    private Result generateEts2(CareerEntity career,Integer expectedPayrollMonth){
        if(expectedPayrollMonth==null)throw badRequest("PAYSLIP_MONTH_REQUIRED","Payroll month required","ETS2 payslip generation requires expectedPayrollMonth");Integer currentMonth=career.getCurrentPayrollMonth();if(currentMonth==null||currentMonth<1)throw new IllegalStateException("ETS2 career must have a current payroll month");
        if(!currentMonth.equals(expectedPayrollMonth))throw conflict("PAYSLIP_MONTH_CONFLICT","Payroll month changed","The requested payroll month is no longer the career current payroll month");if(payslipRepository.existsByCareerIdAndPayrollMonth(career.getId(),currentMonth))throw conflict("PAYSLIP_ALREADY_GENERATED","Payslip already generated","The ETS2 operational payroll month already has a payslip");
        List<PayrollPeriodEntity> periods=payrollPeriodRepository.findAllByCareerIdOrderByOperationalWeekAsc(career.getId()).stream().filter(p->currentMonth.equals(p.getPayrollMonth())).toList();if(periods.size()<ETS2_MIN_WEEKS_PER_PAYROLL_MONTH)throw conflict("PAYSLIP_ETS2_PERIODS_INSUFFICIENT","Not enough closed weeks","Close at least four operational weeks before generating the ETS2 monthly payslip");if(periods.size()>ETS2_MAX_WEEKS_PER_PAYROLL_MONTH)throw new IllegalStateException("ETS2 payroll month cannot contain more than five closed weeks");if(periods.stream().anyMatch(p->p.getPayslipId()!=null))throw conflict("PAYSLIP_PERIOD_ALREADY_PAID","Payroll period already paid","One or more closed operational weeks are already linked to a payslip");
        Map<String,Object> authoritativeContext=snapshot(periods.getLast().getContextSnapshotJson());PayrollCalculator.Context calculationContext=contextFrom(authoritativeContext);List<Integer> weeks=periods.stream().map(PayrollPeriodEntity::getOperationalWeek).toList();List<TripEntity> trips=tripRepository.findAllByCareerIdOrderByOperationalWeekAscCreatedAtAscIdAsc(career.getId()).stream().filter(t->weeks.contains(t.getOperationalWeek())).toList();
        PayrollCalculator.Calculation calculation=calculator.calculate(CareerGame.ETS2,calculationContext,trips);IncidentDeductionPlan incidentPlan=planIncidentDeductions(career.getId(),weeks.getLast(),calculation.deposit());BigDecimal finalDeposit=calculation.deposit().subtract(incidentPlan.total());Instant now=clock.instant();UUID payslipId=UUID.randomUUID();
        PayslipEntity payslip=new PayslipEntity(payslipId,career.getId(),CareerGame.ETS2,null,currentMonth,weeks.getFirst(),weeks.getLast(),calculation.level(),calculationContext.displayCurrency(),calculation.gross(),calculation.taxTotal(),calculation.benefits(),calculation.perDiem(),calculation.netSalary(),incidentPlan.total(),finalDeposit,zero(),zero(),finalDeposit,calculation.totalDistance(),calculation.elapsedMinutes(),calculation.breakMinutes(),calculation.workedMinutes(),calculation.overrunMinutes(),json(payslipSnapshot(authoritativeContext,weeks,periods,trips,calculation,incidentPlan,null)),now);
        payslipRepository.saveAndFlush(payslip);FinancePayrollOperations.PayrollReserveResult reserve=financePayrollOperations.applyPayslipReserve(career,finalDeposit,payslipId,now);payslip.applyEmergencyReserve(reserve.interestAmount(),reserve.contributionAmount(),reserve.balanceCreditAmount(),json(payslipSnapshot(authoritativeContext,weeks,periods,trips,calculation,incidentPlan,reserve)));payslipRepository.flush();
        periods.forEach(p->p.assignPayslip(payslipId));payrollPeriodRepository.saveAllAndFlush(periods);applyIncidentDeductions(payslipId,incidentPlan,now);List<PayslipLineEntity> lines=saveLines(payslipId,calculation.lines(),incidentPlan);
        recordPayslipLedger(career,payslip,reserve,weeks.getLast(),currentMonth,now);career.creditBalance(reserve.balanceCreditAmount(),now);career.advancePayrollMonth(now);careerRepository.flush();return new Result(payslip,lines);
    }

    private void recordPayslipLedger(CareerEntity career,PayslipEntity payslip,FinancePayrollOperations.PayrollReserveResult reserve,int endWeek,Integer payrollMonth,Instant now){
        ledgerWriter.ensureOpeningBalance(career.getId(),career.getBalance(),career.getDisplayCurrency(),career.getGame()==CareerGame.ETS2?1:null,career.getCreatedAt());BigDecimal before=career.getBalance().setScale(2,RoundingMode.UNNECESSARY);BigDecimal credit=reserve.balanceCreditAmount().setScale(2,RoundingMode.UNNECESSARY);
        Map<String,Object> metadata=new LinkedHashMap<>();metadata.put("depositAmount",payslip.getDepositAmount());metadata.put("incidentDeductionAmount",payslip.getIncidentDeductionAmount());metadata.put("reserveContributionAmount",reserve.contributionAmount());metadata.put("reserveInterestAmount",reserve.interestAmount());metadata.put("startOperationalWeek",payslip.getStartOperationalWeek());metadata.put("endOperationalWeek",payslip.getEndOperationalWeek());
        String description=career.getGame()==CareerGame.ATS?"Holerite semanal — Semana "+endWeek:"Holerite mensal — Mês "+payrollMonth;
        ledgerWriter.record(new LedgerEntryDraft(career.getId(),LedgerEntryType.PAYSLIP_CREDIT,LedgerSourceType.PAYSLIP,payslip.getId(),30,endWeek,payrollMonth,credit,credit,zero(),before,before.add(credit),null,null,career.getDisplayCurrency(),description,metadata,now));
    }

    private IncidentDeductionPlan planIncidentDeductions(UUID careerId,int eligibleThroughWeek,BigDecimal availableAmount){BigDecimal available=availableAmount.max(BigDecimal.ZERO),total=zero();List<PlannedIncidentDeduction> deductions=new ArrayList<>();List<IncidentEntity> pending=incidentRepository.findAllByCareerIdAndChargeMethodAndRemainingAmountGreaterThanOrderByRecordedAtAscIdAsc(careerId,IncidentChargeMethod.PAYSLIP,BigDecimal.ZERO);for(IncidentEntity incident:pending){if(incident.getOperationalWeek()>eligibleThroughWeek||available.signum()<=0)continue;BigDecimal amount=incident.getRemainingAmount().min(available);deductions.add(new PlannedIncidentDeduction(UUID.randomUUID(),incident,amount));available=available.subtract(amount);total=total.add(amount);}return new IncidentDeductionPlan(List.copyOf(deductions),total);}
    private void applyIncidentDeductions(UUID payslipId,IncidentDeductionPlan plan,Instant now){if(plan.items().isEmpty())return;List<IncidentEntity> incidents=new ArrayList<>();List<IncidentPayslipDeductionEntity> deductions=new ArrayList<>();for(PlannedIncidentDeduction item:plan.items()){item.incident().applyPayslipDeduction(item.amount(),now);incidents.add(item.incident());deductions.add(new IncidentPayslipDeductionEntity(item.deductionId(),item.incident().getId(),payslipId,item.amount(),now));}incidentRepository.saveAllAndFlush(incidents);incidentDeductionRepository.saveAllAndFlush(deductions);}
    private Result result(PayslipEntity payslip){return new Result(payslip,payslipLineRepository.findAllByPayslipIdOrderByLineOrderAsc(payslip.getId()));}
    private List<PayslipLineEntity> saveLines(UUID payslipId,List<PayrollCalculator.Line> calculatedLines,IncidentDeductionPlan incidentPlan){List<PayslipLineEntity> lines=new ArrayList<>();int order=1;for(PayrollCalculator.Line line:calculatedLines)lines.add(new PayslipLineEntity(UUID.randomUUID(),payslipId,order++,line.code(),line.label(),line.type(),line.amount(),line.quantity(),line.rate(),"{}"));for(PlannedIncidentDeduction item:incidentPlan.items()){Map<String,Object> metadata=new LinkedHashMap<>();metadata.put("incidentId",item.incident().getId().toString());metadata.put("operationalWeek",item.incident().getOperationalWeek());metadata.put("incidentType",item.incident().getType().name());metadata.put("route",item.incident().getRouteLabel());lines.add(new PayslipLineEntity(UUID.randomUUID(),payslipId,order++,"INCIDENT_DEDUCTION",incidentLineLabel(item.incident()),PayslipLineType.DEDUCTION,item.amount(),null,null,json(metadata)));}return payslipLineRepository.saveAllAndFlush(lines);}
    private String incidentLineLabel(IncidentEntity incident){String label="Incident deduction: "+incident.getDescription();return label.length()<=160?label:label.substring(0,157)+"...";}
    private PayrollCalculator.Context contextFrom(Map<String,Object> snapshot){return new PayrollCalculator.Context((short)integer(snapshot,"currentLevel",1),string(snapshot,"stateCode"),string(snapshot,"countryCode"),string(snapshot,"baseCurrency"),string(snapshot,"displayCurrency"),decimal(snapshot,"exchangeRate",BigDecimal.ONE),decimal(snapshot,"citySalaryFactor",BigDecimal.ONE),decimalNullable(snapshot,"payrollLevel1GrossOverride"),decimalNullable(snapshot,"payrollRouteOverrunRateOverride"),decimalNullable(snapshot,"payrollBenefitsOverride"),decimalNullable(snapshot,"payrollPerDiemRateOverride"));}
    private Map<String,Object> payslipSnapshot(Map<String,Object> authoritativeContext,List<Integer> weeks,List<PayrollPeriodEntity> periods,List<TripEntity> trips,PayrollCalculator.Calculation calculation,IncidentDeductionPlan incidentPlan,FinancePayrollOperations.PayrollReserveResult reserve){Map<String,Object> value=new LinkedHashMap<>(authoritativeContext);value.put("policyVersion",POLICY_VERSION);value.put("sourceOperationalWeeks",weeks);value.put("sourcePayrollPeriodIds",periods.stream().map(p->p.getId().toString()).toList());value.put("sourceTripIds",trips.stream().map(t->t.getId().toString()).toList());value.put("dailyWorkBreakdown",calculation.dailyWorkBreakdown());value.put("sourceIncidentIds",incidentPlan.items().stream().map(i->i.incident().getId().toString()).toList());value.put("sourceIncidentDeductionIds",incidentPlan.items().stream().map(i->i.deductionId().toString()).toList());value.put("incidentDeductionsIncluded",!incidentPlan.items().isEmpty());value.put("emergencyReserveIncluded",reserve!=null);if(reserve!=null){value.put("reserveInterestAmount",reserve.interestAmount());value.put("reserveContributionAmount",reserve.contributionAmount());value.put("balanceCreditAmount",reserve.balanceCreditAmount());value.put("reserveBalanceBefore",reserve.reserveBalanceBefore());value.put("reserveBalanceAfter",reserve.reserveBalanceAfter());}return value;}
    private Map<String,Object> snapshot(String json){try{return objectMapper.readValue(json.getBytes(StandardCharsets.UTF_8),SNAPSHOT_TYPE);}catch(JacksonException ex){throw new IllegalStateException("Payroll period snapshot could not be deserialized",ex);}}
    private String json(Map<String,Object> value){try{return objectMapper.writeValueAsString(value);}catch(JacksonException ex){throw new IllegalStateException("Payslip snapshot could not be serialized",ex);}}
    private CareerEntity lockedOwnedCareer(UUID userId,CareerGame game,UUID careerId){return careerRepository.findForUpdateByIdAndUserIdAndGame(careerId,userId,game).orElseThrow(()->new ResourceNotFoundException("CAREER_NOT_FOUND","The requested career does not exist"));}
    private CareerEntity ownedCareer(UUID userId,CareerGame game,UUID careerId){return careerRepository.findByIdAndUserIdAndGame(careerId,userId,game).orElseThrow(()->new ResourceNotFoundException("CAREER_NOT_FOUND","The requested career does not exist"));}
    private String string(Map<String,Object> map,String key){Object value=map.get(key);return value==null?"":String.valueOf(value);}private int integer(Map<String,Object> map,String key,int fallback){Object value=map.get(key);return value==null||String.valueOf(value).isBlank()?fallback:Integer.parseInt(String.valueOf(value));}private BigDecimal decimal(Map<String,Object> map,String key,BigDecimal fallback){Object value=map.get(key);return value==null||String.valueOf(value).isBlank()?fallback:new BigDecimal(String.valueOf(value));}private BigDecimal decimalNullable(Map<String,Object> map,String key){Object value=map.get(key);return value==null||String.valueOf(value).isBlank()?null:new BigDecimal(String.valueOf(value));}
    private BigDecimal zero(){return BigDecimal.ZERO.setScale(2);}private ApiProblemException badRequest(String code,String title,String detail){return new ApiProblemException(HttpStatus.BAD_REQUEST,code,title,detail);}private ApiProblemException conflict(String code,String title,String detail){return new ApiProblemException(HttpStatus.CONFLICT,code,title,detail);}
    private record PlannedIncidentDeduction(UUID deductionId,IncidentEntity incident,BigDecimal amount){}private record IncidentDeductionPlan(List<PlannedIncidentDeduction> items,BigDecimal total){}
}
