package com.tokit.domain.trade.controller;

import com.tokit.domain.trade.entity.Trade;
import com.tokit.domain.trade.service.TradeService;
import com.tokit.domain.trade.dto.CandleResponse;
import com.tokit.global.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Tag(name = "05. Trade (체결)", description = "토큰증권(STO) 체결 내역 조회 및 실시간 SSE 스트리밍 API")
@RestController
@RequestMapping("/api/trades")
@RequiredArgsConstructor
public class TradeController {

    private final TradeService tradeService;

    public record TradeResponse(
        Long id,
        Long buyOrderId,
        Long sellOrderId,
        String assetSymbol,
        BigDecimal price,
        BigDecimal quantity,
        LocalDateTime createdAt
    ) {
        public static TradeResponse from(Trade trade) {
            return new TradeResponse(
                trade.getId(),
                trade.getBuyOrderId(),
                trade.getSellOrderId(),
                trade.getAssetSymbol(),
                trade.getPrice(),
                trade.getQuantity(),
                trade.getCreatedAt()
            );
        }
    }

    @GetMapping("/asset/{symbol}")
    @Operation(summary = "자산별 체결 내역 조회", description = "특정 자산 심볼(예: APPL-STO)의 최근 체결 내역 리스트를 조회합니다.")
    public ResponseEntity<ApiResponse<List<TradeResponse>>> getTradesByAsset(@PathVariable("symbol") String symbol) {
        List<TradeResponse> list = tradeService.getTradesByAsset(symbol).stream()
                .map(TradeResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(list));
    }

    @GetMapping("/asset/{symbol}/candles")
    @Operation(summary = "자산별 1분 단위 캔들스틱 시세 조회", description = "특정 자산 심볼의 1분 단위 OHLCV 시세 목록을 조회합니다.")
    public ResponseEntity<ApiResponse<List<CandleResponse>>> getCandlesBySymbol(@PathVariable("symbol") String symbol) {
        List<CandleResponse> list = tradeService.getCandlesBySymbol(symbol);
        return ResponseEntity.ok(ApiResponse.success(list));
    }

    @GetMapping(value = "/subscribe/{symbol}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "실시간 체결 내역 구독(SSE)", description = "특정 자산 심볼의 체결 내역을 실시간으로 구독합니다. (text/event-stream)")
    public SseEmitter subscribeTrades(@PathVariable("symbol") String symbol) {
        return tradeService.subscribeTrades(symbol);
    }
}
