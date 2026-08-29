package com.luislipinski.trucklife.finance.application;

import com.luislipinski.trucklife.career.domain.CareerGame;
import com.luislipinski.trucklife.career.persistence.CareerEntity;
import com.luislipinski.trucklife.career.persistence.CareerRepository;
import com.luislipinski.trucklife.finance.domain.FinancialContractEventType;
import com.luislipinski.trucklife.finance.domain.FinancialContractStatus;
import com.luislipinski.trucklife.finance.domain.FinancialInstallmentStatus;
import com.luislipinski.trucklife.finance.domain.FinancialPaymentFrequency;
import com.luislipinski.trucklife.finance.domain.FinancialPaymentType;
import com.luislipinski.trucklife.finance.domain.FinancialProductType;
import com.luislipinski.trucklife.finance.persistence.FinancialContractEntity;
import com.luislipinski.trucklife.finance.persistence.FinancialContractEventEntity;
import com.luislipinski.trucklife.finance.persistence.FinancialContractEventRepository;
import com.luislipinski.trucklife.finance.persistence.FinancialContractRepository;
import com.luislipinski.trucklife.finance.persistence.FinancialInstallmentEntity;
import com.luislipinski.trucklife.finance.persistence.FinancialInstallmentRepository;
import com.luislipinski.trucklife.finance.persistence.FinancialPaymentEntity;
import com.luislipinski.trucklife.finance.persistence.FinancialPaymentRepository;
import com.luislipinski.trucklife.ledger.application.LedgerEntryDraft;
import com.luislipinski.trucklife.ledger.application.LedgerWriter;
import com.luislipinski.trucklife.ledger.domain.LedgerEntryType;
import com.luislipinski.trucklife.ledger.domain.LedgerSourceType;
import com.luislipinski.trucklife.shared.error.ApiProblemException;
import com.luislipinski.trucklife.shared.error.ResourceNotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
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
public class FinancingService implements FinancingOperations,FinancingPayrollOperations {
    private final CareerRepository careerRepository;private final FinancialContractRepository contractRepository;
    private final FinancialInstallmentRepository installmentRepository;private final FinancialPaymentRepository paymentRepository;
    private final FinancialContractEventRepository eventRepository;private final FinancingPolicyCatalog policyCatalog;
    private final LedgerWriter ledgerWriter;private final ObjectMapper objectMapper;private final Clock clock;

    public FinancingService(CareerRepository careerRepository,FinancialContractRepository contractRepository,FinancialInstallmentRepository installmentRepository,
            FinancialPaymentRepository paymentRepository,FinancialContractEventRepository eventRepository,FinancingPolicyCatalog policyCatalog,
            LedgerWriter ledgerWriter,ObjectMapper objectMapper,Clock clock){this.careerRepository=careerRepository;this.contractRepository=contractRepository;
        this.installmentRepository=installmentRepository;this.paymentRepository=paymentRepository;this.eventRepository=eventRepository;
        this.policyCatalog=policyCatalog;this.ledgerWriter=ledgerWriter;this.objectMapper=objectMapper;this.clock=clock;}

    @Override @Transactional(readOnly=true)
    public List<Offer> offers(UUID userId,CareerGame game,UUID careerId,FinancialProductType productType,BigDecimal requestedAmount){CareerEntity career=ownedCareer(userId,game,careerId);try{return policyCatalog.offers(career,productType,requestedAmount);}catch(IllegalArgumentException ex){throw policyUnavailable(ex);}}

