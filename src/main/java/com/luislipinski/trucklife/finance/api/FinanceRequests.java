package com.luislipinski.trucklife.finance.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

final class FinanceRequests {
    private FinanceRequests() {}
    record CreateExpense(@Min(1) Integer expectedOperationalWeek,@Min(1) Integer expectedPayrollMonth,@NotBlank @Size(max=120) String name,@NotNull @DecimalMin("0.00") BigDecimal amount,@NotNull Boolean included) {}
    record UpdateExpense(@Min(1) Integer expectedOperationalWeek,@Min(1) Integer expectedPayrollMonth,@Size(max=120) String name,@NotNull @DecimalMin("0.00") BigDecimal amount,@NotNull Boolean included) {}
    record ContextOperation(@NotNull UUID operationId,@Min(1) Integer expectedOperationalWeek,@Min(1) Integer expectedPayrollMonth) {}
    record ReserveTransfer(@NotNull UUID operationId,@Min(1) Integer expectedOperationalWeek,@Min(1) Integer expectedPayrollMonth,@NotNull @DecimalMin(value="0.01") BigDecimal amount) {}
    record ReserveWithdrawal(@NotNull UUID operationId,@Min(1) Integer expectedOperationalWeek,@Min(1) Integer expectedPayrollMonth,@NotNull @DecimalMin(value="0.01") BigDecimal amount,@NotBlank @Size(max=240) String reason) {}
    record ReserveConfiguration(@Min(1) Integer expectedOperationalWeek,@Min(1) Integer expectedPayrollMonth,@NotNull Boolean enabled,@NotNull @DecimalMin("0.00") BigDecimal amount) {}
}
