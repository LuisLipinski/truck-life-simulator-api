CREATE TABLE career_events (
    id UUID PRIMARY KEY,
    career_id UUID NOT NULL,
    event_type VARCHAR(40) NOT NULL,
    effective_date DATE NOT NULL,
    recorded_at TIMESTAMP WITH TIME ZONE NOT NULL,
    changes_json TEXT NOT NULL,
    CONSTRAINT fk_career_events_career
        FOREIGN KEY (career_id) REFERENCES careers (id) ON DELETE CASCADE,
    CONSTRAINT chk_career_events_type CHECK (
        event_type IN ('PROFILE_UPDATED', 'EMPLOYER_CHANGED', 'BASE_CHANGED')
    ),
    CONSTRAINT chk_career_events_changes_json CHECK (
        BTRIM(changes_json) <> ''
        AND jsonb_typeof(changes_json::jsonb) = 'object'
    )
);

CREATE INDEX idx_career_events_career_effective_date
    ON career_events (career_id, effective_date, recorded_at, id);
