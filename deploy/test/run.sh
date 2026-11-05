#!/usr/bin/env bash
# Prueba local del despliegue (NO es para el servidor). Levanta el stack completo como proyecto qiao-deploytest
# en 127.0.0.1:18080/18443 y comprueba: HTTPS con la CA interna de Caddy, redirección HTTP→HTTPS, cabeceras de
# seguridad una sola vez, healthchecks, rotación de logs, permisos del volumen de archivos, re-resolución del
# backend en nginx, backup → OSS (servidor S3 local) → restore (dry-run, fetch y real), status.sh, update.sh con
# rollback automático (registro local), rotate-secret.sh, variante SLB y perfil monitoring.
#
# Requisitos: Docker, rclone (apt-get install rclone) y los artefactos ya compilados:
#   (cd backend && mvn -q -DskipTests package) && (cd web && npm ci && npm run build) && (cd h5 && npm ci && npm run build)
# Uso:  deploy/test/run.sh             QIAO_TEST_SRC=<carpeta con backend/ web/ h5/ compilados> (por defecto el repo)
#       KEEP=1 deploy/test/run.sh      deja todo levantado para mirar; luego borrar con: deploy/test/run.sh --down
set -uo pipefail

TEST_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY="$(dirname "$TEST_DIR")"
REPO="$(dirname "$DEPLOY")"
SRC="${QIAO_TEST_SRC:-$REPO}"
WORK="${QIAO_TEST_WORK:-${TMPDIR:-/tmp}/qiao-deploytest}"
ENVF="$WORK/test.env"
PROJECT=qiao-deploytest
REG_PORT=15000
S3_PORT=19000
BASE=https://qiao.test:18443
pass=0
failures=()

check() { # check "descripción" comando [args...]  (el comando puede ser una función de este script)
  local desc="$1"
  shift
  if "$@" > "$WORK/last.log" 2>&1; then
    printf '  \e[32mPASA\e[0m  %s\n' "$desc"
    pass=$((pass + 1))
  else
    printf '  \e[31mFALLA\e[0m %s\n' "$desc"
    sed 's/^/        /' "$WORK/last.log" | tail -n 20
    failures+=("$desc")
  fi
}
title() { printf '\n\e[1m## %s\e[0m\n' "$*"; }
show_log() { sed 's/^/        /' "$WORK/last.log" | grep -E "${1:-.}" | tail -n "${2:-40}"; }

dc() { (cd "$DEPLOY" && docker compose --env-file "$ENVF" "$@"); }
# AWS_CA_BUNDLE (si el entorno de pruebas lo define) rompe el cliente S3 de rclone; en el servidor no existe.
scripts_env() { env -u AWS_CA_BUNDLE QIAO_ENV_FILE="$ENVF" QIAO_STATE_DIR="$WORK/state" CURL_CA_BUNDLE="$WORK/caddy-root.crt" QIAO_RCLONE_PROVIDER=Other "$@"; }
with_files() { local files="$1"; shift; (export COMPOSE_FILE="$files"; "$@"); }
set_env() { sed -i "/^$1=/d" "$ENVF"; echo "$1='$2'" >> "$ENVF"; }
env_val() { sed -n "s/^$1='\(.*\)'$/\1/p" "$ENVF"; }
https() { curl -sS --noproxy '*' --max-time 15 --cacert "$WORK/caddy-root.crt" --resolve qiao.test:18443:127.0.0.1 "$@"; }
mysql_root() { dc exec -T mysql sh -c "MYSQL_PWD=\"\$MYSQL_ROOT_PASSWORD\" mysql -uroot -N -B qiao -e \"$1\""; }

down() {
  if [[ -f "$ENVF" ]]; then dc --profile monitoring down -v --remove-orphans > /dev/null 2>&1; fi
  docker rm -f qiao-deploytest-registry qiao-deploytest-s3 > /dev/null 2>&1
  docker images --format '{{.Repository}}:{{.Tag}}' | grep -E "^(qiao-(backend|nginx):qdt-|127\.0\.0\.1:$REG_PORT/)" \
    | xargs -r docker rmi -f > /dev/null 2>&1
  rm -rf "$WORK"
}
if [[ "${1:-}" == --down ]]; then down; echo "limpio"; exit 0; fi

