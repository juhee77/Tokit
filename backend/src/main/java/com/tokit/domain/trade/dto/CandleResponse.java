package com.tokit.domain.trade.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "1분 단위 캔들스틱 시세 데이터 DTO")
public record CandleResponse(
    @Schema(description = "봉 시작 일시 (Time Bucket)", example = "2026-06-24T22:40:00")
    LocalDateTime time,
    
    @Schema(description = "시가 (Open Price)", example = "12500.0000")
    BigDecimal open,
    
    @Schema(description = "고가 (High Price)", example = "12700.0000")
    BigDecimal high,
    
    @Schema(description = "저가 (Low Price)", example = "12400.0000")
    BigDecimal low,
    
    @Schema(description = "종가 (Close Price)", example = "12600.0000")
    BigDecimal close,
    
    @Schema(description = "거래량 (Traded Volume)", example = "1500.0000")
    BigDecimal volume
) {
    public static CandleResponse from(Object[] row) {
        LocalDateTime time;
        if (row[0] instanceof java.time.LocalDateTime) {
            time = (LocalDateTime) row[0];
        } else if (row[0] instanceof java.sql.Timestamp) {
            time = ((java.sql.Timestamp) row[0]).toLocalDateTime();
        } else {
            time = LocalDateTime.parse(row[0].toString().replace(" ", "T").split("\\.")[0]);
        }
        return new CandleResponse(
            time,
            (BigDecimal) row[1],
            (BigDecimal) row[2],
            (BigDecimal) row[3],
            (BigDecimal) row[4],
            (BigDecimal) row[5]
        );
    }
}
