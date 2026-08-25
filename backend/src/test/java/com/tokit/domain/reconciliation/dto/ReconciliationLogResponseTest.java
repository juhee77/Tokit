package com.tokit.domain.reconciliation.dto;

import com.tokit.domain.asset.entity.Asset;
import com.tokit.domain.reconciliation.controller.ReconciliationController.*;
import com.tokit.domain.reconciliation.entity.ReconciliationLog;
import com.tokit.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ReconciliationLogResponseTest {

    @Test
    @DisplayName("ReconciliationLogResponse 정적 팩토리 검증: 온-오프체인 정합성 대사 불일치 엔티티가 DTO로 정확히 매핑된다.")
    void from_FactoryMethodMapsLogCorrectly() {
        // Given
        User user = User.builder().name("Juhee").build();
        setField(user, "id", 1L);

        Asset asset = Asset.builder().symbol("GNPM").build();
        setField(asset, "id", 10L);

        LocalDateTime now = LocalDateTime.now();
        ReconciliationLog log = ReconciliationLog.builder()
                .user(user)
                .asset(asset)
                .walletAddress("0xWALLET")
                .offchainBalance(new BigDecimal("100"))
                .onchainBalance(new BigDecimal("90"))
                .difference(new BigDecimal("10"))
                .checkedAt(now)
                .build();
        setField(log, "id", 99L);

        // When
        ReconciliationLogResponse response = ReconciliationLogResponse.from(log);

        // Then
        assertThat(response.id()).isEqualTo(99L);
        assertThat(response.userId()).isEqualTo(1L);
        assertThat(response.userName()).isEqualTo("Juhee");
        assertThat(response.assetId()).isEqualTo(10L);
        assertThat(response.assetSymbol()).isEqualTo("GNPM");
        assertThat(response.walletAddress()).isEqualTo("0xWALLET");
        assertThat(response.offchainBalance()).isEqualTo(new BigDecimal("100"));
        assertThat(response.onchainBalance()).isEqualTo(new BigDecimal("90"));
        assertThat(response.difference()).isEqualTo(new BigDecimal("10"));
        assertThat(response.checkedAt()).isEqualTo(now.toString());
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
