-- 배당 미분배 잔액의 발행사 반환을 위한 스키마.
--
-- 배당 지급액은 원 단위로 절사하므로 주주 1명당 최대 1원이 남는다. 지금까지 이 잔액은
-- 재원에서 빠진 채 어느 계정에도 들어가지 않았다. 원장에서 사라진 금액이므로 결산이
-- 맞지 않는다. 재원을 넣은 주체가 발행사이므로 남은 금액은 발행사에게 돌려준다.

-- 1) 발행사에 서비스 계정을 연결한다.
--    wallets.user_id가 NOT NULL이므로, 발행사가 지갑을 가지려면 사용자 레코드가 필요하다.
--    Wallet을 발행사도 가리킬 수 있게 다형적으로 바꾸는 대안은 모든 지갑 조회(비관적 락
--    포함)를 건드려야 하므로, 서비스 계정을 두어 기존 로직을 그대로 재사용한다.
ALTER TABLE issuers ADD COLUMN IF NOT EXISTS user_id BIGINT;

-- 기존 발행사에 서비스 계정을 만들어 연결한다.
-- 로그인 불가 비밀번호를 넣어 사람이 이 계정으로 접속할 수 없게 한다.
INSERT INTO users (name, email, password, wallet_address, kyc_status, investor_type, role)
SELECT i.company_name || ' (발행사 계정)',
       'issuer-' || i.biz_reg_no || '@tokit.internal',
       '{noop}LOGIN_DISABLED',
       '0xISSUER' || lpad(i.id::text, 32, '0'),
       true, 'GENERAL', 'USER'
FROM issuers i
WHERE i.user_id IS NULL
  AND NOT EXISTS (
      SELECT 1 FROM users u WHERE u.email = 'issuer-' || i.biz_reg_no || '@tokit.internal');

UPDATE issuers i
SET user_id = u.id
FROM users u
WHERE i.user_id IS NULL
  AND u.email = 'issuer-' || i.biz_reg_no || '@tokit.internal';

-- user_id를 NOT NULL로 두지 않는다. 이 변경 이전에 만들어진 발행사와, 정산 계정이 아직
-- 필요하지 않은 발행사가 존재할 수 있기 때문이다. 계정은 배당 집행이 처음 잔액을 돌려줄
-- 때 필요하므로, 없으면 그 시점에 만든다.
ALTER TABLE issuers ADD CONSTRAINT fk_issuers_user FOREIGN KEY (user_id) REFERENCES users(id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_issuers_user ON issuers (user_id);

-- 발행사 서비스 계정에 원화 지갑을 만든다. (asset_id NULL = KRW)
INSERT INTO wallets (user_id, asset_id, balance, locked_balance)
SELECT i.user_id, NULL, 0, 0
FROM issuers i
WHERE NOT EXISTS (
    SELECT 1 FROM wallets w WHERE w.user_id = i.user_id AND w.asset_id IS NULL);

-- 2) 배당 집행 결과를 원장에 명시한다.
--    분배 총액과 미분배 잔액을 기록해 "재원 = 분배 총액 + 미분배 잔액"을 한 레코드에서
--    대조할 수 있게 한다. 지금까지는 상세 내역을 전부 합산해야만 알 수 있었다.
ALTER TABLE dividend_payouts
    ADD COLUMN IF NOT EXISTS distributed_amount   NUMERIC(20,4) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS undistributed_amount NUMERIC(20,4) NOT NULL DEFAULT 0;
