package com.tokit.domain.asset.entity;

import com.tokit.domain.issuer.entity.Issuer;
import com.tokit.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 공모 청약 이력. 지갑 잔고만으로는 "언제, 어느 발행인에게 얼마를 청약했는지"를 알 수 없어
 * 발행인별·연간 누적 투자한도를 검증할 수 없으므로 청약 시점의 사실을 그대로 남깁니다.
 */
@Entity
@Table(
    name = "subscriptions",
    indexes = {
        @Index(name = "idx_subscriptions_user_subscribed_at", columnList = "user_id, subscribed_at"),
        @Index(name = "idx_subscriptions_user_issuer", columnList = "user_id, issuer_id")
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    /** 발행인별 한도 집계를 자산 조인 없이 수행하기 위해 청약 시점의 발행인을 함께 기록합니다. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "issuer_id", nullable = false)
    private Issuer issuer;

    @Column(nullable = false, precision = 20, scale = 4)
    private BigDecimal amount; // 청약 금액 (KRW)

    @Column(name = "token_quantity", nullable = false, precision = 20, scale = 4)
    private BigDecimal tokenQuantity;

    @Column(name = "subscribed_at", nullable = false)
    private LocalDateTime subscribedAt;

    @Builder
    public Subscription(User user, Asset asset, Issuer issuer, BigDecimal amount,
                        BigDecimal tokenQuantity, LocalDateTime subscribedAt) {
        this.user = user;
        this.asset = asset;
        this.issuer = issuer;
        this.amount = amount;
        this.tokenQuantity = tokenQuantity;
        this.subscribedAt = subscribedAt != null ? subscribedAt : LocalDateTime.now();
    }
}
