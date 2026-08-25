package com.tokit.domain.relayer.dto;

import com.tokit.domain.relayer.controller.RelayerController.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class RelayerDTORecordTest {

    @Test
    @DisplayName("RelayTransferRequest DTO 레코드 검증: 가스리스 메타트랜잭션 서명 및 이체 정보가 정확히 저장된다.")
    void relayTransferRequest_InstantiationAndAccessors() {
        // Given & When
        RelayTransferRequest request = new RelayTransferRequest(
                "0xFROM_ADDRESS",
                "0xTO_ADDRESS",
                "GNPM",
                new BigDecimal("100"),
                5L,
                "0xSIGNATURE_DATA"
        );

        // Then
        assertThat(request.fromAddress()).isEqualTo("0xFROM_ADDRESS");
        assertThat(request.toAddress()).isEqualTo("0xTO_ADDRESS");
        assertThat(request.assetSymbol()).isEqualTo("GNPM");
        assertThat(request.amount()).isEqualTo(new BigDecimal("100"));
        assertThat(request.nonce()).isEqualTo(5L);
        assertThat(request.signature()).isEqualTo("0xSIGNATURE_DATA");
    }

    @Test
    @DisplayName("NonceResponse DTO 레코드 검증: 지갑 주소 및 다음 순서 번호(Nonce) 정보가 올바르게 캡슐화된다.")
    void nonceResponse_InstantiationAndAccessors() {
        // Given & When
        NonceResponse response = new NonceResponse("0xMY_WALLET", 12L);

        // Then
        assertThat(response.walletAddress()).isEqualTo("0xMY_WALLET");
        assertThat(response.nonce()).isEqualTo(12L);
    }
}
