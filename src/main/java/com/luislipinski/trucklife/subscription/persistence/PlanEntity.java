package com.luislipinski.trucklife.subscription.persistence;

import com.luislipinski.trucklife.subscription.domain.PlanCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "plans")
public class PlanEntity {
    @Id private UUID id;
    @Enumerated(EnumType.STRING) @Column(nullable = false, unique = true, length = 30) private PlanCode code;
    @Column(nullable = false, length = 80) private String name;
    @Column(nullable = false) private boolean active;
    @Column(name = "price_cents") private Integer priceCents;
    @Column(nullable = false, length = 3) private String currency;
    @Column(name = "billing_period", length = 30) private String billingPeriod;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected PlanEntity() {}

    public PlanEntity(UUID id, PlanCode code, String name, boolean active, Integer priceCents, String currency,
            String billingPeriod, Instant createdAt) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.active = active;
        this.priceCents = priceCents;
        this.currency = currency;
        this.billingPeriod = billingPeriod;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public PlanCode getCode() { return code; }
    public String getName() { return name; }
    public boolean isActive() { return active; }
    public Integer getPriceCents() { return priceCents; }
    public String getCurrency() { return currency; }
    public String getBillingPeriod() { return billingPeriod; }
    public Instant getCreatedAt() { return createdAt; }
}
