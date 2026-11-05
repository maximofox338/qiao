#!/usr/bin/env bash
# Actualiza Qiao a una versión nueva, espera a que esté sana y, si no lo está, vuelve sola a la anterior.
#
# Modo imágenes (COMPOSE_FILE incluye docker-compose.images.yml, imágenes de CI en Alibaba ACR):
#   sudo deploy/update.sh <tag>        descarga qiao-backend:<tag> y qiao-nginx:<tag> (tag = sha del commit en main)
# Modo build (por defecto, se compila en el servidor):
#   sudo deploy/update.sh              git pull de la rama actual, construye con tag = commit, despliega
#   sudo deploy/update.sh --no-pull    construye el código que ya está en el servidor
# En los dos modos:
#   sudo deploy/update.sh --rollback   vuelve a la versión anterior que funcionó
#   sudo deploy/update.sh --history    historial de despliegues
#   --skip-backup                      no hacer el backup previo (no recomendado)
#
# Antes de cambiar nada se hace un backup (manual/<fecha>-pre-<tag>). OJO: si la versión nueva aplicó migraciones
# de base de datos, volver atrás la imagen no deshace el esquema: el script lo avisa y da el comando de restore.
set -euo pipefail
# shellcheck source=deploy/lib.sh
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

WAIT_TIMEOUT="${QIAO_WAIT_TIMEOUT:-420}"
HISTORY="$STATE_DIR/deploy-history"

new_tag=""
action=deploy
git_pull=1
do_backup=1
while [[ $# -gt 0 ]]; do
  case "$1" in
    --rollback) action=rollback; shift ;;
    --history) action=history; shift ;;
    --no-pull) git_pull=0; shift ;;
    --skip-backup) do_backup=0; shift ;;
    -h|--help) sed -n '2,17p' "$0"; exit 0 ;;
    -*) die "Opción desconocida: $1 (ver --help)" ;;
    *) new_tag="$1"; shift ;;
  esac
done

require_root
require_env_file
mkdir -p "$STATE_DIR" && chmod 700 "$STATE_DIR"
touch "$HISTORY"

if [[ "$action" == history ]]; then
  column -t -s $'\t' "$HISTORY" 2>/dev/null || cat "$HISTORY"
  exit 0
fi

exec 8>"$STATE_DIR/update.lock"
flock -n 8 || die "Ya hay otra actualización en curso"

if compose config --images 2>/dev/null | grep -q '/qiao-backend:'; then mode=images; else mode=build; fi
current_tag="$(env_get QIAO_IMAGE_TAG local)"

