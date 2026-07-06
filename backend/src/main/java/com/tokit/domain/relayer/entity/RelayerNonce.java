package com.tokit.domain.relayer.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "relayer_nonces")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RelayerNonce {

    @Id
    @Column(name = "wallet_address", nullable = false)
    private String walletAddress;

    @Column(name = "next_nonce", nullable = false)
    private Long nextNonce;

    @Column(name = "last_tx_date")
    private java.time.LocalDate lastTxDate;

    @Column(name = "daily_tx_count", nullable = false)
    private Integer dailyTxCount;

    @Builder
    public RelayerNonce(String walletAddress, Long nextNonce, java.time.LocalDate lastTxDate, Integer dailyTxCount) {
        this.walletAddress = walletAddress.toLowerCase(); 
        this.nextNonce = nextNonce != null ? nextNonce : 0L;
        this.lastTxDate = lastTxDate;
        this.dailyTxCount = dailyTxCount != null ? dailyTxCount : 0;
    }

    public void incrementNonce() {
        this.nextNonce += 1;
    }

    public void checkAndIncrementLimit() {
        java.time.LocalDate today = java.time.LocalDate.now();
        if (this.lastTxDate == null || !this.lastTxDate.isEqual(today)) {
            this.lastTxDate = today;
            this.dailyTxCount = 1;
        } else {
            if (this.dailyTxCount >= 5) {
                throw new IllegalArgumentException("일일 대납 이체 한도(5회)를 초과했습니다.");
            }
            this.dailyTxCount += 1;
        }
    }
}
