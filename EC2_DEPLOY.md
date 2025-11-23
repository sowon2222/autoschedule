# 🚀 AWS EC2 배포 가이드

EC2 인스턴스에 AutoSchedule을 배포하는 단계별 가이드입니다.

## 📋 사전 준비사항

- [x] AWS EC2 인스턴스 생성 완료
- [x] EC2 인스턴스에 SSH 접속 가능
- [x] EC2 보안 그룹 설정 (포트 22, 80, 443, 8080 열기)
- [ ] 도메인 (선택사항, IP로도 접속 가능)

**변수 설정 (이 문서에서 사용할 값):**
- `EC2_IP`: EC2 퍼블릭 IP 주소 (예: `http://54.206.65.33/`)
- `KEY_PATH`: SSH 키 파일 경로 (예: `C:\Users\sowon\Downloads\autoschedule-keypair.pem`)
- `DB_PASSWORD`: PostgreSQL 데이터베이스 비밀번호

---

## 1단계: EC2 서버 초기 설정 (최초 1회만)

### SSH 접속

```powershell
# Windows (PowerShell)
ssh -i "C:\Users\sowon\Downloads\autoschedule-keypair.pem" ubuntu@3.106.203.246
```

### 시스템 업데이트

```bash
sudo apt update
sudo apt upgrade -y
```

### Java 17 설치

```bash
sudo apt install openjdk-17-jdk -y
java -version  # 확인: openjdk version "17.0.x"
```

### PostgreSQL 설치 및 설정 / RDS 는 ㅠ 알바 퇴직금 받고 해보기로 

```bash
# PostgreSQL 설치
sudo apt install postgresql postgresql-contrib -y

# PostgreSQL 시작
sudo systemctl start postgresql
sudo systemctl enable postgresql

# 데이터베이스 및 사용자 생성
sudo -u postgres psql
```

PostgreSQL 콘솔에서 실행:

```sql
CREATE DATABASE autoschedule;
CREATE USER autosched WITH PASSWORD '$DB_PASSWORD';
GRANT ALL PRIVILEGES ON DATABASE autoschedule TO autosched;

-- PostgreSQL 15+ 버전에서 public 스키마 권한 부여 (필수!)
\c autoschedule
GRANT ALL ON SCHEMA public TO autosched;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO autosched;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO autosched;

\q
```

**중요**: PostgreSQL 15 이상 버전에서는 `public` 스키마에 대한 권한을 명시적으로 부여해야 합니다.

### Nginx 설치 (선택사항 - 프론트엔드를 별도로 서빙하는 경우)

```bash
sudo apt install nginx -y
sudo systemctl start nginx
sudo systemctl enable nginx
```

**참고**: 현재는 프론트엔드가 JAR 파일 안에 포함되어 있어 Nginx 없이도 동작합니다. Nginx는 리버스 프록시나 SSL 설정이 필요한 경우에만 사용하세요.

---

## 2단계: 백엔드 서비스 설정 (최초 1회만)

### systemd 서비스 파일 생성

```bash
sudo nano /etc/systemd/system/autoschedule.service
```

다음 내용 입력 (실제 값으로 변경): 54.206.65.33

```ini
[Unit]
Description=AutoSchedule Spring Boot Application
After=network.target postgresql.service

[Service]
Type=simple
User=ubuntu
WorkingDirectory=/home/ubuntu
Environment="SPRING_PROFILES_ACTIVE=prod"
Environment="SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/autoschedule"
Environment="SPRING_DATASOURCE_USERNAME=autosched"
Environment="SPRING_DATASOURCE_PASSWORD=$DB_PASSWORD"
Environment="APP_WEBSOCKET_ALLOWED_ORIGINS=http://$EC2_IP:8080,https://$EC2_IP:8080,http://$EC2_IP,https://$EC2_IP"
Environment="APP_CORS_ALLOWED_ORIGINS=http://$EC2_IP:8080,https://$EC2_IP:8080,http://$EC2_IP,https://$EC2_IP"
# JVM 성능 최적화 옵션
ExecStart=/usr/bin/java -Xms512m -Xmx1024m -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -Djava.security.egd=file:/dev/./urandom -jar /home/ubuntu/app.jar
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
```

