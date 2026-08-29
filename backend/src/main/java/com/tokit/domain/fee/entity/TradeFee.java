package com.tokit.domain.fee.entity;

import com.tokit.domain.trade.entity.Trade;
import com.tokit.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 체결 수수료 원장. 매수/매도 각 측에 대해 한 건씩 기록되며, 정산·회계와
 * 투자자 문의 대응의 근거가 됩니다.
 */
@Entity
@Table(name = "trade_fees", indexes = {
        @Index(name = "idx_trade_fees_trade", columnList = "trade_id"),
        @Index(name = "idx_trade_fees_user_charged_at", columnList = "user_id, charged_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TradeFee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trade_id", nullable = false)
    private Trade trade;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FeeSide side;

    /** 수수료 산정 기준이 된 체결 금액. */
    @Column(name = "taxable_amount", nullable = false, precision = 20, scale = 4)
    private BigDecimal taxableAmount;

    @Column(name = "fee_amount", nullable = false, precision = 20, scale = 4)
    private BigDecimal feeAmount;

    /** 부과 시점의 요율. 이후 요율이 바뀌어도 과거 정산 근거가 유지됩니다. */
    @Column(name = "fee_rate", nullable = false, precision = 10, scale = 6)
    private BigDecimal feeRate;

    @Column(name = "charged_at", nullable = false)
    private LocalDateTime chargedAt;

    @Builder
    public TradeFee(Trade trade, User user, FeeSide side, BigDecimal taxableAmount,
                    BigDecimal feeAmount, BigDecimal feeRate, LocalDateTime chargedAt) {
        this.trade = trade;
        this.user = user;
        this.side = side;
        this.taxableAmount = taxableAmount;
        this.feeAmount = feeAmount;
        this.feeRate = feeRate;
        this.chargedAt = chargedAt != null ? chargedAt : LocalDateTime.now();
    }

    public enum FeeSide {
        BUY,
        SELL
    }
}
