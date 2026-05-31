package com.tokit.domain.trade.controller;

import com.tokit.domain.trade.entity.Trade;
import com.tokit.domain.trade.service.TradeService;
import com.tokit.global.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

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
    public ResponseEntity<ApiResponse<List<TradeResponse>>> getTradesByAsset(@PathVariable("symbol") String symbol) {
        List<TradeResponse> list = tradeService.getTradesByAsset(symbol).stream()
                .map(TradeResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(list));
    }

    @GetMapping(value = "/subscribe/{symbol}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribeTrades(@PathVariable("symbol") String symbol) {
        return tradeService.subscribeTrades(symbol);
    }
}
