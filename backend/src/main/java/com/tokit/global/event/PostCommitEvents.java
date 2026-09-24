package com.tokit.global.event;

import com.tokit.infra.rabbitmq.TradeEvent;

/**
 * 커밋 이후에만 외부로 내보내야 하는 사건들.
 *
 * <p>체결과 호가창 갱신은 DB 트랜잭션 안에서 일어나지만, 그 결과를 알리는 일(RabbitMQ 발행,
 * SSE·STOMP 전송, Redis 캐시 갱신)을 같은 트랜잭션 안에서 하면 두 가지 문제가 생긴다.
 *
 * <ol>
 *   <li><b>정확성</b>: 트랜잭션이 롤백되어도 이미 내보낸 뒤다. 특히 RabbitMQ로 발행된
 *       체결 이벤트는 온체인 이체를 트리거하므로, 원장에 없는 체결이 블록체인에 기록된다.</li>
 *   <li><b>처리량</b>: 매칭은 종목 행 배타 락을 쥔 채 진행되고 락은 커밋 시점에 풀린다.
 *       트랜잭션 안에서 네트워크 I/O를 하면 그 지연이 그대로 종목별 매칭 처리량의 상한이 된다.</li>
 * </ol>
 *
 * <p>그래서 트랜잭션 안에서는 이벤트만 발행하고, 실제 전송은
 * {@code @TransactionalEventListener(phase = AFTER_COMMIT)}에서 수행한다.
 * 리스너는 커밋 후에 실행되므로 엔티티가 준영속 상태다. 지연 로딩이 터지지 않도록
 * 이벤트에는 엔티티가 아니라 필요한 값만 담는다.
 */
public final class PostCommitEvents {

    private PostCommitEvents() {}

    /** 해당 종목의 호가창이 바뀌었다. 커밋 후 스냅샷을 다시 읽어 캐시·브로드캐스트한다. */
    public record OrderBookChanged(String symbol) {}

    /** 체결이 확정되었다. 커밋 후 온체인 동기화 발행과 실시간 스트리밍을 수행한다. */
    public record TradeSettled(Long tradeId, String assetSymbol, TradeEvent onChainEvent) {}
}