for f in "$SRC/backend/target/qiao-backend.jar" "$SRC/web/dist/index.html" "$SRC/h5/dist/index.html"; do
  [[ -f "$f" ]] || { echo "Falta $f (compilar primero, ver cabecera)"; exit 2; }
done
command -v rclone > /dev/null || { echo "Falta rclone (apt-get install rclone)"; exit 2; }
down
trap 'if [[ "${KEEP:-0}" == 1 ]]; then echo "KEEP=1: queda levantado ($ENVF)"; else down; fi' EXIT
mkdir -p "$WORK/stage/backend" "$WORK/stage/nginx/deploy/nginx" "$WORK/state" "$WORK/backups" "$WORK/s3/qiao-test-bucket"

# ---------------------------------------------------------------------------------------------------------------
title "Preparación"
# Dockerfiles de prueba = etapa final de los reales, copiando artefactos ya compilados.
awk '/^FROM eclipse-temurin/ { p = 1 } p' "$REPO/backend/Dockerfile" \
  | sed 's|^COPY --from=build --chown=qiao:qiao /build/target/qiao-backend.jar app.jar|COPY --chown=qiao:qiao qiao-backend.jar app.jar|' \
  > "$WORK/stage/backend/Dockerfile"
grep -q '^COPY --chown=qiao:qiao qiao-backend.jar app.jar' "$WORK/stage/backend/Dockerfile" || { echo "backend/Dockerfile cambió: adaptar run.sh"; exit 2; }
cp "$SRC/backend/target/qiao-backend.jar" "$WORK/stage/backend/"
awk '/^FROM nginx/ { p = 1 } p' "$DEPLOY/nginx/Dockerfile" \
  | sed -e 's|^COPY --from=web /src/web/dist/|COPY web/|' -e 's|^COPY --from=h5 /src/h5/dist/|COPY h5/|' \
  > "$WORK/stage/nginx/Dockerfile"
grep -q '^COPY h5/' "$WORK/stage/nginx/Dockerfile" || { echo "deploy/nginx/Dockerfile cambió: adaptar run.sh"; exit 2; }
cp -r "$DEPLOY/nginx/templates" "$WORK/stage/nginx/deploy/nginx/"
cp -r "$SRC/web/dist" "$WORK/stage/nginx/web"
cp -r "$SRC/h5/dist" "$WORK/stage/nginx/h5"

ADMIN_PASS="Qdt-$(openssl rand -hex 8)"
cat > "$ENVF" <<EOF
COMPOSE_PROJECT_NAME=$PROJECT
COMPOSE_FILE=docker-compose.yml:test/docker-compose.test.yml:test/docker-compose.test-build.yml
QIAO_TEST_ENV_FILE=$ENVF
QIAO_TEST_STAGE=$WORK/stage
DOMAIN='qiao.test'
ACME_EMAIL='ops@qiao.test'
CADDY_GLOBAL_EXTRA='local_certs'
HTTP_PORT=18080
HTTPS_PORT=18443
APP_BASE_URL='$BASE'
JWT_SECRET='$(openssl rand -hex 32)'
DB_USER='qiao'
DB_PASSWORD='$(openssl rand -hex 16)'
MYSQL_ROOT_PASSWORD='$(openssl rand -hex 16)'
PARTNER_API_KEY='$(openssl rand -hex 24)'
ADMIN_BOOTSTRAP_EMAIL='admin@qiao.test'
ADMIN_BOOTSTRAP_PASSWORD='$ADMIN_PASS'
MAIL_MODE='log'
PAYMENT_PROVIDER='fake'
STORAGE='local'
QIAO_REGISTRY='127.0.0.1:$REG_PORT'
QIAO_NAMESPACE='qiao'
QIAO_IMAGE_TAG='qdt-1'
BACKUP_DIR='$WORK/backups'
BACKUP_KEEP='3'
BACKUP_OSS_BUCKET='qiao-test-bucket'
BACKUP_OSS_ENDPOINT='http://127.0.0.1:$S3_PORT'
BACKUP_OSS_PREFIX='qiao'
BACKUP_OSS_ACCESS_KEY_ID='testkey'
BACKUP_OSS_ACCESS_KEY_SECRET='$(openssl rand -hex 16)'
EOF
chmod 600 "$ENVF"
check "compose config: build (+ overrides de prueba)" dc config -q
check "compose config: imágenes de ACR" with_files docker-compose.yml:docker-compose.images.yml:test/docker-compose.test.yml dc config -q
check "compose config: SLB" with_files docker-compose.yml:docker-compose.slb.yml:test/docker-compose.test.yml dc config -q
check "Caddyfile de producción válido" docker run --rm -e DOMAIN=qiao.example.com -e ACME_EMAIL=ops@example.com \
  -v "$DEPLOY/caddy/Caddyfile:/etc/caddy/Caddyfile:ro" caddy:2.10-alpine caddy validate --config /etc/caddy/Caddyfile --adapter caddyfile

