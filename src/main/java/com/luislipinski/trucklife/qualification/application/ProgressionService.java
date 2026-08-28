package com.luislipinski.trucklife.qualification.application;

import com.luislipinski.trucklife.career.domain.CareerGame;
import com.luislipinski.trucklife.career.persistence.CareerEntity;
import com.luislipinski.trucklife.career.persistence.CareerRepository;
import com.luislipinski.trucklife.ledger.application.LedgerEntryDraft;
import com.luislipinski.trucklife.ledger.application.LedgerWriter;
import com.luislipinski.trucklife.ledger.domain.LedgerEntryType;
import com.luislipinski.trucklife.ledger.domain.LedgerSourceType;
import com.luislipinski.trucklife.qualification.persistence.AcademyProgressEntity;
import com.luislipinski.trucklife.qualification.persistence.AcademyProgressRepository;
import com.luislipinski.trucklife.qualification.persistence.QualificationEntity;
import com.luislipinski.trucklife.qualification.persistence.QualificationRepository;
import com.luislipinski.trucklife.shared.error.ApiProblemException;
import com.luislipinski.trucklife.shared.error.ResourceNotFoundException;
import com.luislipinski.trucklife.trip.persistence.TripRepository;
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
public class ProgressionService implements ProgressionOperations {
    private final CareerRepository careerRepository;private final TripRepository tripRepository;private final AcademyProgressRepository academyRepository;
    private final QualificationRepository qualificationRepository;private final LedgerWriter ledgerWriter;private final ObjectMapper objectMapper;private final Clock clock;
    public ProgressionService(CareerRepository careerRepository,TripRepository tripRepository,AcademyProgressRepository academyRepository,
                              QualificationRepository qualificationRepository,LedgerWriter ledgerWriter,ObjectMapper objectMapper,Clock clock){
        this.careerRepository=careerRepository;this.tripRepository=tripRepository;this.academyRepository=academyRepository;
        this.qualificationRepository=qualificationRepository;this.ledgerWriter=ledgerWriter;this.objectMapper=objectMapper;this.clock=clock;
    }

    @Override @Transactional(readOnly=true) public Status get(UUID userId,CareerGame game,UUID careerId){return statusFor(ownedCareer(userId,game,careerId));}

