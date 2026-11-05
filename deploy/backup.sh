#!/usr/bin/env bash
# Backup de Qiao: base de datos (mysqldump consistente, comprimido) + volumen de archivos subidos,
# con copia opcional en Alibaba OSS. Lo ejecuta todos los días el timer qiao-backup.timer (install.sh).
#
# Uso:  sudo deploy/backup.sh                  backup diario (se conservan los BACKUP_KEEP últimos, 14 por defecto)
#       sudo deploy/backup.sh --label TEXTO    backup manual, p. ej. antes de actualizar (se conservan los 10 últimos)
#       sudo deploy/backup.sh --no-upload      no subir a OSS aunque esté configurado
#
# Resultado: BACKUP_DIR/daily/<fecha>/ (o BACKUP_DIR/manual/<fecha>-<texto>/) con
#   db.sql.gz      mysqldump --single-transaction de la base "qiao" (InnoDB: copia consistente sin bloquear)
#   files.tar.gz   contenido del volumen "files" (adjuntos, entregables)
#   meta.txt       fecha, versión desplegada, migración de Flyway, tamaños
#   SHA256SUMS     sumas para verificar la copia (restore.sh las comprueba)
set -euo pipefail
# shellcheck source=deploy/lib.sh
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

label=""
upload=1
while [[ $# -gt 0 ]]; do
  case "$1" in
    --label) label="${2:-}"; shift 2 ;;
    --no-upload) upload=0; shift ;;
    -h|--help) sed -n '2,15p' "$0"; exit 0 ;;
    *) die "Opción desconocida: $1 (ver --help)" ;;
  esac
done
[[ -z "$label" || "$label" =~ ^[A-Za-z0-9._-]{1,40}$ ]] || die "--label: sólo letras, números, punto, guion y guion bajo"

require_root
require_env_file

BACKUP_DIR="$(env_get BACKUP_DIR /var/backups/qiao)"
KEEP="$(env_get BACKUP_KEEP 14)"
[[ "$KEEP" =~ ^[0-9]+$ && "$KEEP" -ge 1 ]] || die "BACKUP_KEEP debe ser un número ≥ 1"
if [[ -n "$label" ]]; then kind=manual; keep_kind=10; else kind=daily; keep_kind="$KEEP"; fi

mkdir -p "$BACKUP_DIR/daily" "$BACKUP_DIR/manual"
chmod 700 "$BACKUP_DIR"

exec 9>"$BACKUP_DIR/.lock"
flock -n 9 || die "Ya hay otro backup en curso"

name="$(date -u +%Y-%m-%dT%H%M%SZ)${label:+-$label}"
tmp="$BACKUP_DIR/$kind/.partial-$name"
final="$BACKUP_DIR/$kind/$name"
trap 'rm -rf "$tmp"' EXIT
mkdir -m 700 "$tmp"

avail_mb="$(df --output=avail -BM "$BACKUP_DIR" | tail -n 1 | tr -dc '0-9')"
if [[ "$avail_mb" -lt 1024 ]]; then
  die "Menos de 1 GB libre en $BACKUP_DIR (${avail_mb} MB): liberar espacio (ver OPERACIONES.md, «Disco lleno»)"
fi

step "Base de datos ($name)"
[[ "$(service_health mysql)" == healthy ]] || die "MySQL no está en marcha (docker compose ps mysql)"
# El script corre DENTRO del contenedor de MySQL: la contraseña de root se toma de su propio entorno y se pasa
# por un archivo temporal, nunca por la línea de comandos.
# shellcheck disable=SC2016
compose exec -T mysql sh -c '
  set -eu
  cnf="$(mktemp)"
  trap "rm -f \"$cnf\"" EXIT
  printf "[client]\nuser=root\npassword=\"%s\"\n" "$MYSQL_ROOT_PASSWORD" > "$cnf"
  mysqldump --defaults-extra-file="$cnf" --single-transaction --quick --routines --triggers --events \
    --hex-blob --no-tablespaces --set-gtid-purged=OFF --default-character-set=utf8mb4 qiao
' | gzip -6 > "$tmp/db.sql.gz"
gzip -t "$tmp/db.sql.gz" || die "db.sql.gz está dañado"
gzip -dc "$tmp/db.sql.gz" | tail -n 1 | grep -q 'Dump completed' || die "El volcado de la base quedó incompleto"
ok "db.sql.gz ($(du -h "$tmp/db.sql.gz" | cut -f1))"

step "Archivos subidos (volumen files)"
files_vol="$(compose_volume files)"
[[ -n "$files_vol" ]] || die "No existe el volumen files del proyecto $(compose_project) (¿se arrancó alguna vez el backend?)"
# Contenedor efímero, sin red, con la imagen de MySQL (ya presente) y el volumen en sólo lectura: no depende de
# que el backend esté en marcha ni de su imagen. --numeric-owner conserva el usuario dueño de los archivos.
docker run --rm --network none -v "$files_vol:/data/uploads:ro" --entrypoint tar "$(mysql_image)" \
  --numeric-owner -czf - -C /data/uploads . > "$tmp/files.tar.gz"
