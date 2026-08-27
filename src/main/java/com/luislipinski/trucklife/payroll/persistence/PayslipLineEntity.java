package com.luislipinski.trucklife.payroll.persistence;

import com.luislipinski.trucklife.payroll.domain.PayslipLineType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "payslip_lines")
public class PayslipLineEntity {
    @Id private UUID id;
    @Column(name = "payslip_id", nullable = false) private UUID payslipId;
    @Column(name = "line_order", nullable = false) private int lineOrder;
    @Column(nullable = false, length = 60) private String code;
    @Column(nullable = false, length = 160) private String label;
    @Enumerated(EnumType.STRING) @Column(name = "line_type", nullable = false, length = 20) private PayslipLineType lineType;
    @Column(nullable = false, precision = 14, scale = 2) private BigDecimal amount;
    @Column(precision = 16, scale = 4) private BigDecimal quantity;
    @Column(precision = 16, scale = 4) private BigDecimal rate;
    @Column(name = "metadata_json", nullable = false, columnDefinition = "text") private String metadataJson;
    protected PayslipLineEntity() {}
    public PayslipLineEntity(UUID id, UUID payslipId, int lineOrder, String code, String label, PayslipLineType lineType,
                             BigDecimal amount, BigDecimal quantity, BigDecimal rate, String metadataJson) {
        this.id=id; this.payslipId=payslipId; this.lineOrder=lineOrder; this.code=code; this.label=label;
        this.lineType=lineType; this.amount=amount; this.quantity=quantity; this.rate=rate; this.metadataJson=metadataJson;
    }
    public UUID getId(){return id;} public UUID getPayslipId(){return payslipId;} public int getLineOrder(){return lineOrder;}
    public String getCode(){return code;} public String getLabel(){return label;} public PayslipLineType getLineType(){return lineType;}
    public BigDecimal getAmount(){return amount;} public BigDecimal getQuantity(){return quantity;} public BigDecimal getRate(){return rate;}
    public String getMetadataJson(){return metadataJson;}
}
