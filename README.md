# 🏛️ TOKIT — STO 토큰증권 매칭 엔진 & 거래소

> 블록체인 기반 토큰증권(STO)의 발행부터 거래까지, 실시간 매칭 엔진을 갖춘 풀스택 거래 플랫폼

---

## 📐 Architecture

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

## 🛠 Tech Stack

| Layer | Technology |
|-------|-----------|
| **Frontend** | Next.js 16 (App Router), TypeScript, Tailwind CSS, Zustand |
| **Backend** | Java 25 (LTS), Spring Boot 4.0, Spring Data JPA, Spring WebSocket |
| **Database** | PostgreSQL 17 |
| **Cache** | Redis 7 |
| **Message Queue** | RabbitMQ 4 |
| **Blockchain** | Hardhat, Solidity 0.8.28, ERC-1400 |
| **Infra** | Docker Compose |

## 🚀 Quick Start

### Prerequisites
- Java 21+
- Node.js 20+
- Docker & Docker Compose

### 1. 인프라 기동
```bash
docker compose up -d
```

### 2. Backend 실행
```bash
cd backend
./gradlew bootRun
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

### 4. Blockchain 로컬 노드
```bash
cd blockchain
npm install
npx hardhat node
```
> 로컬 노드: http://localhost:8545

## 📁 Project Structure

```
TOKIT/
├── frontend/          # Next.js 클라이언트
├── backend/           # Spring Boot API 서버
├── blockchain/        # Hardhat + Solidity 스마트 컨트랙트
└── docker-compose.yml # 개발 인프라 (PostgreSQL, Redis, RabbitMQ)
```

## 📄 License

MIT License
