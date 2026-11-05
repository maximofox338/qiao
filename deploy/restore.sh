#!/usr/bin/env bash
# Restauración de un backup hecho por backup.sh.
#
# Uso:  sudo deploy/restore.sh --list                    backups locales (y en OSS, si está configurado)
#       sudo deploy/restore.sh --dry-run [BACKUP]        PRUEBA sin tocar producción: verifica las sumas y carga la
#                                                         base en un MySQL temporal (por defecto, el último diario)
#       sudo deploy/restore.sh --fetch NOMBRE            descarga daily/NOMBRE (o manual/NOMBRE) desde OSS
#       sudo deploy/restore.sh BACKUP                    RESTAURA en producción base y archivos (pide confirmación)
#            opciones: --db-only | --files-only | --yes (sin preguntar) | --skip-safety-backup
#
# BACKUP es un nombre (2026-11-05T193000Z), una ruta a la carpeta del backup, o "latest".
# Antes de restaurar se hace un backup de seguridad del estado actual (manual/<fecha>-pre-restore).
set -euo pipefail
# shellcheck source=deploy/lib.sh
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

mode=restore
target=""
fetch_name=""
do_db=1
do_files=1
assume_yes=0
safety_backup=1
while [[ $# -gt 0 ]]; do
  case "$1" in
    --list) mode=list; shift ;;
    --dry-run|--test) mode=dry-run; shift ;;
    --fetch) mode=fetch; fetch_name="${2:-}"; shift 2 ;;
    --db-only) do_files=0; shift ;;
    --files-only) do_db=0; shift ;;
    --yes) assume_yes=1; shift ;;
    --skip-safety-backup) safety_backup=0; shift ;;
    -h|--help) sed -n '2,13p' "$0"; exit 0 ;;
    -*) die "Opción desconocida: $1 (ver --help)" ;;
    *) target="$1"; shift ;;
  esac
done
[[ $do_db -eq 1 || $do_files -eq 1 ]] || die "--db-only y --files-only son excluyentes"

require_root
require_env_file
BACKUP_DIR="$(env_get BACKUP_DIR /var/backups/qiao)"

rclone_env() {
  export RCLONE_CONFIG="$STATE_DIR/rclone-sin-config.conf"
  export RCLONE_CONFIG_QIAOOSS_TYPE=s3
  export RCLONE_CONFIG_QIAOOSS_PROVIDER="${QIAO_RCLONE_PROVIDER:-Alibaba}"
  export RCLONE_CONFIG_QIAOOSS_ENDPOINT RCLONE_CONFIG_QIAOOSS_ACCESS_KEY_ID RCLONE_CONFIG_QIAOOSS_SECRET_ACCESS_KEY
  RCLONE_CONFIG_QIAOOSS_ENDPOINT="$(env_get BACKUP_OSS_ENDPOINT oss-cn-hongkong-internal.aliyuncs.com)"
  RCLONE_CONFIG_QIAOOSS_ACCESS_KEY_ID="$(env_get BACKUP_OSS_ACCESS_KEY_ID)"
  RCLONE_CONFIG_QIAOOSS_SECRET_ACCESS_KEY="$(env_get BACKUP_OSS_ACCESS_KEY_SECRET)"
  export RCLONE_CONFIG_QIAOOSS_NO_CHECK_BUCKET=true
  OSS_BASE="qiaooss:$(env_get BACKUP_OSS_BUCKET)/$(env_get BACKUP_OSS_PREFIX qiao)"
}

# Carpeta del backup a partir de un nombre, una ruta o "latest".
resolve_backup() {
  local t="$1" d
  if [[ -z "$t" || "$t" == latest ]]; then
    t="$(find "$BACKUP_DIR/daily" -mindepth 1 -maxdepth 1 -type d -name '20*' -printf '%f\n' 2>/dev/null | sort -r | head -n 1)"
    [[ -n "$t" ]] || die "No hay backups diarios en $BACKUP_DIR/daily"
  fi
  for d in "$t" "$BACKUP_DIR/daily/$t" "$BACKUP_DIR/manual/$t" "$BACKUP_DIR/oss/$t"; do
    if [[ -f "$d/SHA256SUMS" ]]; then
      (cd "$d" && pwd)
      return 0
    fi
  done
  die "No encuentro el backup «$t» (ver: restore.sh --list)"
}

