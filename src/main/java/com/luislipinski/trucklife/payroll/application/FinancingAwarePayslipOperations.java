package com.luislipinski.trucklife.payroll.application;

import com.luislipinski.trucklife.career.domain.CareerGame;
import com.luislipinski.trucklife.career.persistence.CareerEntity;
import com.luislipinski.trucklife.career.persistence.CareerRepository;
import com.luislipinski.trucklife.finance.application.FinancingPayrollOperations;
import com.luislipinski.trucklife.shared.error.ResourceNotFoundException;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Primary
public class FinancingAwarePayslipOperations implements PayslipOperations {
    private final PayslipService delegate;
    private final CareerRepository careerRepository;
    private final FinancingPayrollOperations financingPayrollOperations;

    public FinancingAwarePayslipOperations(PayslipService delegate,CareerRepository careerRepository,FinancingPayrollOperations financingPayrollOperations){
        this.delegate=delegate;this.careerRepository=careerRepository;this.financingPayrollOperations=financingPayrollOperations;
    }

    @Override
    @Transactional
    public Result generate(UUID userId,CareerGame game,UUID careerId,Integer expectedOperationalWeek,Integer expectedPayrollMonth){
        Result result=delegate.generate(userId,game,careerId,expectedOperationalWeek,expectedPayrollMonth);
        CareerEntity career=careerRepository.findForUpdateByIdAndUserIdAndGame(careerId,userId,game)
                .orElseThrow(()->new ResourceNotFoundException("CAREER_NOT_FOUND","The requested career does not exist"));
        financingPayrollOperations.processDuePayments(career,result.payslip().getEndOperationalWeek(),game==CareerGame.ETS2?result.payslip().getPayrollMonth():null,result.payslip().getGeneratedAt());
        careerRepository.flush();
        return result;
    }

    @Override
    @Transactional(readOnly=true)
    public List<Result> list(UUID userId,CareerGame game,UUID careerId){return delegate.list(userId,game,careerId);}

    @Override
    @Transactional(readOnly=true)
    public Result get(UUID userId,CareerGame game,UUID careerId,UUID payslipId){return delegate.get(userId,game,careerId,payslipId);}
}
