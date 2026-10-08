package com.tokit.domain.trade.dto;

import com.tokit.domain.trade.entity.Trade;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 체결 내역 응답. REST 응답과 SSE 스트리밍이 함께 사용한다.
 *
 * <p>스트리밍에 {@link Trade} 엔티티를 그대로 내보내면 두 가지 문제가 생긴다.
 *
 * <ol>
 *   <li><b>지연 로딩</b>: {@code Trade}는 {@code buyOrder}, {@code sellOrder}, {@code asset}을
 *       지연 로딩으로 들고 있고 {@code Order}는 다시 {@code user}로 이어진다. 직렬화 시점에
 *       이들을 건드리므로, 트랜잭션 밖에서 직렬화되면 예외가 난다. 전송 도중 예외가 나면
 *       전파 로직이 이를 끊긴 구독자로 오인해 정상 구독자를 정리해 버린다.</li>
 *   <li><b>과다 노출</b>: 연관 관계를 따라가면 상대 투자자의 정보까지 내려간다. 엔티티에
 *       필드를 추가하는 것이 곧 외부 계약 변경이 되는 문제도 있다.</li>
 * </ol>
 *
 * <p>그래서 DTO 변환은 <b>트랜잭션 안에서</b> 수행하고, 전파에는 이 DTO만 내보낸다.
 */
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
