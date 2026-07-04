package com.tokit.domain.dividend.entity;

import com.tokit.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "dividend_payout_details")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DividendPayoutDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payout_id", nullable = false)
    private DividendPayout payout;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "wallet_address", nullable = false)
    private String walletAddress;

    @Column(name = "share_ratio", nullable = false, precision = 10, scale = 6)
    private BigDecimal shareRatio; // 지분 비율

    @Column(name = "payout_amount", nullable = false, precision = 20, scale = 4)
    private BigDecimal payoutAmount; // 지급 금액

    @Column(nullable = false)
    private String status; // PENDING, SUCCESS, FAILED

    @Column(name = "error_message")
    private String errorMessage;

    @Builder
    public DividendPayoutDetail(DividendPayout payout, User user, String walletAddress, BigDecimal shareRatio, BigDecimal payoutAmount, String status, String errorMessage) {
        this.payout = payout;
        this.user = user;
        this.walletAddress = walletAddress;
        this.shareRatio = shareRatio;
        this.payoutAmount = payoutAmount;
        this.status = status;
        this.errorMessage = errorMessage;
    }

    public void updateStatus(String status, String errorMessage) {
        this.status = status;
        this.errorMessage = errorMessage;
    }
}
