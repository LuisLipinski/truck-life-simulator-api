package com.luislipinski.trucklife.finance.application;

import com.luislipinski.trucklife.career.domain.CareerGame;
import com.luislipinski.trucklife.career.persistence.CareerEntity;
import com.luislipinski.trucklife.finance.domain.FinancialAmortizationMethod;
import com.luislipinski.trucklife.finance.domain.FinancialPaymentFrequency;
import com.luislipinski.trucklife.finance.domain.FinancialProductType;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class FinancingPolicyCatalog {
    private static final BigDecimal VEHICLE_DOWN_PAYMENT_RATE=new BigDecimal("0.2000000000");
    private static final BigDecimal ZERO_RATE=new BigDecimal("0.0000000000");

    private final FinancingJurisdictionCatalog jurisdictionCatalog;

    public FinancingPolicyCatalog(FinancingJurisdictionCatalog jurisdictionCatalog) {
        this.jurisdictionCatalog = jurisdictionCatalog;
    }

    public List<FinancingOperations.Offer> offers(CareerEntity career,FinancialProductType productType,BigDecimal requestedAmount){
        BigDecimal amount=money(requestedAmount);
        if(amount.compareTo(BigDecimal.ONE)<0)throw new IllegalArgumentException("Requested amount must be at least 1.00 in the career display currency");
        FinancingJurisdictionCatalog.ResolvedPolicy policy=jurisdictionCatalog.resolve(career,productType,amount);
        return terms(career.getGame(),productType).stream().map(term->offer(career,productType,amount,term,policy)).toList();
    }

    public FinancingOperations.Offer offer(CareerEntity career,FinancialProductType productType,BigDecimal requestedAmount,int termPeriods){
        BigDecimal amount=money(requestedAmount);
        if(amount.compareTo(BigDecimal.ONE)<0)throw new IllegalArgumentException("Requested amount must be at least 1.00 in the career display currency");
        List<Integer> allowed=terms(career.getGame(),productType);
        if(!allowed.contains(termPeriods))throw new IllegalArgumentException("The selected term is not available for this product and game");
        return offer(career,productType,amount,termPeriods,jurisdictionCatalog.resolve(career,productType,amount));
    }

    private FinancingOperations.Offer offer(CareerEntity career,FinancialProductType productType,BigDecimal amount,int termPeriods,
            FinancingJurisdictionCatalog.ResolvedPolicy policy){
        BigDecimal downPaymentRate=productType==FinancialProductType.VEHICLE_FINANCING?VEHICLE_DOWN_PAYMENT_RATE:ZERO_RATE;
        BigDecimal downPayment=amount.multiply(downPaymentRate).setScale(2,RoundingMode.HALF_UP);
        BigDecimal principal=amount.subtract(downPayment).setScale(2,RoundingMode.UNNECESSARY);
        FinancialPaymentFrequency frequency=frequency(career.getGame(),productType);
        Plan plan=plan(principal,policy.annualRate(),frequency,termPeriods);
        String country=career.getGame()==CareerGame.ATS?"US":normalize(career.getCountryCode());
        return new FinancingOperations.Offer(
                productType,
                policy.version(),
                policy.marketSource(),
                policy.referenceAsOf(),
                policy.rateBasis(),
                country,
                career.getGame()==CareerGame.ATS?normalize(career.getStateCode()):null,
                career.getBaseCity(),
                career.getDisplayCurrency(),
                amount,
                principal,
                downPayment,
                policy.annualRate(),
                FinancialAmortizationMethod.FIXED_PAYMENT_REDUCING_BALANCE,
                frequency,
                termPeriods,
                plan.periods().getFirst().total(),
                downPayment.add(plan.totalRepayment()).setScale(2,RoundingMode.UNNECESSARY),
                policy.jurisdictionRuleSource(),
                policy.jurisdictionRuleSummary(),
                policy.legalAprCap(),
                policy.prepaymentRuleSummary(),
                policy.latePaymentRuleSummary(),
                policy.prepaymentFeeRate(),
                policy.lateFeeRate(),
                policy.maxMissedInstallments(),
                downPaymentRate
        );
    }

    public Plan plan(BigDecimal principal,BigDecimal annualRate,FinancialPaymentFrequency frequency,int termPeriods){
        BigDecimal balance=money(principal);
        if(balance.signum()<=0||termPeriods<=0)throw new IllegalArgumentException("Amortization inputs are invalid");
        BigDecimal periodicRate=annualRate.divide(BigDecimal.valueOf(frequency.periodsPerYear()),18,RoundingMode.HALF_EVEN);
        BigDecimal regularPayment;
        if(periodicRate.signum()==0){
            regularPayment=balance.divide(BigDecimal.valueOf(termPeriods),2,RoundingMode.HALF_UP);
        }else{
            BigDecimal growth=BigDecimal.ONE.add(periodicRate).pow(termPeriods,MathContext.DECIMAL128);
            BigDecimal discount=BigDecimal.ONE.divide(growth,18,RoundingMode.HALF_EVEN);
            BigDecimal denominator=BigDecimal.ONE.subtract(discount);
            regularPayment=balance.multiply(periodicRate).divide(denominator,2,RoundingMode.HALF_UP);
        }
        java.util.ArrayList<PeriodAmount> periods=new java.util.ArrayList<>(termPeriods);
        BigDecimal total=zeroMoney();
        for(int i=1;i<=termPeriods;i++){
            BigDecimal interest=balance.multiply(periodicRate).setScale(2,RoundingMode.HALF_UP);
            BigDecimal principalPart;
            BigDecimal payment;
            if(i==termPeriods){
                principalPart=balance;
                payment=principalPart.add(interest).setScale(2,RoundingMode.UNNECESSARY);
            }else{
                principalPart=regularPayment.subtract(interest).setScale(2,RoundingMode.UNNECESSARY);
                if(principalPart.signum()<=0)throw new IllegalArgumentException("Interest rate and term do not produce an amortizing installment");
                if(principalPart.compareTo(balance)>0){
                    principalPart=balance;
                    payment=principalPart.add(interest).setScale(2,RoundingMode.UNNECESSARY);
                }else payment=regularPayment;
            }
            periods.add(new PeriodAmount(principalPart,interest,payment));
            balance=balance.subtract(principalPart).setScale(2,RoundingMode.UNNECESSARY);
            total=total.add(payment);
        }
        if(balance.signum()!=0)throw new IllegalStateException("Amortization schedule did not settle principal");
        return new Plan(List.copyOf(periods),total.setScale(2,RoundingMode.UNNECESSARY));
    }

    private List<Integer> terms(CareerGame game,FinancialProductType type){
        if(game==CareerGame.ATS)return type==FinancialProductType.VEHICLE_FINANCING?List.of(78,104,130):List.of(52,104);
        return type==FinancialProductType.VEHICLE_FINANCING?List.of(36,48,60):List.of(12,24,36);
    }

    private FinancialPaymentFrequency frequency(CareerGame game,FinancialProductType type){
        if(game==CareerGame.ETS2)return FinancialPaymentFrequency.MONTHLY;
        return type==FinancialProductType.VEHICLE_FINANCING?FinancialPaymentFrequency.BIWEEKLY:FinancialPaymentFrequency.WEEKLY;
    }

    private BigDecimal money(BigDecimal value){
        if(value==null)throw new IllegalArgumentException("Requested amount is required");
        try{return value.setScale(2,RoundingMode.UNNECESSARY);}
        catch(ArithmeticException ex){throw new IllegalArgumentException("Amounts support at most two decimal places",ex);}
    }

    private BigDecimal zeroMoney(){return BigDecimal.ZERO.setScale(2);}
    private String normalize(String value){return value==null?"":value.strip().toUpperCase(java.util.Locale.ROOT);}

    public record PeriodAmount(BigDecimal principal,BigDecimal interest,BigDecimal total){}
    public record Plan(List<PeriodAmount> periods,BigDecimal totalRepayment){}
}
