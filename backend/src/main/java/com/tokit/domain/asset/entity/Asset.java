package com.tokit.domain.asset.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "assets")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Asset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String symbol; // 예: "APPL-STO"

    @Column(nullable = false)
    private String name; // 예: "Apple Security Token"

    @Column(nullable = false, name = "contract_address")
    private String contractAddress; // 이더리움 ERC-1400 토큰 주소

    @Column(nullable = false, name = "total_supply")
    private BigDecimal totalSupply;

    @Builder
    public Asset(String symbol, String name, String contractAddress, BigDecimal totalSupply) {
        this.symbol = symbol;
        this.name = name;
        this.contractAddress = contractAddress;
        this.totalSupply = totalSupply;
    }
}