    @Override @Transactional
    public Status promote(UUID userId,CareerGame game,UUID careerId,int expectedOperationalWeek,short expectedCurrentLevel,short targetLevel,boolean academyCompleted){
        CareerEntity career=lockedOwnedCareer(userId,game,careerId);preconditions(career,expectedOperationalWeek,expectedCurrentLevel);
        if(!academyCompleted)throw badRequest("ACADEMY_CONFIRMATION_REQUIRED","Academy confirmation required","The completed Driving Academy module must be confirmed before promotion");
        if(targetLevel!=expectedCurrentLevel+1||targetLevel<2||targetLevel>3)throw conflict("PROMOTION_LEVEL_INVALID","Promotion level invalid","Promotions must advance exactly one level from the current career level");
        QualificationPolicyCatalog.Policy policy=policy(career);QualificationPolicyCatalog.Promotion promotion=policy.promotion(targetLevel);
        if(promotion==null)throw conflict("PROMOTION_POLICY_UNAVAILABLE","Promotion policy unavailable","No promotion policy exists for the requested target level");
        if(academyRepository.existsByCareerIdAndTargetLevel(career.getId(),targetLevel))throw conflict("PROMOTION_ALREADY_COMPLETED","Promotion already completed","The requested Academy promotion has already been completed");
        BigDecimal distance=totalDistance(career.getId());if(distance.compareTo(promotion.requiredDistance())<0)throw conflict("PROMOTION_DISTANCE_REQUIRED","Promotion distance not reached","The career has not reached the required official distance for this promotion");
        BigDecimal fee=displayFee(career,policy.baseCurrency(),promotion.baseFee());requireBalance(career,fee);Instant now=clock.instant();
        Map<String,Object> context=snapshot(career,Map.of("targetLevel",targetLevel,"moduleCode",promotion.moduleCode().name(),"moduleName",promotion.moduleName(),"requiredDistance",promotion.requiredDistance(),"distanceAtCompletion",distance,"baseFeeAmount",promotion.baseFee(),"displayFeeAmount",fee));
        AcademyProgressEntity progress=new AcademyProgressEntity(UUID.randomUUID(),career.getId(),targetLevel,promotion.moduleCode(),promotion.moduleName(),promotion.requiredDistance(),distance,fee,career.getDisplayCurrency(),career.getCurrentOperationalWeek(),QualificationPolicyCatalog.POLICY_VERSION,json(context),now);
        ledgerWriter.ensureOpeningBalance(career.getId(),career.getBalance(),career.getDisplayCurrency(),career.getGame()==CareerGame.ETS2?1:null,career.getCreatedAt());BigDecimal before=career.getBalance().setScale(2,RoundingMode.UNNECESSARY);
        academyRepository.saveAndFlush(progress);career.debitBalance(fee,now);career.promoteTo(targetLevel,now);
        ledgerWriter.record(new LedgerEntryDraft(career.getId(),LedgerEntryType.ACADEMY_FEE,LedgerSourceType.ACADEMY_PROGRESS,progress.getId(),10,career.getCurrentOperationalWeek(),career.getGame()==CareerGame.ETS2?career.getCurrentPayrollMonth():null,fee.negate(),fee.negate(),zero(),before,career.getBalance().setScale(2,RoundingMode.UNNECESSARY),null,null,career.getDisplayCurrency(),"Driving Academy: "+promotion.moduleName(),context,now));
        careerRepository.flush();return statusFor(career);
    }

    @Override @Transactional
    public Status acquireDangerousGoods(UUID userId,CareerGame game,UUID careerId,int expectedOperationalWeek,short expectedCurrentLevel){
        CareerEntity career=lockedOwnedCareer(userId,game,careerId);preconditions(career,expectedOperationalWeek,expectedCurrentLevel);QualificationPolicyCatalog.Policy policy=policy(career);QualificationPolicyCatalog.Dangerous dangerous=policy.dangerous();
        if(career.getCurrentLevel()<dangerous.minimumLevel())throw conflict("QUALIFICATION_LEVEL_REQUIRED","Qualification level required","Dangerous-goods qualification is available only from Level 2");
        if(career.isDangerousGoodsQualified()||qualificationRepository.existsByCareerIdAndType(career.getId(),dangerous.type()))throw conflict("QUALIFICATION_ALREADY_ACQUIRED","Qualification already acquired","The dangerous-goods qualification has already been acquired for this career");
        BigDecimal fee=displayFee(career,policy.baseCurrency(),dangerous.baseFee());requireBalance(career,fee);Instant now=clock.instant();
        Map<String,Object> context=snapshot(career,Map.of("qualificationType",dangerous.type().name(),"qualificationName",dangerous.name(),"minimumLevel",dangerous.minimumLevel(),"baseFeeAmount",dangerous.baseFee(),"displayFeeAmount",fee));
        QualificationEntity qualification=new QualificationEntity(UUID.randomUUID(),career.getId(),dangerous.type(),dangerous.name(),fee,career.getDisplayCurrency(),career.getCurrentOperationalWeek(),QualificationPolicyCatalog.POLICY_VERSION,json(context),now);
        ledgerWriter.ensureOpeningBalance(career.getId(),career.getBalance(),career.getDisplayCurrency(),career.getGame()==CareerGame.ETS2?1:null,career.getCreatedAt());BigDecimal before=career.getBalance().setScale(2,RoundingMode.UNNECESSARY);
        qualificationRepository.saveAndFlush(qualification);career.debitBalance(fee,now);career.qualifyDangerousGoods(now);
        ledgerWriter.record(new LedgerEntryDraft(career.getId(),LedgerEntryType.QUALIFICATION_FEE,LedgerSourceType.QUALIFICATION,qualification.getId(),10,career.getCurrentOperationalWeek(),career.getGame()==CareerGame.ETS2?career.getCurrentPayrollMonth():null,fee.negate(),fee.negate(),zero(),before,career.getBalance().setScale(2,RoundingMode.UNNECESSARY),null,null,career.getDisplayCurrency(),"Qualificação: "+dangerous.name(),context,now));
        careerRepository.flush();return statusFor(career);
    }