tar -tzf "$tmp/files.tar.gz" > /dev/null || die "files.tar.gz está dañado"
ok "files.tar.gz ($(du -h "$tmp/files.tar.gz" | cut -f1), $(tar -tzf "$tmp/files.tar.gz" | grep -vc '/$') archivos)"

# shellcheck disable=SC2016
flyway="$(compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot -N -B qiao -e "SELECT version FROM flyway_schema_history WHERE success=1 AND version IS NOT NULL ORDER BY installed_rank DESC LIMIT 1"' 2>/dev/null || true)"
{
  echo "fecha_utc=$name"
  echo "servidor=$(hostname)"
  echo "dominio=$(env_get DOMAIN)"
  echo "version_desplegada=$(env_get QIAO_IMAGE_TAG local)"
  echo "commit_repo=$(git -c safe.directory="$REPO_DIR" -C "$REPO_DIR" rev-parse --short=12 HEAD 2>/dev/null || echo '?')"
  echo "flyway_version=${flyway:-?}"
  echo "db_bytes=$(stat -c %s "$tmp/db.sql.gz")"
  echo "files_bytes=$(stat -c %s "$tmp/files.tar.gz")"
} > "$tmp/meta.txt"
(cd "$tmp" && sha256sum db.sql.gz files.tar.gz meta.txt > SHA256SUMS)
chmod 600 "$tmp"/*
mv "$tmp" "$final"
trap - EXIT
ok "Backup completo: $final"

# Retención local: se borran los más viejos del mismo tipo, y restos de backups interrumpidos.
find "$BACKUP_DIR/$kind" -mindepth 1 -maxdepth 1 -type d -name '20*' -printf '%f\n' | sort -r \
  | tail -n +"$((keep_kind + 1))" | while read -r old; do
      rm -rf "${BACKUP_DIR:?}/$kind/$old"
      log "      borrado backup viejo $kind/$old"
    done
find "$BACKUP_DIR/daily" "$BACKUP_DIR/manual" -mindepth 1 -maxdepth 1 -type d -name '.partial-*' -mmin +1440 \
  -exec rm -rf {} +
[[ "$kind" == daily ]] && echo "$name" > "$BACKUP_DIR/.last-daily"

# Copia externa en Alibaba OSS por la API compatible con S3 (rclone del repositorio de Ubuntu).
bucket="$(env_get BACKUP_OSS_BUCKET)"
if [[ -z "$bucket" ]]; then
  [[ "$kind" == daily ]] && warn "Sin copia externa: BACKUP_OSS_BUCKET vacío (si el disco se pierde, se pierden los backups)"
  exit 0
fi
if [[ "$upload" -eq 0 ]]; then
  log "      (sin subir a OSS: --no-upload)"
  exit 0
fi
command -v rclone > /dev/null || die "rclone no está instalado: sudo apt-get install -y rclone (o volver a ejecutar install.sh)"
step "Subida a OSS"
prefix="$(env_get BACKUP_OSS_PREFIX qiao)"
export RCLONE_CONFIG="$STATE_DIR/rclone-sin-config.conf"
export RCLONE_CONFIG_QIAOOSS_TYPE=s3
export RCLONE_CONFIG_QIAOOSS_PROVIDER="${QIAO_RCLONE_PROVIDER:-Alibaba}"
export RCLONE_CONFIG_QIAOOSS_ENDPOINT
export RCLONE_CONFIG_QIAOOSS_ACCESS_KEY_ID
export RCLONE_CONFIG_QIAOOSS_SECRET_ACCESS_KEY
RCLONE_CONFIG_QIAOOSS_ENDPOINT="$(env_get BACKUP_OSS_ENDPOINT oss-cn-hongkong-internal.aliyuncs.com)"
RCLONE_CONFIG_QIAOOSS_ACCESS_KEY_ID="$(env_get BACKUP_OSS_ACCESS_KEY_ID)"
RCLONE_CONFIG_QIAOOSS_SECRET_ACCESS_KEY="$(env_get BACKUP_OSS_ACCESS_KEY_SECRET)"
export RCLONE_CONFIG_QIAOOSS_ACL=private
# La clave de backups no puede crear buckets ni borrar: no se comprueba el bucket, sólo se escribe.
export RCLONE_CONFIG_QIAOOSS_NO_CHECK_BUCKET=true
if rclone copy -q --retries 3 --low-level-retries 10 "$final" "qiaooss:$bucket/$prefix/$kind/$name"; then
  echo "$name" > "$BACKUP_DIR/.last-upload"
  ok "Subido a oss://$bucket/$prefix/$kind/$name/"
else
  die "Falló la subida a OSS (el backup local sí quedó en $final)"
fi
