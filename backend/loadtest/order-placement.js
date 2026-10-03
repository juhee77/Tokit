import http from 'k6/http';
import { check } from 'k6';
import { Trend } from 'k6/metrics';

/**
 * 주문 접수(POST /api/orders) 부하 테스트.
 *
 * 이 경로는 예치금 홀딩(지갑 행 비관적 락) → 주문 저장 → RabbitMQ 이벤트 발행까지를
 * 동기로 처리한다. 매칭 자체는 컨슈머에서 비동기로 일어나므로 이 측정에 포함되지 않는다.
 * 측정 대상은 "주문을 받아 원장에 안전하게 적재하기까지"의 지연이다.
 */
const BASE = __ENV.BASE_URL || 'http://host.docker.internal:18080';
const USERS = parseInt(__ENV.USERS || '20');

const placeOrderLatency = new Trend('order_placement_latency', true);

// 적재량은 환경변수로 조절한다. 소진 속도가 빨라지면 측정 창(30초) 안에 큐가 말라
// 남은 시간이 분모에 섞이므로, 측정 대상보다 충분히 큰 백로그가 필요하다.
const VUS = parseInt(__ENV.VUS || '10');
const STEADY = __ENV.STEADY || '30s';

export const options = {
  scenarios: {
    ramp: {
      executor: 'ramping-vus',
      startVUs: 1,
      stages: [
        { duration: '10s', target: VUS },
        { duration: STEADY, target: VUS },
        { duration: '10s', target: 0 },
      ],
    },
  },
  thresholds: {
    // 실패율이 1%를 넘으면 측정 자체가 무의미하다.
    http_req_failed: ['rate<0.01'],
  },
};

// 각 VU가 자기 계정으로 로그인해 토큰을 확보한다. (지갑 행 락 경합을 분산)
export function setup() {
  const tokens = [];
  for (let i = 1; i <= USERS; i++) {
    const res = http.post(`${BASE}/api/auth/login`, JSON.stringify({
      email: `loadtest-${i}@tokit.com`, password: 'loadtest123',
    }), { headers: { 'Content-Type': 'application/json' } });
    tokens.push(res.json('data.accessToken'));
  }
  return { tokens };
}

export default function (data) {
  const token = data.tokens[(__VU - 1) % data.tokens.length];

  const res = http.post(`${BASE}/api/orders`, JSON.stringify({
    assetSymbol: 'LOADTEST',
    orderType: 'BUY',
    price: 10000,
    quantity: 1,
  }), {
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${token}`,
      // 멱등성 키가 중복되면 409로 막히므로 매 요청 고유값을 쓴다.
      'X-Idempotency-Key': `${__VU}-${__ITER}-${Date.now()}`,
    },
  });

  placeOrderLatency.add(res.timings.duration);
  check(res, { 'status 200': (r) => r.status === 200 });
}
