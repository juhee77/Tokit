package com.tokit.domain.matching.service;

import com.tokit.domain.matching.engine.MatchResult;
import com.tokit.domain.asset.repository.AssetRepository;
import com.tokit.domain.matching.engine.MatchingEngine;
import com.tokit.domain.order.entity.Order;
import com.tokit.domain.order.entity.OrderStatus;
import com.tokit.domain.order.entity.OrderType;
import com.tokit.domain.order.repository.OrderRepository;
import com.tokit.domain.orderbook.service.OrderBookService;
import com.tokit.global.event.PostCommitEvents;
import com.tokit.domain.trade.service.TradeService;
import com.tokit.infra.redis.OrderBookDto;
import com.tokit.infra.redis.RedisOrderBookRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MatchingService {

    /** 호가창에 노출할 가격대 수. */
    private static final int ORDER_BOOK_DEPTH = 20;
    /**
     * 한 번의 매칭에서 읽어올 최대 체결 후보 수.
     * 잔량이 남으면 다음 이벤트에서 이어 체결되므로, 한 트랜잭션이 무한정 길어지지 않게 막는다.
     */
    private static final int MATCH_CANDIDATE_LIMIT = 200;
    private static final List<OrderStatus> ACTIVE_STATUSES =
            List.of(OrderStatus.OPEN, OrderStatus.PARTIAL);

    private final AssetRepository assetRepository;
    private final OrderRepository orderRepository;
    private final MatchingEngine matchingEngine;
    private final TradeService tradeService;
    private final OrderBookService orderBookService;
    private final RedisOrderBookRepository redisOrderBookRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void matchOrder(Order order) {
        log.info("Starting match process for order id: {}, symbol: {}", order.getId(), order.getAssetSymbol());

        // 0. 종목 행에 배타 락을 건다. 이 아래의 "활성 주문 조회 → 체결 계산 → 잔량 갱신"은
        //    read-modify-write라 락이 없으면 같은 매도 주문이 여러 스레드에서 중복 체결된다.
        //    락은 이 트랜잭션이 커밋될 때 풀리므로, 다음 스레드는 반드시 갱신된 잔량을 읽는다.
        assetRepository.findBySymbolForUpdate(order.getAssetSymbol())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Asset not found with symbol: " + order.getAssetSymbol()));

        // 0-1. 락을 잡은 뒤 주문 상태를 다시 읽는다.
        //      컨슈머가 주문을 읽은 시점과 여기 도달한 시점 사이에 사용자가 취소했을 수 있고,
        //      그대로 진행하면 아래 save가 취소된 주문을 다시 활성 상태로 되살린다.
        final Order incomingOrder = orderRepository.findByIdWithAsset(order.getId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Order not found with id: " + order.getId()));
        if (!ACTIVE_STATUSES.contains(incomingOrder.getStatus())) {
            log.info("Skipping match for order {} — no longer active (status={})",
                    incomingOrder.getId(), incomingOrder.getStatus());
            return;
        }

        // 1. 체결 가능한 반대 방향 주문만 가격-시간 우선순위로 조회한다.
        //    방향·가격 조건을 DB로 내리지 않으면 체결되지 않고 남은 주문이 쌓일수록
        //    주문 1건당 읽는 행이 늘어나, 호가창이 커질수록 매칭이 느려진다.
        OrderType oppositeType = incomingOrder.getOrderType() == OrderType.BUY ? OrderType.SELL : OrderType.BUY;
        List<Order> oppositeOrders = orderRepository.findMatchableOrders(
                incomingOrder.getAssetSymbol(),
                oppositeType,
                incomingOrder.getPrice(),
                ACTIVE_STATUSES,
                Pageable.ofSize(MATCH_CANDIDATE_LIMIT));

        // 2. 매칭 엔진 실행
        MatchResult result = matchingEngine.match(incomingOrder, oppositeOrders);

        // 3. 체결 결과 반영 및 Trade 저장
        for (MatchResult.Match match : result.matches()) {
            Order maker = match.makerOrder();
            BigDecimal price = match.matchPrice();
            BigDecimal quantity = match.matchQuantity();

            // 체결 내역 저장 (TradeService에서 내부적으로 SSE 이벤트 방송)
            Long buyOrderId = incomingOrder.getOrderType() == OrderType.BUY ? incomingOrder.getId() : maker.getId();
            Long sellOrderId = incomingOrder.getOrderType() == OrderType.SELL ? incomingOrder.getId() : maker.getId();
            tradeService.saveTrade(buyOrderId, sellOrderId, incomingOrder.getAssetSymbol(), price, quantity);

            // 체결분만큼 양쪽 가격대에서 잔량을 덜어낸다.
            // 테이커는 자기 지정가에, 메이커는 자기 호가에 올라가 있다.
            orderBookService.reduceFilled(maker, quantity);
            orderBookService.reduceFilled(incomingOrder, quantity);

            // 메이커 주문 DB 업데이트
            orderRepository.save(maker);
        }

        // 4. 신규 주문 DB 업데이트
        orderRepository.save(incomingOrder);

        // 5. 호가창 브로드캐스트는 커밋 이후로 미룬다.
        //    여기서 바로 보내면 롤백 시 이미 잘못된 호가창을 내보낸 뒤가 되고,
        //    네트워크 I/O가 종목 락을 쥔 시간을 늘려 매칭 처리량을 깎는다.
        eventPublisher.publishEvent(
                new PostCommitEvents.OrderBookChanged(incomingOrder.getAssetSymbol()));
    }

    /** 커밋이 끝나고 락이 풀린 뒤에 실행된다. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderBookChanged(PostCommitEvents.OrderBookChanged event) {
        updateAndBroadcastOrderBook(event.symbol());
    }

    public void updateAndBroadcastOrderBook(String symbol) {
        // 물리화된 집계에서 상위 N호가만 읽는다. 스캔 범위가 활성 주문 수와 무관해져,
        // 미체결 주문이 쌓여도 체결 1건의 비용이 늘지 않는다.
        OrderBookDto orderBook = orderBookService.getOrderBook(symbol, ORDER_BOOK_DEPTH);

        // Redis 저장
        redisOrderBookRepository.saveOrderBook(symbol, orderBook);

        // WebSocket STOMP 브로드캐스트
        String destination = "/topic/orderbook/" + symbol;
        log.info("Broadcasting order book to websocket destination: {}", destination);
        messagingTemplate.convertAndSend(destination, orderBook);
    }
}
