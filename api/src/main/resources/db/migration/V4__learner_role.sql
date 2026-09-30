-- Authoring & admin: learner roles for admin-gated content editing.
ALTER TABLE learner ADD COLUMN role VARCHAR(16) NOT NULL DEFAULT 'LEARNER';
