CREATE TABLE trip_drafts (
    career_id UUID PRIMARY KEY,
    operational_week INTEGER NOT NULL,
    payload_json TEXT NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_trip_drafts_career
        FOREIGN KEY (career_id) REFERENCES careers(id) ON DELETE CASCADE,
    CONSTRAINT chk_trip_drafts_week
        CHECK (operational_week > 0),
    CONSTRAINT chk_trip_drafts_payload
        CHECK (char_length(payload_json) BETWEEN 2 AND 32768)
);