verify_backup() {
  local dir="$1"
  step "Verificando $dir"
  (cd "$dir" && sha256sum --quiet -c SHA256SUMS) || die "Las sumas SHA-256 no coinciden: backup dañado"
  ok "sumas SHA-256"
  gzip -t "$dir/db.sql.gz" || die "db.sql.gz dañado"
  gzip -dc "$dir/db.sql.gz" | tail -n 1 | grep -q 'Dump completed' || die "db.sql.gz incompleto"
  ok "db.sql.gz completo ($(du -h "$dir/db.sql.gz" | cut -f1), $(gzip -dc "$dir/db.sql.gz" | grep -c '^CREATE TABLE') tablas)"
  tar -tzf "$dir/files.tar.gz" > /dev/null || die "files.tar.gz dañado"
  ok "files.tar.gz legible ($(tar -tzf "$dir/files.tar.gz" | grep -vc '/$') archivos)"
  sed 's/^/      /' "$dir/meta.txt"
}

case "$mode" in
  list)
    step "Backups locales en $BACKUP_DIR"
    for kind in daily manual oss; do
      [[ -d "$BACKUP_DIR/$kind" ]] || continue
      find "$BACKUP_DIR/$kind" -mindepth 1 -maxdepth 1 -type d -name '20*' -printf '%f\n' | sort -r | while read -r b; do
        printf '  %-7s %-45s %s\n' "$kind" "$b" "$(du -sh "$BACKUP_DIR/$kind/$b" | cut -f1)"
      done
    done
    if [[ -n "$(env_get BACKUP_OSS_BUCKET)" ]] && command -v rclone > /dev/null; then
      rclone_env
      step "Backups en OSS ($OSS_BASE)"
      for kind in daily manual; do
        rclone lsf -q --dirs-only "$OSS_BASE/$kind/" 2>/dev/null | sort -r | head -n 30 | sed "s|^|  $kind  |;s|/$||" || true
      done
    fi
    exit 0
    ;;

  fetch)
    [[ "$fetch_name" =~ ^20[0-9A-Za-z._-]+$ ]] || die "--fetch NOMBRE (ver restore.sh --list)"
    [[ -n "$(env_get BACKUP_OSS_BUCKET)" ]] || die "OSS no está configurado (BACKUP_OSS_BUCKET)"
    command -v rclone > /dev/null || die "Falta rclone: sudo apt-get install -y rclone"
    rclone_env
    mkdir -p "$BACKUP_DIR/oss" && chmod 700 "$BACKUP_DIR/oss"
    for kind in daily manual; do
      if [[ -n "$(rclone lsf -q "$OSS_BASE/$kind/$fetch_name/" 2>/dev/null)" ]]; then
        step "Descargando $OSS_BASE/$kind/$fetch_name"
        rclone copy -q "$OSS_BASE/$kind/$fetch_name" "$BACKUP_DIR/oss/$fetch_name"
        verify_backup "$BACKUP_DIR/oss/$fetch_name"
        ok "Listo. Probar con: sudo $0 --dry-run $fetch_name"
        exit 0
      fi
    done
    die "No está en OSS: $fetch_name"
    ;;

  dry-run)
    dir="$(resolve_backup "$target")"
    verify_backup "$dir"
    step "Carga de prueba en un MySQL temporal (producción no se toca)"
    image="$(docker inspect --format '{{.Config.Image}}' "$(compose ps -q mysql 2>/dev/null | head -n 1)" 2>/dev/null || echo mysql:8.4)"
    name="qiao-restore-test-$$"
    pass="$(openssl rand -hex 16)"
    # -v: borra también el volumen de datos del MySQL temporal (si no, quedaría un volumen huérfano por prueba).
    trap 'docker rm -f -v "$name" > /dev/null 2>&1 || true' EXIT
    docker run -d --name "$name" --network none -e MYSQL_ROOT_PASSWORD="$pass" -e MYSQL_DATABASE=qiao \
      "$image" --performance-schema=OFF --innodb-buffer-pool-size=64M \
      --character-set-server=utf8mb4 --collation-server=utf8mb4_0900_ai_ci > /dev/null
    for _ in $(seq 1 90); do
      if docker exec -e MYSQL_PWD="$pass" "$name" mysql -uroot -h 127.0.0.1 -N -e 'SELECT 1' > /dev/null 2>&1; then
        break
      fi
      sleep 2
    done
    docker exec -e MYSQL_PWD="$pass" "$name" mysql -uroot -h 127.0.0.1 -N -e 'SELECT 1' > /dev/null 2>&1 \
      || die "El MySQL temporal no arrancó (docker logs $name)"
    start=$(date +%s)
    gzip -dc "$dir/db.sql.gz" | docker exec -i -e MYSQL_PWD="$pass" "$name" mysql -uroot -h 127.0.0.1 qiao \
      || die "La carga del volcado FALLÓ"
    ok "volcado cargado en $(( $(date +%s) - start )) s"
    q() { docker exec -e MYSQL_PWD="$pass" "$name" mysql -uroot -h 127.0.0.1 -N -B qiao -e "$1"; }
    log "      versión de Flyway: $(q "SELECT version FROM flyway_schema_history WHERE success=1 AND version IS NOT NULL ORDER BY installed_rank DESC LIMIT 1")"
    log "      filas por tabla:"
    counts="$(q "SET SESSION group_concat_max_len = 1000000; SELECT GROUP_CONCAT(CONCAT('SELECT ''', table_name, ''', COUNT(*) FROM \`', table_name, '\`') SEPARATOR ' UNION ALL ') FROM information_schema.tables WHERE table_schema='qiao' AND table_type='BASE TABLE'")"
    q "$counts" | awk '{ printf "        %-34s %s\n", $1, $2 }'
    ok "PRUEBA DE RESTAURACIÓN CORRECTA: $(basename "$dir") se puede restaurar"
    exit 0
    ;;
esac

# ---------------------------------------------------------------- restauración real en producción
dir="$(resolve_backup "$target")"
verify_backup "$dir"
domain="$(env_get DOMAIN)"
cat <<EOF

${C_RED}${C_BOLD}ATENCIÓN: esto REEMPLAZA los datos de producción de ${domain}${C_RESET}
  backup:   $(basename "$dir")
  base:     $([[ $do_db -eq 1 ]] && echo "se reemplaza la base qiao entera" || echo "no se toca")
  archivos: $([[ $do_files -eq 1 ]] && echo "se reemplaza el volumen files entero" || echo "no se toca")
  Todo lo creado después de ese backup se pierde (queda en el backup de seguridad previo).
  El backend se detiene unos minutos: la API no responde mientras tanto.

EOF
if [[ $assume_yes -ne 1 ]]; then
  read -r -p "Para continuar escribir RESTAURAR: " answer < /dev/tty
  [[ "$answer" == RESTAURAR ]] || die "Cancelado"
fi

if [[ $safety_backup -eq 1 ]]; then
  step "Backup de seguridad del estado actual"
  "$DEPLOY_DIR/backup.sh" --label pre-restore --no-upload \
    || die "El backup de seguridad falló. Si la base está rota y aun así hay que restaurar: --skip-safety-backup"
fi

step "Deteniendo el backend"
compose stop backend

if [[ $do_db -eq 1 ]]; then
  step "Restaurando la base de datos"
  compose up -d --wait --wait-timeout 300 mysql
  # shellcheck disable=SC2016
  compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot -e "DROP DATABASE IF EXISTS qiao; CREATE DATABASE qiao CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci; GRANT ALL PRIVILEGES ON qiao.* TO \"$MYSQL_USER\"@\"%\";"'
  # shellcheck disable=SC2016
  gzip -dc "$dir/db.sql.gz" | compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot qiao' \
    || die "La carga de la base FALLÓ. El backup de seguridad está en $BACKUP_DIR/manual/"
  ok "base restaurada"
fi

if [[ $do_files -eq 1 ]]; then
  step "Restaurando los archivos subidos"
  files_vol="$(compose_volume files)"
  [[ -n "$files_vol" ]] || die "No existe el volumen files: arrancar el stack una vez (docker compose up -d) y repetir"
  # Mismo método que backup.sh: imagen de MySQL, sin red; se conservan los dueños numéricos del backup.
  docker run --rm -i --network none -v "$files_vol:/data/uploads" --entrypoint sh "$(mysql_image)" -c \
    'find /data/uploads -mindepth 1 -delete && tar --numeric-owner -xzf - -C /data/uploads' < "$dir/files.tar.gz" \
    || die "Falló la restauración de los archivos"
  ok "archivos restaurados en el volumen $files_vol"
fi

step "Arrancando el backend"
if compose up -d --wait --wait-timeout 420; then
  ok "Restauración terminada. Comprobar: sudo $DEPLOY_DIR/status.sh"
else
  die "El backend no quedó healthy: docker compose logs --tail 200 backend"
fi
