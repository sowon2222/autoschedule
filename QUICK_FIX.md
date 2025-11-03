# 빠른 해결 방법

## 문제: 애플리케이션 실행이 5분 이상 걸림

## 원인
Flyway가 DB 권한 문제로 타임아웃 대기 중

## 빠른 해결 (2가지 방법)

### 방법 1: Flyway 임시 비활성화 (가장 빠름)

`application.properties`에 이미 설정했습니다:
```properties
spring.flyway.enabled=false
```

이제 애플리케이션을 **중단하고 다시 실행**하세요:
```bash
# Ctrl+C로 중단 후
./gradlew bootRun
```

**주의**: 이 방법은 Flyway 마이그레이션을 실행하지 않으므로, 테이블이 이미 생성되어 있어야 합니다.

---

### 방법 2: DB 권한 부여 후 Flyway 활성화 (정석)

1. **애플리케이션 중단** (Ctrl+C)

2. **PostgreSQL 권한 부여**:
```bash
psql -U postgres -d autoschedule
```

```sql
ALTER SCHEMA public OWNER TO autosched;
```

3. **Flyway 다시 활성화** (`application.properties`):
```properties
spring.flyway.enabled=true
```

4. **애플리케이션 재실행**:
```bash
./gradlew bootRun
```

---

## 어떤 방법을 사용할까요?

- **방법 1**: 테이블이 이미 있으면 바로 사용 가능 (빠름)
- **방법 2**: DB 권한 문제를 해결 (근본 해결)

선택해주세요!


