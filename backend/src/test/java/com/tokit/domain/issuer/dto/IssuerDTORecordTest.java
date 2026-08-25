package com.tokit.domain.issuer.dto;

import com.tokit.domain.asset.entity.AssetReport;
import com.tokit.domain.issuer.controller.IssuerController.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class IssuerDTORecordTest {

    @Test
    @DisplayName("IssuerAssetResponse DTO 레코드 검증: 발행사 STO 자산 및 공모 진행률 정보가 정확히 캡슐화된다.")
    void issuerAssetResponse_InstantiationAndAccessors() {
        // Given & When
        IssuerAssetResponse response = new IssuerAssetResponse(
                1L, "GNPM", "Gangnam STO", "0xCONTRACT",
                new BigDecimal("1000000"), new BigDecimal("10000"), "상장완료",
                new BigDecimal("100"), 120L
        );

        // Then
        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.symbol()).isEqualTo("GNPM");
        assertThat(response.name()).isEqualTo("Gangnam STO");
        assertThat(response.contractAddress()).isEqualTo("0xCONTRACT");
        assertThat(response.totalSupply()).isEqualTo(new BigDecimal("1000000"));
        assertThat(response.issuePrice()).isEqualTo(new BigDecimal("10000"));
        assertThat(response.status()).isEqualTo("상장완료");
        assertThat(response.subscriptionProgress()).isEqualTo(new BigDecimal("100"));
        assertThat(response.totalInvestors()).isEqualTo(120L);
    }

    @Test
    @DisplayName("ShareholderDemographics DTO 레코드 검증: 주주 이름, 지갑 주소, 보유 토큰 및 지분율이 올바르게 캡슐화된다.")
    void shareholderDemographics_InstantiationAndAccessors() {
        // Given & When
        ShareholderDemographics demographics = new ShareholderDemographics(
                "Juhee", "0xSHAREHOLDER", new BigDecimal("100000"), new BigDecimal("10.0")
        );

        // Then
        assertThat(demographics.name()).isEqualTo("Juhee");
        assertThat(demographics.walletAddress()).isEqualTo("0xSHAREHOLDER");
        assertThat(demographics.balance()).isEqualTo(new BigDecimal("100000"));
        assertThat(demographics.shareRatio()).isEqualTo(new BigDecimal("10.0"));
    }

    @Test
    @DisplayName("AssetReportResponse 정적 팩토리 검증: AssetReport 공시 엔티티로부터 DTO로 정확히 매핑된다.")
    void assetReportResponse_FromFactoryMethod() {
        // Given
        LocalDateTime now = LocalDateTime.now();
        AssetReport report = AssetReport.builder()
                .title("2026년 3분기 운용 보고서")
                .filePath("/uploads/report_q3.pdf")
                .createdAt(now)
                .build();
        setField(report, "id", 50L);

        // When
        AssetReportResponse response = AssetReportResponse.from(report);

        // Then
        assertThat(response.id()).isEqualTo(50L);
        assertThat(response.title()).isEqualTo("2026년 3분기 운용 보고서");
        assertThat(response.filePath()).isEqualTo("/uploads/report_q3.pdf");
        assertThat(response.createdAt()).isEqualTo(now.toString());
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
