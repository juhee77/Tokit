package com.tokit.domain.user.dto;

import com.tokit.domain.user.controller.UserController.UserResponse;
import com.tokit.domain.user.entity.InvestorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MyPageResponseTest {

    @Test
    @DisplayName("MyPageResponse 레코드 데이터 보존 검증: 마이페이지 통합 포트폴리오(User, Wallets, Orders, Trades) DTO 필드가 정확히 캡슐화된다.")
    void recordInstantiation_HoldsCompositePortfolioDataCorrectly() {
        // Given
        UserResponse userResponse = new UserResponse(
                10L, "investor@tokit.com", "Investor", "0xMY_WALLET", true
        );

        // When
        MyPageResponse response = new MyPageResponse(
                userResponse,
                List.of(),
                List.of(),
                List.of()
        );

        // Then
        assertThat(response.user()).isEqualTo(userResponse);
        assertThat(response.user().email()).isEqualTo("investor@tokit.com");
        assertThat(response.wallets()).isEmpty();
        assertThat(response.orders()).isEmpty();
        assertThat(response.trades()).isEmpty();
    }
}