    private Status statusFor(CareerEntity career){
        QualificationPolicyCatalog.Policy policy=policy(career);BigDecimal distance=totalDistance(career.getId());
        List<AcademyProgressEntity> academyRows=academyRepository.findAllByCareerIdOrderByTargetLevelAsc(career.getId());List<QualificationEntity> qualificationRows=qualificationRepository.findAllByCareerIdOrderByAcquiredAtAscIdAsc(career.getId());
        List<AcademyCompletion> academy=academyRows.stream().map(row->new AcademyCompletion(row.getId(),row.getTargetLevel(),row.getModuleCode(),row.getModuleName(),row.getRequiredDistance(),row.getDistanceAtCompletion(),row.getFeeAmount(),row.getDisplayCurrency(),row.getOperationalWeek(),row.getPolicyVersion(),row.getCompletedAt())).toList();
        List<QualificationCompletion> qualifications=qualificationRows.stream().map(row->new QualificationCompletion(row.getId(),row.getType(),row.getName(),row.getFeeAmount(),row.getDisplayCurrency(),row.getOperationalWeek(),row.getPolicyVersion(),row.getAcquiredAt())).toList();
        List<PromotionOption> promotions=List.of(option(career,policy,policy.level2(),distance,academyRows),option(career,policy,policy.level3(),distance,academyRows));
        QualificationPolicyCatalog.Dangerous dangerous=policy.dangerous();boolean acquired=career.isDangerousGoodsQualified()||qualificationRows.stream().anyMatch(row->row.getType()==dangerous.type());
        DangerousOption dangerousOption=new DangerousOption(dangerous.type(),dangerous.name(),dangerous.minimumLevel(),displayFee(career,policy.baseCurrency(),dangerous.baseFee()),acquired,!acquired&&career.getCurrentLevel()>=dangerous.minimumLevel());
        return new Status(career.getId(),career.getGame(),career.getCurrentLevel(),career.getBalance(),career.getDisplayCurrency(),distance,acquired,academy,qualifications,promotions,dangerousOption);
    }
    private PromotionOption option(CareerEntity career,QualificationPolicyCatalog.Policy policy,QualificationPolicyCatalog.Promotion promotion,BigDecimal distance,List<AcademyProgressEntity> rows){boolean completed=career.getCurrentLevel()>=promotion.targetLevel()||rows.stream().anyMatch(row->row.getTargetLevel()==promotion.targetLevel());BigDecimal remaining=promotion.requiredDistance().subtract(distance).max(BigDecimal.ZERO).setScale(2,RoundingMode.HALF_UP);boolean ready=!completed&&career.getCurrentLevel()==promotion.targetLevel()-1&&distance.compareTo(promotion.requiredDistance())>=0;return new PromotionOption(promotion.targetLevel(),promotion.moduleCode(),promotion.moduleName(),promotion.requiredDistance(),distance,remaining,displayFee(career,policy.baseCurrency(),promotion.baseFee()),completed,ready);}
    private QualificationPolicyCatalog.Policy policy(CareerEntity career){try{QualificationPolicyCatalog.Policy policy=QualificationPolicyCatalog.resolve(career.getGame(),career.getCountryCode());if(!policy.baseCurrency().equalsIgnoreCase(career.getBaseCurrency()))throw new IllegalArgumentException("Career base currency does not match the qualification jurisdiction policy");return policy;}catch(IllegalArgumentException ex){throw conflict("PROGRESSION_POLICY_UNAVAILABLE","Progression policy unavailable",ex.getMessage());}}
    private void preconditions(CareerEntity career,int expectedWeek,short expectedLevel){if(expectedWeek!=career.getCurrentOperationalWeek())throw conflict("PROGRESSION_WEEK_CONFLICT","Operational week changed","The requested week is no longer the career current operational week");if(expectedLevel!=career.getCurrentLevel())throw conflict("PROGRESSION_LEVEL_CONFLICT","Career level changed","The requested level is no longer the career current level");}
    private BigDecimal totalDistance(UUID careerId){BigDecimal distance=tripRepository.sumOfficialDistanceByCareerId(careerId);return(distance==null?BigDecimal.ZERO:distance).setScale(2,RoundingMode.HALF_UP);}
    private BigDecimal displayFee(CareerEntity career,String expectedBaseCurrency,BigDecimal baseFee){if(!expectedBaseCurrency.equalsIgnoreCase(career.getBaseCurrency()))throw conflict("PROGRESSION_POLICY_UNAVAILABLE","Progression policy unavailable","Career base currency does not match the progression policy");BigDecimal exchangeRate=career.getExchangeRate();if(exchangeRate==null||exchangeRate.signum()<=0)throw conflict("PROGRESSION_POLICY_UNAVAILABLE","Progression policy unavailable","Career exchange rate must be positive");return baseFee.multiply(exchangeRate).setScale(2,RoundingMode.HALF_UP);}
    private void requireBalance(CareerEntity career,BigDecimal fee){if(career.getBalance().compareTo(fee)<0)throw conflict("PROGRESSION_BALANCE_INSUFFICIENT","Insufficient career balance","The career balance is insufficient for the requested training or qualification fee");}
    private Map<String,Object> snapshot(CareerEntity career,Map<String,Object> detail){Map<String,Object> snapshot=new LinkedHashMap<>();snapshot.put("policyVersion",QualificationPolicyCatalog.POLICY_VERSION);snapshot.put("game",career.getGame().name());snapshot.put("stateCode",career.getStateCode());snapshot.put("countryCode",career.getCountryCode());snapshot.put("baseCity",career.getBaseCity());snapshot.put("baseCurrency",career.getBaseCurrency());snapshot.put("displayCurrency",career.getDisplayCurrency());snapshot.put("exchangeRate",career.getExchangeRate());snapshot.put("exchangeRateAsOf",career.getExchangeRateAsOf()==null?null:career.getExchangeRateAsOf().toString());snapshot.put("operationalWeek",career.getCurrentOperationalWeek());snapshot.putAll(detail);return snapshot;}
    private String json(Map<String,Object> value){try{return objectMapper.writeValueAsString(value);}catch(JacksonException ex){throw new IllegalStateException("Progression snapshot could not be serialized",ex);}}
    private BigDecimal zero(){return BigDecimal.ZERO.setScale(2);}
    private CareerEntity lockedOwnedCareer(UUID userId,CareerGame game,UUID careerId){return careerRepository.findForUpdateByIdAndUserIdAndGame(careerId,userId,game).orElseThrow(()->new ResourceNotFoundException("CAREER_NOT_FOUND","The requested career does not exist"));}
    private CareerEntity ownedCareer(UUID userId,CareerGame game,UUID careerId){return careerRepository.findByIdAndUserIdAndGame(careerId,userId,game).orElseThrow(()->new ResourceNotFoundException("CAREER_NOT_FOUND","The requested career does not exist"));}
    private ApiProblemException badRequest(String code,String title,String detail){return new ApiProblemException(HttpStatus.BAD_REQUEST,code,title,detail);}private ApiProblemException conflict(String code,String title,String detail){return new ApiProblemException(HttpStatus.CONFLICT,code,title,detail);}
}
