# PostgreSQL 권한 문제 해결

## 문제
```
오류: public 스키마(schema) 접근 권한 없음
```

`autosched` 사용자가 `public` 스키마에 접근할 수 없어서 Flyway 마이그레이션이 실패합니다.

## 해결 방법

PostgreSQL에 **postgres 사용자**로 접속해서 권한을 부여해야 합니다.

### 1. PostgreSQL 관리자로 접속

```bash
psql -U postgres -d autoschedule
```

또는 Windows에서:
```powershell
psql -U postgres -d autoschedule
```

### 2. 권한 부여 명령어 실행

PostgreSQL 프롬프트에서 다음 명령어들을 실행하세요:

```sql
-- public 스키마에 대한 모든 권한 부여
GRANT ALL PRIVILEGES ON SCHEMA public TO autosched;

-- 기존 객체들에 대한 권한도 부여
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO autosched;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO autosched;

-- 앞으로 생성될 객체들에 대한 기본 권한 부여
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO autosched;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO autosched;

-- 또는 더 간단하게: public 스키마의 소유자를 변경 (권장)
ALTER SCHEMA public OWNER TO autosched;
```

### 3. 확인

권한이 제대로 부여되었는지 확인:

```sql
-- 스키마 권한 확인
\dn+

-- 사용자 확인
\du autosched
```

### 4. 애플리케이션 재실행

권한 부여 후 애플리케이션을 다시 실행:

```bash
./gradlew bootRun
```

## 대안: postgres 사용자로 직접 실행

만약 권한 부여가 어렵다면, `application.properties`에서 postgres 사용자를 사용할 수도 있습니다:

```properties
spring.datasource.username=postgres
spring.datasource.password=your_postgres_password
```

하지만 보안상 별도 사용자를 만드는 것이 좋습니다.

