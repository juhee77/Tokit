package com.tokit.domain.asset.entity;

import com.tokit.domain.issuer.entity.Issuer;
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "issuer_id", nullable = false)
    private Issuer issuer;

    @Column(nullable = false)
    private String name; // 자산명 (ex. 강남A빌딩 토큰)

    @Column(name = "total_supply", nullable = false, precision = 20, scale = 4)
    private BigDecimal totalSupply; // 총 발행량

    @Column(name = "issue_price", nullable = false, precision = 20, scale = 4)
    private BigDecimal issuePrice; // 초기 공모가

    @Column(nullable = false)
    private String status; // 상태 (청약중/거래중/종료)

    @Column(nullable = false, unique = true)
    private String symbol; // 토큰 심볼 (ex. APPL-STO)

    @Column(name = "contract_address", nullable = false)
    private String contractAddress; // 블록체인 스마트 컨트랙트 주소

    @Builder
    public Asset(Issuer issuer, String name, BigDecimal totalSupply, BigDecimal issuePrice, String status, String symbol, String contractAddress) {
        this.issuer = issuer;
        this.name = name;
        this.totalSupply = totalSupply;
        this.issuePrice = issuePrice;
        this.status = status;
        this.symbol = symbol;
        this.contractAddress = contractAddress;
    }

    public void updateStatus(String status) {
        this.status = status;
    }
}