# ---------------------------------------------------------------------------------------------------------------
title "Arranque (build con artefactos locales)"
check "build de imágenes" dc build
check "up --wait: mysql, backend, nginx y caddy healthy" dc up -d --wait --wait-timeout 420
dc ps --format 'table {{.Service}}\t{{.State}}\t{{.Health}}\t{{.Ports}}'
dc exec -T caddy cat /data/caddy/pki/authorities/local/root.crt > "$WORK/caddy-root.crt" 2>/dev/null

# ---------------------------------------------------------------------------------------------------------------
title "HTTPS y cabeceras"
redirect_ok() {
  local h
  h="$(curl -sS -o /dev/null -D - -H 'Host: qiao.test' http://127.0.0.1:18080/m/ | tr -d '\r')"
  echo "$h"
  grep -q '^HTTP/1.1 308' <<< "$h" && grep -qx 'Location: https://qiao.test/m/' <<< "$h"
}
health_up() { local out; out="$(https "$BASE/api/health")"; echo "$out"; grep -q '"UP"' <<< "$out"; }
headers_once() { # ruta código-esperado
  local h name n
  h="$(https -o /dev/null -D - "$BASE$1" | tr -d '\r')"
  head -n 1 <<< "$h" | grep -q " $2" || { echo "código: $(head -n 1 <<< "$h")"; return 1; }
  for name in content-security-policy strict-transport-security x-frame-options x-content-type-options referrer-policy; do
    n="$(grep -ci "^$name:" <<< "$h")"
    [[ "$n" == 1 ]] || { echo "$name aparece $n veces en $1"; echo "$h"; return 1; }
  done
  if grep -qiE '^(server: *caddy|alt-svc:|via:)' <<< "$h"; then echo "cabecera de Caddy filtrada"; echo "$h"; return 1; fi
}
relative_redirect() { https -o /dev/null -D - "$BASE/m" | tr -d '\r' | grep -qix 'Location: /m/'; }
immutable_assets() { https -o /dev/null -D - "$BASE/m/assets/$1" | grep -qi '^cache-control: public, max-age=31536000, immutable'; }
no_udp() { ! docker port "$PROJECT-caddy-1" | grep -q udp; }
admin_login() {
  https -H 'Content-Type: application/json' -d "{\"email\":\"admin@qiao.test\",\"password\":\"$ADMIN_PASS\"}" \
    "$BASE/api/admin/auth/login" | grep -q '"code":"OK"'
}
nginx_unpublished() { [[ -z "$(docker port "$PROJECT-nginx-1")" ]]; }
log_rotation() {
  local c
  for c in $(docker ps -q --filter "label=com.docker.compose.project=$PROJECT"); do
    docker inspect --format '{{.Name}} {{.HostConfig.LogConfig.Type}} {{index .HostConfig.LogConfig.Config "max-size"}} {{index .HostConfig.LogConfig.Config "max-file"}}' "$c"
    docker inspect --format '{{.HostConfig.LogConfig.Type}} {{index .HostConfig.LogConfig.Config "max-size"}} {{index .HostConfig.LogConfig.Config "max-file"}}' "$c" | grep -qx 'json-file 10m 5' || return 1
  done
}
backend_writes() { dc exec -T backend sh -c 'id && mkdir -p /data/uploads/qdt && echo hola > /data/uploads/qdt/hola.txt'; }
no_root_pw() { [[ -z "$(docker exec "$PROJECT-backend-1" printenv MYSQL_ROOT_PASSWORD)" ]]; }

