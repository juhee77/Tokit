# 🏛️ TOKIT — STO 토큰증권 초고속 매칭 엔진 & 거래소 플랫폼

> **대규모 금융 트래픽 분산 처리**와 **0.1원의 오차도 허용하지 않는 강력한 데이터 정합성**을 목표로 설계된 엔터프라이즈급 STO 거래소 플랫폼입니다.

---

## 🎯 1. 프로젝트 목적 및 기획 의도 (Core Vision)

### 💡 진짜 목적 (Core Engineering Goal)
단순한 게시판 CRUD나 토이 프로젝트를 넘어선 **Expert 수준의 백엔드 아키텍처**를 증명합니다.
- DB 데드락(Deadlock) 및 핫스팟(Hotspot)을 차단하는 동시성 제어.
- 인메모리(Redis) 기반의 초고속 호가창 매칭 알고리즘 구현.
- 서버 푸시(SSE/WebSocket) 스트리밍 최적화로 클라이언트 렌더링 부하 최소화.
- 온체인(블록체인)과 오프체인(RDBMS) 간의 비동기 동기화 및 상시 데이터 대사(Reconciliation).

### 💼 기획 의도 (Business Value)
고가의 실물 자산(예: 상업용 부동산)을 신탁하여 수익증권을 발행하고, 이를 블록체인 기반의 토큰으로 분할 발행(STO)하여 다수의 투자자가 공모 청약 및 2차 거래(호가 매칭)를 할 수 있도록 지원하는 초고속 매칭 플랫폼입니다.

---

## 📐 2. 아키텍처 및 역할 정의 (Tech Stack & R&R)

```
┌──────────────┐     WebSocket/SSE      ┌──────────────────┐
│   Frontend   │ ◄────────────────────► │     Backend      │
│  (Next.js)   │      REST API          │  (Spring Boot)   │
└──────────────┘                        └────────┬─────────┘
                                                 │
                           ┌─────────────────────┼─────────────────────┐
                           │                     │                     │
                    ┌──────▼──────┐     ┌────────▼───────┐    ┌───────▼───────┐
                    │  PostgreSQL │     │     Redis      │    │   RabbitMQ    │
                    │  (주문/체결)  │     │  (호가창 캐시)   │    │  (이벤트 큐)   │
                    └─────────────┘     └────────────────┘    └───────────────┘
                                                                      │
                                                             ┌────────▼────────┐
                                                             │ Matching Engine │
                                                             │ (Price-Time)    │
                                                             └────────┬────────┘
                                                                      │
                                                             ┌────────▼────────┐
                                                             │   Blockchain    │
                                                             │  (ERC-1400)    │
                                                             └─────────────────┘
```

| 레이어 | 기술 스택 | 핵심 역할 및 책임 (R&R) |
| :--- | :--- | :--- |
| **Frontend** | Next.js 16 (App Router), TS, Tailwind, Zustand | **"Dumb Client"**. 가공되지 않은 상태(State)의 렌더링에만 집중하며, 중복 클릭 방지 등 클라이언트 측 멱등성 UI를 구현합니다. |
| **Backend** | Java 25 (LTS), Spring Boot 4.0, JPA, WebSocket | **"The Brain"**. 비즈니스 로직 제어, 트랜잭션 격리수준 관리, 인메모리 매칭 엔진 구동 및 실시간 데이터 푸시를 비차단(Non-blocking)으로 수행합니다. |
| **Database** | PostgreSQL 17 | **"Source of Truth"**. 모든 자산 거래 원장(Ledger)과 회원 정보가 엄격한 무결성 하에 보존되는 유일한 물리 저장소입니다. |
| **Cache & MQ** | Redis 7, RabbitMQ 4 | **"Shock Absorber"**. 초당 수만 건의 트래픽 스파이크를 흡수하고, 매칭 파이프라인의 결합도를 낮추는 버퍼 역할을 담당합니다. |
| **Blockchain** | Hardhat, Solidity 0.8.28 (ERC-1400) | **"Final Ledger"**. 오프체인의 비동기 온체인 동기화 워커에 의해 호출되며, 화이트리스트 및 파티션 전송을 통해 법적 컴플라이언스를 최종 보장합니다. |

---

## 🔄 3. 스택 간 싱크 및 개발 규칙 (Cross-Stack Sync Rules)

아키텍처 붕괴 방지를 위해 3인의 전문 개발자가 독립적으로 협업하듯 아래 3가지 대원칙을 엄격하게 고수합니다.

### 1) API Contract First (계약 우선 개발)
- 백엔드 개발 전에 무조건 JSON 응답 구조(`ApiResponse<T>`)를 확정합니다.
- 프론트엔드는 확정된 계약서(명세)를 기반으로 Mock 데이터를 만들어 UI 바인딩과 상태 관리(Zustand)를 먼저 완성합니다.