    @Override @Transactional
    public ContractDetails create(UUID userId,CareerGame game,UUID careerId,CreateContractCommand command){CareerEntity career=lockedCareer(userId,game,careerId);
        FinancialContractEntity existing=contractRepository.findByOriginationOperationId(command.operationId()).orElse(null);if(existing!=null){if(!existing.getCareerId().equals(careerId))throw conflict("FINANCING_OPERATION_ID_CONFLICT","Operation identifier conflict","The supplied operationId already belongs to another career");return details(existing);}
        validateContext(career,command.expectedOperationalWeek(),command.expectedPayrollMonth());BigDecimal before=validateExpectedBalance(career,command.expectedBalance());Offer offer;try{offer=policyCatalog.offer(career,command.productType(),command.requestedAmount(),command.termPeriods());}catch(IllegalArgumentException ex){throw policyUnavailable(ex);}
        ledgerWriter.ensureOpeningBalance(careerId,before,career.getDisplayCurrency(),game==CareerGame.ETS2?1:null,career.getCreatedAt());
        if(offer.productType()==FinancialProductType.VEHICLE_FINANCING&&before.compareTo(offer.downPayment())<0)throw conflict("FINANCING_DOWN_PAYMENT_INSUFFICIENT","Insufficient balance for down payment","The current career balance cannot cover the server-calculated down payment");
        Instant now=clock.instant();UUID contractId=UUID.randomUUID();FinancialContractEntity contract=new FinancialContractEntity(contractId,careerId,command.operationId(),offer.productType(),
                FinancialContractStatus.ACTIVE,offer.policyVersion(),offer.policySource(),offer.policyReferenceAsOf(),offer.rateBasis(),offer.jurisdictionCountryCode(),offer.jurisdictionStateCode(),offer.jurisdictionCity(),
                offer.displayCurrency(),offer.requestedAmount(),offer.principal(),offer.downPayment(),offer.annualInterestRate(),offer.amortizationMethod(),offer.paymentFrequency(),offer.termPeriods(),1,offer.expectedTotalCost(),offer.principal(),
                policyCatalog.prepaymentFeeRate(),policyCatalog.lateFeeRate(),policyCatalog.maxMissedInstallments(),career.getCurrentOperationalWeek(),game==CareerGame.ETS2?career.getCurrentPayrollMonth():null,json(policySnapshot(offer)),now,now);
        contractRepository.saveAndFlush(contract);installmentRepository.saveAllAndFlush(initialSchedule(contract,now));event(contract,FinancialContractEventType.ORIGINATED,career.getCurrentOperationalWeek(),game==CareerGame.ETS2?career.getCurrentPayrollMonth():null,Map.of("operationId",command.operationId().toString()),now);
        if(offer.productType()==FinancialProductType.PERSONAL_LOAN){career.creditBalance(offer.principal(),now);recordOriginationLedger(career,contract,before,before.add(offer.principal()),offer.principal(),LedgerEntryType.LOAN_DISBURSEMENT,"Crédito de empréstimo pessoal",now);}else{
            career.debitBalance(offer.downPayment(),now);recordOriginationLedger(career,contract,before,before.subtract(offer.downPayment()),offer.downPayment().negate(),LedgerEntryType.FINANCING_DOWN_PAYMENT,"Entrada de financiamento de veículo",now);}
        careerRepository.flush();return details(contract);
    }

    @Override @Transactional(readOnly=true)
    public List<ContractDetails> list(UUID userId,CareerGame game,UUID careerId){CareerEntity career=ownedCareer(userId,game,careerId);return contractRepository.findAllByCareerIdOrderByCreatedAtDescIdDesc(career.getId()).stream().map(this::details).toList();}
    @Override @Transactional(readOnly=true)
    public ContractDetails get(UUID userId,CareerGame game,UUID careerId,UUID contractId){CareerEntity career=ownedCareer(userId,game,careerId);return details(contractRepository.findByIdAndCareerId(contractId,career.getId()).orElseThrow(()->new ResourceNotFoundException("FINANCING_CONTRACT_NOT_FOUND","The requested financing contract does not exist")));}

