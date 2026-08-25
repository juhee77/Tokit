package com.tokit.domain.dividend.dto;

import com.tokit.domain.asset.entity.Asset;
import com.tokit.domain.dividend.controller.DividendController.*;
import com.tokit.domain.dividend.entity.DividendPayout;
import com.tokit.domain.dividend.entity.DividendPayoutDetail;
import com.tokit.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class DividendDTORecordTest {

    @Test
    @DisplayName("CreateDividendRequest 레코드 검증: 자산 ID와 총 배당금액 정보가 올바르게 저장된다.")
    void createDividendRequest_InstantiationAndAccessors() {
        // Given & When
        CreateDividendRequest request = new CreateDividendRequest(1L, new BigDecimal("50000000"));

        // Then
        assertThat(request.assetId()).isEqualTo(1L);
        assertThat(request.totalDividendAmount()).isEqualTo(new BigDecimal("50000000"));
    }

    @Test
    @DisplayName("DividendResponse 정적 팩토리 검증: DividendPayout 엔티티로부터 팩토리 매핑이 정상 수행된다.")
    void dividendResponse_FromFactoryMethod() {
        // Given
        Asset asset = Asset.builder().name("Songdo STO").symbol("SONGDO-STO").build();
        setField(asset, "id", 2L);

        LocalDateTime now = LocalDateTime.now();
        DividendPayout payout = DividendPayout.builder()
                .asset(asset)
                .totalDividendAmount(new BigDecimal("10000000"))
                .payoutDate(now)
                .status("COMPLETED")
                .build();
        setField(payout, "id", 20L);

        // When
        DividendResponse response = DividendResponse.from(payout);

        // Then
        assertThat(response.id()).isEqualTo(20L);
        assertThat(response.assetId()).isEqualTo(2L);
        assertThat(response.assetSymbol()).isEqualTo("SONGDO-STO");
        assertThat(response.assetName()).isEqualTo("Songdo STO");
        assertThat(response.totalDividendAmount()).isEqualTo(new BigDecimal("10000000"));
        assertThat(response.payoutDate()).isEqualTo(now.toString());
        assertThat(response.status()).isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("DividendDetailResponse 정적 팩토리 검증: DividendPayoutDetail 엔티티로부터 주별 분부율과 수령액이 정상 매핑된다.")
    void dividendDetailResponse_FromFactoryMethod() {
        // Given
        User user = User.builder().name("Juhee").build();
        setField(user, "id", 5L);

        DividendPayoutDetail detail = DividendPayoutDetail.builder()
                .user(user)
                .walletAddress("0xDIVIDEND_WALLET")
                .shareRatio(new BigDecimal("12.5"))
                .payoutAmount(new BigDecimal("1250000"))
                .status("SUCCESS")
                .build();
        setField(detail, "id", 100L);

        // When
        DividendDetailResponse response = DividendDetailResponse.from(detail);

        // Then
        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.userId()).isEqualTo(5L);
        assertThat(response.userName()).isEqualTo("Juhee");
        assertThat(response.walletAddress()).isEqualTo("0xDIVIDEND_WALLET");
        assertThat(response.shareRatio()).isEqualTo(new BigDecimal("12.5"));
        assertThat(response.payoutAmount()).isEqualTo(new BigDecimal("1250000"));
        assertThat(response.status()).isEqualTo("SUCCESS");
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
