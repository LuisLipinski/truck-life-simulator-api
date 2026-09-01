package com.luislipinski.trucklife.payroll.api;

import com.luislipinski.trucklife.payroll.application.PayrollPreferencesService;
import com.luislipinski.trucklife.payroll.domain.PayslipLineType;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.util.List;
import java.util.Map;

public record PayrollPreviewResponse(
        boolean ready,
        Integer operationalWeek,
        Integer payrollMonth,
        List<Integer> weeks,
        short level,
        String displayCurrency,
        BigDecimal grossAmount,
        BigDecimal taxAmount,
        BigDecimal benefitsAmount,
        BigDecimal perDiemAmount,
        BigDecimal netSalaryAmount,
        BigDecimal incidentDeductionAmount,
        BigDecimal depositAmount,
        BigDecimal totalDistance,
        int elapsedMinutes,
        int breakMinutes,
        int workedMinutes,
        int overrunMinutes,
        List<DailyWorkBreakdownResponse> dailyWorkBreakdown,
        List<LineResponse> lines,
        Map<String,Object> contextSnapshot
) {
    static PayrollPreviewResponse from(PayrollPreferencesService.Preview preview) {
        return new PayrollPreviewResponse(
                preview.ready(), preview.operationalWeek(), preview.payrollMonth(), preview.weeks(), preview.level(),
                preview.displayCurrency(), preview.grossAmount(), preview.taxAmount(), preview.benefitsAmount(),
                preview.perDiemAmount(), preview.netSalaryAmount(), preview.incidentDeductionAmount(), preview.depositAmount(),
                preview.totalDistance(), preview.elapsedMinutes(), preview.breakMinutes(), preview.workedMinutes(),
                preview.overrunMinutes(), preview.dailyWorkBreakdown().stream().map(DailyWorkBreakdownResponse::from).toList(),
                preview.lines().stream().map(LineResponse::from).toList(), preview.contextSnapshot()
        );
    }

    public record DailyWorkBreakdownResponse(int operationalWeek, DayOfWeek day, int elapsedMinutes,
                                             int breakMinutes, int workedMinutes, int overrunMinutes) {
        static DailyWorkBreakdownResponse from(
                com.luislipinski.trucklife.payroll.application.PayrollCalculator.DailyWorkBreakdown day) {
            return new DailyWorkBreakdownResponse(day.operationalWeek(), day.day(), day.elapsedMinutes(),
                    day.breakMinutes(), day.workedMinutes(), day.overrunMinutes());
        }
    }

    public record LineResponse(String code, String label, PayslipLineType type, BigDecimal amount,
                               BigDecimal quantity, BigDecimal rate) {
        static LineResponse from(com.luislipinski.trucklife.payroll.application.PayrollCalculator.Line line) {
            return new LineResponse(line.code(), line.label(), line.type(), line.amount(), line.quantity(), line.rate());
        }
    }
}
