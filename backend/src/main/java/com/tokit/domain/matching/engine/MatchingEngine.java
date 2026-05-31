package com.tokit.domain.matching.engine;

import com.tokit.domain.order.entity.Order;
import com.tokit.domain.order.entity.OrderType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
public class MatchingEngine {

    public MatchResult match(Order incomingOrder, List<Order> activeOppositeOrders) {
        List<MatchResult.Match> matches = new ArrayList<>();
        
        // 1. 반대 방향 주문들을 정렬
        // BUY 주문 매칭 대상(SELL 주문들)은 가격 오름차순(가장 저렴한 매도호가 우선), 등록 시간 오름차순
        // SELL 주문 매칭 대상(BUY 주문들)은 가격 내림차순(가장 비싼 매수호가 우선), 등록 시간 오름차순
        List<Order> sortedMakers = new ArrayList<>(activeOppositeOrders);
        if (incomingOrder.getOrderType() == OrderType.BUY) {
            sortedMakers.sort(Comparator.comparing(Order::getPrice)
                    .thenComparing(Order::getCreatedAt));
        } else {
            sortedMakers.sort(Comparator.comparing(Order::getPrice, Comparator.reverseOrder())
                    .thenComparing(Order::getCreatedAt));
        }

        // 2. 매칭 루프
        for (Order maker : sortedMakers) {
            if (incomingOrder.getRemainingQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                break; // 신규 주문이 완전히 체결됨
            }

            // 매칭 가능 가격대 확인
            boolean canMatch = false;
            if (incomingOrder.getOrderType() == OrderType.BUY) {
                // 매수 가격 >= 매도 호가 일 때 매칭 가능
                canMatch = incomingOrder.getPrice().compareTo(maker.getPrice()) >= 0;
            } else {
                // 매도 가격 <= 매수 호가 일 때 매칭 가능
                canMatch = incomingOrder.getPrice().compareTo(maker.getPrice()) <= 0;
            }

            if (!canMatch) {
                break; // 더 이상 매칭 가능한 호가가 없음
            }

            // 체결 수량 결정 = min(신규 주문 잔량, 기존 주문 잔량)
            BigDecimal matchQuantity = incomingOrder.getRemainingQuantity().min(maker.getRemainingQuantity());
            BigDecimal matchPrice = maker.getPrice(); // Maker(기존 등록 주문) 가격으로 체결

            // 주문 엔티티 업데이트
            incomingOrder.updateRemainingQuantity(matchQuantity);
            maker.updateRemainingQuantity(matchQuantity);

            matches.add(new MatchResult.Match(maker, matchPrice, matchQuantity));
        }

        return new MatchResult(incomingOrder, matches);
    }
}
