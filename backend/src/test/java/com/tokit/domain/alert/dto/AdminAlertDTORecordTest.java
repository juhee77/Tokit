package com.tokit.domain.alert.dto;

import com.tokit.domain.reconciliation.controller.ReconciliationController.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AdminAlertDTORecordTest {

    @Test
    @DisplayName("RunBatchResponse DTO 레코드 검증: 대사 배치 즉시 실행 결과 상태값이 정확히 저장된다.")
    void runBatchResponse_InstantiationAndAccessors() {
        // Given & When
        RunBatchResponse response = new RunBatchResponse("COMPLETED");

        // Then
        assertThat(response.status()).isEqualTo("COMPLETED");
    }
}
