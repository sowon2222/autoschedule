-- 반복 일정 기능 추가
ALTER TABLE calendar_event 
  ADD COLUMN recurrence_type VARCHAR(20),  -- 'DAILY', 'WEEKLY', 'MONTHLY', 'YEARLY', NULL(반복 없음)
  ADD COLUMN recurrence_end_date TIMESTAMPTZ;  -- 반복 종료일 (NULL이면 무제한)

-- 반복 일정 조회를 위한 인덱스
CREATE INDEX idx_event_recurrence 
    ON calendar_event(recurrence_type, recurrence_end_date) 
    WHERE recurrence_type IS NOT NULL;