    @Override @Transactional
    public ContractDetails pay(UUID userId,CareerGame game,UUID careerId,UUID contractId,PaymentCommand command){CareerEntity career=lockedCareer(userId,game,careerId);
        FinancialPaymentEntity existing=paymentRepository.findByOperationId(command.operationId()).orElse(null);if(existing!=null){FinancialContractEntity existingContract=contractRepository.findById(existing.getContractId()).orElseThrow();if(!existingContract.getCareerId().equals(careerId)||!existingContract.getId().equals(contractId))throw conflict("FINANCING_OPERATION_ID_CONFLICT","Operation identifier conflict","The supplied operationId already belongs to another financing payment");return details(existingContract);}
        FinancialContractEntity contract=contractRepository.findByIdAndCareerId(contractId,careerId).orElseThrow(()->new ResourceNotFoundException("FINANCING_CONTRACT_NOT_FOUND","The requested financing contract does not exist"));
        if(command.paymentType()==FinancialPaymentType.AUTO)throw badRequest("FINANCING_AUTO_PAYMENT_INTERNAL","Automatic payment is server-managed","AUTO payments can only be created by the operational close");
        if(contract.getStatus()==FinancialContractStatus.PAID_OFF)throw conflict("FINANCING_ALREADY_PAID_OFF","Contract already paid off","The financing contract has no outstanding debt");
        validateContext(career,command.expectedOperationalWeek(),command.expectedPayrollMonth());validateExpectedBalance(career,command.expectedBalance());
        ledgerWriter.ensureOpeningBalance(careerId,career.getBalance(),career.getDisplayCurrency(),game==CareerGame.ETS2?1:null,career.getCreatedAt());Instant now=clock.instant();
        executePayment(career,contract,command.paymentType(),command.operationId(),command.amount(),career.getCurrentOperationalWeek(),game==CareerGame.ETS2?career.getCurrentPayrollMonth():null,now,false);careerRepository.flush();return details(contract);
    }

    @Override @Transactional
    public void processDuePayments(CareerEntity career,int operationalWeek,Integer payrollMonth,Instant now){List<FinancialContractEntity> contracts=contractRepository.findAllByCareerIdAndStatusInOrderByCreatedAtAscIdAsc(career.getId(),List.of(FinancialContractStatus.ACTIVE,FinancialContractStatus.DELINQUENT));
        for(FinancialContractEntity contract:contracts){List<FinancialInstallmentEntity> due=dueInstallments(contract,operationalWeek,payrollMonth,true);if(due.isEmpty())continue;
            UUID operationId=UUID.nameUUIDFromBytes((contract.getId()+"|AUTO|"+operationalWeek+"|"+(payrollMonth==null?"-":payrollMonth)).getBytes(StandardCharsets.UTF_8));
            if(paymentRepository.findByOperationId(operationId).isEmpty()){BigDecimal available=career.getBalance().max(BigDecimal.ZERO).setScale(2,RoundingMode.UNNECESSARY);BigDecimal dueTotal=due.stream().map(FinancialInstallmentEntity::outstanding).reduce(zero(),BigDecimal::add);BigDecimal amount=available.min(dueTotal);
                if(amount.signum()>0)executePayment(career,contract,FinancialPaymentType.AUTO,operationId,amount,operationalWeek,payrollMonth,now,true);else refreshStatus(contract,operationalWeek,payrollMonth,now,true);}
            else refreshStatus(contract,operationalWeek,payrollMonth,now,true);
        }
    }

