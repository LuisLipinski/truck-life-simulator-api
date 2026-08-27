package com.luislipinski.trucklife.incident.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.luislipinski.trucklife.incident.domain.IncidentChargeMethod;
import com.luislipinski.trucklife.incident.domain.IncidentStatus;
import com.luislipinski.trucklife.incident.domain.IncidentType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IncidentEntityTest {

    @Test
    void appliesPartialAndFinalPayslipDeductions() {
        Instant now = Instant.parse("2026-08-27T10:00:00Z");
        IncidentEntity incident = pending(now, "150.00");

        incident.applyPayslipDeduction(new BigDecimal("40.00"), now.plusSeconds(10));

        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.PARTIALLY_DEDUCTED);
        assertThat(incident.getRemainingAmount()).isEqualByComparingTo("110.00");
        assertThat(incident.canCancel()).isFalse();

        incident.applyPayslipDeduction(new BigDecimal("110.00"), now.plusSeconds(20));

        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.DEDUCTED_PAYSLIP);
        assertThat(incident.getRemainingAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    void cancelsOnlyAnUntouchedPendingIncident() {
        Instant now = Instant.parse("2026-08-27T10:00:00Z");
        IncidentEntity incident = pending(now, "80.00");

        assertThat(incident.canCancel()).isTrue();
        incident.cancel(now.plusSeconds(10));

        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.CANCELLED);
        assertThat(incident.getRemainingAmount()).isEqualByComparingTo("0.00");
        assertThatThrownBy(() -> incident.applyPayslipDeduction(
                new BigDecimal("1.00"),
                now.plusSeconds(20)
        )).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsInvalidPayslipDeductionAmounts() {
        Instant now = Instant.parse("2026-08-27T10:00:00Z");
        IncidentEntity incident = pending(now, "50.00");

        assertThatThrownBy(() -> incident.applyPayslipDeduction(
                BigDecimal.ZERO,
                now
        )).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> incident.applyPayslipDeduction(
                new BigDecimal("60.00"),
                now
        )).isInstanceOf(IllegalArgumentException.class);
    }

    private IncidentEntity pending(Instant now, String amount) {
        BigDecimal value = new BigDecimal(amount);
        return new IncidentEntity(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                1,
                IncidentType.INFRACTION,
                value,
                value,
                "I-10",
                "Test incident",
                IncidentChargeMethod.PAYSLIP,
                IncidentStatus.PENDING_PAYSLIP,
                now,
                now
        );
    }
}
