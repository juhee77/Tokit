# 작업 보고서: 코드 리뷰 후속 수정 및 주문 취소 잔고 반환 버그 수정

- **일자**: 2026-08-22
- **커밋**: `b330bfd` (main 브랜치)
- **범위**: `/code-review` 실행 결과 발견된 이슈 수정 + 백엔드 아키텍처 조사 중 발견한 `cancelOrder` 잠재 버그 수정

---

## 1. 배경

`/code-review`를 working-tree diff 대상으로 실행한 결과, 여러 finder 에이전트가 아래 3가지 이슈에 공통적으로 수렴했습니다.

1. `README.md` — 오타로 인한 문서 훼손
2. `start.sh` — 잘못된 줄바꿈으로 인한 콘솔 출력 깨짐
3. `Order.java` 생성자 — `remainQty`/`status`가 `null`일 때 값을 침묵 처리(silent default)하는 로직이 추가되어, 향후 호출부가 값을 누락해도 컴파일/런타임 신호 없이 잘못된 상태(전량 미체결 OPEN)로 주문이 생성될 위험이 있다는 지적

이어서 사용자 요청으로 백엔드 아키텍처 전반을 조사했고, 그 과정에서 `OrderService.cancelOrder()`가 주문 상태만 `CANCELED`로 바꿀 뿐 **주문 시 홀딩(lock)해 둔 예치금/자산을 반환하지 않는** 잠재 버그를 발견하여 이번 작업 범위에 포함했습니다.

---

## 2. 수정 내역

### 2-1. README.md / start.sh (오타·포맷 복구)

- `README.md:219` — `실시간 호가 및 체결 거래소 (9당0%)` → `(90%)`로 수정
- `start.sh:13-14` — `echo "4. Start\ning Frontend..."`로 쪼개져 있던 문자열을 한 줄로 병합

> 두 파일 모두 수정 후 원래 커밋된 상태(`HEAD`)와 완전히 동일해졌습니다. 즉, 세션 시작 시점에 이미 작업 트리에 있던 실수(오타·줄바꿈 삽입)를 원상복구한 것으로, 커밋할 변경사항이 남지 않았습니다.

### 2-2. Order.java — 생성자 null-coalescing 되돌림

`remainQty`/`status`가 `null`일 때 자동으로 기본값(`quantity`, `OrderStatus.OPEN`)을 채우던 로직을 제거하고, 엔티티의 `@Column(nullable = false)` 제약과 일치하는 **fail-fast** 방식으로 되돌렸습니다.

```java
// Before (되돌림 대상)
this.remainQty = remainQty != null ? remainQty : quantity;
this.status = status != null ? status : OrderStatus.OPEN;

// After
this.remainQty = remainQty;
this.status = status;
```

이 기본값에 의존하던 신규 테스트(`MatchingEngineTest`)의 `Order.builder()` 호출 9곳 전부에 `.remainQty(...)`, `.status(OrderStatus.OPEN)`을 명시적으로 추가해, "신규 주문 = OPEN + 전량 미체결"이라는 규칙이 실제 프로덕션 코드(`OrderService.placeOrder`) 한 곳에만 존재하도록 정리했습니다. 이 파일 역시 최종적으로 `HEAD`와 동일해져 커밋 대상에서 제외되었습니다.

### 2-3. 테스트 리플렉션 헬퍼 통일

`MatchingEngineTest`, `DividendBatchConfigTest`, `ReconciliationBatchConfigTest` 3개 신규 테스트가 각자 손수 구현했던 5줄짜리 `setField` 리플렉션 헬퍼를 제거하고, 이미 `ContractServiceTest` 등에서 쓰이던 Spring 표준 `org.springframework.test.util.ReflectionTestUtils.setField`로 통일했습니다. (상속 필드 처리 등에서 더 견고함)

### 2-4. ⭐ cancelOrder — 주문 취소 시 홀딩 잔고 미반환 버그 수정 (신규 발견)

**증상**: `OrderService.placeOrder()`는 매수 주문 시 KRW 지갑의 `balance`를 깎아 `lockedBalance`로, 매도 주문 시 자산 지갑의 `balance`를 깎아 `lockedBalance`로 옮겨 홀딩합니다. 그런데 `cancelOrder()`는 `order.cancel()`로 상태만 바꿀 뿐, 이 홀딩분을 되돌리는 코드가 전혀 없었습니다. → **주문을 취소할 때마다 그만큼의 예치금/자산이 영구히 `lockedBalance`에 묶여버리는 실자금 버그**였습니다.

**수정 내용** (`OrderService.java`):

