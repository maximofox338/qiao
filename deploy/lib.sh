# shellcheck shell=bash
# Funciones comunes de los scripts de deploy/ (backup, restore, status, update, rotate-secret).
# No se ejecuta solo: lo cargan los otros scripts con "source".
#
# Variables para pruebas (deploy/test); en el servidor no hace falta tocarlas:
#   QIAO_ENV_FILE   archivo .env (por defecto deploy/.env)
#   QIAO_STATE_DIR  estado de los scripts: historial de versiones, copias de .env (por defecto /var/lib/qiao)

DEPLOY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC2034  # la usan los scripts que cargan este archivo
REPO_DIR="$(dirname "$DEPLOY_DIR")"
ENV_FILE="${QIAO_ENV_FILE:-$DEPLOY_DIR/.env}"
STATE_DIR="${QIAO_STATE_DIR:-/var/lib/qiao}"

if [[ -t 1 ]]; then
  C_RED=$'\e[31m' C_GREEN=$'\e[32m' C_YELLOW=$'\e[33m' C_BOLD=$'\e[1m' C_RESET=$'\e[0m'
else
  C_RED='' C_GREEN='' C_YELLOW='' C_BOLD='' C_RESET=''
fi

log()  { printf '%s\n' "$*"; }
step() { printf '%s== %s%s\n' "$C_BOLD" "$*" "$C_RESET"; }
ok()   { printf '%sOK%s    %s\n' "$C_GREEN" "$C_RESET" "$*"; }
warn() { printf '%sAVISO%s %s\n' "$C_YELLOW" "$C_RESET" "$*" >&2; }
fail() { printf '%sFALLA%s %s\n' "$C_RED" "$C_RESET" "$*" >&2; }
die()  { fail "$*"; exit 1; }

require_root() {
  [[ $EUID -eq 0 ]] || die "Ejecutar como root: sudo $0"
}

require_env_file() {
  [[ -f "$ENV_FILE" ]] || die "No existe $ENV_FILE. Crearlo con deploy/install.sh (o cp .env.example .env)."
  [[ -r "$ENV_FILE" ]] || die "No se puede leer $ENV_FILE: ejecutar con sudo."
}