    private PaymentTotals executePayment(CareerEntity career,FinancialContractEntity contract,FinancialPaymentType type,UUID operationId,BigDecimal requestedAmount,int operationalWeek,Integer payrollMonth,Instant now,boolean includeCurrentAsOverdue){
        BigDecimal before=career.getBalance().setScale(2,RoundingMode.UNNECESSARY);List<FinancialInstallmentEntity> due=dueInstallments(contract,operationalWeek,payrollMonth,true);BigDecimal amount;PaymentTotals totals;
        if(type==FinancialPaymentType.REGULAR||type==FinancialPaymentType.AUTO){amount=positiveMoney(requestedAmount,"Payment amount");BigDecimal dueTotal=due.stream().map(FinancialInstallmentEntity::outstanding).reduce(zero(),BigDecimal::add);if(dueTotal.signum()==0)throw conflict("FINANCING_NOTHING_DUE","No installment is due","There is no due installment in the current operational period");if(amount.compareTo(dueTotal)>0)throw badRequest("FINANCING_PAYMENT_EXCEEDS_DUE","Payment exceeds due amount","Use an extra principal payment for amounts above the currently due installments");ensureBalance(before,amount);totals=allocate(due,amount,now);contract.applyPrincipalPayment(totals.principal(),now);
        }else if(type==FinancialPaymentType.EXTRA_PRINCIPAL){amount=positiveMoney(requestedAmount,"Extra principal amount");BigDecimal dueTotal=due.stream().map(FinancialInstallmentEntity::outstanding).reduce(zero(),BigDecimal::add);if(dueTotal.signum()>0)throw conflict("FINANCING_DUE_INSTALLMENT_PENDING","Due installment pending","Settle currently due installments before making an extra principal payment");if(amount.compareTo(contract.getRemainingPrincipal())>0)throw badRequest("FINANCING_EXTRA_PRINCIPAL_TOO_HIGH","Extra principal exceeds debt","The extra principal amount cannot exceed remaining principal");ensureBalance(before,amount);contract.applyPrincipalPayment(amount,now);totals=new PaymentTotals(amount,zero(),zero());if(contract.getRemainingPrincipal().signum()>0)rescheduleFuture(contract,operationalWeek,payrollMonth,now);else supersedeOpenSchedule(contract,now);
        }else if(type==FinancialPaymentType.PAYOFF){BigDecimal dueInterest=due.stream().map(FinancialInstallmentEntity::outstandingInterest).reduce(zero(),BigDecimal::add);BigDecimal dueFee=due.stream().map(FinancialInstallmentEntity::outstandingFee).reduce(zero(),BigDecimal::add);BigDecimal initialPrincipal=contract.getRemainingPrincipal();amount=initialPrincipal.add(dueInterest).add(dueFee).setScale(2,RoundingMode.UNNECESSARY);if(amount.signum()<=0)throw conflict("FINANCING_ALREADY_PAID_OFF","Contract already paid off","The financing contract has no outstanding debt");ensureBalance(before,amount);PaymentTotals dueTotals=allocate(due,due.stream().map(FinancialInstallmentEntity::outstanding).reduce(zero(),BigDecimal::add),now);if(dueTotals.principal().signum()>0)contract.applyPrincipalPayment(dueTotals.principal(),now);BigDecimal futurePrincipal=contract.getRemainingPrincipal();if(futurePrincipal.signum()>0)contract.applyPrincipalPayment(futurePrincipal,now);totals=new PaymentTotals(initialPrincipal,dueTotals.interest(),dueTotals.fee());supersedeOpenSchedule(contract,now);
        }else throw new IllegalArgumentException("Unsupported payment type");
        career.debitBalance(amount,now);BigDecimal after=before.subtract(amount).setScale(2,RoundingMode.UNNECESSARY);FinancialPaymentEntity payment=new FinancialPaymentEntity(UUID.randomUUID(),contract.getId(),operationId,type,amount,totals.principal(),totals.interest(),totals.fee(),before,after,operationalWeek,payrollMonth,career.getDisplayCurrency(),json(Map.of("contractId",contract.getId().toString(),"paymentType",type.name())),now);paymentRepository.saveAndFlush(payment);installmentRepository.flush();contractRepository.flush();
        ledgerWriter.record(new LedgerEntryDraft(career.getId(),LedgerEntryType.DEBT_PAYMENT,LedgerSourceType.FINANCIAL_PAYMENT,payment.getId(),40,operationalWeek,payrollMonth,amount.negate(),amount.negate(),zero(),before,after,null,null,career.getDisplayCurrency(),"Pagamento de dívida — "+type.name(),Map.of("contractId",contract.getId().toString(),"principal",totals.principal(),"interest",totals.interest(),"fees",totals.fee()),now));
        refreshStatus(contract,operationalWeek,payrollMonth,now,includeCurrentAsOverdue);return totals;
    }

