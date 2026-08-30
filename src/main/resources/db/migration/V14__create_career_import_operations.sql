CREATE TABLE career_import_operations (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    operation_id UUID NOT NULL,
    source_career_id VARCHAR(200) NOT NULL,
    game_id VARCHAR(10) NOT NULL,
    source_version INTEGER NOT NULL,
    snapshot_sha256 VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,
    imported_career_id UUID,
    result_summary_json TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_career_import_operations_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_career_import_operations_career
        FOREIGN KEY (imported_career_id) REFERENCES careers (id),
    CONSTRAINT uq_career_import_operations_user_operation
        UNIQUE (user_id, operation_id),
    CONSTRAINT uq_career_import_operations_user_source
        UNIQUE (user_id, game_id, source_career_id),
    CONSTRAINT chk_career_import_operations_game
        CHECK (game_id IN ('ATS','ETS2')),
    CONSTRAINT chk_career_import_operations_source_version
        CHECK (source_version = 12),
    CONSTRAINT chk_career_import_operations_hash
        CHECK (snapshot_sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT chk_career_import_operations_status
        CHECK (status IN ('PROCESSING','COMPLETED')),
    CONSTRAINT chk_career_import_operations_completion
        CHECK (
            (status = 'PROCESSING' AND imported_career_id IS NULL AND result_summary_json IS NULL)
            OR
            (status = 'COMPLETED' AND imported_career_id IS NOT NULL AND result_summary_json IS NOT NULL)
        ),
    CONSTRAINT chk_career_import_operations_summary
        CHECK (
            result_summary_json IS NULL
            OR (BTRIM(result_summary_json) <> '' AND jsonb_typeof(result_summary_json::jsonb) = 'object')
        )
);

CREATE INDEX idx_career_import_operations_user_created
    ON career_import_operations (user_id, created_at DESC, id DESC);

CREATE INDEX idx_career_import_operations_imported_career
    ON career_import_operations (imported_career_id)
    WHERE imported_career_id IS NOT NULL;
