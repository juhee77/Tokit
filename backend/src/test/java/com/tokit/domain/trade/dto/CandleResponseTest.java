package com.tokit.domain.trade.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class CandleResponseTest {

    @Test
    @DisplayName("CandleResponse.from: DB Native SQL 쿼리의 1분 단위 OHLCV 시세 데이터 배열을 성공적으로 파싱한다.")
    void from_SqlRowArray_ParsesOhlcvCorrectly() {
        // Given
        LocalDateTime time = LocalDateTime.of(2026, 8, 23, 10, 0, 0);
        Object[] rowWithLocalDateTime = new Object[]{
                time,
                new BigDecimal("12500"),
                new BigDecimal("12700"),
                new BigDecimal("12400"),
                new BigDecimal("12600"),
                new BigDecimal("1500")
        };

        Object[] rowWithTimestamp = new Object[]{
                Timestamp.valueOf(time),
                new BigDecimal("12500"),
                new BigDecimal("12700"),
                new BigDecimal("12400"),
                new BigDecimal("12600"),
                new BigDecimal("1500")
        };

        // When
        CandleResponse candle1 = CandleResponse.from(rowWithLocalDateTime);
        CandleResponse candle2 = CandleResponse.from(rowWithTimestamp);

        // Then
        assertThat(candle1.time()).isEqualTo(time);
        assertThat(candle1.open()).isEqualTo(new BigDecimal("12500"));
        assertThat(candle1.high()).isEqualTo(new BigDecimal("12700"));
        assertThat(candle1.low()).isEqualTo(new BigDecimal("12400"));
        assertThat(candle1.close()).isEqualTo(new BigDecimal("12600"));
        assertThat(candle1.volume()).isEqualTo(new BigDecimal("1500"));

        assertThat(candle2.time()).isEqualTo(time);
    }
}
