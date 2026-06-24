package com.tokit.domain.trade.repository;

import com.tokit.domain.trade.entity.Trade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TradeRepository extends JpaRepository<Trade, Long> {
    List<Trade> findByAsset_SymbolOrderByTradedAtDesc(String symbol);

    @Query("SELECT t FROM Trade t WHERE t.buyOrder.user.id = :userId OR t.sellOrder.user.id = :userId ORDER BY t.tradedAt DESC")
    List<Trade> findByUserIdOrderByTradedAtDesc(@Param("userId") Long userId);

    @Query(value = "SELECT " +
            "date_trunc('minute', t.traded_at) as time, " +
            "(array_agg(t.price ORDER BY t.traded_at ASC))[1] as open, " +
            "MAX(t.price) as high, " +
            "MIN(t.price) as low, " +
            "(array_agg(t.price ORDER BY t.traded_at DESC))[1] as close, " +
            "SUM(t.quantity) as volume " +
            "FROM trades t " +
            "JOIN assets a ON t.asset_id = a.id " +
            "WHERE a.symbol = :symbol " +
            "GROUP BY time " +
            "ORDER BY time ASC", nativeQuery = true)
    List<Object[]> findCandlesBySymbol(@Param("symbol") String symbol);
}
