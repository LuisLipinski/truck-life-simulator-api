package com.luislipinski.trucklife.identity.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens")
public class RefreshTokenEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "user_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_refresh_tokens_user")
    )
    private UserEntity user;

    @Column(name = "family_id", nullable = false)
    private UUID familyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "parent_id",
            foreignKey = @ForeignKey(name = "fk_refresh_tokens_parent")
    )
    private RefreshTokenEntity parent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "replaced_by_id",
            foreignKey = @ForeignKey(name = "fk_refresh_tokens_replaced_by")
    )
    private RefreshTokenEntity replacedBy;

    @Column(name = "token_hash", nullable = false, unique = true, columnDefinition = "CHAR(64)")
    private String tokenHash;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "reuse_detected_at")
    private Instant reuseDetectedAt;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    protected RefreshTokenEntity() {
    }

    public RefreshTokenEntity(
            UUID id,
            UserEntity user,
            UUID familyId,
            RefreshTokenEntity parent,
            String tokenHash,
            Instant issuedAt,
            Instant expiresAt,
            Instant revokedAt,
            Instant reuseDetectedAt,
            String ipAddress,
            String userAgent
    ) {
        this.id = id;
        this.user = user;
        this.familyId = familyId;
        this.parent = parent;
        this.tokenHash = tokenHash;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
        this.revokedAt = revokedAt;
        this.reuseDetectedAt = reuseDetectedAt;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
    }

    public void markReplacedBy(RefreshTokenEntity replacement, Instant replacementTime) {
        this.replacedBy = replacement;
        this.revokedAt = replacementTime;
    }

    public UUID getId() {
        return id;
    }

    public UserEntity getUser() {
        return user;
    }

    public UUID getFamilyId() {
        return familyId;
    }

    public RefreshTokenEntity getParent() {
        return parent;
    }

    public RefreshTokenEntity getReplacedBy() {
        return replacedBy;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public Instant getReuseDetectedAt() {
        return reuseDetectedAt;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }
}
