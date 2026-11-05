#!/usr/bin/env bash
# Estado de Qiao en una pantalla: contenedores, salud de punta a punta, certificado, disco, memoria y backups.
# Uso: sudo deploy/status.sh        (código de salida 0 = todo bien, 1 = hay al menos una FALLA)
# Sólo hace pedidos al propio servidor (127.0.0.1): nada sale a Internet.
set -uo pipefail
# shellcheck source=deploy/lib.sh
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

require_env_file
problems=0
bad() { fail "$*"; problems=$((problems + 1)); }

domain="$(env_get DOMAIN)"
printf '%sQiao — %s — %s%s\n' "$C_BOLD" "$domain" "$(date '+%Y-%m-%d %H:%M %Z')" "$C_RESET"
log "versión desplegada: $(env_get QIAO_IMAGE_TAG local)   repo: $(git -c safe.directory="$REPO_DIR" -C "$REPO_DIR" log -1 --format='%h %cs %s' 2>/dev/null | cut -c1-70)"

step "Contenedores"
compose ps --all --format 'table {{.Service}}\t{{.Status}}\t{{.Image}}'
for svc in $(compose config --services 2>/dev/null); do
  h="$(service_health "$svc")"
  case "$h" in
    healthy|running) ;;
    starting) warn "$svc todavía está arrancando" ;;
    *) bad "$svc: $h  →  docker compose logs --tail 100 $svc" ;;
  esac
done

step "Salud de punta a punta"
if body="$(edge_curl /api/health -f 2>&1)" && [[ "$body" == *'"UP"'* ]]; then
  ok "/api/health → UP"
else
  bad "/api/health no responde bien: $body"
fi
for path in / /m/ /admin; do
  headers="$(edge_curl "$path" -o /dev/null -D - 2>/dev/null || true)"
  code="$(printf '%s' "$headers" | head -n 1 | awk '{print $2}')"
  csp="$(printf '%s' "$headers" | grep -ci '^content-security-policy:' || true)"
  hsts="$(printf '%s' "$headers" | grep -ci '^strict-transport-security:' || true)"
  if [[ "$code" == 200 && "$csp" == 1 && "$hsts" == 1 ]]; then
    ok "$path → 200, CSP y HSTS una vez"
  else
    bad "$path → código ${code:-sin respuesta}, CSP ×${csp}, HSTS ×${hsts} (se espera 200, ×1, ×1)"
  fi
done

step "Certificado HTTPS"
if has_service caddy; then
  cert_host=127.0.0.1
  cert_port="$(env_get HTTPS_PORT 443)"
else
  cert_host="$domain"
  cert_port=443
fi
cert="$(echo | timeout 15 openssl s_client -connect "$cert_host:$cert_port" -servername "$domain" 2>/dev/null \
  | openssl x509 -noout -enddate -issuer 2>/dev/null || true)"
if [[ -z "$cert" ]]; then
  bad "no se pudo leer el certificado de $domain en $cert_host:$cert_port (docker compose logs caddy)"
else
  end="$(printf '%s\n' "$cert" | sed -n 's/^notAfter=//p')"
  issuer="$(printf '%s\n' "$cert" | sed -n 's/^issuer=//p')"
  days=$(( ($(date -d "$end" +%s) - $(date +%s)) / 86400 ))
  if [[ "$issuer" == *"Caddy Local Authority"* ]]; then
    warn "certificado de PRUEBA (CA interna de Caddy), vence en $days días"
  elif [[ $days -lt 7 ]]; then
    bad "el certificado vence en $days días ($end) — ver OPERACIONES.md, «Falló la renovación del certificado»"
  elif [[ $days -lt 20 ]]; then
    warn "el certificado vence en $days días ($end): Caddy debería haberlo renovado ya"
  else
    ok "vence en $days días ($end) — $issuer"
  fi
fi

step "Disco y memoria"
used="$(df --output=pcent / | tail -n 1 | tr -dc '0-9')"
df -h / | tail -n 1 | awk '{ printf "      disco /: %s usados de %s (%s), libres %s\n", $3, $2, $5, $4 }'
if [[ "$used" -ge 90 ]]; then bad "disco al ${used}% — ver OPERACIONES.md, «Disco lleno»"
elif [[ "$used" -ge 80 ]]; then warn "disco al ${used}%"; fi
docker system df --format '      docker {{.Type}}: {{.Size}} (recuperable {{.Reclaimable}})' 2>/dev/null || true
free -m | awk '/^Mem:/ { printf "      memoria: %d MB usados de %d MB (disponibles %d MB)\n", $3, $2, $7 } /^Swap:/ { printf "      swap: %d MB usados de %d MB\n", $3, $2 }'
if [[ -f /var/run/reboot-required ]]; then warn "el sistema pide reiniciar (actualizaciones de seguridad del kernel): ver OPERACIONES.md"; fi

step "Backups"
BACKUP_DIR="$(env_get BACKUP_DIR /var/backups/qiao)"
if [[ ! -r "$BACKUP_DIR" ]]; then
  warn "no se puede leer $BACKUP_DIR (ejecutar con sudo)"
else
  last="$(find "$BACKUP_DIR/daily" -mindepth 1 -maxdepth 1 -type d -name '20*' -printf '%T@ %f\n' 2>/dev/null | sort -rn | head -n 1)"
  if [[ -z "$last" ]]; then
    bad "no hay ningún backup diario en $BACKUP_DIR/daily"
  else
    age_h=$(( ($(date +%s) - ${last%%.*}) / 3600 ))
    count="$(find "$BACKUP_DIR/daily" -mindepth 1 -maxdepth 1 -type d -name '20*' | wc -l)"
    if [[ $age_h -gt 50 ]]; then bad "último backup hace $age_h horas (${last#* }): journalctl -u qiao-backup"
    elif [[ $age_h -gt 26 ]]; then warn "último backup hace $age_h horas (${last#* })"
    else ok "último backup hace $age_h h (${last#* }), $count copias diarias, $(du -sh "$BACKUP_DIR" | cut -f1) en total"; fi
  fi
  if [[ -n "$(env_get BACKUP_OSS_BUCKET)" ]]; then
    if [[ -f "$BACKUP_DIR/.last-upload" ]]; then
      up_h=$(( ($(date +%s) - $(stat -c %Y "$BACKUP_DIR/.last-upload")) / 3600 ))
      if [[ $up_h -gt 50 ]]; then bad "última subida a OSS hace $up_h horas"; else ok "última subida a OSS hace $up_h h ($(cat "$BACKUP_DIR/.last-upload"))"; fi
    else
      bad "OSS configurado pero todavía no se subió ningún backup"
    fi
  else
    warn "sin copia externa en OSS (BACKUP_OSS_BUCKET vacío)"
  fi
  if [[ -f /etc/systemd/system/qiao-backup.timer ]]; then
    systemctl is-active --quiet qiao-backup.timer || bad "el timer qiao-backup.timer no está activo: systemctl enable --now qiao-backup.timer"
  fi
fi

if [[ "$(env_get PAYMENT_PROVIDER)" == fake ]]; then warn "PAYMENT_PROVIDER=fake: los pagos NO son reales"; fi
if [[ "$(env_get MAIL_MODE)" == log ]]; then warn "MAIL_MODE=log: los correos NO se envían"; fi

echo
if [[ $problems -eq 0 ]]; then
  printf '%sTodo en orden.%s\n' "$C_GREEN" "$C_RESET"
else
  printf '%s%d problema(s). Ver la lista de incidentes en docs/OPERACIONES.md.%s\n' "$C_RED" "$problems" "$C_RESET"
  exit 1
fi
