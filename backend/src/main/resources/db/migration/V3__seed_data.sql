-- V3__seed_data.sql
-- Description: Seed initial issuer and default STO assets (one in offering, one in trading)

INSERT INTO issuers (company_name, biz_reg_no) 
VALUES ('서울랜드트러스트', '123-45-67890')
ON CONFLICT (biz_reg_no) DO NOTHING;

-- '서울 강남 프라임 오피스 빌딩' (GNPM) - status: '청약중' (Funding)
INSERT INTO assets (issuer_id, name, total_supply, issue_price, status, symbol, contract_address)
VALUES (
    (SELECT id FROM issuers WHERE biz_reg_no = '123-45-67890'),
    '서울 강남 프라임 오피스 빌딩',
    5000000.0000,
    10000.0000,
    '청약중',
    'GNPM',
    '0x5FbDB2315678afecb367f032d93F642f64180aa3'
)
ON CONFLICT (symbol) DO NOTHING;

-- '홍대 청년주택 제1호' (HDYT) - status: '거래중' (Trading)
INSERT INTO assets (issuer_id, name, total_supply, issue_price, status, symbol, contract_address)
VALUES (
    (SELECT id FROM issuers WHERE biz_reg_no = '123-45-67890'),
    '홍대 청년주택 제1호',
    1000000.0000,
    5000.0000,
    '거래중',
    'HDYT',
    '0xe7f1725E7734CE288F8367e1Bb143E90bb3F0512'
)
ON CONFLICT (symbol) DO NOTHING;
