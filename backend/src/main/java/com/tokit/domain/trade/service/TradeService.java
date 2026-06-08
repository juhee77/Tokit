package com.tokit.domain.trade.service;

import com.tokit.domain.trade.entity.Trade;
import com.tokit.domain.trade.repository.TradeRepository;
import com.tokit.domain.order.entity.Order;
import com.tokit.domain.order.repository.OrderRepository;
import com.tokit.domain.asset.entity.Asset;
import com.tokit.domain.asset.repository.AssetRepository;
import com.tokit.domain.wallet.entity.Wallet;
import com.tokit.domain.wallet.repository.WalletRepository;
import com.tokit.infra.rabbitmq.TradeEvent;
import com.tokit.infra.rabbitmq.OrderEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
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
    private final OrderRepository orderRepository;
    private final AssetRepository assetRepository;
    private final WalletRepository walletRepository;
    private final OrderEventPublisher orderEventPublisher;
    
    // 심볼별 SSE Emitter 리스트 관리
    private final Map<String, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    @Transactional
    public Trade saveTrade(Long buyOrderId, Long sellOrderId, String assetSymbol, BigDecimal price, BigDecimal quantity) {
        Order buyOrder = orderRepository.findById(buyOrderId)
                .orElseThrow(() -> new IllegalArgumentException("Buy order not found: " + buyOrderId));
        Order sellOrder = orderRepository.findById(sellOrderId)
                .orElseThrow(() -> new IllegalArgumentException("Sell order not found: " + sellOrderId));
        Asset asset = assetRepository.findBySymbol(assetSymbol)
                .orElseThrow(() -> new IllegalArgumentException("Asset not found with symbol: " + assetSymbol));

        Trade trade = Trade.builder()
                .buyOrder(buyOrder)
                .sellOrder(sellOrder)
                .asset(asset)
                .price(price)
                .quantity(quantity)
                .tradedAt(LocalDateTime.now())
                .build();
        
        // 1. 체결 금액 및 수량 계산
        BigDecimal totalAmount = price.multiply(quantity);

        // 2. 매수자(Buyer)와 매도자(Seller)의 원화(KRW) 지갑 비관적 락 조회 및 업데이트
        Wallet buyerKrwWallet = walletRepository.findKrwWalletByUserIdWithPessimisticLock(buyOrder.getUser().getId())
                .orElseThrow(() -> new IllegalArgumentException("Buyer KRW wallet not found"));
        Wallet sellerKrwWallet = walletRepository.findKrwWalletByUserIdWithPessimisticLock(sellOrder.getUser().getId())
                .orElseThrow(() -> new IllegalArgumentException("Seller KRW wallet not found"));

        // 매수자: 홀딩된 원화 차감
        buyerKrwWallet.updateBalance(buyerKrwWallet.getBalance(), buyerKrwWallet.getLockedBalance().subtract(totalAmount));
        // 매도자: 원화 잔고 증가
        sellerKrwWallet.updateBalance(sellerKrwWallet.getBalance().add(totalAmount), sellerKrwWallet.getLockedBalance());

        // 3. 매수자(Buyer)와 매도자(Seller)의 토큰(Asset) 지갑 비관적 락 조회 및 업데이트
        // 매수자는 지갑이 없을 수 있으므로 없으면 새로 생성 후 락
        Wallet buyerAssetWallet = walletRepository.findAssetWalletByUserIdAndAssetIdWithPessimisticLock(buyOrder.getUser().getId(), asset.getId())
                .orElseGet(() -> {
                    Wallet newWallet = Wallet.builder()
                            .user(buyOrder.getUser())
                            .asset(asset)
                            .balance(BigDecimal.ZERO)
                            .lockedBalance(BigDecimal.ZERO)
                            .build();
                    return walletRepository.save(newWallet);
                });
                
        Wallet sellerAssetWallet = walletRepository.findAssetWalletByUserIdAndAssetIdWithPessimisticLock(sellOrder.getUser().getId(), asset.getId())
                .orElseThrow(() -> new IllegalArgumentException("Seller Asset wallet not found"));

        // 매도자: 홀딩된 토큰 수량 차감
        sellerAssetWallet.updateBalance(sellerAssetWallet.getBalance(), sellerAssetWallet.getLockedBalance().subtract(quantity));
        // 매수자: 토큰 잔고 증가
        buyerAssetWallet.updateBalance(buyerAssetWallet.getBalance().add(quantity), buyerAssetWallet.getLockedBalance());
        
        Trade savedTrade = tradeRepository.save(trade);
        
        // RabbitMQ 체결 이벤트 발행 (온체인 동기화용)
        TradeEvent tradeEvent = new TradeEvent(
                savedTrade.getId(),
                buyOrder.getId(),
                sellOrder.getId(),
                assetSymbol,
                buyOrder.getUser().getWalletAddress(),
                sellOrder.getUser().getWalletAddress(),
                price,
                quantity
        );
        orderEventPublisher.publishTrade(tradeEvent);
        
        // 실시간 스트리밍 전송
        broadcastTrade(savedTrade);
        
        return savedTrade;
    }

    public List<Trade> getTradesByAsset(String assetSymbol) {
        return tradeRepository.findByAsset_SymbolOrderByTradedAtDesc(assetSymbol);
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
