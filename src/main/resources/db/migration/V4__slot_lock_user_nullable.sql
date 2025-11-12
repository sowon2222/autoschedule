-- ===========================================
-- V4 slot_lock.user_id nullable 복구
-- ===========================================

ALTER TABLE slot_lock
    ALTER COLUMN user_id DROP NOT NULL;


