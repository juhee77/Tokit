# 🏛️ TOKIT STO — AI Agent Instructions & Guidelines

이 문서는 **TOKIT 토큰증권(STO) 플랫폼** 개발을 위한 통합 개발 표준, 아키텍처 규칙, 보안 가이드라인 및 검증 프로세스를 정의합니다. 모든 AI 에이전트와 개발자는 이 규약을 엄격하게 준수하여 코드를 작성해야 합니다.

---

## 📌 1. Core Principles (핵심 원칙)

1. **불변성 유지 (Immutability)**:
   - 모든 데이터 오브젝트 및 DTO는 불변 객체로 설계합니다. 자바에서는 가능하면 `record` 구조를 사용하고, 도메인 엔티티 변경 시에는 Setter 호출 대신 새로운 인스턴스를 빌더 등으로 복사 생성하여 반환합니다.
2. **테스트 주도 개발 (TDD, Coverage 80%+)**:
   - 신규 기능 추가 또는 도메인 수정 시 반드시 단위 및 통합 테스트를 작성합니다.
   - Mockito 기반 테스트 시 데이터베이스 저장 모의 상황을 고려하여 리플렉션 헬퍼(`setField`) 등을 활용해 엔티티 PK(`id`)를 세팅하여 스텁 매칭 정합성을 확보합니다.
3. **선 설계 후 개발 (Plan Before Execute)**:
   - 복잡한 아키텍처 설계가 필요한 기능은 반드시 `implementation_plan.md` 기안 및 승인을 거쳐 구현을 시작합니다.
4. **보안 및 규정 준수 (Security-First)**:
   - 비공개 키, 웹훅 URL 등 비밀 값은 소스코드에 하드코딩하지 않고 환경 변수로 관리합니다.
   - 사용자 지갑 주소 등을 쿼리할 때 체크섬 및 대소문자 혼용에 대비하여 대소문자 구분 없는 검색(`findByWalletAddressIgnoreCase`)을 생활화합니다.

---

## 🗂️ 2. API 개발 표준 (OpenAPI / Swagger)

### 1) REST Controller 어노테이션 필수 적용
- **Controller 클래스**: `@Tag(name = "[번호. 도메인명]", description = "[상세 역할 명세]")`
- **엔드포인트 메소드**: `@Operation(summary = "[기능 요약]", description = "[상세 동작 및 비즈니스 파이프라인 명세]")`
- 모든 REST API 응답은 `com.tokit.global.dto.ApiResponse<T>` 로 감싸서 리턴합니다.

### 2) DTO 및 유효성 검증
- 요청 Body DTO는 자바 `record` 타입으로 구성하고 불변성을 띱니다.
- `@NotBlank`, `@NotNull`, `@Positive` 등 검증용 어노테이션을 부착하고, Controller 파라미터에 `@Valid`를 반드시 적용합니다.

---

## ⚡ 3. 핵심 비즈니스 도메인 아키텍처 규칙

### 1) 멱등성 보장 (Idempotency Key)
- 이중 클릭 및 네트워크 재전송에 따른 원장 중복 차감을 원천 차단하기 위해 멱등성 키 검증을 지원해야 합니다.
- 클라이언트는 쓰기(CUD) 요청 시 HTTP 헤더에 `X-Idempotency-Key` (UUIDv4)를 전송하고, 백엔드는 Redis 분산 락 혹은 메모리 캐시를 이용해 동일 키 요청 시 `409 Conflict` 반환 또는 기존 결과를 재반환합니다.

### 2) 결산 및 배당 분배 동시성 제어 (Pessimistic Lock)
- 예치금 충전/차감 및 배당금 분배 배치 시 데드락과 잔액 오차를 방지하기 위해 비관적 배타 락(`PESSIMISTIC_WRITE` / `SELECT ... FOR UPDATE`)을 적용하여 원장 자산을 트랜잭션 격리 하에 연산합니다.

### 3) 실시간 어드민 알림 푸시 (Alert System)
- 배치 대사(`ReconciliationBatch`) 실행 중 PostgreSQL 오프체인 잔액과 블록체인 온체인 지갑 간의 데이터 정합성 오차 발견 시 즉각 대응해야 합니다.
- 정합성 오차 감지 시 `SlackAlertService`(비동기 슬랙 메세지 송출) 및 `AdminAlertController`(Server-Sent Events)를 연동하여 백오피스 브라우저 클라이언트들에 실시간 적색 토스트 팝업을 브로드캐스트합니다.

### 4) 가스비 대납 메타 트랜잭션 (Gasless Relayer)
- 일반 투자자가 가스비 지불용 ETH 없이 토큰증권을 무상 양도할 수 있도록 지원합니다.
- **검증 흐름**:
  1. 클라이언트는 MetaMask 지갑을 이용해 이체 명세 평문 메시지(`from:to:symbol:amount:nonce`)에 대해 `personal_sign`을 수행하고 서명 데이터를 획득합니다.
  2. 백엔드 `RelayerService`는 사용자 주소별로 DB에 관리되는 논스(`relayer_nonces`)와 서명 데이터를 획득합니다.
  3. Web3j `Sign.recoverFromSignature` API를 통해 서명에서 복원된 공개키 주소가 `fromAddress`와 정확히 대소문자 무관하게 매칭되는지 오프체인 검증합니다.
  4. 검증 성공 시 논스를 +1 하고, 오프체인 RDBMS 지갑 잔고를 갱신하며, 백엔드 오너(Relayer) 개인키 권한으로 온체인의 `forceTransferByPartition` 스마트 컨트랙트 트랜잭션을 날려 수수료를 대납합니다.

---

## 🧪 4. 검증 및 테스트 가이드

- 전체 테스트 스위트 빌드는 다음 명령어로 정합성을 검증합니다:
  ```bash
  ./gradlew test
  ```
- 테스트 격리성 유지를 위해 비동기 큐(RabbitMQ) 테스트가 묶여 있는 테스트 실행 시 타임아웃 레이스 컨디션을 방지하기 위한 유예 슬립 시간을 넉넉하게 주거나 단독 격리 테스트를 검토하십시오.
