package com.tokit.infra.rabbitmq;

import com.tokit.domain.asset.entity.Asset;
import com.tokit.domain.asset.repository.AssetRepository;
import com.tokit.infra.alert.SlackAlertService;
import com.tokit.infra.blockchain.BlockchainException;
import com.tokit.infra.blockchain.ContractService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TradeEventConsumerTest {

    @InjectMocks
    private TradeEventConsumer consumer;

    @Mock
    private ContractService contractService;

    @Mock
    private SlackAlertService slackAlertService;

    @Mock
    private com.tokit.global.observability.TradingMetrics tradingMetrics;

    @Mock
    private AssetRepository assetRepository;

    private TradeEvent event;

    @BeforeEach
    void setUp() {
        event = new TradeEvent(
                77L, 1L, 2L, "GANGNAM-STO",
                "0xBUYER_ADDRESS", "0xSELLER_ADDRESS",
                new BigDecimal("10000"), new BigDecimal("5"));
    }

    private Asset asset(String symbol, String contractAddress) {
        return Asset.builder().symbol(symbol).name(symbol).contractAddress(contractAddress)
                .totalSupply(BigDecimal.ONE).issuePrice(BigDecimal.ONE).status("거래중").build();
    }

    @Test
    @DisplayName("온체인 결제 성공 시 경보를 보내지 않는다.")
    void onSuccess_NoAlert() {
        when(assetRepository.findBySymbol("GANGNAM-STO"))
                .thenReturn(Optional.of(asset("GANGNAM-STO", "0xGANGNAM_CONTRACT")));

        consumer.consumeTradeEvent(event);

        verify(contractService, times(1)).handleTransferByPartition(
                eq("0xGANGNAM_CONTRACT"), eq("GANGNAM-STO"), eq("DEFAULT"),
                eq("0xSELLER_ADDRESS"), eq("0xBUYER_ADDRESS"), any());
        verifyNoInteractions(slackAlertService);
    }

    @Test
    @DisplayName("종목마다 해당 종목의 컨트랙트 주소로 이체한다.")
    void eachAssetSettlesOnItsOwnContract() {
        when(assetRepository.findBySymbol("GANGNAM-STO"))
                .thenReturn(Optional.of(asset("GANGNAM-STO", "0xGANGNAM_CONTRACT")));
        when(assetRepository.findBySymbol("PANGYO-STO"))
                .thenReturn(Optional.of(asset("PANGYO-STO", "0xPANGYO_CONTRACT")));
        TradeEvent pangyoEvent = new TradeEvent(
                78L, 3L, 4L, "PANGYO-STO",
                "0xBUYER_ADDRESS", "0xSELLER_ADDRESS",
                new BigDecimal("20000"), new BigDecimal("1"));

        consumer.consumeTradeEvent(event);
        consumer.consumeTradeEvent(pangyoEvent);

        verify(contractService).handleTransferByPartition(
                eq("0xGANGNAM_CONTRACT"), eq("GANGNAM-STO"), any(), any(), any(), any());
        verify(contractService).handleTransferByPartition(
                eq("0xPANGYO_CONTRACT"), eq("PANGYO-STO"), any(), any(), any(), any());
    }

    @Test
    @DisplayName("종목을 찾을 수 없으면 기본 컨트랙트로 보내지 않고 경보를 발송한다.")
    void unknownAsset_AlertsWithoutTransfer() {
        when(assetRepository.findBySymbol("GANGNAM-STO")).thenReturn(Optional.empty());

        assertThatCode(() -> consumer.consumeTradeEvent(event)).doesNotThrowAnyException();

        verifyNoInteractions(contractService);
        verify(slackAlertService, times(1)).sendAlert(anyString(), contains("77"));
    }

    @Test
    @DisplayName("온체인 결제가 최종 실패해도 예외를 전파하지 않고 경보를 발송한다.")
    void onFailure_SwallowsExceptionAndAlerts() {
        when(assetRepository.findBySymbol("GANGNAM-STO"))
                .thenReturn(Optional.of(asset("GANGNAM-STO", "0xGANGNAM_CONTRACT")));
        doThrow(new BlockchainException("Blockchain TX reverted"))
                .when(contractService).handleTransferByPartition(anyString(), anyString(), anyString(), anyString(), anyString(), any());

        // 매칭 파이프라인이 멈추지 않도록 예외는 소비 단계에서 흡수되어야 합니다.
        assertThatCode(() -> consumer.consumeTradeEvent(event)).doesNotThrowAnyException();

        // 온체인/오프체인 불일치가 조용히 묻히지 않도록 운영자에게 알립니다.
        verify(slackAlertService, times(1)).sendAlert(anyString(), contains("77"));
    }
}