check "HTTP → HTTPS (308 a https://qiao.test/m/)" redirect_ok
check "certificado verificado con la CA interna de Caddy" https -f -o /dev/null "$BASE/"
check "/api/health por HTTPS → UP" health_up
asset="$(docker exec "$PROJECT-nginx-1" sh -c 'ls /usr/share/nginx/html/m/assets | head -n 1')"
for spec in "/ 200" "/admin 200" "/m/ 200" "/m 301" "/m/assets/$asset 200" "/api/health 200" "/api/admin/dashboard 401" "/no-existe 200"; do
  read -r path code <<< "$spec"
  check "cabeceras de seguridad una sola vez: $path ($code)" headers_once "$path" "$code"
done
check "/m redirige a /m/ con Location relativa" relative_redirect
check "assets con Cache-Control inmutable" immutable_assets "$asset"
check "sin puerto UDP (HTTP/3 desactivado)" no_udp
check "login del admin bootstrap (secretos y base OK)" admin_login
check "nginx no publica puertos al host" nginx_unpublished
check "rotación de logs json-file 10m x5 en todos" log_rotation
check "el backend escribe en el volumen files (/data/uploads)" backend_writes
check "el backend no recibe MYSQL_ROOT_PASSWORD" no_root_pw

# ---------------------------------------------------------------------------------------------------------------
title "nginx vuelve a resolver el backend recreado (sin reiniciar nginx)"
ip_of() { docker inspect --format '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' "$1"; }
old_ip="$(ip_of "$PROJECT-backend-1")"
# Un contenedor ocupa la IP que deja libre el backend, así el nuevo recibe otra seguro.
dc stop backend > /dev/null 2>&1
dc rm -f backend > /dev/null 2>&1
docker run -d --rm --name qdt-ip-thief --network "${PROJECT}_default" --ip "$old_ip" alpine:3 sleep 600 > /dev/null
dc up -d --no-deps --wait --wait-timeout 420 backend > /dev/null 2>&1
echo "  IP del backend: $old_ip → $(ip_of "$PROJECT-backend-1")"
after_recreate() { sleep 12; https -f "$BASE/api/health"; }
check "/api/health responde con el backend en otra IP" after_recreate
docker rm -f qdt-ip-thief > /dev/null 2>&1

# ---------------------------------------------------------------------------------------------------------------
title "Backups, OSS (servidor S3 local: rclone serve s3) y restore"
docker run -d --name qiao-deploytest-s3 -p 127.0.0.1:$S3_PORT:9000 -v "$WORK/s3:/data" rclone/rclone:1.71 \
  serve s3 --addr :9000 --auth-key "testkey,$(env_val BACKUP_OSS_ACCESS_KEY_SECRET)" /data > /dev/null
