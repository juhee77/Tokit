package com.tokit.domain.asset.repository;

import com.tokit.domain.asset.entity.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    /** 특정 발행인에 대한 누적 청약 금액 (발행인별 한도 검증용). */
    @Query("SELECT COALESCE(SUM(s.amount), 0) FROM Subscription s "
            + "WHERE s.user.id = :userId AND s.issuer.id = :issuerId")
    BigDecimal sumAmountByUserAndIssuer(@Param("userId") Long userId, @Param("issuerId") Long issuerId);

    /** 기준 시점 이후의 전체 누적 청약 금액 (연간 한도 검증용). */
    @Query("SELECT COALESCE(SUM(s.amount), 0) FROM Subscription s "
            + "WHERE s.user.id = :userId AND s.subscribedAt >= :since")
    BigDecimal sumAmountByUserSince(@Param("userId") Long userId, @Param("since") LocalDateTime since);

    List<Subscription> findByUser_IdOrderBySubscribedAtDesc(Long userId);
}
