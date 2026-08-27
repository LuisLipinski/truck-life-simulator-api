ALTER TABLE career_events
    ADD COLUMN operational_week INTEGER,
    ADD COLUMN effective_day VARCHAR(9);

UPDATE career_events AS event
SET operational_week = GREATEST(career.current_operational_week, 1),
    effective_day = CASE
        WHEN event.event_type = 'PROFILE_UPDATED' THEN NULL
        WHEN EXTRACT(ISODOW FROM event.effective_date) = 1 THEN 'MONDAY'
        WHEN EXTRACT(ISODOW FROM event.effective_date) = 2 THEN 'TUESDAY'
        WHEN EXTRACT(ISODOW FROM event.effective_date) = 3 THEN 'WEDNESDAY'
        WHEN EXTRACT(ISODOW FROM event.effective_date) = 4 THEN 'THURSDAY'
        WHEN EXTRACT(ISODOW FROM event.effective_date) = 5 THEN 'FRIDAY'
        WHEN EXTRACT(ISODOW FROM event.effective_date) = 6 THEN 'SATURDAY'
        WHEN EXTRACT(ISODOW FROM event.effective_date) = 7 THEN 'SUNDAY'
    END
FROM careers AS career
WHERE career.id = event.career_id;

ALTER TABLE career_events
    ALTER COLUMN operational_week SET NOT NULL,
    ADD CONSTRAINT chk_career_events_operational_week CHECK (operational_week > 0),
    ADD CONSTRAINT chk_career_events_effective_day CHECK (
        effective_day IS NULL
        OR effective_day IN ('MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY')
    ),
    ADD CONSTRAINT chk_career_events_effective_day_required CHECK (
        event_type = 'PROFILE_UPDATED' OR effective_day IS NOT NULL
    );

DROP INDEX IF EXISTS idx_career_events_career_effective_date;

ALTER TABLE career_events
    DROP COLUMN effective_date;

CREATE INDEX idx_career_events_career_week_recorded_at
    ON career_events (career_id, operational_week, recorded_at, id);
