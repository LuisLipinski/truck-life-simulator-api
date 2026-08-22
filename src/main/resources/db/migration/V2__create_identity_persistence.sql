CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(320) NOT NULL,
    normalized_email VARCHAR(320) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    status VARCHAR(30) NOT NULL,
    role VARCHAR(30) NOT NULL DEFAULT 'USER',
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    email_verified_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_login_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uq_users_normalized_email UNIQUE (normalized_email),
    CONSTRAINT chk_users_email_trimmed CHECK (email = BTRIM(email) AND email <> ''),
    CONSTRAINT chk_users_normalized_email CHECK (
        normalized_email = LOWER(BTRIM(email))
    ),
    CONSTRAINT chk_users_password_hash_not_blank CHECK (BTRIM(password_hash) <> ''),
    CONSTRAINT chk_users_display_name_length CHECK (
        CHAR_LENGTH(BTRIM(display_name)) BETWEEN 2 AND 120
    ),
    CONSTRAINT chk_users_status CHECK (
        status IN ('PENDING_VERIFICATION', 'ACTIVE', 'LOCKED', 'DISABLED')
    ),
    CONSTRAINT chk_users_role CHECK (role IN ('USER', 'ADMIN')),
    CONSTRAINT chk_users_email_verification CHECK (
        email_verified = (email_verified_at IS NOT NULL)
    ),
    CONSTRAINT chk_users_updated_at CHECK (updated_at >= created_at),
    CONSTRAINT chk_users_last_login_at CHECK (
        last_login_at IS NULL OR last_login_at >= created_at
    )
);

CREATE INDEX idx_users_status ON users (status);

CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    family_id UUID NOT NULL,
    parent_id UUID,
    replaced_by_id UUID,
    token_hash CHAR(64) NOT NULL,
    issued_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE,
    reuse_detected_at TIMESTAMP WITH TIME ZONE,
    ip_address VARCHAR(64),
    user_agent VARCHAR(500),
    CONSTRAINT fk_refresh_tokens_user
        FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_refresh_tokens_parent
        FOREIGN KEY (parent_id) REFERENCES refresh_tokens (id),
    CONSTRAINT fk_refresh_tokens_replaced_by
        FOREIGN KEY (replaced_by_id) REFERENCES refresh_tokens (id),
    CONSTRAINT uq_refresh_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT chk_refresh_tokens_hash CHECK (
        token_hash ~ '^[0-9a-f]{64}$'
    ),
    CONSTRAINT chk_refresh_tokens_expiration CHECK (expires_at > issued_at),
    CONSTRAINT chk_refresh_tokens_revocation CHECK (
        revoked_at IS NULL OR revoked_at >= issued_at
    ),
    CONSTRAINT chk_refresh_tokens_reuse_detection CHECK (
        reuse_detected_at IS NULL OR reuse_detected_at >= issued_at
    ),
    CONSTRAINT chk_refresh_tokens_parent_not_self CHECK (
        parent_id IS NULL OR parent_id <> id
    ),
    CONSTRAINT chk_refresh_tokens_replaced_by_not_self CHECK (
        replaced_by_id IS NULL OR replaced_by_id <> id
    )
);

CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_family_id ON refresh_tokens (family_id);
CREATE INDEX idx_refresh_tokens_parent_id
    ON refresh_tokens (parent_id)
    WHERE parent_id IS NOT NULL;
CREATE INDEX idx_refresh_tokens_replaced_by_id
    ON refresh_tokens (replaced_by_id)
    WHERE replaced_by_id IS NOT NULL;
CREATE INDEX idx_refresh_tokens_expires_at ON refresh_tokens (expires_at);
CREATE INDEX idx_refresh_tokens_active_user
    ON refresh_tokens (user_id, expires_at)
    WHERE revoked_at IS NULL;

CREATE TABLE user_action_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    purpose VARCHAR(30) NOT NULL,
    token_hash CHAR(64) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    used_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_user_action_tokens_user
        FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uq_user_action_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT chk_user_action_tokens_purpose CHECK (
        purpose IN ('EMAIL_VERIFICATION', 'PASSWORD_RESET')
    ),
    CONSTRAINT chk_user_action_tokens_hash CHECK (
        token_hash ~ '^[0-9a-f]{64}$'
    ),
    CONSTRAINT chk_user_action_tokens_expiration CHECK (expires_at > created_at),
    CONSTRAINT chk_user_action_tokens_used_at CHECK (
        used_at IS NULL OR (used_at >= created_at AND used_at <= expires_at)
    )
);

CREATE INDEX idx_user_action_tokens_user_purpose
    ON user_action_tokens (user_id, purpose);
CREATE INDEX idx_user_action_tokens_expires_at ON user_action_tokens (expires_at);
CREATE INDEX idx_user_action_tokens_active
    ON user_action_tokens (user_id, purpose, expires_at)
    WHERE used_at IS NULL;
