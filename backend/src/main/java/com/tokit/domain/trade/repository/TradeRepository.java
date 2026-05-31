package com.tokit.domain.trade.repository;

import com.tokit.domain.trade.entity.Trade;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TradeRepository extends JpaRepository<Trade, Long> {
    List<Trade> findByAssetSymbolOrderByCreatedAtDesc(String assetSymbol);
}
