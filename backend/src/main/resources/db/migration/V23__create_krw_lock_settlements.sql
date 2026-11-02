-- 매수 홀딩 잔여 정산 내역 (#27).
--
-- 매수 주문은 지정가 기준으로 체결대금+수수료를 홀딩하지만, 체결은 메이커 호가로 이뤄지고
-- 체결분마다 수수료를 절사했기 때문에, 그 차액이 KRW 지갑의 locked_balance에 남았다.
-- 체결 로직은 고쳤지만 이미 묶인 금액은 일회성 정산으로 balance로 돌려준다.
-- 사후 감사가 가능하도록 정산 전후 값을 그대로 남긴다. wallet_id는 기록용 값이라 FK를 걸지 않는다.
CREATE TABLE krw_lock_settlements (
    id                     BIGSERIAL PRIMARY KEY,
    user_id                BIGINT         NOT NULL,
    wallet_id              BIGINT         NOT NULL,
    locked_balance_before  NUMERIC(20, 4) NOT NULL,
    expected_locked        NUMERIC(20, 4) NOT NULL, -- 활성 매수 주문 잔량 홀딩액 합
    refund_amount          NUMERIC(20, 4) NOT NULL, -- locked_balance_before - expected_locked
    balance_before         NUMERIC(20, 4) NOT NULL,
    balance_after          NUMERIC(20, 4) NOT NULL,
    settled_by             BIGINT,                  -- 정산을 실행한 관리자
    settled_at             TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_krw_lock_settlement_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_krw_lock_settlements_user ON krw_lock_settlements (user_id);
