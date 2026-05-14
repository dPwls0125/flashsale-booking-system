import http from 'k6/http';
import { check, sleep } from 'k6';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

export const options = {
  scenarios: {
    constant_request_rate: {
      executor: 'constant-arrival-rate',
      rate: 1000, // 초당 1000건 요청 (1000 TPS)
      timeUnit: '1s',
      duration: '3s',
      preAllocatedVUs: 200,
      maxVUs: 500,
    },
  },
};

export default function () {
  const productId = 1; // 테스트할 상품 ID
  const memberId = Math.floor(Math.random() * 1999) + 1; // 1~2000 사이의 회원 ID
  const idempotencyKey = uuidv4();
  
  const url = `http://localhost:8080/api/booking/${productId}`;
  const payload = JSON.stringify({
    paymentMethods: ['CREDIT_CARD']
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
      'X-Member-Id': memberId.toString(),
      'Idempotency-Key': idempotencyKey,
    },
  };

  const res = http.post(url, payload, params);

  check(res, {
    'status is 200 (Success)': (r) => r.status === 200,
    'status is 409 (Sold Out)': (r) => r.status === 409,
    'status is 4xx/5xx (Error)': (r) => r.status >= 400 && r.status !== 409,
  });
}
