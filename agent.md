# TOKIT STO AI Agent Configuration

본 프로젝트를 작업하는 AI 에이전트와 개발자를 위한 규약 파일입니다.
전체 상세 개발 규약 및 가이드라인은 아래 파일에 통합 정의되어 있습니다.

👉 **[상세 개발 규약 및 규칙 보기](file:///.agents/AGENTS.md)**

## 핵심 지침 요약
1. **불변성(Immutability)**: 엔티티 및 DTO 변경 시 항상 새 객체를 복사하여 생성.
2. **테스트(TDD)**: 기능 추가 시 반드시 JUnit5 / Mockito 테스트 작성 (커버리지 80%+).
3. **OpenAPI**: REST Controller에 `@Tag` 및 `@Operation` 적용, `ApiResponse<T>` 래핑 필수.
4. **동시성 및 락**: 예치금 및 배당금 분배 작업 시 비관적 배타 락(`PESSIMISTIC_WRITE`) 적용.
5. **실시간 알림**: 원장 대사 정합성 오차 발생 시 슬랙 웹훅 및 SSE 실시간 브로드캐스트 자동 연동.
6. **가스비 대납**: 이더리움 `personal_sign` 복원 서명 검증 및 논스 원장 기반의 Relayer 대납 구현.
