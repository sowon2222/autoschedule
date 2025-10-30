-- ================================
-- 1. 사용자(User) 관련 테이블
-- ================================

CREATE TABLE "user" (
  id           BIGSERIAL PRIMARY KEY,            -- 고유 사용자 ID (자동 증가)
  email        TEXT UNIQUE NOT NULL,             -- 로그인용 이메일 (유니크)
  name         TEXT NOT NULL,                    -- 표시 이름
  timezone     TEXT NOT NULL DEFAULT 'Asia/Seoul', -- 사용자별 시간대 (스케줄 계산 시 사용)
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now()  -- 생성 시각 (UTC 기반)
);

-- ================================
-- 2. 팀(Team) 관련 테이블
-- ================================

CREATE TABLE team (
  id           BIGSERIAL PRIMARY KEY,            -- 팀 고유 ID
  name         TEXT NOT NULL,                    -- 팀 이름 (예: "AI개발팀")
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now() -- 생성 시각
);

-- ================================
-- 3. 팀 구성원(Team Member) 매핑 테이블
-- ================================

CREATE TABLE team_member (
  team_id      BIGINT NOT NULL REFERENCES team(id) ON DELETE CASCADE, -- 소속 팀
  user_id      BIGINT NOT NULL REFERENCES "user"(id) ON DELETE CASCADE, -- 팀원 사용자 ID
  role         TEXT NOT NULL DEFAULT 'member',     -- 역할: 'owner' | 'member' | 'viewer'
  PRIMARY KEY (team_id, user_id)                   -- 팀-사용자 쌍이 유일해야 함
);

-- ================================
-- 4. 근무 가능 시간(Work Hour) 테이블
-- ================================

CREATE TABLE work_hour (
  id           BIGSERIAL PRIMARY KEY,            -- 근무시간 레코드 ID
  team_id      BIGINT NOT NULL REFERENCES team(id) ON DELETE CASCADE, -- 팀 기준
  user_id      BIGINT REFERENCES "user"(id) ON DELETE SET NULL,       -- 해당 근무시간의 사용자 (없으면 NULL)
  dow          INT NOT NULL CHECK (dow BETWEEN 1 AND 7),              -- 요일 (1=월, ..., 7=일)
  start_min    INT NOT NULL,                    -- 하루 시작 시각(분단위, 예: 540 = 09:00)
  end_min      INT NOT NULL                     -- 하루 종료 시각(분단위, 예: 1080 = 18:00)
);

-- ================================
-- 5. 캘린더 이벤트(Calendar Event)
-- ================================

CREATE TABLE calendar_event (
  id           BIGSERIAL PRIMARY KEY,            -- 이벤트 ID
  team_id      BIGINT NOT NULL REFERENCES team(id) ON DELETE CASCADE, -- 소속 팀
  owner_id     BIGINT REFERENCES "user"(id),     -- 생성자(소유자)
  title        TEXT NOT NULL,                    -- 이벤트 제목
  starts_at    TIMESTAMPTZ NOT NULL,             -- 시작 시각
  ends_at      TIMESTAMPTZ NOT NULL,             -- 종료 시각
  fixed        BOOLEAN NOT NULL DEFAULT FALSE,   -- 고정 이벤트(회의 등)는 TRUE
  location     TEXT,                             -- 위치 정보(선택)
  attendees    TEXT,                             -- 참석자 ID 목록("3,5,9" 형태)
  notes        TEXT,                             -- 비고/메모
  version      BIGINT NOT NULL DEFAULT 0,        -- 낙관적 락(동시수정 감지)
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now(), -- 생성 시각
  updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()  -- 수정 시각
);

-- 팀별 시간대 조회 효율을 위한 인덱스
CREATE INDEX idx_event_team_start ON calendar_event(team_id, starts_at);
CREATE INDEX idx_event_team_updated ON calendar_event(team_id, updated_at);

-- ================================
-- 6. 할 일(Task) 테이블
-- ================================

