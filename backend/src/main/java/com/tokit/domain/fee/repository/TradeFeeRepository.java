package com.tokit.domain.fee.repository;

import com.tokit.domain.fee.entity.TradeFee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface TradeFeeRepository extends JpaRepository<TradeFee, Long> {

    List<TradeFee> findByTrade_Id(Long tradeId);

    List<TradeFee> findByUser_IdOrderByChargedAtDesc(Long userId);

    @Query("SELECT COALESCE(SUM(f.feeAmount), 0) FROM TradeFee f WHERE f.chargedAt >= :since")
    BigDecimal sumFeesSince(@Param("since") LocalDateTime since);
}