sleep 3
newest_daily() { find "$WORK/backups/daily" -mindepth 1 -maxdepth 1 -type d -name '20*' -printf '%f\n' | sort | tail -n 1; }
backup_complete() {
  first="$(newest_daily)"
  (cd "$WORK/backups/daily/$first" && sha256sum -c SHA256SUMS && cat meta.txt && tar -tzf files.tar.gz | grep -q qdt/hola.txt)
}
uploaded() { ls -la "$WORK/s3/qiao-test-bucket/qiao/daily/$first/"; [[ -s "$WORK/s3/qiao-test-bucket/qiao/daily/$first/db.sql.gz" ]]; }
retention() { ls -1 "$WORK/backups/daily"; [[ "$(find "$WORK/backups/daily" -mindepth 1 -maxdepth 1 -type d -name '20*' | wc -l)" == 3 ]]; }
dry_run() { local out; out="$(scripts_env "$DEPLOY/restore.sh" --dry-run "$last")"; echo "$out"; grep -qE 'zz_antes +1$' <<< "$out"; }
# El primer backup ya no está en disco (lo borró la retención) pero sí en OSS: se recupera de ahí.
fetch() { [[ ! -d "$WORK/backups/daily/$first" ]] && scripts_env "$DEPLOY/restore.sh" --fetch "$first" && [[ -s "$WORK/backups/oss/$first/db.sql.gz" ]]; }
change_after() { mysql_root 'DROP TABLE zz_antes; CREATE TABLE zz_despues (id INT);' && docker exec "$PROJECT-backend-1" rm -rf /data/uploads/qdt; }
db_restored() { [[ "$(mysql_root 'SELECT id FROM zz_antes')" == 42 ]] && ! mysql_root 'SELECT 1 FROM zz_despues'; }
files_restored() { docker exec "$PROJECT-backend-1" sh -c 'ls -ln /data/uploads/qdt; test "$(stat -c %U /data/uploads/qdt/hola.txt)" = qiao && grep -q hola /data/uploads/qdt/hola.txt && touch /data/uploads/qdt/escribible'; }
safety_backup() { ls -d "$WORK/backups/manual/"*-pre-restore; }

check "marca en la base antes del backup" mysql_root "CREATE TABLE zz_antes (id INT PRIMARY KEY); INSERT INTO zz_antes VALUES (42);"
check "backup.sh diario con subida a OSS" scripts_env "$DEPLOY/backup.sh"
check "backup completo y verificable (SHA256SUMS, adjunto incluido)" backup_complete
check "copia subida al bucket" uploaded
for _ in 1 2 3; do sleep 1; scripts_env "$DEPLOY/backup.sh" --no-upload > /dev/null 2>&1; done
check "retención: quedan BACKUP_KEEP=3 diarios" retention
last="$(newest_daily)"
check "restore.sh --list (local y OSS)" scripts_env "$DEPLOY/restore.sh" --list
show_log 'daily|manual' 8
check "restore.sh --dry-run carga el volcado en un MySQL temporal" dry_run
check "restore.sh --fetch descarga y verifica desde OSS" fetch
check "cambios posteriores al backup (base y archivos)" change_after
check "restore.sh real (--yes) del backup traído de OSS" scripts_env "$DEPLOY/restore.sh" --yes "$first"
check "la base volvió al estado del backup" db_restored
check "los archivos volvieron, con dueño qiao y escribibles" files_restored
check "se hizo el backup de seguridad pre-restore" safety_backup
mysql_root "DROP TABLE IF EXISTS zz_antes" > /dev/null 2>&1

# ---------------------------------------------------------------------------------------------------------------
title "status.sh"
check "status.sh sin fallas" scripts_env "$DEPLOY/status.sh"
show_log . 40

# ---------------------------------------------------------------------------------------------------------------
title "update.sh con imágenes de un registro (como ACR) y rollback automático"
docker run -d --name qiao-deploytest-registry -p 127.0.0.1:$REG_PORT:5000 registry:2 > /dev/null
sleep 2
R="127.0.0.1:$REG_PORT/qiao"
for t in good1 good2 bad1; do docker tag qiao-nginx:qdt-1 "$R/qiao-nginx:$t"; done
docker tag qiao-backend:qdt-1 "$R/qiao-backend:good1"
docker tag qiao-backend:qdt-1 "$R/qiao-backend:good2"
# Versión rota: el backend termina al arrancar (como una configuración inválida).
printf 'FROM qiao-backend:qdt-1\nENTRYPOINT ["sh","-c","echo version rota de prueba >&2; exit 1"]\n' \
  | docker build -q -t "$R/qiao-backend:bad1" - > /dev/null