    private List<FinancialInstallmentEntity> initialSchedule(FinancialContractEntity contract,Instant now){FinancingPolicyCatalog.Plan plan=policyCatalog.plan(contract.getPrincipal(),contract.getAnnualInterestRate(),contract.getPaymentFrequency(),contract.getTermPeriods());List<FinancialInstallmentEntity> result=new ArrayList<>();for(int i=0;i<plan.periods().size();i++){FinancingPolicyCatalog.PeriodAmount amount=plan.periods().get(i);int number=i+1;Integer dueWeek=null,dueMonth=null;if(contract.getPaymentFrequency()==FinancialPaymentFrequency.MONTHLY)dueMonth=contract.getOriginatedPayrollMonth()+number;else dueWeek=contract.getOriginatedOperationalWeek()+number*(contract.getPaymentFrequency()==FinancialPaymentFrequency.WEEKLY?1:2);result.add(new FinancialInstallmentEntity(UUID.randomUUID(),contract.getId(),1,number,dueWeek,dueMonth,amount.total(),amount.principal(),amount.interest(),zero(),now));}return result;}

    private void rescheduleFuture(FinancialContractEntity contract,int operationalWeek,Integer payrollMonth,Instant now){List<FinancialInstallmentEntity> current=installmentRepository.findAllByContractIdAndScheduleVersionOrderByInstallmentNumberAsc(contract.getId(),contract.getCurrentScheduleVersion());List<FinancialInstallmentEntity> future=current.stream().filter(FinancialInstallmentEntity::isOpen).filter(i->!isDue(i,operationalWeek,payrollMonth,true)).sorted(Comparator.comparingInt(FinancialInstallmentEntity::getInstallmentNumber)).toList();if(future.isEmpty())return;future.forEach(i->i.supersede(now));installmentRepository.saveAllAndFlush(future);int version=contract.advanceScheduleVersion(now);FinancingPolicyCatalog.Plan plan=policyCatalog.plan(contract.getRemainingPrincipal(),contract.getAnnualInterestRate(),contract.getPaymentFrequency(),future.size());List<FinancialInstallmentEntity> replacements=new ArrayList<>();for(int i=0;i<future.size();i++){FinancialInstallmentEntity old=future.get(i);FinancingPolicyCatalog.PeriodAmount value=plan.periods().get(i);replacements.add(new FinancialInstallmentEntity(UUID.randomUUID(),contract.getId(),version,i+1,old.getDueOperationalWeek(),old.getDuePayrollMonth(),value.total(),value.principal(),value.interest(),zero(),now));}installmentRepository.saveAllAndFlush(replacements);event(contract,FinancialContractEventType.RESCHEDULED,operationalWeek,payrollMonth,Map.of("scheduleVersion",version,"remainingPrincipal",contract.getRemainingPrincipal()),now);}
    private void supersedeOpenSchedule(FinancialContractEntity contract,Instant now){List<FinancialInstallmentEntity> open=installmentRepository.findAllByContractIdAndScheduleVersionOrderByInstallmentNumberAsc(contract.getId(),contract.getCurrentScheduleVersion()).stream().filter(FinancialInstallmentEntity::isOpen).toList();open.forEach(i->i.supersede(now));if(!open.isEmpty())installmentRepository.saveAllAndFlush(open);}

    private PaymentTotals allocate(List<FinancialInstallmentEntity> installments,BigDecimal requested,Instant now){BigDecimal remaining=requested.setScale(2,RoundingMode.UNNECESSARY),principal=zero(),interest=zero(),fee=zero();for(FinancialInstallmentEntity installment:installments){if(remaining.signum()==0)break;BigDecimal applied=remaining.min(installment.outstanding());if(applied.signum()==0)continue;FinancialInstallmentEntity.Allocation allocation=installment.applyPayment(applied,now);principal=principal.add(allocation.principal());interest=interest.add(allocation.interest());fee=fee.add(allocation.fee());remaining=remaining.subtract(applied);}if(remaining.signum()!=0)throw new IllegalStateException("Payment allocation did not reconcile");return new PaymentTotals(principal,interest,fee);}
    private List<FinancialInstallmentEntity> dueInstallments(FinancialContractEntity contract,int operationalWeek,Integer payrollMonth,boolean includeCurrent){return installmentRepository.findAllByContractIdAndScheduleVersionOrderByInstallmentNumberAsc(contract.getId(),contract.getCurrentScheduleVersion()).stream().filter(FinancialInstallmentEntity::isOpen).filter(i->isDue(i,operationalWeek,payrollMonth,includeCurrent)).sorted(Comparator.comparingInt(FinancialInstallmentEntity::getInstallmentNumber)).toList();}
    private boolean isDue(FinancialInstallmentEntity installment,int operationalWeek,Integer payrollMonth,boolean includeCurrent){if(installment.getDueOperationalWeek()!=null)return includeCurrent?installment.getDueOperationalWeek()<=operationalWeek:installment.getDueOperationalWeek()<operationalWeek;if(payrollMonth==null)return false;return includeCurrent?installment.getDuePayrollMonth()<=payrollMonth:installment.getDuePayrollMonth()<payrollMonth;}

