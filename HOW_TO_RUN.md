# 프로젝트 실행 방법

## 🚀 실행 방법 3가지

### 1. Gradle로 실행 (가장 간단)

터미널에서 실행:
```bash
./gradlew bootRun
```

Windows PowerShell:
```powershell
.\gradlew.bat bootRun
```

**장점**: 바로 실행 가능, 자동 리로드(DevTools)

---

### 2. IDE에서 실행 (Eclipse)

1. `SbbApplication.java` 파일 열기
2. 우클릭 → **Run As** → **Java Application**
3. 또는 메인 메서드에서 `Ctrl + F11`

**장점**: 디버깅 편함, 로그 보기 쉬움

---

### 3. JAR 파일 빌드 후 실행

#### 빌드
```bash
./gradlew build
```

#### 실행
```bash
java -jar build/libs/sbb-0.0.1-SNAPSHOT.jar
```

**장점**: 실제 배포 환경과 유사

---

## 📋 실행 전 확인사항

### 필수 체크리스트

1. **PostgreSQL 실행 중인지 확인**
   ```bash
   # PostgreSQL 서비스 상태 확인 (Windows)
   sc query postgresql-x64-18
   
   # 또는 psql 접속 테스트
   psql -U autosched -d autoschedule
   ```

2. **데이터베이스 준비**
   - DB 이름: `autoschedule`
   - 사용자: `autosched`
   - 비밀번호: `postgres`
   - 포트: `5432`

3. **포트 확인**
   - 기본 포트: `8080`
   - 이미 사용 중이면 `application.properties`에서 변경:
     ```properties
     server.port=8081
     ```

---

## ✅ 실행 확인

### 성공 시 로그 예시
```
Started SbbApplication in 2.345 seconds
```

### API 테스트
브라우저에서 접속:
```
http://localhost:8080
```

또는 API 엔드포인트 테스트:
```bash
curl http://localhost:8080/api/auth/signup
```

---

## ⚠️ 에러 발생 시

### 1. PostgreSQL 연결 실패
```
Connection refused 또는 Connection timeout
```
**해결**: PostgreSQL 서비스 시작 확인

### 2. 포트 충돌
```
Port 8080 is already in use
```
**해결**: `application.properties`에서 포트 변경

### 3. Flyway 마이그레이션 실패
```
Flyway migration failed
```
**해결**: DB 권한 확인, 또는 기존 스키마 확인

### 4. JWT 관련 에러
```
JwtUtil cannot be resolved
```
**해결**: `./gradlew clean build --refresh-dependencies`

---

## 🔄 개발 모드 (자동 리로드)

`spring-boot-devtools`가 포함되어 있어서:
- 코드 수정 후 저장하면 자동 리로드됨
- 컴파일만 하면 재시작 안 해도 됨

---

## 📝 실행 후 할 일

1. **회원가입 테스트**
   ```
   POST http://localhost:8080/api/auth/signup
   ```

2. **로그인 테스트**
   ```
   POST http://localhost:8080/api/auth/login
   ```

3. **API 문서 확인** (Swagger)
   ```
   http://localhost:8080/swagger-ui.html
   ```

---

## 💡 추천 실행 방법

**개발 중**: Gradle `bootRun` 또는 IDE에서 실행
**배포**: JAR 파일로 빌드 후 실행