# env_get CLAVE [valor-por-defecto]: lee una variable de .env SIN ejecutarlo (no se usa "source").
# Acepta CLAVE=valor, CLAVE='valor' y CLAVE="valor"; si la clave se repite, vale la última.
env_get() {
  local key="$1" default="${2-}" line val
  line="$(grep -E "^[[:space:]]*(export[[:space:]]+)?${key}=" "$ENV_FILE" 2>/dev/null | tail -n 1 || true)"
  if [[ -z "$line" ]]; then
    printf '%s' "$default"
    return 0
  fi
  val="${line#*=}"
  val="${val%$'\r'}"
  if [[ "$val" =~ ^\'(.*)\'[[:space:]]*$ ]]; then
    val="${BASH_REMATCH[1]}"
  elif [[ "$val" =~ ^\"(.*)\"[[:space:]]*$ ]]; then
    val="${BASH_REMATCH[1]}"
  else
    val="${val%%[[:space:]]#*}"
    val="${val%"${val##*[![:space:]]}"}"
  fi
  if [[ -z "$val" ]]; then
    val="$default"
  fi
  printf '%s' "$val"
}

# env_set CLAVE VALOR: reemplaza (o agrega) CLAVE='VALOR' en .env, de forma atómica y conservando los permisos.
env_set() {
  local key="$1" value="$2" tmp
  [[ "$value" != *"'"* && "$value" != *$'\n'* ]] || die "El valor de $key no puede tener comillas simples ni saltos de línea"
  tmp="$(mktemp "$ENV_FILE.XXXXXX")"
  chmod --reference="$ENV_FILE" "$tmp"
  chown --reference="$ENV_FILE" "$tmp" 2>/dev/null || true
  awk -v k="$key" -v v="$value" '
    BEGIN { done = 0 }
    $0 ~ "^[[:space:]]*(export[[:space:]]+)?" k "=" { if (!done) { print k "='\''" v "'\''"; done = 1 }; next }
    { print }
    END { if (!done) print k "='\''" v "'\''" }
  ' "$ENV_FILE" > "$tmp"
  mv -f "$tmp" "$ENV_FILE"
}

# Copia de seguridad de .env antes de modificarlo (sólo root, 0600).
env_backup() {
  local dir="$STATE_DIR/env-backups"
  mkdir -p "$dir" && chmod 700 "$dir"
  cp -p "$ENV_FILE" "$dir/env.$(date -u +%Y%m%dT%H%M%SZ)"
  # se conservan las 20 últimas
  find "$dir" -maxdepth 1 -name 'env.*' -type f -printf '%f\n' | sort -r | tail -n +21 | while read -r f; do rm -f "$dir/$f"; done
}

# docker compose con el .env del despliegue (COMPOSE_FILE y COMPOSE_PROFILES salen de ahí).
compose() {
  (cd "$DEPLOY_DIR" && docker compose --env-file "$ENV_FILE" "$@")
}

# ¿Está activo el servicio en la configuración actual? (p. ej. caddy no lo está con docker-compose.slb.yml)
has_service() {
  compose config --services 2>/dev/null | grep -qx "$1"
}

# Estado de salud de un servicio: healthy | unhealthy | starting | running | exited | missing
service_health() {
  local id
  id="$(compose ps -q "$1" 2>/dev/null | head -n 1)"
  if [[ -z "$id" ]]; then
    echo missing
    return 0
  fi
  docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$id" 2>/dev/null || echo missing
}

# edge_curl RUTA [opciones de curl...]: pide RUTA a través de la entrada pública, pero sin salir del servidor
# (Caddy en 127.0.0.1 con el nombre del dominio, así se verifica también el certificado; o nginx en modo SLB).
edge_curl() {
  local path="$1"
  shift
  local domain https_port http_port
  domain="$(env_get DOMAIN)"
  https_port="$(env_get HTTPS_PORT 443)"
  http_port="$(env_get HTTP_PORT 80)"
  # --noproxy: es una comprobación local, nunca debe pasar por un proxy aunque el sistema tenga uno configurado.
  if has_service caddy; then
    curl -sS --noproxy '*' --max-time 15 --resolve "$domain:$https_port:127.0.0.1" "$@" "https://$domain:$https_port$path"
  else
    curl -sS --noproxy '*' --max-time 15 -H "Host: $domain" -H "X-Forwarded-Proto: https" "$@" "http://127.0.0.1:$http_port$path"
  fi
}

# Nombre del proyecto de Compose ("qiao" en el servidor).
compose_project() {
  compose config 2>/dev/null | sed -n 's/^name: *//p' | head -n 1
}

# Volumen Docker del servicio (p. ej. "files" → qiao_files), buscado por las etiquetas que pone Compose.
compose_volume() {
  docker volume ls -q --filter "label=com.docker.compose.project=$(compose_project)" \
    --filter "label=com.docker.compose.volume=$1" | head -n 1
}

# Imagen del contenedor de MySQL en marcha (trae tar y gzip: sirve para copiar volúmenes sin descargar nada).
mysql_image() {
  local id
  id="$(compose ps -q mysql 2>/dev/null | head -n 1)"
  [[ -n "$id" ]] && docker inspect --format '{{.Config.Image}}' "$id" 2>/dev/null || echo mysql:8.4
}

# Número de la última migración de Flyway aplicada (para saber si una versión nueva migró el esquema).
flyway_version() {
  # shellcheck disable=SC2016  # $MYSQL_ROOT_PASSWORD lo expande el shell del contenedor
  compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot -N -B qiao -e "SELECT COALESCE(MAX(installed_rank),0) FROM flyway_schema_history WHERE success=1" 2>/dev/null' 2>/dev/null || echo "?"
}

valid_tag() {
  [[ "$1" =~ ^[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}$ ]]
}