CREATE TABLE task (
  id           BIGSERIAL PRIMARY KEY,            -- 태스크 ID
  team_id      BIGINT NOT NULL REFERENCES team(id) ON DELETE CASCADE, -- 팀 기준
  assignee_id  BIGINT REFERENCES "user"(id),     -- 담당자(없을 수도 있음)
  title        TEXT NOT NULL,                    -- 작업명
  duration_min INT NOT NULL,                     -- 예상 소요 시간(분 단위)
  due_at       TIMESTAMPTZ,                      -- 마감 시각 (NULL 가능)
  priority     INT NOT NULL DEFAULT 3,           -- 우선순위 (1=높음 ~ 5=낮음)
  splittable   BOOLEAN NOT NULL DEFAULT TRUE,    -- 분할 작업 가능 여부
  tags         TEXT,                             -- 태그(쉼표 구분: "deep,java")
  version      BIGINT NOT NULL DEFAULT 0,        -- 낙관적 락(동시수정 감지)
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now(), -- 생성 시각
  updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()  -- 수정 시각
);

-- 마감일/우선순위 기반 최적화 쿼리 성능용 인덱스
CREATE INDEX idx_task_team_due ON task(team_id, due_at, priority);

-- ================================
-- 7. 생성된 스케줄(Schedule) 테이블
-- ================================

CREATE TABLE schedule (
  id           BIGSERIAL PRIMARY KEY,            -- 스케줄 ID
  team_id      BIGINT NOT NULL REFERENCES team(id) ON DELETE CASCADE, -- 소속 팀
  range_start  DATE NOT NULL,                    -- 스케줄 시작 날짜(주간 시작일 등)
  range_end    DATE NOT NULL,                    -- 스케줄 종료 날짜
  score        INT,                              -- 최적화 점수(Soft Score)
  created_by   BIGINT REFERENCES "user"(id),     -- 생성자 ID
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now() -- 생성 시각
);

-- 주간 단위 스케줄 조회용 인덱스
CREATE INDEX idx_sched_team_range ON schedule(team_id, range_start, range_end);

-- ================================
-- 8. 스케줄 결과 할당(Assignment) 테이블
-- ================================

CREATE TABLE assignment (
  id           BIGSERIAL PRIMARY KEY,            -- 할당 ID
  schedule_id  BIGINT NOT NULL REFERENCES schedule(id) ON DELETE CASCADE, -- 소속 스케줄
  task_id      BIGINT REFERENCES task(id) ON DELETE SET NULL,  -- 연결된 작업(없을 수 있음)
  title        TEXT NOT NULL,                    -- 표시 제목 (이벤트/휴식 포함)
  starts_at    TIMESTAMPTZ NOT NULL,             -- 시작 시각
  ends_at      TIMESTAMPTZ NOT NULL,             -- 종료 시각
  source       TEXT NOT NULL,                    -- 출처: TASK | EVENT | BREAK | LUNCH
  slot_index   INT,                              -- 슬롯 인덱스(30분 단위 등)
  meta         JSONB,                            -- 추가 정보(점수, 이유, 태그 등)
  CONSTRAINT fk_assignment_sched FOREIGN KEY (schedule_id) REFERENCES schedule(id) ON DELETE CASCADE
);

-- 특정 스케줄 내 시간순 조회용 인덱스
CREATE INDEX idx_assign_sched_start ON assignment(schedule_id, starts_at);

-- ================================
-- 9. 편집 중 잠금(Slot Lock) — 옵션 (실시간 협업용)
-- ================================

CREATE TABLE slot_lock (
  slot_key     TEXT PRIMARY KEY,                 -- 고유 슬롯 키 (예: "team42:20251030:1000")
  user_id      BIGINT REFERENCES "user"(id),     -- 잠근 사용자
  expires_at   TIMESTAMPTZ NOT NULL              -- 만료 시각 (TTL)
);

-- ================================
-- 10. updated_at 자동 갱신 트리거
-- ================================

CREATE OR REPLACE FUNCTION touch_updated_at() RETURNS trigger AS $$
BEGIN
  NEW.updated_at = now();                        -- UPDATE 시 자동으로 현재 시각 갱신
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 이벤트/태스크 수정 시 updated_at 자동 반영
CREATE TRIGGER trg_event_touch
BEFORE UPDATE ON calendar_event
FOR EACH ROW EXECUTE FUNCTION touch_updated_at();

CREATE TRIGGER trg_task_touch
BEFORE UPDATE ON task
FOR EACH ROW EXECUTE FUNCTION touch_updated_at();