### 2) Event-Driven Sync (이벤트 기반 동기화)
- 체결 발생 시 즉각 DB를 업데이트하지 않고 비동기 결합 파이프라인을 탑재합니다.
- `[체결 완료]` $\rightarrow$ 백엔드 PostgreSQL 반영 $\rightarrow$ RabbitMQ에 `Trade_Success` 이벤트 발행.
  1. **WebSocket Worker**: 이벤트를 수신하여 Next.js 클라이언트로 실시간 호가 및 시세 갱신 푸시.
  2. **Web3j Worker**: 이벤트를 수신하여 Hardhat 블록체인 노드로의 ERC-1400 전송 트랜잭션 실행.

### 3) 멱등성 (Idempotency) 보장
- 중복 주문/연타로 인한 사고 방지를 위해 프론트엔드는 주문 전송 시 UUID 기반의 `X-Idempotency-Key`를 HTTP 헤더에 실어 보냅니다.
- 백엔드는 Redis 분산 락 및 키 조회를 통해 중복 요청을 원천 차단하여 **이중 결제를 차단**합니다.

---

## 📅 4. 12주 애자일 스프린트 로드맵 (Action Plan)

포트폴리오 및 아키텍처 완성도를 위해 기능 구현 7주, 최적화 및 카오스 엔지니어링 5주로 스프린트를 전개합니다.

```mermaid
gantt
    title TOKIT STO 12주 개발 로드맵
    dateFormat  WEEKS
    axisFormat  W%V
    
    section Phase 1: 기반 공사
    인프라 & Monorepo 세팅 :active, w1, 2w
    section Phase 2: 코어 비즈니스
    돈과 주문 & 동시성 제어 (Hold/Lock) : w3, 3w
    section Phase 3: 매칭 엔진 & 스트리밍
    인메모리 호가창 체결 & SSE/WS 스트림 : w6, 3w
    section Phase 4: 블록체인 연동
    ERC-1400 배포 & 배치 데이터 대사(Reconciliation) : w9, 2w
    section Phase 5: 최적화 & 카오스
    JMeter 부하 테스트 & 트러블슈팅 & 블로그 연재 : w11, 2w
```

*   **Phase 1: 기반 공사 (Week 1~2)** - [완료]
    *   Monorepo 환경 세팅 및 PostgreSQL, Redis, RabbitMQ, Hardhat 로컬 세팅.
    *   Java 25, Boot 4.0, Next.js 16, Solidity 0.8.28 뼈대 코드 구축 및 빌드/컴파일 검증 완료.
*   **Phase 2: 코어 비즈니스 - 돈과 주문 (Week 3~5)**
    *   예치금 충전/차감 로직 및 주문 진입 시 예치금 홀딩(`Lock`) 처리.
    *   낙관적 락(Optimistic Lock) 및 비관적 락(Pessimistic Lock)을 활용한 동시성 완벽 제어.
    *   세그먼트 트리(Segment Tree) 기반 공모 청약 실시간 달성률 최적화.
*   **Phase 3: 매칭 엔진 및 스트리밍 (Week 6~8)**
    *   Redis Sorted Set을 활용한 인메모리 호가창 설계 및 이분 탐색 체결 엔진 완성.
    *   체결 시 RabbitMQ 비동기 이벤트 발행 파이프라인 정밀 연동.
    *   Next.js 프론트엔드의 화면 깜빡임을 최소화한 최적화된 SSE/WS 스트리밍 렌더링.
*   **Phase 4: 블록체인 연동 및 검증 (Week 9~10)**
    *   ERC-1400 규격의 스마트 컨트랙트 배포 및 비동기 온체인 동기화 워커 구축.
    *   **Spring Batch** 기반 매일 자정 동작하는 온-오프체인 원장 잔고 데이터 대사(Reconciliation) 시스템 개발.
*   **Phase 5: 카오스 엔지니어링 및 자산화 (Week 11~12)**
    *   JMeter 기반 초당 10,000건 스파이크 트래픽 부하 테스트 실행.
    *   데드락(Deadlock), 메모리 누수(Memory Leak), 스레드 풀 병목에 대한 실전 트러블슈팅.
    *   lahezy 블로그 개발기 작성 및 juhee77 GitHub 리포지토리 아키텍처 시각화 리포트 등록.

---

## 🚀 Quick Start

### Prerequisites
- Java 25 (LTS)
- Node.js 24 (LTS)
- Docker & Docker Compose

### 1. 인프라 기동
```bash
docker compose up -d
```

### 2. Backend 실행
```bash
cd backend
# Gradle Wrapper 로드를 위해 최초 1회 빌드 혹은 IDE에서 프로젝트 임포트
# IDE에서 com.tokit.TokitApplication 실행
```
> 서버: http://localhost:8080
> Swagger API 문서: http://localhost:8080/swagger-ui/index.html

### 3. Frontend 실행
```bash
cd frontend
npm install
npm run dev
```
> 클라이언트: http://localhost:3000

### 4. Blockchain 로컬 노드 기동
```bash
cd blockchain
npm install
npx hardhat node
```
> 로컬 노드: http://localhost:8545

---

## 📄 License
MIT License