    private void refreshStatus(FinancialContractEntity contract,int operationalWeek,Integer payrollMonth,Instant now,boolean includeCurrentAsOverdue){FinancialContractStatus before=contract.getStatus();if(contract.getRemainingPrincipal().signum()==0){contract.markPaidOff(now);if(before!=FinancialContractStatus.PAID_OFF)event(contract,FinancialContractEventType.PAID_OFF,operationalWeek,payrollMonth,Map.of(),now);contractRepository.flush();return;}
        List<FinancialInstallmentEntity> active=installmentRepository.findAllByContractIdAndScheduleVersionOrderByInstallmentNumberAsc(contract.getId(),contract.getCurrentScheduleVersion());for(FinancialInstallmentEntity installment:active)if(installment.isOpen()&&isDue(installment,operationalWeek,payrollMonth,includeCurrentAsOverdue))installment.markOverdue(now);installmentRepository.saveAllAndFlush(active);
        long overdue=active.stream().filter(i->i.getStatus()==FinancialInstallmentStatus.OVERDUE).count();if(before==FinancialContractStatus.DEFAULTED)return;if(overdue>=contract.getMaxMissedInstallments())contract.markDefaulted(now);else if(overdue>0)contract.markDelinquent(now);else contract.markActive(now);FinancialContractStatus after=contract.getStatus();if(after!=before){if(after==FinancialContractStatus.DEFAULTED)event(contract,FinancialContractEventType.DEFAULTED,operationalWeek,payrollMonth,Map.of("overdueInstallments",overdue),now);else if(after==FinancialContractStatus.DELINQUENT)event(contract,FinancialContractEventType.DELINQUENT,operationalWeek,payrollMonth,Map.of("overdueInstallments",overdue),now);}contractRepository.flush();}

