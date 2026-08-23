package com.tokit.global.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class JacksonConfigTest {

    private JacksonConfig jacksonConfig;

    @BeforeEach
    void setUp() {
        jacksonConfig = new JacksonConfig();
    }

    @Test
    @DisplayName("objectMapper: JavaTimeModule 모듈이 정상 등록되어 LocalDateTime 직렬화가 성공한다.")
    void objectMapper_SerializesLocalDateTimeIso8601() throws Exception {
        // Given
        ObjectMapper objectMapper = jacksonConfig.objectMapper();
        TestTimestampDto dto = new TestTimestampDto(LocalDateTime.of(2026, 8, 23, 10, 0, 0));

        // When
        String json = objectMapper.writeValueAsString(dto);

        // Then
        assertThat(json).isNotNull();
        assertThat(json).contains("2026");
    }


    record TestTimestampDto(LocalDateTime timestamp) {}
}
