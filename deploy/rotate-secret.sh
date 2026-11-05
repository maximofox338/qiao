#!/usr/bin/env bash
# Rota (cambia por uno nuevo y aleatorio) un secreto generado por install.sh y reinicia lo necesario.
#
# Uso:  sudo deploy/rotate-secret.sh JWT_SECRET            cierra todas las sesiones (todos vuelven a entrar)
#       sudo deploy/rotate-secret.sh PARTNER_API_KEY       clave nueva para el socio, SIN corte: la anterior pasa a
#                                                          PARTNER_API_KEY_PREVIOUS y sigue valiendo hasta terminar
#       sudo deploy/rotate-secret.sh --finish-partner-rotation [--force]
#                                                          cuando el socio ya usa la clave nueva: la anterior deja de
#                                                          valer (se niega si la auditoría muestra que el socio todavía
#                                                          usa la anterior; --force lo hace igual)
#       sudo deploy/rotate-secret.sh DB_PASSWORD           contraseña del usuario de la base que usa el backend
#       sudo deploy/rotate-secret.sh MYSQL_ROOT_PASSWORD   contraseña de root de MySQL (backups y restore la usan)
#
# El valor nuevo nunca se imprime: queda en deploy/.env. Se guarda una copia del .env anterior en
# /var/lib/qiao/env-backups/. Los secretos de terceros (SMTP, Stripe, OSS, ACR) se rotan en su consola y se
# copian a .env a mano: ver docs/OPERACIONES.md, «Rotar secretos».
set -euo pipefail
# shellcheck source=deploy/lib.sh
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

key="${1:-}"
force=0
case "$key" in
  JWT_SECRET) len=64 ;;
  PARTNER_API_KEY) len=48 ;;
  DB_PASSWORD|MYSQL_ROOT_PASSWORD) len=32 ;;
  --finish-partner-rotation)
    len=0
    case "${2:-}" in
      --force) force=1 ;;
      "") ;;
      *) die "Opción desconocida: $2 (ver --help)" ;;
    esac
    ;;
  -h|--help|"") sed -n '2,16p' "$0"; exit 0 ;;
  *) die "No sé rotar $key (ver --help)" ;;
esac

require_root
require_env_file
ROTATION_MARK="$STATE_DIR/partner-key-rotation"

recreate_backend() {
  step "Recreando el backend con $1"
  compose up -d --no-build --wait --wait-timeout 420 backend
}

# Última llamada del socio con la clave anterior y con la actual desde que empezó la rotación, según la auditoría
# (el backend marca partnerKey=PREVIOUS en las entradas hechas con la anterior). Imprime "anterior<TAB>actual",
# cada una como fecha UTC (con microsegundos, comparables como texto) o "-" si no hubo; falla si MySQL no responde.
partner_key_usage() {
  local since="$1" sql
  sql="SELECT COALESCE(DATE_FORMAT(MAX(CASE WHEN JSON_UNQUOTE(JSON_EXTRACT(detail, '\$.partnerKey')) = 'PREVIOUS' THEN created_at END), '%Y-%m-%d %H:%i:%s.%f'), '-'),
  COALESCE(DATE_FORMAT(MAX(CASE WHEN detail IS NULL OR JSON_EXTRACT(detail, '\$.partnerKey') IS NULL THEN created_at END), '%Y-%m-%d %H:%i:%s.%f'), '-')
  FROM audit_log WHERE actor_type = 'PARTNER' AND created_at >= '$since';"
  # La consulta viaja por stdin; $MYSQL_ROOT_PASSWORD la expande el shell del contenedor.
  # shellcheck disable=SC2016
  printf '%s\n' "$sql" | compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot -N -B qiao'
}

finish_partner_rotation() {
  local previous since usage last_previous last_current
  previous="$(env_get PARTNER_API_KEY_PREVIOUS)"
  if [[ -z "$previous" ]]; then
    rm -f "$ROTATION_MARK"
    ok "No hay ninguna rotación de PARTNER_API_KEY en curso (PARTNER_API_KEY_PREVIOUS está vacía). Nada que hacer."
    return 0
  fi
  since="$(cat "$ROTATION_MARK" 2>/dev/null || true)"
  [[ "$since" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}\ [0-9]{2}:[0-9]{2}:[0-9]{2}$ ]] || since="$(date -u -d '90 days ago' '+%Y-%m-%d %H:%M:%S')"
  step "Uso de las claves por el socio desde $since UTC (auditoría)"
  if [[ "$(service_health mysql)" == healthy ]] && usage="$(partner_key_usage "$since")" && [[ "$usage" == *$'\t'* ]]; then
    last_previous="${usage%%$'\t'*}"
    last_current="${usage##*$'\t'}"
    log "      última llamada con la clave ANTERIOR: $last_previous"
    log "      última llamada con la clave NUEVA:    $last_current"
    if [[ "$last_previous" != - && ( "$last_current" == - || "$last_previous" > "$last_current" ) ]]; then
      if [[ $force -eq 1 ]]; then
        warn "El socio todavía usa la clave anterior: con --force deja de funcionar YA (su próxima llamada dará 401)."
      else
        die "El socio todavía usa la clave anterior (y no la nueva después). Confirmar con el socio que ya cambió y volver a ejecutar; para cortarla igual: $0 --finish-partner-rotation --force"
      fi
    fi
  elif [[ $force -eq 1 ]]; then
    warn "No se pudo leer la auditoría (¿MySQL caído?): se termina igual por --force."
  else
    die "No se pudo leer la auditoría en MySQL para saber qué clave usa el socio. Revisar con status.sh, o forzar: $0 --finish-partner-rotation --force"
  fi
  env_backup
  env_set PARTNER_API_KEY_PREVIOUS ""
  recreate_backend "sólo la clave nueva del socio"
  rm -f "$ROTATION_MARK"
  ok "Rotación de PARTNER_API_KEY terminada: la clave anterior ya no vale. Copia del .env anterior en $STATE_DIR/env-backups/"
  log "      Guardar el .env actualizado en el gestor de contraseñas (ver OPERACIONES.md)."
}

