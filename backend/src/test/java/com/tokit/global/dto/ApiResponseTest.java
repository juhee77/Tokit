package com.tokit.global.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiResponseTest {

    @Test
    @DisplayName("ApiResponse.success(data): HTTP 200, 'SUCCESS' 메시지 및 페이로드가 포함된 응답을 생성한다.")
    void success_WithData_Returns200AndMessageSuccess() {
        // Given
        String dataPayload = "Sample Asset Payload";

        // When
        ApiResponse<String> response = ApiResponse.success(dataPayload);

        // Then
        assertThat(response.status()).isEqualTo(200);
        assertThat(response.message()).isEqualTo("SUCCESS");
        assertThat(response.data()).isEqualTo("Sample Asset Payload");
    }

    @Test
    @DisplayName("ApiResponse.success(): HTTP 200, 'SUCCESS' 메시지 및 null 데이터가 포함된 응답을 생성한다.")
    void success_WithoutData_Returns200AndNullData() {
        // When
        ApiResponse<Void> response = ApiResponse.success();

        // Then
        assertThat(response.status()).isEqualTo(200);
        assertThat(response.message()).isEqualTo("SUCCESS");
        assertThat(response.data()).isNull();
    }

    @Test
    @DisplayName("ApiResponse.error(status, message): 지정된 에러 커스텀 상태 코드와 메시지를 감싸는 실패 응답을 생성한다.")
    void error_ReturnsSpecifiedStatusAndMessage() {
        // When
        ApiResponse<Object> response = ApiResponse.error(400, "Insufficient token balance");

        // Then
        assertThat(response.status()).isEqualTo(400);
        assertThat(response.message()).isEqualTo("Insufficient token balance");
        assertThat(response.data()).isNull();
    }
}
