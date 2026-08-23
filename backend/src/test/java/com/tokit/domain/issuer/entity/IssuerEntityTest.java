package com.tokit.domain.issuer.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IssuerEntityTest {

    @Test
    @DisplayName("Issuer 엔티티 생성: 토큰증권 발행 신탁사명 및 사업자등록번호가 올바르게 저장된다.")
    void builder_StoresIssuerInfoCorrectly() {
        // Given & When
        Issuer issuer = Issuer.builder()
                .companyName("한국토지신탁")
                .bizRegNo("120-81-12345")
                .build();

        // Then
        assertThat(issuer.getCompanyName()).isEqualTo("한국토지신탁");
        assertThat(issuer.getBizRegNo()).isEqualTo("120-81-12345");
    }
}