for t in good1 good2 bad1; do docker push -q "$R/qiao-backend:$t" > /dev/null; docker push -q "$R/qiao-nginx:$t" > /dev/null; done
docker rmi "$R/qiao-backend:good1" "$R/qiao-nginx:good1" "$R/qiao-backend:bad1" "$R/qiao-nginx:bad1" > /dev/null
set_env COMPOSE_FILE "docker-compose.yml:docker-compose.images.yml:test/docker-compose.test.yml"
export QIAO_WAIT_TIMEOUT=300
tag_is() { grep -qx "QIAO_IMAGE_TAG='$1'" "$ENVF"; }
bad_fails() { ! scripts_env "$DEPLOY/update.sh" bad1; }
back_on_good1() { tag_is good1 && https -f "$BASE/api/health" && grep -q 'qiao-backend:good1' <<< "$(docker inspect --format '{{.Config.Image}}' "$PROJECT-backend-1")"; }
rollback_good1() { scripts_env "$DEPLOY/update.sh" --rollback && tag_is good1; }

check "update.sh good1: descarga del registro y despliega" scripts_env "$DEPLOY/update.sh" good1
check ".env quedó con QIAO_IMAGE_TAG=good1" tag_is good1
check "update.sh bad1 falla (código ≠ 0)" bad_fails
show_log 'FALLA|Volviendo|versión' 6
check "…y volvió sola a good1, sana" back_on_good1
check "update.sh good2" scripts_env "$DEPLOY/update.sh" good2
check "update.sh --rollback vuelve a good1" rollback_good1
check "update.sh --history" scripts_env "$DEPLOY/update.sh" --history
show_log . 10

# ---------------------------------------------------------------------------------------------------------------
title "rotate-secret.sh"
after_rotation() { scripts_env "$DEPLOY/backup.sh" --label rot --no-upload && https -f "$BASE/api/health" && admin_login; }
check "rotar DB_PASSWORD (ALTER USER y recrear)" scripts_env "$DEPLOY/rotate-secret.sh" DB_PASSWORD
check "rotar MYSQL_ROOT_PASSWORD" scripts_env "$DEPLOY/rotate-secret.sh" MYSQL_ROOT_PASSWORD
check "rotar JWT_SECRET" scripts_env "$DEPLOY/rotate-secret.sh" JWT_SECRET
check "todo sano tras rotar (backup con la clave nueva, login)" after_rotation

# ---------------------------------------------------------------------------------------------------------------
title "Variante SLB (sin Caddy; nginx publica el puerto HTTP)"
slb_headers() {
  local h
  h="$(curl -sS -o /dev/null -D - -H 'Host: qiao.test' -H 'X-Forwarded-Proto: https' http://127.0.0.1:18080/m/ | tr -d '\r')"
  echo "$h"
  [[ "$(grep -ci '^content-security-policy:' <<< "$h")" == 1 && "$(grep -ci '^strict-transport-security:' <<< "$h")" == 1 ]]
}
no_caddy() { [[ -z "$(docker ps -q --filter "name=$PROJECT-caddy")" ]]; }
set_env COMPOSE_FILE "docker-compose.yml:docker-compose.images.yml:docker-compose.slb.yml:test/docker-compose.test.yml"
dc rm -sf caddy > /dev/null 2>&1
check "up con docker-compose.slb.yml" dc up -d --wait --wait-timeout 300
check "SLB: cabeceras una vez vía nginx" slb_headers
check "SLB: caddy no corre" no_caddy
set_env COMPOSE_FILE "docker-compose.yml:docker-compose.images.yml:test/docker-compose.test.yml"
dc up -d --wait --wait-timeout 300 > /dev/null 2>&1

# ---------------------------------------------------------------------------------------------------------------
title "Perfil monitoring (Uptime Kuma sólo en 127.0.0.1)"
kuma_local() { docker port "$PROJECT-uptime-kuma-1"; [[ "$(docker port "$PROJECT-uptime-kuma-1")" == '3001/tcp -> 127.0.0.1:13001' ]]; }
check "uptime-kuma healthy" dc --profile monitoring up -d --wait --wait-timeout 300 uptime-kuma
check "escucha sólo en 127.0.0.1" kuma_local
check "responde en http://127.0.0.1:13001" curl -sS -f -o /dev/null -L http://127.0.0.1:13001/

printf '\n\e[1mResultado: %d pasan, %d fallan\e[0m\n' "$pass" "${#failures[@]}"
for f in "${failures[@]}"; do echo "  - $f"; done
[[ ${#failures[@]} -eq 0 ]]
