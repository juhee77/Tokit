-- 매칭 경로의 주문 조회 인덱스.
--
-- MatchingService는 주문 1건마다 해당 종목의 활성 주문을 조회하고(matchOrder),
-- 체결 후 호가창 재집계에서 같은 조회를 한 번 더 수행한다(updateAndBroadcastOrderBook).
-- 인덱스가 없으면 이 조회가 orders 전체를 순차 스캔하므로, 체결이 끝난 과거 주문이
-- 쌓일수록 활성 주문 수와 무관하게 느려진다.
--
-- 측정 (로컬 PostgreSQL 17, 활성 주문 비율 2%, 조회 결과 58건 고정):
--   누적 20만 건 : 14.172 ms -> 0.051 ms
--   누적 50만 건 : 34.555 ms -> 0.031 ms
-- 인덱스가 없으면 누적량에 비례해 선형으로 느려지고, 있으면 평탄하게 유지된다.
--
-- 이 조회는 종목 행 배타 락을 잡은 구간 안에서 실행되므로, 조회 지연이 곧 해당 종목의
-- 매칭 처리량 상한이 된다. 인덱스는 정합성이 아니라 처리량을 위한 것이다.
--
-- 컬럼 순서: 등가 조건(asset_id, status)을 앞에 두어 탐색 범위를 좁히고,
-- 매칭 엔진이 사용하는 정렬 키(price, created_at)를 뒤에 붙여 정렬 비용을 줄인다.
CREATE INDEX IF NOT EXISTS idx_orders_matching
    ON orders (asset_id, status, price, created_at);
