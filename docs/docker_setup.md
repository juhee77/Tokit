# 🐳 TOKIT — Docker 인프라 구축 및 가이드북

이 문서는 **TOKIT 토큰증권(STO) 플랫폼**의 핵심 오프체인 인프라(PostgreSQL, Redis, RabbitMQ)를 Docker Compose를 통해 신속하게 세팅하고 모니터링하는 방법을 다룹니다.

---

## 📌 1. 사전 요구사항 (Prerequisites)

Docker 기동 전에 로컬 PC 환경에 맞게 설치가 완료되었는지 확인하십시오.
- **Docker Desktop** (macOS / Windows): [Docker 공식 홈페이지](https://www.docker.com/products/docker-desktop/)에서 OS 버전에 맞는 최신 버전을 내려받아 설치합니다.
- **Terminal CLI 확인**: Terminal에서 아래 명령이 실행되는지 확인합니다.
  ```bash
  docker --version
  docker compose version
  ```

---

## 🚀 2. Docker Compose 인프라 기동

프로젝트 루트 디렉토리에서 아래 명령어를 실행하여 컨테이너들을 백그라운드 모드(`-d`)로 실행합니다.

```bash
docker compose up -d
```

### 실행되는 컨테이너 구성 정보

| 서비스명 | 이미지 | 외부 포트 | 내부 포트 | 주 용도 |
| :--- | :--- | :--- | :--- | :--- |
| **PostgreSQL 17** | `postgres:17-alpine` | `5432` | `5432` | 주문/체결 자산 원장 영속성 저장소 |
| **Redis 7** | `redis:7-alpine` | `6379` | `6379` | 초고속 실시간 호가창(Order Book) 인메모리 캐시 |
| **RabbitMQ 4** | `rabbitmq:4-management` | `5672` (AMQP)<br>`15672` (Admin) | `5672`<br>`15672` | 매칭 엔진 비동기 이벤트 큐 버퍼 |

---

## 📊 3. 인프라 모니터링 및 상태 확인

### 1) 컨테이너 구동 상태 조회
컨테이너들이 올바르게 기동되었는지 다음 명령어로 조회가 가능합니다.
```bash
docker compose ps
```
모든 서비스의 `STATUS`가 `Up` 또는 `running`으로 표시되어야 합니다.

### 2) 실시간 로그 분석
동작 중 예기치 못한 에러나 연결 지연을 추적하려면 로그 출력을 확인합니다.
```bash
# 전체 서비스 실시간 로그
docker compose logs -f

# 특정 서비스(예: PostgreSQL)의 최근 100라인 로그 확인
docker compose logs --tail=100 -f postgres
```

### 3) RabbitMQ 관리자 웹 콘솔 접속
RabbitMQ는 GUI 기반의 관리용 Management UI 플러그인을 제공합니다.
- **URL**: [http://localhost:15672](http://localhost:15672)
- **ID / Password**: `tokit` / `tokit`
- **핵심 모니터링 포인트**:
  - `Exchanges` 탭: `tokit.exchange` Direct Exchange 바인딩 확인.
  - `Queues` 탭: `tokit.order.queue`에 주문이 유입되어 컨슈머가 실시간 처리하고 있는지 메시지 수 추이 확인.

---

## 🛠️ 4. 예외 발생 시 트러블슈팅 (FAQ)

### Q1. "port is already allocated" 또는 "FATAL: role \"tokit\" does not exist" (로컬 포트 충돌)
로컬 머신에 이미 PostgreSQL, Redis 또는 RabbitMQ가 서비스(Homebrew 등)로 켜져 있을 때 주로 발생합니다.
특히 macOS에서 로컬 Postgres가 켜져 있으면, 스프링 부트가 Docker 컨테이너 대신 로컬 Postgres(예: v14.0)에 접속을 시도하여 계정이 없다는 에러(`FATAL: role "tokit" does not exist`)나 DB 버젼 불일치 경고를 유발합니다.

- **해결 방안 1 (로컬 서비스 정지)**:
  - macOS PostgreSQL 정지 (버전 명시):
    ```bash
    brew services stop postgresql@14
    # 또는 기본 postgresql 서비스 정지
    brew services stop postgresql
    ```
  - macOS Redis 정지: `brew services stop redis`
- **해결 방안 2 (Docker 외부 포트 변경)**:
  - [docker-compose.yml](file:///Users/juhee/IdeaProjects/TOKIT/docker-compose.yml) 파일의 `ports:` 항목에서 좌측 값(로컬 포트)을 변경하십시오. (예: `5432:5432` $\rightarrow$ `5433:5432`)

### Q2. 인프라를 완전히 초기화(데이터 리셋)하고 싶습니다.
PostgreSQL 데이터베이스 데이터가 꼬였거나 다시 최초 상태로 원장 데이터를 세팅하고 싶다면 볼륨을 포함하여 완전 삭제 후 기동해야 합니다.
```bash
# 컨테이너 및 연동 볼륨 완전 파괴
docker compose down -v

# 재기동
docker compose up -d
```
> [!CAUTION]
> `-v` 옵션을 주면 PostgreSQL 및 Redis 데이터 볼륨이 물리적으로 완전히 삭제되어 복구가 불가능합니다. 개발 모드에서만 사용하십시오.

### Q3. "FATAL: role \"tokit\" does not exist" (DB 계정/초기화 꼬임) 에러 발생 시
PostgreSQL 컨테이너가 생성될 때 기존의 오래된 볼륨 잔해가 남아 있어 `docker-compose.yml`에 지정한 `tokit` 계정과 데이터베이스가 정상적으로 초기화 및 생성되지 못해 발생합니다.
- **해결 방안**:
  1. 기동 중인 컨테이너와 볼륨을 강제로 삭제하여 데이터베이스 설정을 완전히 비웁니다:
     ```bash
     docker compose down -v
     ```
  2. 컨테이너를 백그라운드로 재기동하여 계정과 DB가 신규 생성되도록 초기화합니다:
     ```bash
     docker compose up -d
     ```
  3. 백엔드를 다시 실행하여 정상 접속되는지 확인합니다.

