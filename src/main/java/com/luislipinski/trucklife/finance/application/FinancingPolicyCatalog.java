package com.luislipinski.trucklife.finance.application;

import com.luislipinski.trucklife.career.domain.CareerGame;
import com.luislipinski.trucklife.career.persistence.CareerEntity;
import com.luislipinski.trucklife.finance.domain.FinancialAmortizationMethod;
import com.luislipinski.trucklife.finance.domain.FinancialPaymentFrequency;
import com.luislipinski.trucklife.finance.domain.FinancialProductType;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class FinancingPolicyCatalog {
    private static final BigDecimal VEHICLE_DOWN_PAYMENT_RATE=new BigDecimal("0.2000000000");
    private static final BigDecimal ZERO_RATE=new BigDecimal("0.0000000000");
    private static final Set<String> EURO_AREA=Set.of("AT","BE","DE","EE","ES","FI","FR","GR","HR","IT","LT","LU","LV","NL","PT","SI","SK");
    private static final Map<String,BigDecimal> ECB_COUNTRY_RATES=Map.ofEntries(
            Map.entry("AT",new BigDecimal("0.0816000000")),Map.entry("BE",new BigDecimal("0.0595000000")),
            Map.entry("DE",new BigDecimal("0.0812000000")),Map.entry("FR",new BigDecimal("0.0629000000")),
            Map.entry("GR",new BigDecimal("0.1037000000")),Map.entry("IT",new BigDecimal("0.0869000000")),
            Map.entry("LT",new BigDecimal("0.0826000000")),Map.entry("LU",new BigDecimal("0.0449000000")),
            Map.entry("LV",new BigDecimal("0.1179000000")),Map.entry("PT",new BigDecimal("0.0898000000")),
            Map.entry("SI",new BigDecimal("0.0574000000")),Map.entry("SK",new BigDecimal("0.0892000000")));
    private static final BigDecimal ECB_EURO_AREA_RATE=new BigDecimal("0.0759000000");
    private static final BigDecimal BOE_PNFC_NEW_BUSINESS_LOAN_RATE=new BigDecimal("0.0535000000");
    private static final BigDecimal FED_AUTO_60M_RATE=new BigDecimal("0.0714000000");
    private static final BigDecimal FED_PERSONAL_24M_RATE=new BigDecimal("0.1186000000");

    public List<FinancingOperations.Offer> offers(CareerEntity career,FinancialProductType productType,BigDecimal requestedAmount){
        BigDecimal amount=money(requestedAmount);if(amount.compareTo(BigDecimal.ONE)<0)throw new IllegalArgumentException("Requested amount must be at least 1.00 in the career display currency");
        Policy policy=policy(career,productType);return terms(career.getGame(),productType).stream().map(term->offer(career,productType,amount,term,policy)).toList();
    }

    public FinancingOperations.Offer offer(CareerEntity career,FinancialProductType productType,BigDecimal requestedAmount,int termPeriods){
        BigDecimal amount=money(requestedAmount);if(amount.compareTo(BigDecimal.ONE)<0)throw new IllegalArgumentException("Requested amount must be at least 1.00 in the career display currency");
        List<Integer> allowed=terms(career.getGame(),productType);if(!allowed.contains(termPeriods))throw new IllegalArgumentException("The selected term is not available for this product and game");
        return offer(career,productType,amount,termPeriods,policy(career,productType));
    }

    private FinancingOperations.Offer offer(CareerEntity career,FinancialProductType productType,BigDecimal amount,int termPeriods,Policy policy){
        BigDecimal downPayment=productType==FinancialProductType.VEHICLE_FINANCING?amount.multiply(VEHICLE_DOWN_PAYMENT_RATE).setScale(2,RoundingMode.HALF_UP):zeroMoney();
        BigDecimal principal=amount.subtract(downPayment).setScale(2,RoundingMode.UNNECESSARY);FinancialPaymentFrequency frequency=frequency(career.getGame(),productType);
        Plan plan=plan(principal,policy.annualRate(),frequency,termPeriods);String country=career.getGame()==CareerGame.ATS?"US":normalize(career.getCountryCode());
        return new FinancingOperations.Offer(productType,policy.version(),policy.source(),policy.referenceAsOf(),policy.rateBasis(),country,
                career.getGame()==CareerGame.ATS?normalize(career.getStateCode()):null,career.getBaseCity(),career.getDisplayCurrency(),amount,principal,
                downPayment,policy.annualRate(),FinancialAmortizationMethod.FIXED_PAYMENT_REDUCING_BALANCE,frequency,termPeriods,
                plan.periods().getFirst().total(),downPayment.add(plan.totalRepayment()).setScale(2,RoundingMode.UNNECESSARY));
    }

    public Plan plan(BigDecimal principal,BigDecimal annualRate,FinancialPaymentFrequency frequency,int termPeriods){
        BigDecimal balance=money(principal);if(balance.signum()<=0||termPeriods<=0)throw new IllegalArgumentException("Amortization inputs are invalid");
        BigDecimal periodicRate=annualRate.divide(BigDecimal.valueOf(frequency.periodsPerYear()),18,RoundingMode.HALF_EVEN);BigDecimal regularPayment;
        if(periodicRate.signum()==0){regularPayment=balance.divide(BigDecimal.valueOf(termPeriods),2,RoundingMode.HALF_UP);}else{
            BigDecimal growth=BigDecimal.ONE.add(periodicRate).pow(termPeriods,MathContext.DECIMAL128);
            BigDecimal discount=BigDecimal.ONE.divide(growth,18,RoundingMode.HALF_EVEN);BigDecimal denominator=BigDecimal.ONE.subtract(discount);
            regularPayment=balance.multiply(periodicRate).divide(denominator,2,RoundingMode.HALF_UP);
        }
        java.util.ArrayList<PeriodAmount> periods=new java.util.ArrayList<>(termPeriods);BigDecimal total=zeroMoney();
        for(int i=1;i<=termPeriods;i++){
            BigDecimal interest=balance.multiply(periodicRate).setScale(2,RoundingMode.HALF_UP);BigDecimal principalPart;
            BigDecimal payment;if(i==termPeriods){principalPart=balance;payment=principalPart.add(interest).setScale(2,RoundingMode.UNNECESSARY);}else{
                principalPart=regularPayment.subtract(interest).setScale(2,RoundingMode.UNNECESSARY);if(principalPart.signum()<=0)throw new IllegalArgumentException("Interest rate and term do not produce an amortizing installment");
                if(principalPart.compareTo(balance)>0){principalPart=balance;payment=principalPart.add(interest).setScale(2,RoundingMode.UNNECESSARY);}else payment=regularPayment;
            }
            periods.add(new PeriodAmount(principalPart,interest,payment));balance=balance.subtract(principalPart).setScale(2,RoundingMode.UNNECESSARY);total=total.add(payment);
        }
        if(balance.signum()!=0)throw new IllegalStateException("Amortization schedule did not settle principal");return new Plan(List.copyOf(periods),total.setScale(2,RoundingMode.UNNECESSARY));
    }

    private Policy policy(CareerEntity career,FinancialProductType productType){
        if(career.getGame()==CareerGame.ATS){BigDecimal rate=productType==FinancialProductType.VEHICLE_FINANCING?FED_AUTO_60M_RATE:FED_PERSONAL_24M_RATE;
            String basis=productType==FinancialProductType.VEHICLE_FINANCING?"FED_G19_60_MONTH_NEW_AUTO_FINANCE_RATE":"FED_G19_24_MONTH_PERSONAL_LOAN_RATE";
            String source=productType==FinancialProductType.VEHICLE_FINANCING?"https://fred.stlouisfed.org/series/RIFLPBCIANM60NM":"https://fred.stlouisfed.org/series/TERMCBPER24NS";
            return new Policy("phase1-financing-us-fed-g19-2026-05-v1",source,LocalDate.of(2026,5,1),basis,rate);
        }
        String country=normalize(career.getCountryCode());if("GB".equals(country))return new Policy("phase1-financing-gb-boe-pnfc-2026-05-v1",
                "https://www.bankofengland.co.uk/statistics/effective-interest-rates/2026/may-2026",LocalDate.of(2026,5,1),"BOE_PNFC_NEW_BUSINESS_LOAN_REFERENCE",BOE_PNFC_NEW_BUSINESS_LOAN_RATE);
        if(EURO_AREA.contains(country)){BigDecimal rate=ECB_COUNTRY_RATES.getOrDefault(country,ECB_EURO_AREA_RATE);String suffix=ECB_COUNTRY_RATES.containsKey(country)?country:"EA";
            return new Policy("phase1-financing-ecb-consumer-credit-2026-04-v1-"+suffix,"https://data.ecb.europa.eu/data/datasets/MIR",LocalDate.of(2026,4,1),"ECB_MFI_NEW_CONSUMER_CREDIT_REFERENCE",rate);}
        throw new IllegalArgumentException("No researched financing reference policy is available yet for country "+country+"; the backend refuses to invent a rate");
    }

    private List<Integer> terms(CareerGame game,FinancialProductType type){if(game==CareerGame.ATS)return type==FinancialProductType.VEHICLE_FINANCING?List.of(78,104,130):List.of(52,104);return type==FinancialProductType.VEHICLE_FINANCING?List.of(36,48,60):List.of(12,24,36);}
    private FinancialPaymentFrequency frequency(CareerGame game,FinancialProductType type){if(game==CareerGame.ETS2)return FinancialPaymentFrequency.MONTHLY;return type==FinancialProductType.VEHICLE_FINANCING?FinancialPaymentFrequency.BIWEEKLY:FinancialPaymentFrequency.WEEKLY;}
    private BigDecimal money(BigDecimal value){if(value==null)throw new IllegalArgumentException("Requested amount is required");try{return value.setScale(2,RoundingMode.UNNECESSARY);}catch(ArithmeticException ex){throw new IllegalArgumentException("Amounts support at most two decimal places",ex);}}
    private BigDecimal zeroMoney(){return BigDecimal.ZERO.setScale(2);}private String normalize(String value){return value==null?"":value.strip().toUpperCase(Locale.ROOT);}
    public BigDecimal prepaymentFeeRate(){return ZERO_RATE;} public BigDecimal lateFeeRate(){return ZERO_RATE;} public int maxMissedInstallments(){return 3;}
    private record Policy(String version,String source,LocalDate referenceAsOf,String rateBasis,BigDecimal annualRate){}
    public record PeriodAmount(BigDecimal principal,BigDecimal interest,BigDecimal total){}
    public record Plan(List<PeriodAmount> periods,BigDecimal totalRepayment){}
}