```java
@Transactional
public void cancelOrder(Long orderId) {
    Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

    // 이미 종결된 주문(FILLED/CANCELED)은 취소 불가
    if (order.getStatus() != OrderStatus.OPEN && order.getStatus() != OrderStatus.PARTIAL) {
        throw new BusinessException(ErrorCode.ORDER_ALREADY_CLOSED);
    }

    // 미체결 잔량(remainQty)만큼만 홀딩 해제 — 부분체결분은 그대로 유지
    if (order.getOrderType() == OrderType.BUY) {
        Wallet krwWallet = walletRepository.findKrwWalletByUserIdWithPessimisticLock(order.getUserId())...
        BigDecimal releaseAmount = order.getPrice().multiply(order.getRemainingQuantity());
        krwWallet.updateBalance(krwWallet.getBalance().add(releaseAmount), krwWallet.getLockedBalance().subtract(releaseAmount));
    } else {
        Wallet assetWallet = walletRepository.findAssetWalletByUserIdAndAssetIdWithPessimisticLock(...)...
        BigDecimal releaseQuantity = order.getRemainingQuantity();
        assetWallet.updateBalance(assetWallet.getBalance().add(releaseQuantity), assetWallet.getLockedBalance().subtract(releaseQuantity));
    }

    order.cancel();
}
```

핵심 설계 포인트:
- **부분체결 주문도 정확히 처리**: 체결된 만큼은 `TradeService.saveTrade()`가 이미 `lockedBalance`에서 차감했으므로, 취소 시에는 반드시 `order.getRemainingQuantity()`(= 남은 미체결 잔량) 기준으로만 반환해야 이중 반환/과다 반환이 없습니다.
- **동시성**: `placeOrder`/`saveTrade`와 동일한 비관적 락(`PESSIMISTIC_WRITE`) 조회 메서드를 재사용해 락 전략 일관성을 유지했습니다.
- **가드 추가**: 이미 `FILLED`/`CANCELED`된 주문을 다시 취소하려는 시도는 신규 에러코드 `ORDER_ALREADY_CLOSED`(`ErrorCode.java`, HTTP 409)로 명시적으로 막았습니다. (기존에는 이런 가드가 없어 FILLED 주문도 취소 처리되며 존재하지도 않는 홀딩분을 반환하려다 로직이 깨질 수 있었습니다.)

**추가된 테스트** (`OrderServiceTest.java`):
- `cancelOrder_BuyOrder_ReleasesHeldKrw` — 전량 미체결 매수 주문 취소 시 KRW 홀딩 전액 반환 검증
- `cancelOrder_PartiallyFilledSellOrder_ReleasesRemainingAssetOnly` — 20주 중 12주 체결된 매도 주문 취소 시, 남은 8주분만 반환되고 체결된 12주분 락은 유지되는지 검증
- `cancelOrder_AlreadyFilledOrder_ThrowsException` — FILLED 주문 취소 시도 시 `ORDER_ALREADY_CLOSED` 예외 발생 및 지갑 조회 자체가 일어나지 않음을 검증

---

## 3. 검증

- 전체 관련 유닛테스트(`OrderServiceTest`, `MatchingEngineTest`, `DividendBatchConfigTest`, `ReconciliationBatchConfigTest`) 컴파일 및 실행 — **전부 통과**
- 세션 도중 발견된 무관한 미추적 파일(`relayer/service`, `global/aspect` 등, 다른 작업으로 추정)은 손대지 않고 검증 시에만 임시로 옮겼다가 복구 처리해 격리했습니다.

---

## 4. 커밋

```
b330bfd fix: release held wallet balance when canceling an order

 6 files changed, 784 insertions(+), 1 deletion(-)
 - backend/src/main/java/com/tokit/domain/order/service/OrderService.java (수정)
 - backend/src/main/java/com/tokit/global/exception/ErrorCode.java (수정)
 - backend/src/test/java/com/tokit/domain/order/service/OrderServiceTest.java (신규)
 - backend/src/test/java/com/tokit/domain/matching/engine/MatchingEngineTest.java (신규)
 - backend/src/test/java/com/tokit/domain/dividend/service/DividendBatchConfigTest.java (신규)
 - backend/src/test/java/com/tokit/domain/reconciliation/service/ReconciliationBatchConfigTest.java (신규)
```

README.md, start.sh, Order.java는 수정 후 원래 커밋 상태와 동일해져 커밋 대상에 포함되지 않았습니다.

---

## 5. 남은 이슈 (이번 범위 밖, 참고용)

- `OrderController.cancelOrder()`에 소유권(본인 주문인지) 검증이 없어 보입니다 — 다른 사용자의 주문 ID로 취소 요청 시 처리 가능한지 확인 필요.
- `ReconciliationBatchConfig`(배치 설정 계층)가 `AdminAlertController`(컨트롤러 계층)를 직접 주입받아 호출하는 역방향 레이어링 — 알림 발송을 별도 서비스로 분리 권장.
