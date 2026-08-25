package com.tokit.domain.asset.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class AssetReportEntityTest {

    @Test
    @DisplayName("AssetReport 엔티티 생성: 기초자산 분기별 공시 보고서 제목, 파일 경로 및 생성일시가 올바르게 기록된다.")
    void builder_StoresReportInfoCorrectly() {
        // Given
        Asset asset = Asset.builder().name("Songdo STO").symbol("SONGDO-STO").build();
        LocalDateTime now = LocalDateTime.now();

        // When
        AssetReport report = AssetReport.builder()
                .asset(asset)
                .title("2026년 3분기 결산 보고서")
                .filePath("/uploads/reports/2026_q3_songdo.pdf")
                .createdAt(now)
                .build();

        // Then
        assertThat(report.getAsset()).isEqualTo(asset);
        assertThat(report.getTitle()).isEqualTo("2026년 3분기 결산 보고서");
        assertThat(report.getFilePath()).isEqualTo("/uploads/reports/2026_q3_songdo.pdf");
        assertThat(report.getCreatedAt()).isEqualTo(now);
    }
}
