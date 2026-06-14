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
}
