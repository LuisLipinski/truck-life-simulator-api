package com.luislipinski.trucklife.subscription.persistence;

import com.luislipinski.trucklife.subscription.domain.SubscriptionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "subscriptions")
public class SubscriptionEntity {
    @Id private UUID id;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(name = "plan_id", nullable = false) private UUID planId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private SubscriptionStatus status;
    @Column(name = "started_at") private Instant startedAt;
    @Column(name = "current_period_start") private Instant currentPeriodStart;
    @Column(name = "current_period_end") private Instant currentPeriodEnd;
    @Column(name = "cancel_at_period_end", nullable = false) private boolean cancelAtPeriodEnd;
    @Column(name = "canceled_at") private Instant canceledAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version @Column(nullable = false) private long version;

    protected SubscriptionEntity() {}

    public SubscriptionEntity(UUID id, UUID userId, UUID planId, SubscriptionStatus status, Instant startedAt,
            Instant currentPeriodStart, Instant currentPeriodEnd, boolean cancelAtPeriodEnd, Instant canceledAt,
            Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.userId = userId;
        this.planId = planId;
        this.status = status;
        this.startedAt = startedAt;
        this.currentPeriodStart = currentPeriodStart;
        this.currentPeriodEnd = currentPeriodEnd;
        this.cancelAtPeriodEnd = cancelAtPeriodEnd;
        this.canceledAt = canceledAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public UUID getPlanId() { return planId; }
    public SubscriptionStatus getStatus() { return status; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCurrentPeriodStart() { return currentPeriodStart; }
    public Instant getCurrentPeriodEnd() { return currentPeriodEnd; }
    public boolean isCancelAtPeriodEnd() { return cancelAtPeriodEnd; }
    public Instant getCanceledAt() { return canceledAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
