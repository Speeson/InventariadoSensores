import http from "k6/http";
import { check, sleep } from "k6";

const BASE_URL = __ENV.BASE_URL || "http://host.docker.internal:8000";
const TEST_USER = __ENV.TEST_USER || "admin@example.com";
const TEST_PASSWORD = __ENV.TEST_PASSWORD || "Pass123!";
const DURATION = __ENV.DURATION || "60s";
const VUS = Number(__ENV.VUS || 20);
const REQ_TIMEOUT = __ENV.REQ_TIMEOUT || "5s";

export const options = {
  vus: VUS,
  duration: DURATION,
  thresholds: {
    http_req_failed: ["rate<0.05"],
    http_req_duration: ["p(95)<800"],
  },
};

function login() {
  const payload = `username=${encodeURIComponent(TEST_USER)}&password=${encodeURIComponent(TEST_PASSWORD)}`;
  const res = http.post(`${BASE_URL}/auth/login`, payload, {
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    timeout: REQ_TIMEOUT,
  });

  check(res, {
    "login status 200": (r) => r.status === 200,
  });

  if (res.status === 200) {
    const json = res.json();
    return json.access_token;
  }

  return null;
}

export function setup() {
  const token = login();
  return { token };
}

export default function (data) {
  const token = data && data.token ? data.token : null;
  const authHeaders = token ? { Authorization: `Bearer ${token}` } : {};

  if (data && data.token) {
    const me = http.get(`${BASE_URL}/users/me`, {
      headers: authHeaders,
      timeout: REQ_TIMEOUT,
    });
    check(me, {
      "users/me 200": (r) => r.status === 200,
    });
  }

  const products = http.get(`${BASE_URL}/products/?limit=50&offset=0`, {
    headers: authHeaders,
    timeout: REQ_TIMEOUT,
  });
  check(products, {
    "products 200": (r) => r.status === 200,
  });

  const metrics = http.get(`${BASE_URL}/metrics`, { timeout: REQ_TIMEOUT });
  check(metrics, {
    "metrics 200": (r) => r.status === 200,
  });

  sleep(0.1);
}
