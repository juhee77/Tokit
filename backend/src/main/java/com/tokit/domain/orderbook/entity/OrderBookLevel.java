package com.tokit.domain.orderbook.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 호가창의 한 가격대(잔량 집계).
 *
 * <p>주문 원장으로부터 파생된 집계이므로 이 엔티티를 직접 수정하지 않는다. 갱신은 언제나
 * "특정 가격대에 얼마를 더하거나 뺀다"는 원자적 증감이어야 하며, 영속성 컨텍스트에 올려
 * read-modify-write 하면 동시 갱신 시 갱신 손실이 생긴다.
 * ({@code OrderBookLevelRepository}가 네이티브 UPSERT만 노출하는 이유)
 */
@Entity
@Table(name = "order_book_levels")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderBookLevel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "asset_id", nullable = false)
    private Long assetId;

    /** 매수/매도. {@code OrderType} 이름을 그대로 저장한다. */
    @Column(nullable = false, length = 20)
    private String side;

    @Column(nullable = false, precision = 20, scale = 4)
    private BigDecimal price;

    @Column(nullable = false, precision = 20, scale = 4)
    private BigDecimal quantity;
}