IP 주소가 바뀌었을 때는 $EC2_IP 부분을 새로운 IP 주소로 바꿔주어야 합니다. 
그 다음 :
```
- 서비스 리로드
sudo systemctl daemon-reload

- 서비스 재시작
sudo systemctl restart autoschedule
```

**JVM 옵션 설명**:
- `-Xms512m`: 초기 힙 메모리 512MB
- `-Xmx1024m`: 최대 힙 메모리 1GB (EC2 인스턴스 메모리에 맞게 조정)
- `-XX:+UseG1GC`: G1 가비지 컬렉터 사용
- `-XX:MaxGCPauseMillis=200`: GC 일시정지 시간 최대 200ms

**중요**: 
- `$EC2_IP`를 실제 EC2 퍼블릭 IP로 변경
- `$DB_PASSWORD`를 실제 데이터베이스 비밀번호로 변경

### 서비스 활성화

```bash
# 서비스 리로드
sudo systemctl daemon-reload

# 부팅 시 자동 시작
sudo systemctl enable autoschedule
```

---

## 3단계: 배포 프로세스 (코드 업데이트 시마다)

### 로컬에서 빌드 및 업로드

**1. 프론트엔드 빌드 (상대 경로로 설정)**

```powershell
# 프로젝트 루트 디렉토리에서
cd frontend

# .env.production 파일 확인 (VITE_API_BASE_URL= 로 설정되어 있어야 함)
# 빌드
npm run build

# 빌드된 파일을 static 폴더로 복사
node copy-build.cjs
```

**2. 백엔드 JAR 빌드**

```powershell
# 프로젝트 루트 디렉토리로 돌아가기
cd ..

# 클린 빌드
.\gradlew.bat clean bootJar
```

**3. EC2에 JAR 파일 업로드**

```powershell
scp -i $KEY_PATH build\libs\sbb-0.0.1-SNAPSHOT.jar ubuntu@$EC2_IP:~/app.jar
```

**4. EC2에서 서비스 재시작**

```bash
# SSH 접속 후
sudo systemctl restart autoschedule

# 상태 확인
sudo systemctl status autoschedule

# 로그 확인 (필요시)
sudo journalctl -u autoschedule -f
```

---

## 4단계: Nginx 설정 (선택사항)

프론트엔드가 JAR에 포함되어 있어 기본적으로는 Nginx가 필요 없습니다. 하지만 리버스 프록시나 SSL 설정이 필요한 경우:

### Nginx 설정 파일 생성

```bash
sudo nano /etc/nginx/sites-available/autoschedule
```

다음 내용 입력:

```nginx
server {
    listen 80;
    server_name _;  # 모든 호스트 이름 허용 (IP 주소 포함)

    # 백엔드 API 프록시
    location /api {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # WebSocket 프록시
    location /ws {
        proxy_pass http://localhost:8080;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # 프론트엔드 (JAR에서 서빙하므로 프록시)
    location / {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

### 설정 활성화

```bash
# 심볼릭 링크 생성
sudo ln -s /etc/nginx/sites-available/autoschedule /etc/nginx/sites-enabled/

# 기본 설정 제거 (선택사항)
sudo rm /etc/nginx/sites-enabled/default

# Nginx 설정 테스트
sudo nginx -t

# Nginx 재시작
sudo systemctl restart nginx
```

---

## 5단계: SSL 인증서 설정 (HTTPS, 선택사항)

도메인이 있는 경우 Let's Encrypt로 무료 SSL 인증서 발급:

```bash
# Certbot 설치
sudo apt install certbot python3-certbot-nginx -y

# SSL 인증서 발급
sudo certbot --nginx -d your-domain.com -d www.your-domain.com

# 자동 갱신 테스트
sudo certbot renew --dry-run
```

---

## 6단계: 방화벽 설정

### AWS 보안 그룹 설정

AWS 콘솔에서 EC2 인스턴스의 보안 그룹에 다음 규칙 추가:

| Type | Protocol | Port Range | Source |
|------|----------|------------|--------|
| SSH | TCP | 22 | My IP |
| HTTP | TCP | 80 | 0.0.0.0/0 |
| HTTPS | TCP | 443 | 0.0.0.0/0 |
| Custom TCP | TCP | 8080 | 0.0.0.0/0 (또는 내부만) |

### UFW 방화벽 설정 (서버 내부)

```bash
# UFW 방화벽 활성화
sudo ufw enable

