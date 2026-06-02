package com.tokit.domain.wallet.entity;

import com.tokit.domain.user.entity.User;
import com.tokit.domain.asset.entity.Asset;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Entity
@Table(name = "wallets")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Wallet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id") // NULL일 경우 법정화폐(KRW)
    private Asset asset;

    @Column(nullable = false, precision = 20, scale = 4)
    private BigDecimal balance; // 출금/주문 가능 잔고

    @Column(name = "locked_balance", nullable = false, precision = 20, scale = 4)
    private BigDecimal lockedBalance; // 주문 대기중 홀딩(Lock) 잔고

    @Version
    private Integer version; // 낙관적 락(Optimistic Lock) 제어용

    @Builder
    public Wallet(User user, Asset asset, BigDecimal balance, BigDecimal lockedBalance) {
        this.user = user;
        this.asset = asset;
        this.balance = balance;
        this.lockedBalance = lockedBalance;
    }

    public void updateBalance(BigDecimal balance, BigDecimal lockedBalance) {
        this.balance = balance;
        this.lockedBalance = lockedBalance;
    }
}
