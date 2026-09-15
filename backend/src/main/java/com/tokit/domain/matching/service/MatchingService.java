package com.tokit.domain.matching.service;

import com.tokit.domain.matching.engine.MatchResult;
import com.tokit.domain.asset.repository.AssetRepository;
import com.tokit.domain.matching.engine.MatchingEngine;
import com.tokit.domain.order.entity.Order;
import com.tokit.domain.order.entity.OrderStatus;
import com.tokit.domain.order.entity.OrderType;
import com.tokit.domain.order.repository.OrderRepository;
import com.tokit.domain.trade.service.TradeService;
import com.tokit.infra.redis.OrderBookDto;
import com.tokit.infra.redis.RedisOrderBookRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final RedisOrderBookRepository redisOrderBookRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional
    public void matchOrder(Order incomingOrder) {
        log.info("Starting match process for order id: {}, symbol: {}", incomingOrder.getId(), incomingOrder.getAssetSymbol());

        // 0. 종목 행에 배타 락을 건다. 이 아래의 "활성 주문 조회 → 체결 계산 → 잔량 갱신"은
        //    read-modify-write라 락이 없으면 같은 매도 주문이 여러 스레드에서 중복 체결된다.
        //    락은 이 트랜잭션이 커밋될 때 풀리므로, 다음 스레드는 반드시 갱신된 잔량을 읽는다.
        assetRepository.findBySymbolForUpdate(incomingOrder.getAssetSymbol())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Asset not found with symbol: " + incomingOrder.getAssetSymbol()));

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

            // 메이커 주문 DB 업데이트
            orderRepository.save(maker);
        }

        // 4. 신규 주문 DB 업데이트
        orderRepository.save(incomingOrder);

        // 5. 호가창(Order Book) 업데이트 및 Redis 저장 / WebSocket 브로드캐스트
        updateAndBroadcastOrderBook(incomingOrder.getAssetSymbol());
    }

    public void updateAndBroadcastOrderBook(String symbol) {
        // 집계·정렬·상위 N 제한을 모두 DB에서 수행한다. 결과가 20호가뿐인데 활성 주문
        // 전체를 메모리로 읽으면, 체결마다 수행되는 이 재집계가 매칭 처리량의 상한이 된다.
        List<OrderBookDto.OrderBookEntry> bids = orderRepository.aggregateOrderBookSide(
                symbol, OrderType.BUY, ACTIVE_STATUSES, Pageable.ofSize(ORDER_BOOK_DEPTH));
        List<OrderBookDto.OrderBookEntry> asks = orderRepository.aggregateOrderBookSide(
                symbol, OrderType.SELL, ACTIVE_STATUSES, Pageable.ofSize(ORDER_BOOK_DEPTH));

        OrderBookDto orderBook = new OrderBookDto(symbol, bids, asks);

        // Redis 저장
        redisOrderBookRepository.saveOrderBook(symbol, orderBook);

        // WebSocket STOMP 브로드캐스트
        String destination = "/topic/orderbook/" + symbol;
        log.info("Broadcasting order book to websocket destination: {}", destination);
        messagingTemplate.convertAndSend(destination, orderBook);
    }
}
