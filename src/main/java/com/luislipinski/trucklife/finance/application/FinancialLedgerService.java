package com.luislipinski.trucklife.finance.application;

import com.luislipinski.trucklife.career.domain.CareerGame;
import com.luislipinski.trucklife.career.persistence.CareerEntity;
import com.luislipinski.trucklife.career.persistence.CareerRepository;
import com.luislipinski.trucklife.ledger.application.LedgerEntryDraft;
import com.luislipinski.trucklife.ledger.application.LedgerWriter;
import com.luislipinski.trucklife.ledger.domain.LedgerEntryType;
import com.luislipinski.trucklife.ledger.domain.LedgerSourceType;
import com.luislipinski.trucklife.ledger.persistence.LedgerEntryEntity;
import com.luislipinski.trucklife.ledger.persistence.LedgerEntryRepository;
import com.luislipinski.trucklife.shared.error.ApiProblemException;
import com.luislipinski.trucklife.shared.error.ResourceNotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FinancialLedgerService implements FinancialLedgerOperations {
    private final CareerRepository careerRepository; private final LedgerEntryRepository ledgerRepository;
    private final LedgerWriter ledgerWriter; private final Clock clock;
    public FinancialLedgerService(CareerRepository careerRepository,LedgerEntryRepository ledgerRepository,LedgerWriter ledgerWriter,Clock clock){this.careerRepository=careerRepository;this.ledgerRepository=ledgerRepository;this.ledgerWriter=ledgerWriter;this.clock=clock;}

    @Override @Transactional
    public List<LedgerEntryEntity> list(UUID userId,CareerGame game,UUID careerId,int limit){
        CareerEntity career=lockedCareer(userId,game,careerId);ensureOpening(career);
        return ledgerRepository.findAllByCareerIdOrderByRecordedAtDescEntryOrderDescIdDesc(careerId,PageRequest.of(0,Math.min(Math.max(limit,1),500)));
    }

    @Override @Transactional
    public LedgerEntryEntity adjustBalance(UUID userId,CareerGame game,UUID careerId,UUID operationId,Integer expectedWeek,Integer expectedMonth,BigDecimal expectedBalance,BigDecimal newBalance,String note){
        CareerEntity career=lockedCareer(userId,game,careerId);ensureOpening(career);
        LedgerEntryEntity existing=ledgerRepository.findBySourceTypeAndSourceIdAndEntryType(LedgerSourceType.BALANCE_ADJUSTMENT,operationId,LedgerEntryType.BALANCE_ADJUSTMENT).orElse(null);
        if(existing!=null){if(!existing.getCareerId().equals(careerId))throw conflict("FINANCE_OPERATION_ID_CONFLICT","Operation identifier conflict","The supplied operationId already belongs to another career");return existing;}
        validateContext(career,expectedWeek,expectedMonth);
        BigDecimal expected=money(expectedBalance),next=money(newBalance),before=career.getBalance().setScale(2,RoundingMode.UNNECESSARY);
        if(before.compareTo(expected)!=0)throw conflict("LEDGER_BALANCE_CONFLICT","Career balance changed","The expected balance no longer matches the current career balance");
        BigDecimal delta=next.subtract(before).setScale(2,RoundingMode.UNNECESSARY);
        if(delta.signum()==0)throw new ApiProblemException(HttpStatus.BAD_REQUEST,"LEDGER_ADJUSTMENT_NO_CHANGE","Balance adjustment has no change","newBalance must differ from the current balance");
        Instant now=clock.instant(); if(delta.signum()>0)career.creditBalance(delta,now);else career.debitBalance(delta.abs(),now); careerRepository.flush();
        String normalized=note==null||note.isBlank()?null:note.strip();
        return ledgerWriter.record(new LedgerEntryDraft(careerId,LedgerEntryType.BALANCE_ADJUSTMENT,LedgerSourceType.BALANCE_ADJUSTMENT,operationId,10,
                career.getCurrentOperationalWeek(),career.getGame()==CareerGame.ETS2?career.getCurrentPayrollMonth():null,delta,delta,zero(),before,next,null,null,
                career.getDisplayCurrency(),normalized==null?"Ajuste manual de saldo":"Ajuste manual de saldo — "+normalized,
                Map.of("expectedBalance",expected,"newBalance",next),now));
    }

    private void ensureOpening(CareerEntity career){ledgerWriter.ensureOpeningBalance(career.getId(),career.getBalance(),career.getDisplayCurrency(),career.getGame()==CareerGame.ETS2?1:null,career.getCreatedAt());}
    private void validateContext(CareerEntity career,Integer expectedWeek,Integer expectedMonth){if(expectedWeek==null||expectedWeek!=career.getCurrentOperationalWeek())throw conflict("FINANCE_WEEK_CONFLICT","Operational week changed","The requested operational week is no longer current");if(career.getGame()==CareerGame.ETS2&&(expectedMonth==null||!expectedMonth.equals(career.getCurrentPayrollMonth())))throw conflict("FINANCE_MONTH_CONFLICT","Payroll month changed","The requested payroll month is no longer current");}
    private CareerEntity lockedCareer(UUID userId,CareerGame game,UUID careerId){return careerRepository.findForUpdateByIdAndUserIdAndGame(careerId,userId,game).orElseThrow(()->new ResourceNotFoundException("CAREER_NOT_FOUND","The requested career does not exist"));}
    private BigDecimal money(BigDecimal value){if(value==null)throw new ApiProblemException(HttpStatus.BAD_REQUEST,"LEDGER_BALANCE_REQUIRED","Balance required","expectedBalance and newBalance are required");try{return value.setScale(2,RoundingMode.UNNECESSARY);}catch(ArithmeticException ex){throw new ApiProblemException(HttpStatus.BAD_REQUEST,"LEDGER_BALANCE_INVALID","Balance invalid","Balance values support at most two decimal places");}}
    private BigDecimal zero(){return BigDecimal.ZERO.setScale(2);} private ApiProblemException conflict(String code,String title,String detail){return new ApiProblemException(HttpStatus.CONFLICT,code,title,detail);}
}
