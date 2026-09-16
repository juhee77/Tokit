package com.tokit.domain.orderbook.service;

import com.tokit.domain.order.entity.Order;
import com.tokit.domain.order.entity.OrderType;
import com.tokit.domain.orderbook.repository.OrderBookLevelRepository;
import com.tokit.infra.redis.OrderBookDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 호가창 집계의 증분 갱신과 조회를 담당한다.
 *
 * <p>호가창 잔량이 변하는 지점은 셋뿐이다. 주문 접수(잔량 추가), 체결(잔량 감소),
 * 주문 취소(미체결 잔량 제거). 이 세 곳에서만 증감을 반영하면 집계는 항상 원장과 일치한다.
 *
 * <p>모든 갱신은 호출자의 트랜잭션에 참여한다. 주문·체결과 같은 트랜잭션에서 커밋되므로
 * 한쪽만 반영되는 상태가 존재하지 않는다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderBookService {

    private final OrderBookLevelRepository levelRepository;

    /** 신규 주문이 호가창에 올라간다. */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void addOrder(Order order) {
        applyDelta(order, order.getRemainingQuantity());
    }

    /** 체결된 수량만큼 해당 주문의 가격대에서 잔량을 덜어낸다. */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void reduceFilled(Order order, BigDecimal filledQuantity) {
        applyDelta(order, filledQuantity.negate());
    }

    /** 취소된 주문의 미체결 잔량을 호가창에서 제거한다. */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void removeRemaining(Order order, BigDecimal remainingQuantity) {
        applyDelta(order, remainingQuantity.negate());
    }

    private void applyDelta(Order order, BigDecimal delta) {
        if (delta.signum() == 0) {
            return;
        }
        Long assetId = order.getAsset().getId();
        levelRepository.applyDelta(assetId, order.getOrderType().name(), order.getPrice(), delta);
        if (delta.signum() < 0) {
            // 잔량이 모두 소진된 가격대는 호가창에서 사라져야 한다.
            levelRepository.removeEmptyLevels(assetId);
        }
    }

    /** 상위 N호가를 읽는다. 스캔 범위가 활성 주문 수가 아니라 노출 호가 수에만 비례한다. */
    @Transactional(readOnly = true)
    public OrderBookDto getOrderBook(String symbol, int depth) {
        return new OrderBookDto(
                symbol,
                toEntries(levelRepository.findTopBids(symbol, depth)),
                toEntries(levelRepository.findTopAsks(symbol, depth)));
    }

    /**
     * 활성 주문으로부터 집계를 다시 만든다.
     *
     * <p>증분 갱신은 변경 지점을 하나라도 놓치면 그 오차가 계속 누적된다. 원장으로
     * 되돌릴 수 있는 경로를 남겨 복구와 검증에 쓴다.
     */
    @Transactional
    public void rebuild(Long assetId) {
        levelRepository.deleteByAssetId(assetId);
        levelRepository.rebuildFromOrders(assetId);
    }

    private List<OrderBookDto.OrderBookEntry> toEntries(List<Object[]> rows) {
        return rows.stream()
                .map(r -> new OrderBookDto.OrderBookEntry(
                        (BigDecimal) r[0], (BigDecimal) r[1]))
                .toList();
    }
}
