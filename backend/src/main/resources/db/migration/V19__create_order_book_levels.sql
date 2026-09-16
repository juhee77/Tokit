-- 호가창 가격대별 잔량을 물리화한 집계 테이블.
--
-- 이전에는 체결마다 해당 종목의 활성 주문을 모두 GROUP BY로 집계해 상위 20호가를 뽑았다.
-- 최종 결과가 20행인데도 스캔 범위는 활성 주문 수에 비례해, 미체결 주문이 쌓일수록
-- 체결 1건의 비용이 함께 늘었다. 이 테이블은 변화분만 반영(+/-)하므로 갱신 비용이
-- 건드린 가격대 수에만 비례하고, 조회는 인덱스 상위 20행만 읽는다.
--
-- Redis 캐시가 아니라 DB 테이블인 이유는 두 가지다.
--  1) 주문·체결과 같은 트랜잭션에서 갱신되어 원장과 어긋날 수 없다.
--  2) numeric으로 정확한 소수 연산이 가능하다. (부동소수 누적 오차 없음)
CREATE TABLE IF NOT EXISTS order_book_levels (
    id        BIGSERIAL      PRIMARY KEY,
    asset_id  BIGINT         NOT NULL REFERENCES assets(id),
    side      VARCHAR(20)    NOT NULL,
    price     NUMERIC(20,4)  NOT NULL,
    quantity  NUMERIC(20,4)  NOT NULL,
    CONSTRAINT uq_order_book_levels UNIQUE (asset_id, side, price)
);

-- 조회는 "종목+방향"으로 좁혀 가격순 상위 N호가만 읽는다.
-- 가격을 인덱스에 포함해 정렬 없이 앞에서부터 끊어 읽을 수 있게 한다.
CREATE INDEX IF NOT EXISTS idx_order_book_levels_lookup
    ON order_book_levels (asset_id, side, price);
