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

    @Builder
    public RelayerNonce(String walletAddress, Long nextNonce) {
        this.walletAddress = walletAddress.toLowerCase(); // 일관성을 위해 소문자화
        this.nextNonce = nextNonce != null ? nextNonce : 0L;
    }

    public void incrementNonce() {
        this.nextNonce += 1;
    }
}