    private void recordOriginationLedger(CareerEntity career,FinancialContractEntity contract,BigDecimal before,BigDecimal after,BigDecimal delta,LedgerEntryType type,String description,Instant now){ledgerWriter.record(new LedgerEntryDraft(career.getId(),type,LedgerSourceType.FINANCIAL_CONTRACT,contract.getId(),15,career.getCurrentOperationalWeek(),career.getGame()==CareerGame.ETS2?career.getCurrentPayrollMonth():null,delta,delta,zero(),before,after,null,null,career.getDisplayCurrency(),description,Map.of("contractId",contract.getId().toString(),"productType",contract.getProductType().name(),"policyVersion",contract.getPolicyVersion()),now));}
    private void event(FinancialContractEntity contract,FinancialContractEventType type,int week,Integer month,Map<String,Object> metadata,Instant now){eventRepository.saveAndFlush(new FinancialContractEventEntity(UUID.randomUUID(),contract.getId(),type,week,month,json(metadata),now));}
    private ContractDetails details(FinancialContractEntity contract){return new ContractDetails(contract,installmentRepository.findAllByContractIdOrderByScheduleVersionAscInstallmentNumberAsc(contract.getId()),paymentRepository.findAllByContractIdOrderByRecordedAtAscIdAsc(contract.getId()),eventRepository.findAllByContractIdOrderByRecordedAtAscIdAsc(contract.getId()));}
    private Map<String,Object> policySnapshot(Offer offer){Map<String,Object> value=new LinkedHashMap<>();value.put("policyVersion",offer.policyVersion());value.put("policySource",offer.policySource());value.put("policyReferenceAsOf",offer.policyReferenceAsOf().toString());value.put("rateBasis",offer.rateBasis());value.put("countryCode",offer.jurisdictionCountryCode());value.put("stateCode",offer.jurisdictionStateCode());value.put("city",offer.jurisdictionCity());value.put("displayCurrency",offer.displayCurrency());value.put("annualInterestRate",offer.annualInterestRate());value.put("amortizationMethod",offer.amortizationMethod().name());value.put("paymentFrequency",offer.paymentFrequency().name());value.put("termPeriods",offer.termPeriods());value.put("downPayment",offer.downPayment());value.put("prepaymentFeeRate",policyCatalog.prepaymentFeeRate());value.put("lateFeeRate",policyCatalog.lateFeeRate());return value;}
    private CareerEntity ownedCareer(UUID userId,CareerGame game,UUID careerId){return careerRepository.findByIdAndUserIdAndGame(careerId,userId,game).orElseThrow(()->new ResourceNotFoundException("CAREER_NOT_FOUND","The requested career does not exist"));}
    private CareerEntity lockedCareer(UUID userId,CareerGame game,UUID careerId){return careerRepository.findForUpdateByIdAndUserIdAndGame(careerId,userId,game).orElseThrow(()->new ResourceNotFoundException("CAREER_NOT_FOUND","The requested career does not exist"));}
    private void validateContext(CareerEntity career,Integer expectedWeek,Integer expectedMonth){if(expectedWeek==null||expectedWeek!=career.getCurrentOperationalWeek())throw conflict("FINANCING_WEEK_CONFLICT","Operational week changed","The requested operational week is no longer current");if(career.getGame()==CareerGame.ETS2&&(expectedMonth==null||!expectedMonth.equals(career.getCurrentPayrollMonth())))throw conflict("FINANCING_MONTH_CONFLICT","Payroll month changed","The requested payroll month is no longer current");}
    private BigDecimal validateExpectedBalance(CareerEntity career,BigDecimal expected){BigDecimal value=money(expected,"Expected balance"),current=career.getBalance().setScale(2,RoundingMode.UNNECESSARY);if(current.compareTo(value)!=0)throw conflict("FINANCING_BALANCE_CONFLICT","Career balance changed","The expected balance no longer matches the current career balance");return current;}
    private void ensureBalance(BigDecimal balance,BigDecimal amount){if(balance.compareTo(amount)<0)throw conflict("FINANCING_PAYMENT_INSUFFICIENT","Insufficient balance","The current career balance cannot cover this financing payment");}
    private BigDecimal positiveMoney(BigDecimal value,String label){BigDecimal result=money(value,label);if(result.signum()<=0)throw badRequest("FINANCING_PAYMENT_AMOUNT_INVALID","Payment amount invalid",label+" must be greater than zero");return result;}
    private BigDecimal money(BigDecimal value,String label){if(value==null)throw badRequest("FINANCING_AMOUNT_REQUIRED","Amount required",label+" is required");try{return value.setScale(2,RoundingMode.UNNECESSARY);}catch(ArithmeticException ex){throw badRequest("FINANCING_AMOUNT_INVALID","Amount invalid",label+" supports at most two decimal places");}}
    private String json(Map<String,Object> value){try{return objectMapper.writeValueAsString(value);}catch(JacksonException ex){throw new IllegalStateException("Financing snapshot could not be serialized",ex);}}
    private BigDecimal zero(){return BigDecimal.ZERO.setScale(2);}private ApiProblemException badRequest(String code,String title,String detail){return new ApiProblemException(HttpStatus.BAD_REQUEST,code,title,detail);}private ApiProblemException conflict(String code,String title,String detail){return new ApiProblemException(HttpStatus.CONFLICT,code,title,detail);}private ApiProblemException policyUnavailable(IllegalArgumentException ex){return new ApiProblemException(HttpStatus.CONFLICT,"FINANCING_POLICY_UNAVAILABLE","Financing policy unavailable",ex.getMessage());}
    private record PaymentTotals(BigDecimal principal,BigDecimal interest,BigDecimal fee){}
}
