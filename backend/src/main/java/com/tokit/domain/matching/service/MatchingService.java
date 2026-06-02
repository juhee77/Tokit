package com.tokit.domain.matching.service;

import com.tokit.domain.matching.engine.MatchResult;
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

    private final OrderRepository orderRepository;
    private final MatchingEngine matchingEngine;
    private final TradeService tradeService;
    private final RedisOrderBookRepository redisOrderBookRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional
    public void matchOrder(Order incomingOrder) {
        log.info("Starting match process for order id: {}, symbol: {}", incomingOrder.getId(), incomingOrder.getAssetSymbol());

        // 1. 매칭을 위해 반대 방향의 활성화된(OPEN, PARTIAL) 주문 리스트를 DB에서 조회
        OrderType oppositeType = incomingOrder.getOrderType() == OrderType.BUY ? OrderType.SELL : OrderType.BUY;
        List<Order> oppositeOrders = orderRepository.findByAsset_SymbolAndStatusIn(
                incomingOrder.getAssetSymbol(),
                Arrays.asList(OrderStatus.OPEN, OrderStatus.PARTIAL)
        ).stream()
                .filter(o -> o.getOrderType() == oppositeType)
                .collect(Collectors.toList());

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
        // 활성화된 모든 주문 조회
        List<Order> activeOrders = orderRepository.findByAsset_SymbolAndStatusIn(
                symbol,
                Arrays.asList(OrderStatus.OPEN, OrderStatus.PARTIAL)
        );

        // 매수 호가 집계 (가격 내림차순 정렬)
        Map<BigDecimal, BigDecimal> bidsMap = activeOrders.stream()
                .filter(o -> o.getOrderType() == OrderType.BUY)
                .collect(Collectors.groupingBy(
                        Order::getPrice,
                        Collectors.reducing(BigDecimal.ZERO, Order::getRemainingQuantity, BigDecimal::add)
                ));

        List<OrderBookDto.OrderBookEntry> bids = bidsMap.entrySet().stream()
                .map(e -> new OrderBookDto.OrderBookEntry(e.getKey(), e.getValue()))
                .sorted((e1, e2) -> e2.price().compareTo(e1.price())) // 비싼 가격 우선
                .limit(20) // 최대 20개만 호가 노출
                .toList();

        // 매도 호가 집계 (가격 오름차순 정렬)
        Map<BigDecimal, BigDecimal> asksMap = activeOrders.stream()
                .filter(o -> o.getOrderType() == OrderType.SELL)
                .collect(Collectors.groupingBy(
                        Order::getPrice,
                        Collectors.reducing(BigDecimal.ZERO, Order::getRemainingQuantity, BigDecimal::add)
                ));

        List<OrderBookDto.OrderBookEntry> asks = asksMap.entrySet().stream()
                .map(e -> new OrderBookDto.OrderBookEntry(e.getKey(), e.getValue()))
                .sorted((e1, e2) -> e1.price().compareTo(e2.price())) // 저렴한 가격 우선
                .limit(20) // 최대 20개만 호가 노출
                .toList();

        OrderBookDto orderBook = new OrderBookDto(symbol, bids, asks);

        // Redis 저장
        redisOrderBookRepository.saveOrderBook(symbol, orderBook);

        // WebSocket STOMP 브로드캐스트
        String destination = "/topic/orderbook/" + symbol;
        log.info("Broadcasting order book to websocket destination: {}", destination);
        messagingTemplate.convertAndSend(destination, orderBook);
    }
}
