package com.tokit.domain.trade.service;

import com.tokit.domain.trade.entity.Trade;
import com.tokit.domain.trade.repository.TradeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class TradeService {

    private final TradeRepository tradeRepository;
    
    // 심볼별 SSE Emitter 리스트 관리
    private final Map<String, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    @Transactional
    public Trade saveTrade(Long buyOrderId, Long sellOrderId, String assetSymbol, BigDecimal price, BigDecimal quantity) {
        Trade trade = Trade.builder()
                .buyOrderId(buyOrderId)
                .sellOrderId(sellOrderId)
                .assetSymbol(assetSymbol)
                .price(price)
                .quantity(quantity)
                .build();
        
        Trade savedTrade = tradeRepository.save(trade);
        
        // 실시간 스트리밍 전송
        broadcastTrade(savedTrade);
        
        return savedTrade;
    }

    public List<Trade> getTradesByAsset(String assetSymbol) {
        return tradeRepository.findByAssetSymbolOrderByCreatedAtDesc(assetSymbol);
    }

    public SseEmitter subscribeTrades(String assetSymbol) {
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L); // 30분 만료
        
        emitters.computeIfAbsent(assetSymbol, k -> new CopyOnWriteArrayList<>()).add(emitter);
        
        emitter.onCompletion(() -> removeEmitter(assetSymbol, emitter));
        emitter.onTimeout(() -> removeEmitter(assetSymbol, emitter));
        emitter.onError((e) -> removeEmitter(assetSymbol, emitter));
        
        // 연결 성공 이벤트 전송
        try {
            emitter.send(SseEmitter.event()
                    .name("INIT")
                    .data("Connected to Trade Stream for " + assetSymbol));
        } catch (IOException e) {
            removeEmitter(assetSymbol, emitter);
        }
        
        return emitter;
    }

    private void removeEmitter(String assetSymbol, SseEmitter emitter) {
        List<SseEmitter> symbolEmitters = emitters.get(assetSymbol);
        if (symbolEmitters != null) {
            symbolEmitters.remove(emitter);
            if (symbolEmitters.isEmpty()) {
                emitters.remove(assetSymbol);
            }
        }
    }

    private void broadcastTrade(Trade trade) {
        List<SseEmitter> symbolEmitters = emitters.get(trade.getAssetSymbol());
        if (symbolEmitters == null || symbolEmitters.isEmpty()) {
            return;
        }
        
        log.info("Broadcasting trade: {}", trade);
        for (SseEmitter emitter : symbolEmitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("TRADE")
                        .id(trade.getId().toString())
                        .data(trade));
            } catch (IOException e) {
                removeEmitter(trade.getAssetSymbol(), emitter);
            }
        }
    }
}
