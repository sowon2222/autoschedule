-- ===========================================
-- V3 Slot lock 테이블 + TTL 최적화 쿼리
-- ===========================================

-- 기본 테이블이 존재하는지 확인 (처음 환경에서 안전)
CREATE TABLE IF NOT EXISTS slot_lock (
    slot_key   TEXT PRIMARY KEY,
    user_id    BIGINT NOT NULL REFERENCES "user"(id) ON DELETE CASCADE,
    expires_at TIMESTAMPTZ NOT NULL
);

-- 테이블이 이미 존재하는 경우 널 가능성 제한
ALTER TABLE slot_lock
    ALTER COLUMN user_id SET NOT NULL,
    ALTER COLUMN expires_at SET NOT NULL;

-- 만료 시간 인덱스
CREATE INDEX IF NOT EXISTS idx_slot_lock_expires_at
    ON slot_lock (expires_at);

