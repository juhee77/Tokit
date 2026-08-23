package com.tokit.global.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorCodeTest {

    @Test
    @DisplayName("ErrorCode 열거형 매핑 검증: HTTP Status 및 커스텀 에러 코드가 정밀 할당되어 있다.")
    void errorCodeEnumValues_MatchHttpStatusAndMessages() {
        // Given & When & Then
        assertThat(ErrorCode.INVALID_INPUT_VALUE.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ErrorCode.INVALID_INPUT_VALUE.getCode()).isEqualTo("C001");

        assertThat(ErrorCode.MEMBER_NOT_FOUND.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ErrorCode.MEMBER_NOT_FOUND.getCode()).isEqualTo("M001");

        assertThat(ErrorCode.INSUFFICIENT_BALANCE.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ErrorCode.INSUFFICIENT_BALANCE.getCode()).isEqualTo("A002");

        assertThat(ErrorCode.ORDER_ALREADY_CLOSED.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ErrorCode.ORDER_ALREADY_CLOSED.getCode()).isEqualTo("O004");
    }
}
