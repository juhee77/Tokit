package com.tokit.domain.asset.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class AssetEntityTest {

    @Test
    @DisplayName("Asset 엔티티 생성 및 상태 변경: 기초 자산 심볼, 공모가, 스마트 컨트랙트 주소 및 거래 상태 전환이 정상 작동한다.")
    void builderAndUpdateStatus_WorksCorrectly() {
        // Given
        Asset asset = Asset.builder()
                .name("Hongdae STO")
                .symbol("HONGDAE-STO")
                .contractAddress("0xCONTRACT_HONGDAE")
                .totalSupply(new BigDecimal("200000"))
                .issuePrice(new BigDecimal("5000"))
                .status("공모중")
                .build();

        // When
        asset.updateStatus("상장완료");

        // Then
        assertThat(asset.getName()).isEqualTo("Hongdae STO");
        assertThat(asset.getSymbol()).isEqualTo("HONGDAE-STO");
        assertThat(asset.getContractAddress()).isEqualTo("0xCONTRACT_HONGDAE");
        assertThat(asset.getTotalSupply()).isEqualTo(new BigDecimal("200000"));
        assertThat(asset.getIssuePrice()).isEqualTo(new BigDecimal("5000"));
        assertThat(asset.getStatus()).isEqualTo("상장완료");
    }
}
