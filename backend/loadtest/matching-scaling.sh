#!/bin/bash
# 다종목 매칭 확장성 통제 비교.
#
# 종목 락은 종목 단위이므로 서로 다른 종목의 매칭은 병렬로 진행되어야 한다.
# 컨슈머 수를 4로 고정하고 종목 수만 1 -> 4로 바꿔, 그 주장이 성립하는지 확인한다.
#
# 사전 준비: docker compose up -d, backend/build/libs 에 부트 jar 빌드
# 실행:      ./backend/loadtest/matching-scaling.sh
#
# 앞선 두 번의 실패는 k6와 앱이 호스트를 포화시킨 상태에서 소진 속도를 재다가
# 측정 루프 자체가 밀린 것이 원인이었다. 이번에는 부하 생성과 측정을 분리한다.
#   1) 앱을 띄워 k6로 큐를 채운다
#   2) 앱을 내린다 (미확인 메시지는 큐로 되돌아온다)
#   3) 호스트가 조용해질 때까지 기다린다
#   4) 앱을 다시 띄워, 오직 소진만 일어나는 상태에서 30초를 잰다
set -e
REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
S="${TMPDIR:-/tmp}/tokit-scaling"; mkdir -p "$S"
PG="docker exec -i tokit-postgres psql -U tokit -d tokit -q"
depth(){ curl -s -u tokit:tokit "http://localhost:15672/api/queues/%2F/tokit.order.queue" \
         | python3 -c "import sys,json;print(json.load(sys.stdin)['messages'])"; }
loadavg(){ uptime | sed 's/.*averages*: *//' | awk '{print int($1)}'; }

stop_app(){ PID=$(lsof -ti :18080 2>/dev/null || true); [ -n "$PID" ] && kill $PID 2>/dev/null || true
            while lsof -ti :18080 >/dev/null 2>&1; do sleep 1; done; }

start_app(){ cd "$REPO/backend"
             PORT=18080 MANAGEMENT_PORT=18081 \
               SPRING_RABBITMQ_LISTENER_SIMPLE_CONCURRENCY=4 \
               SPRING_RABBITMQ_LISTENER_SIMPLE_MAX_CONCURRENCY=4 \
               java -jar build/libs/backend-0.0.1-SNAPSHOT.jar > $S/app.log 2>&1 &
             while ! grep -qE "Started TokitApplication|APPLICATION FAILED" $S/app.log 2>/dev/null; do sleep 2; done; }

settle(){ # 호스트가 조용해질 때까지 대기 (최대 4분)
  local limit=$((SECONDS+240))
  while [ $(loadavg) -ge 4 ] && [ $SECONDS -lt $limit ]; do sleep 10; done
  echo "     (측정 시작 시 load=$(uptime | sed 's/.*averages*: *//' | awk '{print $1}'))"
}

run_case(){
  local label="$1" script="$2" symbols="$3"
  stop_app
  docker exec tokit-rabbitmq rabbitmqctl purge_queue tokit.order.queue >/dev/null 2>&1 || true
  docker exec tokit-rabbitmq rabbitmqctl purge_queue tokit.order.dlq   >/dev/null 2>&1 || true
  $PG <<SQL
DELETE FROM order_book_levels;
DELETE FROM orders  WHERE asset_id IN (SELECT id FROM assets WHERE symbol LIKE 'LOADTEST%');
DELETE FROM wallets WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'loadtest-%');
DELETE FROM users   WHERE email LIKE 'loadtest-%';
DELETE FROM assets  WHERE symbol LIKE 'LOADTEST%';
DELETE FROM issuers WHERE biz_reg_no='loadtest-issuer';
INSERT INTO issuers (company_name, biz_reg_no) VALUES ('LoadTest Issuer','loadtest-issuer');
INSERT INTO assets (issuer_id,name,symbol,total_supply,issue_price,status,contract_address)
SELECT i.id,'LoadTest '||n,'LOADTEST'||CASE WHEN n=1 THEN '' ELSE n::text END,
       1000000,10000,'거래중','0xLT'||n
FROM issuers i, generate_series(1,$symbols) AS n WHERE i.biz_reg_no='loadtest-issuer';
SQL
  start_app
  for i in $(seq 1 20); do
    curl -s -o /dev/null -X POST http://localhost:18080/api/users/signup -H "Content-Type: application/json" \
      -d "{\"email\":\"loadtest-$i@tokit.com\",\"name\":\"LT $i\",\"password\":\"loadtest123\",\"walletAddress\":\"0xLT$(printf '%06d' $i)\"}"
  done
  $PG <<'SQL'
DELETE FROM wallets w USING wallets w2
WHERE w.user_id=w2.user_id AND w.asset_id IS NULL AND w2.asset_id IS NULL AND w.id>w2.id
  AND w.user_id IN (SELECT id FROM users WHERE email LIKE 'loadtest-%');
UPDATE wallets SET balance=100000000, locked_balance=0
WHERE asset_id IS NULL AND user_id IN (SELECT id FROM users WHERE email LIKE 'loadtest-%');
SQL

  # 1) 큐 적재
  cd "$REPO/backend"/loadtest
  # 측정 창(30초)보다 충분히 큰 백로그를 만든다. 큐가 창 안에 마르면 하한값만 나온다.
  docker run --rm -i --add-host=host.docker.internal:host-gateway -v "$PWD:/scripts" \
    -e VUS="${LOAD_VUS:-20}" -e STEADY="${LOAD_STEADY:-60s}" \
    grafana/k6:latest run "/scripts/$script" >/dev/null 2>&1 || true

  # 2) 앱 정지 — 부하 생성과 측정을 분리한다
  stop_app
  # 3) 호스트 안정화 대기
  settle
  QUEUED=$(depth)

  # 4) 오직 소진만 일어나는 상태에서 측정
  start_app
  A=$(depth); T0=$SECONDS; sleep 30; B=$(depth); T=$((SECONDS-T0))
  stop_app
  python3 -c "
a,b,t,q=$A,$B,$T,$QUEUED
print(f'  >>> $label : {(a-b)/t:7.1f} 건/초   (적재 {q}, 잔량 {a}→{b}, 창 {t}초)')
"
}

echo "===== A. 단일 종목 / 컨슈머 4 ====="
run_case "단일 종목" order-placement.js 1
echo
echo "===== B. 종목 4개 / 컨슈머 4 ====="
run_case "종목 4개" order-placement-multi.js 4
