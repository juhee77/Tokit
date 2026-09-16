package com.tokit.domain.orderbook.repository;

import com.tokit.domain.orderbook.entity.OrderBookLevel;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

/**
 * 호가창 가격대별 잔량 집계에 대한 증분 갱신·조회.
 *
 * <p>엔티티 매핑 없이 네이티브 쿼리만 쓴다. 이 테이블은 조회 전용 집계이고, 갱신은
 * 언제나 "특정 가격대에 얼마를 더하거나 뺀다"는 원자적 연산이기 때문이다. 영속성 컨텍스트에
 * 올려 read-modify-write 하면 동시 갱신 시 갱신 손실이 생긴다.
 */
public interface OrderBookLevelRepository extends Repository<OrderBookLevel, Long> {

    /**
     * 가격대 잔량을 delta만큼 증감한다.
     *
     * <p>UPSERT로 처리하는 이유는 "행이 있으면 갱신, 없으면 삽입"을 애플리케이션에서
     * 두 번의 쿼리로 나누면 그 사이에 다른 트랜잭션이 같은 행을 만들어 유니크 제약을
     * 위반할 수 있기 때문이다. 증감 자체도 DB가 행 락을 잡고 수행하므로 갱신 손실이 없다.
     */
    @Modifying
    @Query(value = """
            INSERT INTO order_book_levels (asset_id, side, price, quantity)
            VALUES (:assetId, :side, :price, :delta)
            ON CONFLICT (asset_id, side, price)
            DO UPDATE SET quantity = order_book_levels.quantity + EXCLUDED.quantity
            """, nativeQuery = true)
    void applyDelta(@Param("assetId") Long assetId,
                    @Param("side") String side,
                    @Param("price") BigDecimal price,
                    @Param("delta") BigDecimal delta);

    /** 잔량이 0 이하로 떨어진 가격대를 제거한다. (호가창에 빈 가격이 남지 않도록) */
    @Modifying
    @Query(value = """
            DELETE FROM order_book_levels
            WHERE asset_id = :assetId AND quantity <= 0
            """, nativeQuery = true)
    void removeEmptyLevels(@Param("assetId") Long assetId);

    /** 매수 호가: 비싼 가격 우선. */
    @Query(value = """
            SELECT l.price, l.quantity FROM order_book_levels l
            JOIN assets a ON l.asset_id = a.id
            WHERE a.symbol = :symbol AND l.side = 'BUY' AND l.quantity > 0
            ORDER BY l.price DESC
            LIMIT :depth
            """, nativeQuery = true)
    List<Object[]> findTopBids(@Param("symbol") String symbol, @Param("depth") int depth);

    /** 매도 호가: 저렴한 가격 우선. */
    @Query(value = """
            SELECT l.price, l.quantity FROM order_book_levels l
            JOIN assets a ON l.asset_id = a.id
            WHERE a.symbol = :symbol AND l.side = 'SELL' AND l.quantity > 0
            ORDER BY l.price ASC
            LIMIT :depth
            """, nativeQuery = true)
    List<Object[]> findTopAsks(@Param("symbol") String symbol, @Param("depth") int depth);

    /** 특정 종목의 집계를 모두 비운다. (재구축 전 단계) */
    @Modifying
    @Query(value = "DELETE FROM order_book_levels WHERE asset_id = :assetId", nativeQuery = true)
    void deleteByAssetId(@Param("assetId") Long assetId);

    /**
     * 활성 주문으로부터 집계를 다시 만든다.
     *
     * <p>증분 갱신은 모든 변경 지점을 빠짐없이 반영해야 정확하다. 누락이 생기면 집계가
     * 원장과 어긋난 채 계속 누적되므로, 원장으로부터 되돌릴 수 있는 경로를 함께 둔다.
     * 정합성 테스트도 이 경로의 결과와 증분 결과를 대조해 검증한다.
     */
    @Modifying
    @Query(value = """
            INSERT INTO order_book_levels (asset_id, side, price, quantity)
            SELECT o.asset_id, o.type, o.price, SUM(o.remain_qty)
            FROM orders o
            WHERE o.asset_id = :assetId
              AND o.status IN ('OPEN', 'PARTIAL')
              AND o.remain_qty > 0
            GROUP BY o.asset_id, o.type, o.price
            """, nativeQuery = true)
    void rebuildFromOrders(@Param("assetId") Long assetId);
}
