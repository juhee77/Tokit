package com.tokit.domain.order.repository;

import com.tokit.domain.order.entity.Order;
import com.tokit.domain.order.entity.OrderStatus;
import com.tokit.domain.order.entity.OrderType;
import com.tokit.infra.redis.OrderBookDto;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByUser_Id(Long userId);
    List<Order> findByAsset_Symbol(String symbol);
    List<Order> findByAsset_SymbolAndStatusIn(String symbol, List<OrderStatus> statuses);

    @Query("select o from Order o join fetch o.asset where o.id = :id")
    Optional<Order> findByIdWithAsset(@Param("id") Long id);

    /**
     * 매칭 대상(체결 가능한 반대 방향 주문)만 골라 우선순위 순으로 가져온다.
     *
     * <p>이전에는 종목의 활성 주문을 전부 읽어 애플리케이션에서 방향·가격을 걸렀다.
     * 체결되지 않고 남은 주문이 쌓일수록 주문 1건의 비용이 함께 늘어나, 호가창이 커지면
     * 매칭 처리량이 그대로 떨어졌다. 방향·가격 조건과 가격-시간 우선 정렬을 DB로 내려
     * 읽는 행 수를 실제 체결 후보로 제한한다.
     *
     * <p>정렬은 매칭 엔진이 사용하는 가격-시간 우선순위와 동일하다. 매수는 싼 매도호가부터,
     * 매도는 비싼 매수호가부터 채운다.
     */
    @Query("""
            select o from Order o
            where o.asset.symbol = :symbol
              and o.type = :oppositeType
              and o.status in :statuses
              and (:oppositeType = com.tokit.domain.order.entity.OrderType.SELL
                     and o.price <= :price
                   or :oppositeType = com.tokit.domain.order.entity.OrderType.BUY
                     and o.price >= :price)
            order by case when :oppositeType = com.tokit.domain.order.entity.OrderType.SELL
                          then o.price else -o.price end asc,
                     o.createdAt asc
            """)
    List<Order> findMatchableOrders(@Param("symbol") String symbol,
                                    @Param("oppositeType") OrderType oppositeType,
                                    @Param("price") BigDecimal price,
                                    @Param("statuses") List<OrderStatus> statuses,
                                    Pageable pageable);

    /**
     * 호가창 한쪽(매수 또는 매도)을 가격대별로 집계한다.
     *
     * <p>이전에는 활성 주문 전체를 메모리로 읽어 Java에서 그룹핑·정렬한 뒤 상위 20호가만
     * 남겼다. 최종 결과가 20건인데도 읽는 행 수는 활성 주문 수에 비례해, 체결마다 수행되는
     * 이 재집계가 매칭 처리량의 상한이 되었다. 집계와 정렬, 상위 N 제한을 모두 DB로 내린다.
     */
    @Query("""
            select new com.tokit.infra.redis.OrderBookDto$OrderBookEntry(
                       o.price, sum(o.remainQty))
            from Order o
            where o.asset.symbol = :symbol
              and o.type = :type
              and o.status in :statuses
            group by o.price
            order by case when :type = com.tokit.domain.order.entity.OrderType.BUY
                          then -o.price else o.price end asc
            """)
    List<OrderBookDto.OrderBookEntry> aggregateOrderBookSide(@Param("symbol") String symbol,
                                                            @Param("type") OrderType type,
                                                            @Param("statuses") List<OrderStatus> statuses,
                                                            Pageable pageable);
}
