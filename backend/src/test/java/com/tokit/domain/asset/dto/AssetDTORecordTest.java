package com.tokit.domain.asset.dto;

import com.tokit.domain.asset.controller.AssetController.*;
import com.tokit.domain.asset.entity.Asset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class AssetDTORecordTest {

    @Test
    @DisplayName("RegisterAssetRequest DTO 레코드 검증: 신규 STO 자산 등록 파라미터가 정확히 저장된다.")
    void registerAssetRequest_InstantiationAndAccessors() {
        // Given & When
        RegisterAssetRequest request = new RegisterAssetRequest(
                "GNPM", "Gangnam STO", "0x5FbDB2315678afecb367f032d93F642f64180aa3",
                new BigDecimal("1000000"), new BigDecimal("10000"), "상장완료", 1L
        );

        // Then
        assertThat(request.symbol()).isEqualTo("GNPM");
        assertThat(request.name()).isEqualTo("Gangnam STO");
        assertThat(request.contractAddress()).isEqualTo("0x5FbDB2315678afecb367f032d93F642f64180aa3");
        assertThat(request.totalSupply()).isEqualTo(new BigDecimal("1000000"));
        assertThat(request.issuePrice()).isEqualTo(new BigDecimal("10000"));
        assertThat(request.status()).isEqualTo("상장완료");
        assertThat(request.issuerId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("SubscribeAssetRequest DTO 레코드 검증: 공모 청약 요청 사용자 ID와 신청 금액이 정확히 저장된다.")
    void subscribeAssetRequest_InstantiationAndAccessors() {
        // Given & When
        SubscribeAssetRequest request = new SubscribeAssetRequest(5L, new BigDecimal("500000"));

        // Then
        assertThat(request.userId()).isEqualTo(5L);
        assertThat(request.amount()).isEqualTo(new BigDecimal("500000"));
    }

    @Test
    @DisplayName("AssetResponse 정적 팩토리 검증: Asset 엔티티로부터 누적 모금액 및 주주수가 포함된 DTO로 정확히 매핑된다.")
    void assetResponse_FromFactoryMethod() {
        // Given
        Asset asset = Asset.builder()
                .name("Gangnam STO")
                .symbol("GNPM")
                .contractAddress("0xCONTRACT")
                .totalSupply(new BigDecimal("1000000"))
                .issuePrice(new BigDecimal("10000"))
                .status("상장완료")
                .build();
        setField(asset, "id", 10L);

        // When
        AssetResponse response = AssetResponse.from(asset, new BigDecimal("35750000000"), 1847);

        // Then
        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.symbol()).isEqualTo("GNPM");
        assertThat(response.name()).isEqualTo("Gangnam STO");
        assertThat(response.contractAddress()).isEqualTo("0xCONTRACT");
        assertThat(response.totalSupply()).isEqualTo(new BigDecimal("1000000"));
        assertThat(response.issuePrice()).isEqualTo(new BigDecimal("10000"));
        assertThat(response.status()).isEqualTo("상장완료");
        assertThat(response.currentAmount()).isEqualTo(new BigDecimal("35750000000"));
        assertThat(response.totalInvestors()).isEqualTo(1847);
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
