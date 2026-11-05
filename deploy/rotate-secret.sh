#!/usr/bin/env bash
# Rota (cambia por uno nuevo y aleatorio) un secreto generado por install.sh y reinicia lo necesario.
#
# Uso:  sudo deploy/rotate-secret.sh JWT_SECRET            cierra todas las sesiones (todos vuelven a entrar)
#       sudo deploy/rotate-secret.sh PARTNER_API_KEY       el socio deja de poder llamar hasta recibir la clave nueva
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
case "$key" in
  JWT_SECRET) len=64 ;;
  PARTNER_API_KEY) len=48 ;;
  DB_PASSWORD|MYSQL_ROOT_PASSWORD) len=32 ;;
  -h|--help|"") sed -n '2,12p' "$0"; exit 0 ;;
  *) die "No sé rotar $key (ver --help)" ;;
esac

require_root
require_env_file
new="$(openssl rand -hex $((len / 2)))"
[[ ${#new} -eq $len ]] || die "No se pudo generar el secreto"

env_backup
case "$key" in
  JWT_SECRET|PARTNER_API_KEY)
    env_set "$key" "$new"
    step "Recreando el backend con el $key nuevo"
    compose up -d --no-build --wait --wait-timeout 420 backend
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
  PARTNER_API_KEY) log "      Entregar la clave nueva al socio por un canal seguro: sudo grep ^PARTNER_API_KEY= $ENV_FILE" ;;
  *) log "      Guardar el .env actualizado en el gestor de contraseñas (ver OPERACIONES.md)." ;;
esac
