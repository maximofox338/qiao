#!/usr/bin/env bash
# Prueba de punta a punta: MySQL en Docker, backend en el host, Nginx (con CSP de producción) sirviendo web y H5,
# y Playwright recorriendo el flujo completo cliente → revisión → ingeniero → contrato → pago → entrega → liquidación.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PORT="${E2E_PORT:-8088}"
cleanup() {
  [[ -n "${BACKEND_PID:-}" ]] && kill "$BACKEND_PID" 2>/dev/null || true
  docker rm -f qiao-e2e-mysql qiao-e2e-nginx >/dev/null 2>&1 || true
}
trap cleanup EXIT

echo "== MySQL"
docker rm -f qiao-e2e-mysql >/dev/null 2>&1 || true
docker run -d --name qiao-e2e-mysql -e MYSQL_ROOT_PASSWORD=root -e MYSQL_DATABASE=qiao -p 3316:3306 mysql:8.4 \
  --character-set-server=utf8mb4 --collation-server=utf8mb4_0900_ai_ci >/dev/null
for i in $(seq 1 60); do docker exec qiao-e2e-mysql mysqladmin ping -uroot -proot --silent 2>/dev/null && break; sleep 2; done

echo "== Builds"
(cd "$ROOT/backend" && mvn -B -q -DskipTests package)
(cd "$ROOT/web" && npm ci --silent && npm run build --silent)
(cd "$ROOT/h5" && npm ci --silent && npm run build --silent && npm run check:external --silent)

echo "== Backend"
JAR="$(ls "$ROOT"/backend/target/*.jar | grep -v plain | head -1)"
mkdir -p "$ROOT/e2e/test-results"
SPRING_PROFILES_ACTIVE=dev \
DB_URL="jdbc:mysql://127.0.0.1:3316/qiao?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC" DB_USER=root DB_PASSWORD=root \
JWT_SECRET="e2e-secret-e2e-secret-e2e-secret-e2e-secret" APP_BASE_URL="http://localhost:$PORT" \
MAIL_MODE=log PAYMENT_PROVIDER=fake PARTNER_API_KEY=e2e-partner-key \
ADMIN_BOOTSTRAP_EMAIL=admin@e2e.qiao ADMIN_BOOTSTRAP_PASSWORD='E2e-Admin-Password-123' \
STORAGE=local STORAGE_LOCAL_DIR="$ROOT/e2e/test-results/files" \
  java -jar "$JAR" > "$ROOT/e2e/test-results/backend.log" 2>&1 &
BACKEND_PID=$!
for i in $(seq 1 90); do curl -sf http://127.0.0.1:8080/api/health >/dev/null && break; sleep 2; done
curl -sf http://127.0.0.1:8080/api/health >/dev/null || { echo "backend no arrancó"; tail -50 "$ROOT/e2e/test-results/backend.log"; exit 1; }

echo "== Nginx"
docker rm -f qiao-e2e-nginx >/dev/null 2>&1 || true
docker run -d --name qiao-e2e-nginx --network host \
  -e BACKEND_UPSTREAM=127.0.0.1:8080 -e NGINX_PORT="$PORT" \
  -v "$ROOT/deploy/nginx/templates:/etc/nginx/templates:ro" \
  -v "$ROOT/web/dist:/usr/share/nginx/html:ro" \
  -v "$ROOT/h5/dist:/usr/share/nginx/html/m:ro" \
  nginx:1.27-alpine >/dev/null
for i in $(seq 1 30); do curl -sf "http://127.0.0.1:$PORT/" >/dev/null && break; sleep 1; done

echo "== Playwright"
cd "$ROOT/e2e"
npm ci --silent
[[ -d /opt/pw-browsers ]] || npx playwright install --with-deps chromium
BASE_URL="http://localhost:$PORT" API_URL="http://127.0.0.1:8080" PARTNER_API_KEY=e2e-partner-key \
ADMIN_EMAIL=admin@e2e.qiao ADMIN_PASSWORD='E2e-Admin-Password-123' npx playwright test "$@"
