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
