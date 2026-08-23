package com.tokit.global.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessExceptionTest {

    @Test
    @DisplayName("BusinessException 생성자 검증: ErrorCode 및 커스텀 메시지가 정상 바인딩된다.")
    void businessException_BindsErrorCodeAndCustomMessage() {
        // Given & When
        BusinessException exWithCustomMessage = new BusinessException("예치금 잔액이 부족합니다.", ErrorCode.INSUFFICIENT_BALANCE);
        BusinessException exWithErrorCodeMessage = new BusinessException(ErrorCode.MEMBER_NOT_FOUND);

        // Then
        assertThat(exWithCustomMessage.getMessage()).isEqualTo("예치금 잔액이 부족합니다.");
        assertThat(exWithCustomMessage.getErrorCode()).isEqualTo(ErrorCode.INSUFFICIENT_BALANCE);

        assertThat(exWithErrorCodeMessage.getMessage()).isEqualTo(ErrorCode.MEMBER_NOT_FOUND.getMessage());
        assertThat(exWithErrorCodeMessage.getErrorCode()).isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
    }
}
