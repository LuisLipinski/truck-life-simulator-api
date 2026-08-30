CREATE TABLE career_import_archives (
    import_operation_id UUID PRIMARY KEY,
    career_id UUID NOT NULL UNIQUE,
    source_version INTEGER NOT NULL,
    snapshot_json TEXT NOT NULL,
    archived_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_career_import_archives_operation
        FOREIGN KEY (import_operation_id) REFERENCES career_import_operations (id) ON DELETE CASCADE,
    CONSTRAINT fk_career_import_archives_career
        FOREIGN KEY (career_id) REFERENCES careers (id) ON DELETE CASCADE,
    CONSTRAINT chk_career_import_archives_source_version
        CHECK (source_version = 12),
    CONSTRAINT chk_career_import_archives_snapshot
        CHECK (BTRIM(snapshot_json) <> '' AND jsonb_typeof(snapshot_json::jsonb) = 'object')
);

CREATE INDEX idx_career_import_archives_archived_at
    ON career_import_archives (archived_at DESC, career_id);
