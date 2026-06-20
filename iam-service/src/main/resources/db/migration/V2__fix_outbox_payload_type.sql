ALTER TABLE iam.outbox_events ALTER COLUMN payload TYPE TEXT USING payload::TEXT;
