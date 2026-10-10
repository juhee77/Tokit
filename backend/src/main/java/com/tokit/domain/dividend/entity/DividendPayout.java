package com.tokit.domain.dividend.entity;

import com.tokit.domain.asset.entity.Asset;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "dividend_payouts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DividendPayout {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    @Column(name = "total_dividend_amount", nullable = false, precision = 20, scale = 4)
    private BigDecimal totalDividendAmount;

    @Column(name = "payout_date", nullable = false)
    private LocalDateTime payoutDate;

    @Column(nullable = false)
    private String status; // PENDING, PROCESSING, COMPLETED, FAILED

    /**
     * 실제로 주주에게 분배된 금액.
     *
     * <p>지급액을 원 단위로 절사하므로 재원과 반드시 같지 않다. 상세 내역을 전부 합산해야만
     * 알 수 있던 값을 한 레코드에서 대조할 수 있게 기록한다.
     */
    @Column(name = "distributed_amount", nullable = false, precision = 20, scale = 4)
    private BigDecimal distributedAmount = BigDecimal.ZERO;

    /** 절사로 남아 발행사에게 반환된 금액. {@code 재원 = 분배 총액 + 미분배 잔액}이 성립해야 한다. */
    @Column(name = "undistributed_amount", nullable = false, precision = 20, scale = 4)
    private BigDecimal undistributedAmount = BigDecimal.ZERO;

    /** 배치 종료 시점에 집행 결과를 확정한다. */
    public void recordSettlement(BigDecimal distributed, BigDecimal undistributed) {
        this.distributedAmount = distributed;
        this.undistributedAmount = undistributed;
    }

    @Builder
    public DividendPayout(Asset asset, BigDecimal totalDividendAmount, LocalDateTime payoutDate, String status) {
        this.asset = asset;
        this.totalDividendAmount = totalDividendAmount;
        this.payoutDate = payoutDate != null ? payoutDate : LocalDateTime.now();
        this.status = status;
    }

    public void updateStatus(String status) {
        this.status = status;
    }
}
