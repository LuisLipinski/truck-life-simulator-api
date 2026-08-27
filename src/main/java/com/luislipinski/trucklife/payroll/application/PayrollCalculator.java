package com.luislipinski.trucklife.payroll.application;

import com.luislipinski.trucklife.career.domain.CareerGame;
import com.luislipinski.trucklife.payroll.domain.PayslipLineType;
import com.luislipinski.trucklife.trip.domain.TripPaymentCategory;
import com.luislipinski.trucklife.trip.persistence.TripEntity;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class PayrollCalculator {

    static final int DAILY_WORK_MINUTES = 8 * 60;
    static final int ETS2_DRIVING_BLOCK_MINUTES = 270;
    static final int ETS2_DRIVING_BREAK_MINUTES = 45;
    private static final BigDecimal WEEKS_PER_YEAR = bd("52");
    private static final BigDecimal MONTHS_PER_YEAR = bd("12");

    public Calculation calculate(CareerGame game, Context context, List<TripEntity> trips) {
        List<TripEntity> safeTrips = trips == null ? List.of() : List.copyOf(trips);
        validateContext(game, context);
        TimeSummary time = summarizeTime(game, safeTrips);
        return context.currentLevel() <= 1
                ? levelOne(game, context, safeTrips, time)
                : mileage(game, context, safeTrips, time);
    }

    private Calculation levelOne(CareerGame game, Context context, List<TripEntity> trips, TimeSummary time) {
        BigDecimal exchangeRate = context.exchangeRate();
        BigDecimal salaryFactor = context.citySalaryFactor();
        BigDecimal baseSalary;
        BigDecimal overrunRate;
        BigDecimal benefits;
        if (game == CareerGame.ATS) {
            PayrollPolicyCatalog.StatePolicy policy = requireAtsPolicy(context.stateCode());
            baseSalary = money(policy.weeklyGross().multiply(salaryFactor).multiply(exchangeRate));
            overrunRate = money(policy.weeklyGross().divide(bd("40"), 12, RoundingMode.HALF_UP)
                    .multiply(salaryFactor).multiply(exchangeRate));
            benefits = money(PayrollPolicyCatalog.ATS_BENEFITS.multiply(exchangeRate));
        } else {
            PayrollPolicyCatalog.CountryPolicy policy = requireEtsPolicy(context.countryCode());
            baseSalary = money(policy.level1Gross().multiply(salaryFactor).multiply(exchangeRate));
            overrunRate = money(policy.routeOverrunRate().multiply(salaryFactor).multiply(exchangeRate));
            benefits = money(BigDecimal.ZERO);
        }
        BigDecimal overrunHours = bd(time.overrunMinutes()).divide(bd("60"), 4, RoundingMode.HALF_UP);
        BigDecimal overrunPay = money(overrunRate.multiply(bd(time.overrunMinutes()))
                .divide(bd("60"), 12, RoundingMode.HALF_UP));
        BigDecimal gross = money(baseSalary.add(overrunPay));
        BigDecimal perDiem = game == CareerGame.ATS
                ? money(perDiemRate(game, context).multiply(bd(time.eligiblePerDiemDays())))
                : money(BigDecimal.ZERO);
        Map<String, BigDecimal> taxes = taxes(game, context, gross);
        BigDecimal taxTotal = sum(taxes.values());
        BigDecimal netSalary = money(gross.subtract(taxTotal).subtract(benefits));
        BigDecimal deposit = money(netSalary.add(perDiem).max(BigDecimal.ZERO));
        List<Line> lines = new ArrayList<>();
        lines.add(new Line("BASE_SALARY", game == CareerGame.ATS ? "Weekly salary" : "Monthly salary",
                PayslipLineType.EARNING, baseSalary, null, null));
        if (overrunPay.signum() > 0) {
            lines.add(new Line("ROUTE_OVERRUN", game == CareerGame.ATS ? "Route Overrun" : "Route overtime",
                    PayslipLineType.EARNING, overrunPay, overrunHours, rate4(overrunRate)));
        }
        if (perDiem.signum() > 0) {
            BigDecimal rate = perDiemRate(game, context);
            lines.add(new Line("PER_DIEM", "Per diem", PayslipLineType.EARNING, perDiem,
                    bd(time.eligiblePerDiemDays()), rate4(rate)));
        }
        addTaxLines(lines, taxes);
        if (benefits.signum() > 0) {
            lines.add(new Line("BENEFITS", "Benefits", PayslipLineType.DEDUCTION, benefits, null, null));
        }
        return new Calculation(context.currentLevel(), gross, taxTotal, benefits, perDiem, netSalary, deposit,
                totalDistance(trips), time.elapsedMinutes(), time.breakMinutes(), time.workedMinutes(),
                time.overrunMinutes(), List.copyOf(lines));
    }

    private Calculation mileage(CareerGame game, Context context, List<TripEntity> trips, TimeSummary time) {
        Map<TripPaymentCategory, BigDecimal> rates = payRates(game, context);
        Map<TripPaymentCategory, BigDecimal> distances = new EnumMap<>(TripPaymentCategory.class);
        for (TripPaymentCategory category : TripPaymentCategory.values()) distances.put(category, BigDecimal.ZERO);
        for (TripEntity trip : trips) {
            distances.merge(trip.getPaymentCategory(), nonNegative(trip.getOfficialDistance()), BigDecimal::add);
        }
        List<Line> lines = new ArrayList<>();
        BigDecimal gross = BigDecimal.ZERO;
        for (TripPaymentCategory category : TripPaymentCategory.values()) {
            BigDecimal distance = distances.get(category);
            if (distance.signum() <= 0) continue;
            BigDecimal rate = rates.getOrDefault(category, BigDecimal.ZERO);
            BigDecimal amount = money(distance.multiply(rate));
            gross = gross.add(amount);
            lines.add(new Line("MILEAGE_" + category.name(), mileageLabel(game, category),
                    PayslipLineType.EARNING, amount, distance.setScale(2, RoundingMode.HALF_UP), rate4(rate)));
        }
        gross = money(gross);
        BigDecimal perDiemRate = perDiemRate(game, context);
        BigDecimal perDiem = money(perDiemRate.multiply(bd(time.eligiblePerDiemDays())));
        if (perDiem.signum() > 0) {
            lines.add(new Line("PER_DIEM", game == CareerGame.ATS ? "Per diem" : "International per diem",
                    PayslipLineType.EARNING, perDiem, bd(time.eligiblePerDiemDays()), rate4(perDiemRate)));
        }
        Map<String, BigDecimal> taxes = taxes(game, context, gross);
        BigDecimal taxTotal = sum(taxes.values());
        BigDecimal benefits = game == CareerGame.ATS
                ? money(PayrollPolicyCatalog.ATS_BENEFITS.multiply(context.exchangeRate())) : money(BigDecimal.ZERO);
        BigDecimal netSalary = money(gross.subtract(taxTotal).subtract(benefits));
        BigDecimal deposit = money(netSalary.add(perDiem).max(BigDecimal.ZERO));
        addTaxLines(lines, taxes);
        if (benefits.signum() > 0) {
            lines.add(new Line("BENEFITS", "Benefits", PayslipLineType.DEDUCTION, benefits, null, null));
        }
        return new Calculation(context.currentLevel(), gross, taxTotal, benefits, perDiem, netSalary, deposit,
                totalDistance(trips), time.elapsedMinutes(), time.breakMinutes(), time.workedMinutes(),
                time.overrunMinutes(), List.copyOf(lines));
    }

    private Map<TripPaymentCategory, BigDecimal> payRates(CareerGame game, Context context) {
        Map<TripPaymentCategory, BigDecimal> result = new EnumMap<>(TripPaymentCategory.class);
        if (game == CareerGame.ATS) {
            PayrollPolicyCatalog.StatePolicy policy = requireAtsPolicy(context.stateCode());
            BigDecimal regionalFactor = policy.weeklyGross().divide(bd("960"), 12, RoundingMode.HALF_UP);
            PayrollPolicyCatalog.ATS_BASE_PAY_RATES.forEach((category, baseRate) -> result.put(category,
                    rate4(baseRate.multiply(regionalFactor).multiply(context.citySalaryFactor())
                            .multiply(context.exchangeRate()))));
        } else {
            requireEtsPolicy(context.countryCode()).payRates().forEach((category, baseRate) -> result.put(category,
                    rate4(baseRate.multiply(context.citySalaryFactor()).multiply(context.exchangeRate()))));
        }
        return result;
    }

    private BigDecimal perDiemRate(CareerGame game, Context context) {
        BigDecimal baseRate = game == CareerGame.ATS
                ? requireAtsPolicy(context.stateCode()).perDiemRate()
                : requireEtsPolicy(context.countryCode()).perDiemRate();
        return money(baseRate.multiply(context.exchangeRate()));
    }

    private Map<String, BigDecimal> taxes(CareerGame game, Context context, BigDecimal displayGross) {
        BigDecimal baseGross = displayGross.divide(context.exchangeRate(), 12, RoundingMode.HALF_UP);
        Map<String, BigDecimal> baseTaxes = game == CareerGame.ATS
                ? atsTaxes(baseGross, requireAtsPolicy(context.stateCode()))
                : etsTaxes(baseGross, requireEtsPolicy(context.countryCode()));
        Map<String, BigDecimal> displayTaxes = new LinkedHashMap<>();
        baseTaxes.forEach((code, amount) -> displayTaxes.put(code, money(amount.multiply(context.exchangeRate()))));
        return displayTaxes;
    }

    private Map<String, BigDecimal> atsTaxes(BigDecimal weeklyGross, PayrollPolicyCatalog.StatePolicy policy) {
        BigDecimal annualGross = weeklyGross.multiply(WEEKS_PER_YEAR);
        BigDecimal federalTaxable = annualGross.subtract(bd("16100")).max(BigDecimal.ZERO);
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        result.put("FEDERAL_TAX", progressiveTax(federalTaxable, List.of(
                bracket("12400", "0.10"), bracket("50400", "0.12"), bracket("105700", "0.22"),
                bracket("201775", "0.24"), bracket("256225", "0.32"), bracket("640600", "0.35"), open("0.37")
        )).divide(WEEKS_PER_YEAR, 12, RoundingMode.HALF_UP));
        result.put("SOCIAL_SECURITY", annualGross.min(bd("184500")).multiply(bd("0.062"))
                .divide(WEEKS_PER_YEAR, 12, RoundingMode.HALF_UP));
        result.put("MEDICARE", annualGross.multiply(bd("0.0145"))
                .add(annualGross.subtract(bd("200000")).max(BigDecimal.ZERO).multiply(bd("0.009")))
                .divide(WEEKS_PER_YEAR, 12, RoundingMode.HALF_UP));
        PayrollPolicyCatalog.StateIncomeTax incomeTax = policy.incomeTax();
        if (incomeTax != null) {
            BigDecimal stateTaxable = annualGross.subtract(incomeTax.standardDeduction())
                    .subtract(incomeTax.personalExemption()).max(BigDecimal.ZERO);
            BigDecimal annualStateTax = progressiveTax(stateTaxable, incomeTax.brackets())
                    .subtract(incomeTax.credit()).max(BigDecimal.ZERO);
            result.put("STATE_INCOME_TAX", annualStateTax.divide(WEEKS_PER_YEAR, 12, RoundingMode.HALF_UP));
        }
        if (policy.payrollTaxRate() != null) result.put("STATE_PAYROLL_TAX", weeklyGross.multiply(policy.payrollTaxRate()));
        return result;
    }

    private Map<String, BigDecimal> etsTaxes(BigDecimal monthlyGross, PayrollPolicyCatalog.CountryPolicy policy) {
        return switch (policy.taxModel()) {
            case DE -> germanTaxes(monthlyGross);
            case GB -> britishTaxes(monthlyGross);
            case PL -> polishTaxes(monthlyGross);
            case EFFECTIVE -> effectiveTaxes(monthlyGross, policy);
        };
    }

    private Map<String, BigDecimal> germanTaxes(BigDecimal monthlyGross) {
        BigDecimal pensionBase = monthlyGross.min(bd("8450"));
        BigDecimal healthBase = monthlyGross.min(bd("5812.5"));
        BigDecimal pensionInsurance = pensionBase.multiply(bd("0.093"));
        BigDecimal unemploymentInsurance = pensionBase.multiply(bd("0.013"));
        BigDecimal healthInsurance = healthBase.multiply(bd("0.0875"));
        BigDecimal careInsurance = healthBase.multiply(bd("0.024"));
        BigDecimal annualSocial = pensionInsurance.add(unemploymentInsurance).add(healthInsurance).add(careInsurance)
                .multiply(MONTHS_PER_YEAR);
        BigDecimal annualTaxable = monthlyGross.multiply(MONTHS_PER_YEAR).subtract(annualSocial).max(BigDecimal.ZERO);
        BigDecimal incomeTax = progressiveTax(annualTaxable, List.of(
                bracket("12348", "0"), bracket("17799", "0.14"), bracket("69878", "0.24"),
                bracket("277825", "0.42"), open("0.45")
        )).divide(MONTHS_PER_YEAR, 12, RoundingMode.HALF_UP);
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        result.put("INCOME_TAX", incomeTax);
        result.put("PENSION_INSURANCE", pensionInsurance);
        result.put("HEALTH_INSURANCE", healthInsurance);
        result.put("UNEMPLOYMENT_INSURANCE", unemploymentInsurance);
        result.put("CARE_INSURANCE", careInsurance);
        return result;
    }

    private Map<String, BigDecimal> britishTaxes(BigDecimal monthlyGross) {
        BigDecimal annualGross = monthlyGross.multiply(MONTHS_PER_YEAR);
        BigDecimal allowance = annualGross.compareTo(bd("100000")) > 0
                ? bd("12570").subtract(annualGross.subtract(bd("100000")).divide(bd("2"), 12, RoundingMode.HALF_UP))
                        .max(BigDecimal.ZERO)
                : bd("12570");
        BigDecimal taxable = annualGross.subtract(allowance).max(BigDecimal.ZERO);
        BigDecimal secondLimit = bd("125140").subtract(allowance);
        BigDecimal incomeTax = progressiveTax(taxable, List.of(
                bracket("37700", "0.20"), new PayrollPolicyCatalog.TaxBracket(secondLimit, bd("0.40")), open("0.45")
        )).divide(MONTHS_PER_YEAR, 12, RoundingMode.HALF_UP);
        BigDecimal nationalInsurance = annualGross.subtract(bd("12570")).max(BigDecimal.ZERO)
                .min(bd("50270").subtract(bd("12570"))).multiply(bd("0.08"))
                .add(annualGross.subtract(bd("50270")).max(BigDecimal.ZERO).multiply(bd("0.02")))
                .divide(MONTHS_PER_YEAR, 12, RoundingMode.HALF_UP);
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        result.put("INCOME_TAX", incomeTax);
        result.put("NATIONAL_INSURANCE", nationalInsurance);
        return result;
    }

    private Map<String, BigDecimal> polishTaxes(BigDecimal monthlyGross) {
        BigDecimal pensionInsurance = monthlyGross.multiply(bd("0.0976"));
        BigDecimal disabilityInsurance = monthlyGross.multiply(bd("0.015"));
        BigDecimal sicknessInsurance = monthlyGross.multiply(bd("0.0245"));
        BigDecimal socialTotal = pensionInsurance.add(disabilityInsurance).add(sicknessInsurance);
        BigDecimal healthInsurance = monthlyGross.subtract(socialTotal).max(BigDecimal.ZERO).multiply(bd("0.09"));
        BigDecimal annualTaxable = monthlyGross.subtract(socialTotal).max(BigDecimal.ZERO).multiply(MONTHS_PER_YEAR);
        BigDecimal annualPit = annualTaxable.min(bd("120000")).multiply(bd("0.12"))
                .add(annualTaxable.subtract(bd("120000")).max(BigDecimal.ZERO).multiply(bd("0.32")))
                .subtract(bd("3600")).max(BigDecimal.ZERO);
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        result.put("INCOME_TAX", annualPit.divide(MONTHS_PER_YEAR, 12, RoundingMode.HALF_UP));
        result.put("PENSION_INSURANCE", pensionInsurance);
        result.put("DISABILITY_INSURANCE", disabilityInsurance);
        result.put("SICKNESS_INSURANCE", sicknessInsurance);
        result.put("HEALTH_INSURANCE", healthInsurance);
        return result;
    }

    private Map<String, BigDecimal> effectiveTaxes(BigDecimal monthlyGross, PayrollPolicyCatalog.CountryPolicy policy) {
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        result.put("INCOME_TAX", monthlyGross.multiply(policy.incomeTaxRate()));
        result.put("SOCIAL_CONTRIBUTIONS", monthlyGross.multiply(policy.socialRate()));
        return result;
    }

    private BigDecimal progressiveTax(BigDecimal value, List<PayrollPolicyCatalog.TaxBracket> brackets) {
        BigDecimal remaining = value.max(BigDecimal.ZERO);
        BigDecimal previous = BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;
        for (PayrollPolicyCatalog.TaxBracket bracket : brackets) {
            BigDecimal slice;
            if (bracket.upperLimit() == null) slice = remaining;
            else {
                BigDecimal width = bracket.upperLimit().subtract(previous).max(BigDecimal.ZERO);
                slice = remaining.min(width).max(BigDecimal.ZERO);
            }
            total = total.add(slice.multiply(bracket.rate()));
            remaining = remaining.subtract(slice);
            if (bracket.upperLimit() != null) previous = bracket.upperLimit();
            if (remaining.signum() <= 0) break;
        }
        return total;
    }

    private TimeSummary summarizeTime(CareerGame game, List<TripEntity> trips) {
        Map<Integer, DayTotals> totalsByDay = new LinkedHashMap<>();
        Set<Integer> perDiemDays = new LinkedHashSet<>();
        for (TripEntity trip : trips) {
            List<Segment> segments = segments(trip);
            if (segments.isEmpty()) continue;
            int elapsedMinutes = segments.stream().mapToInt(Segment::minutes).sum();
            int breakMinutes = breakMinutes(game, trip, elapsedMinutes);
            int[] allocations = allocateBreaks(segments, breakMinutes);
            for (int index = 0; index < segments.size(); index++) {
                Segment segment = segments.get(index);
                DayTotals totals = totalsByDay.computeIfAbsent(segment.dayIndex(), ignored -> new DayTotals());
                totals.elapsedMinutes += segment.minutes();
                totals.breakMinutes += allocations[index];
            }
            int departureIndex = trip.getDepartureDay().getValue() - 1;
            int arrivalIndex = adjustedArrivalDayIndex(trip.getDepartureDay(), trip.getArrivalDay());
            if (arrivalIndex > departureIndex) {
                for (int day = departureIndex; day <= arrivalIndex; day++) perDiemDays.add(day);
            }
        }
        int elapsed = 0, breaks = 0, worked = 0, overrun = 0;
        for (DayTotals totals : totalsByDay.values()) {
            int dayBreak = Math.min(totals.elapsedMinutes, totals.breakMinutes);
            int dayWorked = Math.max(0, totals.elapsedMinutes - dayBreak);
            elapsed += totals.elapsedMinutes;
            breaks += dayBreak;
            worked += dayWorked;
            overrun += Math.max(0, dayWorked - DAILY_WORK_MINUTES);
        }
        return new TimeSummary(elapsed, breaks, worked, overrun, perDiemDays.size());
    }

    private List<Segment> segments(TripEntity trip) {
        int departureDayIndex = trip.getDepartureDay().getValue() - 1;
        int arrivalDayIndex = adjustedArrivalDayIndex(trip.getDepartureDay(), trip.getArrivalDay());
        int start = departureDayIndex * 24 * 60 + minuteOfDay(trip.getDepartureTime());
        int end = arrivalDayIndex * 24 * 60 + minuteOfDay(trip.getArrivalTime());
        if (end <= start) return List.of();
        List<Segment> result = new ArrayList<>();
        int cursor = start;
        while (cursor < end) {
            int dayIndex = Math.floorDiv(cursor, 24 * 60);
            int nextMidnight = (dayIndex + 1) * 24 * 60;
            int segmentEnd = Math.min(end, nextMidnight);
            result.add(new Segment(dayIndex, segmentEnd - cursor));
            cursor = segmentEnd;
        }
        return result;
    }

    private int breakMinutes(CareerGame game, TripEntity trip, int elapsedMinutes) {
        Integer recorded = trip.getBreakMinutes();
        int selected = recorded != null ? recorded
                : game == CareerGame.ETS2 ? suggestedEtsBreakMinutes(elapsedMinutes) : 0;
        return Math.min(elapsedMinutes, Math.max(0, selected));
    }

    private int suggestedEtsBreakMinutes(int elapsedMinutes) {
        int suggested = 0;
        int nextDrivingLimit = ETS2_DRIVING_BLOCK_MINUTES;
        while (elapsedMinutes > nextDrivingLimit) {
            suggested += ETS2_DRIVING_BREAK_MINUTES;
            nextDrivingLimit += ETS2_DRIVING_BREAK_MINUTES + ETS2_DRIVING_BLOCK_MINUTES;
        }
        return Math.min(suggested, elapsedMinutes);
    }

    private int[] allocateBreaks(List<Segment> segments, int breakMinutes) {
        int[] allocations = new int[segments.size()];
        int totalMinutes = segments.stream().mapToInt(Segment::minutes).sum();
        if (totalMinutes <= 0 || breakMinutes <= 0) return allocations;
        List<Remainder> remainders = new ArrayList<>();
        int allocated = 0;
        for (int index = 0; index < segments.size(); index++) {
            long numerator = (long) breakMinutes * segments.get(index).minutes();
            allocations[index] = (int) (numerator / totalMinutes);
            allocated += allocations[index];
            remainders.add(new Remainder(index, numerator % totalMinutes));
        }
        remainders.sort(Comparator.comparingLong(Remainder::value).reversed());
        int remaining = breakMinutes - allocated;
        int cursor = 0;
        while (remaining > 0 && !remainders.isEmpty()) {
            int segmentIndex = remainders.get(cursor % remainders.size()).index();
            if (allocations[segmentIndex] < segments.get(segmentIndex).minutes()) {
                allocations[segmentIndex]++;
                remaining--;
            }
            cursor++;
        }
        return allocations;
    }

    private int adjustedArrivalDayIndex(DayOfWeek departureDay, DayOfWeek arrivalDay) {
        int departure = departureDay.getValue() - 1;
        int arrival = arrivalDay.getValue() - 1;
        return arrival < departure ? arrival + 7 : arrival;
    }

    private int minuteOfDay(LocalTime time) { return time.getHour() * 60 + time.getMinute(); }

    private BigDecimal totalDistance(List<TripEntity> trips) {
        return money(trips.stream().map(TripEntity::getOfficialDistance).map(PayrollCalculator::nonNegative)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private void addTaxLines(List<Line> lines, Map<String, BigDecimal> taxes) {
        taxes.forEach((code, amount) -> {
            BigDecimal rounded = money(amount);
            if (rounded.signum() > 0) lines.add(new Line(code, taxLabel(code), PayslipLineType.DEDUCTION, rounded, null, null));
        });
    }

    private String taxLabel(String code) {
        return switch (code) {
            case "FEDERAL_TAX" -> "Federal Income Tax";
            case "SOCIAL_SECURITY" -> "Social Security";
            case "MEDICARE" -> "Medicare";
            case "STATE_INCOME_TAX" -> "State Income Tax";
            case "STATE_PAYROLL_TAX" -> "State payroll contribution";
            case "INCOME_TAX" -> "Income Tax";
            case "PENSION_INSURANCE" -> "Pension insurance";
            case "HEALTH_INSURANCE" -> "Health insurance";
            case "UNEMPLOYMENT_INSURANCE" -> "Unemployment insurance";
            case "CARE_INSURANCE" -> "Care insurance";
            case "NATIONAL_INSURANCE" -> "National Insurance";
            case "DISABILITY_INSURANCE" -> "Disability insurance";
            case "SICKNESS_INSURANCE" -> "Sickness insurance";
            case "SOCIAL_CONTRIBUTIONS" -> "Social contributions";
            default -> code;
        };
    }

    private String mileageLabel(CareerGame game, TripPaymentCategory category) {
        if (game == CareerGame.ATS) {
            return switch (category) {
                case NORMAL -> "Loaded normal";
                case HAZMAT -> "Loaded HazMat";
                case DOUBLES -> "Loaded Doubles";
                case HAZMAT_DOUBLES -> "Loaded HazMat + Doubles";
                case DEADHEAD -> "Deadhead";
            };
        }
        return switch (category) {
            case NORMAL -> "Standard load";
            case HAZMAT -> "ADR load";
            case DOUBLES -> "Euro Combi";
            case HAZMAT_DOUBLES -> "ADR + Euro Combi";
            case DEADHEAD -> "Empty repositioning";
        };
    }

    private void validateContext(CareerGame game, Context context) {
        if (context == null || context.currentLevel() < 1 || context.currentLevel() > 3)
            throw new IllegalArgumentException("Payroll calculation context level is invalid");
        if (context.exchangeRate() == null || context.exchangeRate().signum() <= 0)
            throw new IllegalArgumentException("Payroll calculation exchange rate is invalid");
        if (context.citySalaryFactor() == null || context.citySalaryFactor().signum() <= 0)
            throw new IllegalArgumentException("Payroll calculation city salary factor is invalid");
        if (game == CareerGame.ATS) {
            requireAtsPolicy(context.stateCode());
            if (!"USD".equals(context.baseCurrency()))
                throw new IllegalArgumentException("ATS payroll base currency must be USD");
            return;
        }
        PayrollPolicyCatalog.CountryPolicy policy = requireEtsPolicy(context.countryCode());
        if (!policy.baseCurrency().equals(context.baseCurrency()))
            throw new IllegalArgumentException("ETS2 payroll base currency does not match the country policy");
    }

    private PayrollPolicyCatalog.StatePolicy requireAtsPolicy(String stateCode) {
        PayrollPolicyCatalog.StatePolicy policy = PayrollPolicyCatalog.ats(stateCode);
        if (policy == null) throw new IllegalArgumentException("ATS payroll policy is unavailable for state " + stateCode);
        return policy;
    }

    private PayrollPolicyCatalog.CountryPolicy requireEtsPolicy(String countryCode) {
        PayrollPolicyCatalog.CountryPolicy policy = PayrollPolicyCatalog.ets2(countryCode);
        if (policy == null) throw new IllegalArgumentException("ETS2 payroll policy is unavailable for country " + countryCode);
        return policy;
    }

    private BigDecimal sum(Iterable<BigDecimal> values) {
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal value : values) total = total.add(value);
        return money(total);
    }

    private static PayrollPolicyCatalog.TaxBracket bracket(String upperLimit, String rate) {
        return new PayrollPolicyCatalog.TaxBracket(bd(upperLimit), bd(rate));
    }
    private static PayrollPolicyCatalog.TaxBracket open(String rate) { return new PayrollPolicyCatalog.TaxBracket(null, bd(rate)); }
    private static BigDecimal nonNegative(BigDecimal value) { return value == null ? BigDecimal.ZERO : value.max(BigDecimal.ZERO); }
    private static BigDecimal bd(long value) { return BigDecimal.valueOf(value); }
    private static BigDecimal bd(String value) { return new BigDecimal(value); }
    private static BigDecimal money(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP); }
    private static BigDecimal rate4(BigDecimal value) { return value.setScale(4, RoundingMode.HALF_UP); }

    public record Context(short currentLevel, String stateCode, String countryCode, String baseCurrency,
                          String displayCurrency, BigDecimal exchangeRate, BigDecimal citySalaryFactor) {
        public Context { citySalaryFactor = citySalaryFactor == null ? BigDecimal.ONE : citySalaryFactor; }
    }
    public record Line(String code, String label, PayslipLineType type, BigDecimal amount,
                       BigDecimal quantity, BigDecimal rate) {}
    public record Calculation(short level, BigDecimal gross, BigDecimal taxTotal, BigDecimal benefits,
                              BigDecimal perDiem, BigDecimal netSalary, BigDecimal deposit,
                              BigDecimal totalDistance, int elapsedMinutes, int breakMinutes,
                              int workedMinutes, int overrunMinutes, List<Line> lines) {}
    private record Segment(int dayIndex, int minutes) {}
    private record Remainder(int index, long value) {}
    private record TimeSummary(int elapsedMinutes, int breakMinutes, int workedMinutes,
                               int overrunMinutes, int eligiblePerDiemDays) {}
    private static final class DayTotals { private int elapsedMinutes; private int breakMinutes; }
}