record() { printf '%s\t%s\t%s\t%s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$1" "$2" "$3" >> "$HISTORY"; }

# Última versión que quedó sana (OK) y que no es la actual.
previous_good_tag() {
  awk -F'\t' -v cur="$current_tag" '$3 == "OK" && $2 != cur { t = $2 } END { print t }' "$HISTORY"
}

# Despliega backend y nginx con el tag dado y espera healthchecks + respuesta a través de Caddy/SLB.
deploy_tag() {
  local tag="$1"
  QIAO_IMAGE_TAG="$tag" compose up -d --no-build --wait --wait-timeout "$WAIT_TIMEOUT" backend nginx || return 1
  for _ in 1 2 3 4 5 6; do
    if edge_curl /api/health -f -o /dev/null 2>/dev/null && edge_curl /m/ -f -o /dev/null 2>/dev/null; then
      return 0
    fi
    sleep 5
  done
  return 1
}

# Nombre de imagen que Compose usaría para un servicio con un tag dado (qiao-backend:<tag> o <registro>/…:<tag>).
image_name_for() {
  QIAO_IMAGE_TAG="$2" compose config --images 2>/dev/null | grep -E "(^|/)qiao-$1:" | head -n 1
}

# Garantiza que la versión EN MARCHA se pueda volver a levantar sin descargar nada: si la imagen que Compose
# esperaría para el tag actual no existe localmente (p. ej. al pasar de modo build a imágenes, o si se borró de
# ACR), se etiqueta con ese nombre la imagen que está corriendo ahora.
pin_running_images() {
  local svc id want
  for svc in backend nginx; do
    id="$(compose ps -q "$svc" 2>/dev/null | head -n 1)"
    [[ -n "$id" ]] || continue
    want="$(image_name_for "$svc" "$current_tag")"
    [[ -n "$want" ]] || continue
    if ! docker image inspect "$want" > /dev/null 2>&1; then
      docker tag "$(docker inspect --format '{{.Image}}' "$id")" "$want"
      log "      imagen en marcha de $svc guardada como $want (para poder volver atrás)"
    fi
  done
}

# Limpieza para no llenar el disco: se conservan las imágenes de la versión actual y de las 3 últimas sanas
# (para --rollback). Sólo se borran tags que este script desplegó alguna vez (figuran en el historial), con los
# nombres locales (modo build) o del registro configurado; en modo imágenes se pueden volver a bajar de ACR.
prune_old_images() {
  local keep tag svc ref
  keep="$(awk -F'\t' '$3 == "OK" { print $2 }' "$HISTORY" | tail -n 4)"
  awk -F'\t' '{ print $2 }' "$HISTORY" | sort -u | while read -r tag; do
    if [[ -z "$tag" ]] || grep -qx -- "$tag" <<< "$keep"; then continue; fi
    for svc in backend nginx; do
      for ref in "qiao-$svc:$tag" "$(env_get QIAO_REGISTRY)/$(env_get QIAO_NAMESPACE)/qiao-$svc:$tag"; do
        if docker image inspect "$ref" > /dev/null 2>&1; then docker image rm "$ref" > /dev/null 2>&1 || true; fi
      done
    done
  done
  docker image prune -f > /dev/null 2>&1 || true
  if [[ "$mode" == build ]]; then docker builder prune -f --filter until=720h > /dev/null 2>&1 || true; fi
}

# ------------------------------------------------------------------ preparar la versión nueva
if [[ "$action" == rollback ]]; then
  new_tag="$(previous_good_tag)"
  [[ -n "$new_tag" ]] || die "No hay una versión anterior sana en $HISTORY"
  step "Volviendo a la versión anterior: $new_tag (actual: $current_tag)"
elif [[ "$mode" == images ]]; then
  [[ -n "$new_tag" ]] || die "Modo imágenes: indicar el tag, p. ej. sudo $0 3f2c1a9…  (lo muestra el job «images» de CI)"
else
  [[ -z "$new_tag" ]] || die "Modo build: el tag es el commit; usar «$0» (o activar docker-compose.images.yml)"
  if [[ $git_pull -eq 1 ]]; then
    step "git pull"
    owner="$(stat -c %U "$REPO_DIR")"
    if [[ "$owner" != root ]]; then
      runuser -u "$owner" -- git -C "$REPO_DIR" pull --ff-only
    else
      git -C "$REPO_DIR" pull --ff-only
    fi
  fi
  new_tag="$(git -c safe.directory="$REPO_DIR" -C "$REPO_DIR" rev-parse --short=12 HEAD)"
  if ! git -c safe.directory="$REPO_DIR" -C "$REPO_DIR" diff --quiet HEAD -- backend web h5 scripts deploy/nginx; then
    new_tag="$new_tag-mod$(date -u +%m%d%H%M)"
    warn "Hay cambios locales sin commit en el código: se despliegan con el tag $new_tag"
  fi
fi
valid_tag "$new_tag" || die "Tag inválido: $new_tag"
if [[ "$new_tag" == "$current_tag" && "$(service_health backend)" == healthy ]]; then
  ok "Ya está desplegada la versión $new_tag y está sana. Nada que hacer."
  exit 0
fi

avail_mb="$(df --output=avail -BM / | tail -n 1 | tr -dc '0-9')"
[[ "$avail_mb" -ge 2048 ]] || die "Menos de 2 GB libres en disco (${avail_mb} MB): ver OPERACIONES.md, «Disco lleno»"

if [[ "$mode" == images ]]; then
  step "Descargando imágenes $new_tag"
  QIAO_IMAGE_TAG="$new_tag" compose pull backend nginx \
    || die "No se pudieron descargar las imágenes $new_tag (¿tag correcto? ¿docker login hecho?). Nada cambió."
elif [[ "$action" != rollback ]]; then
  step "Construyendo $new_tag (la versión actual sigue atendiendo mientras tanto)"
  QIAO_IMAGE_TAG="$new_tag" compose build backend nginx || die "Falló la construcción. Nada cambió."
fi

pin_running_images

if [[ $do_backup -eq 1 && "$(service_health mysql)" == healthy ]]; then
  step "Backup previo"
  "$DEPLOY_DIR/backup.sh" --label "pre-${new_tag:0:20}" --no-upload || die "Falló el backup previo. Nada cambió."
fi
flyway_before="$(flyway_version)"

# ------------------------------------------------------------------ desplegar
# El tag se escribe en .env ANTES de desplegar (el backend lee .env: así su configuración queda coherente).
env_set QIAO_IMAGE_TAG "$new_tag"
trap 'warn "Interrumpido: .env quedó con QIAO_IMAGE_TAG=$new_tag; revisar con status.sh o volver con: sudo $0 --rollback"' INT TERM
step "Desplegando $new_tag (el backend se reinicia: ~1-2 minutos sin API)"
if deploy_tag "$new_tag"; then
  record "$new_tag" OK "$([[ $action == rollback ]] && echo "rollback desde $current_tag" || echo "antes $current_tag")"
  ok "Versión $new_tag desplegada y sana."
  prune_old_images
  exit 0
fi

# ------------------------------------------------------------------ falló: volver atrás
fail "La versión $new_tag NO quedó sana. Últimas líneas del backend:"
compose logs --no-color --tail 60 backend >&2 || true
record "$new_tag" FALLA "antes $current_tag"
flyway_after="$(flyway_version)"

if [[ "$action" == rollback || "$current_tag" == "$new_tag" ]]; then
  die "Tampoco arranca la versión anterior. Ver OPERACIONES.md, «Incidentes»."
fi
step "Volviendo a $current_tag"
env_set QIAO_IMAGE_TAG "$current_tag"
if deploy_tag "$current_tag"; then
  record "$current_tag" OK "rollback automático desde $new_tag"
  fail "Se volvió a la versión anterior $current_tag, que está sana. La versión $new_tag queda descartada."
else
  fail "La versión anterior $current_tag tampoco quedó sana. Ver OPERACIONES.md, «Incidentes»."
fi
if [[ "$flyway_before" != "$flyway_after" ]]; then
  warn "La versión $new_tag aplicó migraciones de base de datos (Flyway $flyway_before → $flyway_after)."
  warn "Si la versión anterior falla por el esquema, restaurar el backup previo:"
  warn "  sudo $DEPLOY_DIR/restore.sh --db-only $(find "$(env_get BACKUP_DIR /var/backups/qiao)/manual" -mindepth 1 -maxdepth 1 -type d -name "*-pre-${new_tag:0:20}" -printf '%f\n' 2>/dev/null | sort -r | head -n 1)"
fi
exit 1