# HTTP, HTTPS, SSH 허용
sudo ufw allow 22/tcp    # SSH
sudo ufw allow 80/tcp    # HTTP
sudo ufw allow 443/tcp   # HTTPS

# 상태 확인
sudo ufw status
```

---

## 7단계: 접속 확인

### 백엔드 확인

```bash
# 서비스 상태 확인
sudo systemctl status autoschedule

# 로그 확인
sudo journalctl -u autoschedule -n 50

# API 테스트
curl http://localhost:8080/api/auth/hello
```

### 프론트엔드 확인

브라우저에서 접속:
- `http://$EC2_IP:8080` (Spring Boot 직접 접속)
- 또는 `http://$EC2_IP` (Nginx를 통해 접속하는 경우)

---

## 📝 관리 명령어

### 백엔드 서비스 관리

```bash
# 서비스 시작
sudo systemctl start autoschedule

# 서비스 중지
sudo systemctl stop autoschedule

# 서비스 재시작
sudo systemctl restart autoschedule

# 상태 확인
sudo systemctl status autoschedule

# 로그 확인 (실시간)
sudo journalctl -u autoschedule -f

# 로그 확인 (최근 100줄)
sudo journalctl -u autoschedule -n 100
```

### Nginx 관리

```bash
# 재시작
sudo systemctl restart nginx

# 상태 확인
sudo systemctl status nginx

# 설정 테스트
sudo nginx -t
```

---

## 🔧 문제 해결

### 백엔드가 시작되지 않으면

```bash
# 로그 확인
sudo journalctl -u autoschedule -n 50

# 포트 사용 확인
sudo netstat -tulpn | grep 8080

# Java 프로세스 확인
ps aux | grep java
```

### 데이터베이스 연결 오류

```bash
# PostgreSQL 상태 확인
sudo systemctl status postgresql

# PostgreSQL 접속 테스트
sudo -u postgres psql -c "\l"
psql -U autosched -d autoschedule -h localhost
```

### Flyway "permission denied for schema public" 오류

PostgreSQL 15 이상 버전에서 발생하는 오류입니다:

```bash
# PostgreSQL에 접속
sudo -u postgres psql

# 다음 SQL 명령 실행
\c autoschedule
GRANT ALL ON SCHEMA public TO autosched;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO autosched;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO autosched;
\q
```

그 후 백엔드 서비스를 재시작:

```bash
sudo systemctl restart autoschedule
```

### 인터넷에서 IP 주소로 접속이 안 되는 경우

**1. AWS 보안 그룹 확인 (가장 중요!)**

AWS 콘솔에서 EC2 인스턴스의 보안 그룹을 확인하고 다음 규칙이 있는지 확인:

| Type | Protocol | Port Range | Source |
|------|----------|------------|--------|
| HTTP | TCP | 80 | 0.0.0.0/0 |
| Custom TCP | TCP | 8080 | 0.0.0.0/0 |

없으면 추가:
1. EC2 콘솔 → 인스턴스 선택 → 보안 탭 → 보안 그룹 클릭
2. 인바운드 규칙 편집 → 규칙 추가
3. Type: HTTP 또는 Custom TCP, Port: 80 또는 8080, Source: 0.0.0.0/0
4. 저장

**2. 서비스 상태 확인**

```bash
sudo systemctl status autoschedule
```

**3. 포트 리스닝 확인**

```bash
sudo netstat -tulpn | grep 8080
```

---

## 🚀 빠른 배포 체크리스트

코드 업데이트 후 배포할 때:

- [ ] 로컬에서 프론트엔드 빌드 (`cd frontend && npm run build && node copy-build.cjs`)
- [ ] 로컬에서 백엔드 JAR 빌드 (`.\gradlew.bat clean bootJar`)
- [ ] EC2에 JAR 업로드 (`scp ...`)
- [ ] EC2에서 서비스 재시작 (`sudo systemctl restart autoschedule`)
- [ ] 접속 확인 (`http://$EC2_IP:8080`)

---

## 📌 주요 변경사항

- **프론트엔드가 JAR에 포함**: 별도 Nginx 설정 없이도 동작
- **상대 경로 API**: `VITE_API_BASE_URL`을 빈 문자열로 설정하여 로컬/EC2 모두 동작
- **SPA 라우팅**: React Router 경로가 자동으로 `index.html`로 포워딩됨
