package com.tokit.domain.reconciliation.entity;

import com.tokit.domain.asset.entity.Asset;
import com.tokit.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "reconciliation_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReconciliationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    @Column(name = "wallet_address", nullable = false)
    private String walletAddress;

    @Column(name = "offchain_balance", nullable = false, precision = 20, scale = 4)
    private BigDecimal offchainBalance;

    @Column(name = "onchain_balance", nullable = false, precision = 20, scale = 4)
    private BigDecimal onchainBalance;

    @Column(name = "difference", nullable = false, precision = 20, scale = 4)
    private BigDecimal difference;

    @Column(name = "checked_at", nullable = false)
    private LocalDateTime checkedAt;

    @Builder
    public ReconciliationLog(User user, Asset asset, String walletAddress, 
                             BigDecimal offchainBalance, BigDecimal onchainBalance, 
                             BigDecimal difference, LocalDateTime checkedAt) {
        this.user = user;
        this.asset = asset;
        this.walletAddress = walletAddress;
        this.offchainBalance = offchainBalance;
        this.onchainBalance = onchainBalance;
        this.difference = difference;
        this.checkedAt = checkedAt != null ? checkedAt : LocalDateTime.now();
    }
}