if [[ "$key" == --finish-partner-rotation ]]; then
  finish_partner_rotation
  exit 0
fi

new="$(openssl rand -hex $((len / 2)))"
[[ ${#new} -eq $len ]] || die "No se pudo generar el secreto"

if [[ "$key" == PARTNER_API_KEY && -n "$(env_get PARTNER_API_KEY_PREVIOUS)" ]]; then
  die "Ya hay una rotación de PARTNER_API_KEY en curso (PARTNER_API_KEY_PREVIOUS no está vacía). Terminarla antes con: $0 --finish-partner-rotation"
fi

env_backup
case "$key" in
  JWT_SECRET)
    env_set "$key" "$new"
    recreate_backend "el $key nuevo"
    ;;
  PARTNER_API_KEY)
    old="$(env_get PARTNER_API_KEY)"
    if [[ -n "$old" ]]; then
      # Sin corte: la clave actual pasa a ser la anterior y las dos valen hasta --finish-partner-rotation.
      env_set PARTNER_API_KEY_PREVIOUS "$old"
      mkdir -p "$STATE_DIR"
      date -u '+%Y-%m-%d %H:%M:%S' > "$ROTATION_MARK"
    fi
    env_set "$key" "$new"
    recreate_backend "la clave nueva del socio${old:+ (la anterior sigue valiendo)}"
    ;;
  DB_PASSWORD)
    [[ "$(service_health mysql)" == healthy ]] || die "MySQL tiene que estar en marcha"
    db_user="$(env_get DB_USER qiao)"
    [[ "$db_user" =~ ^[A-Za-z0-9_]+$ ]] || die "DB_USER inválido"
    step "Cambiando la contraseña de $db_user en MySQL"
    # La sentencia viaja por stdin (no queda en la línea de comandos ni en el historial).
    # shellcheck disable=SC2016
    printf "ALTER USER '%s'@'%%' IDENTIFIED BY '%s';\n" "$db_user" "$new" \
      | compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot'
    env_set "$key" "$new"
    step "Recreando MySQL y el backend con la contraseña nueva"
    compose up -d --no-build --wait --wait-timeout 420 mysql backend
    ;;
  MYSQL_ROOT_PASSWORD)
    [[ "$(service_health mysql)" == healthy ]] || die "MySQL tiene que estar en marcha"
    step "Cambiando la contraseña de root en MySQL"
    # shellcheck disable=SC2016
    printf "ALTER USER 'root'@'%%' IDENTIFIED BY '%s';\nALTER USER IF EXISTS 'root'@'localhost' IDENTIFIED BY '%s';\n" "$new" "$new" \
      | compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot'
    env_set "$key" "$new"
    step "Recreando MySQL con la contraseña nueva (el backend se reconecta solo)"
    compose up -d --no-build --no-deps --wait --wait-timeout 420 mysql
    ;;
esac
ok "$key rotado. Copia del .env anterior en $STATE_DIR/env-backups/"
case "$key" in
  JWT_SECRET) log "      Todas las sesiones se cerraron: clientes, ingenieros y admins vuelven a iniciar sesión." ;;
  PARTNER_API_KEY)
    log "      1. Entregar la clave nueva al socio por un canal seguro: sudo grep ^PARTNER_API_KEY= $ENV_FILE"
    if [[ -n "$old" ]]; then
      log "      2. Mientras tanto la clave anterior sigue valiendo (PARTNER_API_KEY_PREVIOUS). Cada llamada con ella queda"
      log "         en la auditoría con partnerKey=PREVIOUS y un aviso (back-office → Cumplimiento → Auditoría)."
      log "      3. Cuando el socio confirme que usa la nueva: sudo $0 --finish-partner-rotation"
    fi
    ;;
  *) log "      Guardar el .env actualizado en el gestor de contraseñas (ver OPERACIONES.md)." ;;
esac
