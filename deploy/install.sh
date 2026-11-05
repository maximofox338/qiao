#!/usr/bin/env bash
# Instalación de Qiao en un ECS nuevo de Alibaba Cloud Hong Kong (Ubuntu 24.04), como root.
# Es idempotente: se puede volver a ejecutar para completar un paso que falló o actualizar el repo; nunca
# pisa un deploy/.env existente ni regenera secretos.
#
# Uso:   bash install.sh <url-del-repo> [rama]
#        p. ej.  bash install.sh git@github.com:mi-org/qiao.git main
#
# Pasos: 1 paquetes base · 2 Docker (repositorio oficial) · 3 swap · 4 usuario qiao · 5 repo en /opt/qiao
#        6 deploy/.env (secretos aleatorios; pregunta dominio, email del admin, SMTP y Stripe)
#        7 firewall ufw (SSH, 80, 443) · 8 actualizaciones automáticas de seguridad · 9 backups diarios
#        10 arranque con HTTPS y resumen.
#
# Instalación desatendida (sin preguntas): definir QIAO_DOMAIN, QIAO_ADMIN_EMAIL, QIAO_MAIL_MODE (smtp|log),
# QIAO_SMTP_HOST, QIAO_SMTP_PORT, QIAO_SMTP_USER, QIAO_SMTP_PASSWORD, QIAO_SMTP_FROM,
# QIAO_PAYMENT_PROVIDER (stripe|fake), QIAO_STRIPE_SECRET_KEY, QIAO_STRIPE_WEBHOOK_SECRET.
# Recuperación en un servidor nuevo con el .env guardado: QIAO_ENV_SOURCE=/root/qiao.env bash install.sh <url> [rama]
set -euo pipefail

QIAO_DIR="${QIAO_DIR:-/opt/qiao}"
QIAO_USER="${QIAO_USER:-qiao}"
BACKUP_ROOT="/var/backups/qiao"
STATE_DIR="/var/lib/qiao"
# Huella de la clave con la que Docker firma sus paquetes (docs.docker.com/engine/install/ubuntu).
DOCKER_GPG_FINGERPRINT="9DC858229FC7DD38854AE2D88D81803C0EBFCD88"
# Huellas publicadas por GitHub de sus claves SSH (docs.github.com, "GitHub's SSH key fingerprints").
GITHUB_SSH_FINGERPRINTS="SHA256:+DiY3wvvV6TuJJhbpZisF/zLDA0zPMSvHdkr4UvCOqU SHA256:p2QAMXNIC1TJYWeIOttrVc98/R1BUFWu3/LiyKgUfQM SHA256:uNiVztksCsDhcc0u9e8BujQXVUpKZIDTMczCvj3tD2s"

if [[ -t 1 ]]; then
  C_RED=$'\e[31m' C_GREEN=$'\e[32m' C_YELLOW=$'\e[33m' C_BOLD=$'\e[1m' C_RESET=$'\e[0m'
else
  C_RED='' C_GREEN='' C_YELLOW='' C_BOLD='' C_RESET=''
fi
step() { printf '\n%s== %s%s\n' "$C_BOLD" "$*" "$C_RESET"; }
ok()   { printf '%sOK%s    %s\n' "$C_GREEN" "$C_RESET" "$*"; }
warn() { printf '%sAVISO%s %s\n' "$C_YELLOW" "$C_RESET" "$*" >&2; }
die()  { printf '%sERROR%s %s\n' "$C_RED" "$C_RESET" "$*" >&2; exit 1; }
loud() {
  printf '\n%s%s' "$C_RED" "$C_BOLD" >&2
  printf '!!! %s\n' "$@" >&2
  printf '%s\n' "$C_RESET" >&2
}

as_qiao() { runuser -u "$QIAO_USER" -- "$@"; }
# ¿Hay una terminal para preguntar? (/dev/tty puede existir y no poder abrirse, p. ej. sin sesión interactiva)
have_tty() { { true < /dev/tty; } 2> /dev/null; }

# ------------------------------------------------------------------ preguntas
# ask VARIABLE "pregunta" [defecto] → deja la respuesta en la variable. Si existe QIAO_<VARIABLE> en el
# entorno, se usa sin preguntar. Las preguntas se leen de /dev/tty (funciona también con curl | bash).
ask() {
  local var="$1" prompt="$2" default="${3-}" preset="QIAO_$1" answer
  if [[ -n "${!preset:-}" ]]; then
    printf -v "$var" '%s' "${!preset}"
    return 0
  fi
  have_tty || die "Sin terminal para preguntar $var: definir $preset"
  while true; do
    if [[ -n "$default" ]]; then
      read -r -p "$prompt [$default]: " answer < /dev/tty
      answer="${answer:-$default}"
    else
      read -r -p "$prompt: " answer < /dev/tty
    fi
    if [[ -n "$answer" ]]; then break; fi
    echo "  (obligatorio)"
  done
  printf -v "$var" '%s' "$answer"
}

ask_secret() {
  local var="$1" prompt="$2" preset="QIAO_$1" answer
  if [[ -n "${!preset:-}" ]]; then
    printf -v "$var" '%s' "${!preset}"
    return 0
  fi
  have_tty || die "Sin terminal para preguntar $var: definir $preset"
  while true; do
    read -r -s -p "$prompt (no se muestra): " answer < /dev/tty
    echo
    if [[ -n "$answer" ]]; then break; fi
    echo "  (obligatorio)"
  done
  printf -v "$var" '%s' "$answer"
}

# Valores que van entre comillas simples en .env: no pueden tener comillas simples ni saltos de línea.
check_value() {
  [[ "$2" != *"'"* && "$2" != *$'\n'* && "$2" != *\\* ]] || die "$1 no puede contener comillas simples ni barras invertidas"
}

gen_hex() { openssl rand -hex "$1"; }

# Contraseña legible de 20 caracteres con mayúsculas, minúsculas y números.
gen_password() {
  local p
  while true; do
    p="$(openssl rand -base64 48 | tr -dc 'A-Za-z0-9' | cut -c1-20)"
    if [[ ${#p} -eq 20 && "$p" =~ [A-Z] && "$p" =~ [a-z] && "$p" =~ [0-9] ]]; then
      printf '%s' "$p"
      return 0
    fi
  done
}

# ------------------------------------------------------------------ 1. sistema y paquetes
check_system() {
  [[ $EUID -eq 0 ]] || die "Ejecutar como root (en el ECS: sudo -i, o entrar como root)"
  # shellcheck disable=SC1091
  . /etc/os-release
  if [[ "${ID:-}" != ubuntu || "${VERSION_ID:-}" != "24.04" ]]; then
    warn "Probado en Ubuntu 24.04; este sistema es ${PRETTY_NAME:-desconocido}."
    ask CONTINUE_OS "¿Continuar de todos modos? (si/no)" no
    [[ "$CONTINUE_OS" == si ]] || die "Cancelado"
  fi
  local mem_mb
  mem_mb="$(awk '/MemTotal/ { print int($2 / 1024) }' /proc/meminfo)"
  [[ "$mem_mb" -ge 3500 ]] || warn "Hay ${mem_mb} MB de RAM; se recomiendan 4 GB."
}

install_packages() {
  step "1/10 Paquetes base"
  export DEBIAN_FRONTEND=noninteractive
  apt-get update -q
  apt-get install -y -q --no-install-recommends \
    ca-certificates curl git gnupg openssl ufw unattended-upgrades rclone openssh-client
  ok "git, curl, ufw, unattended-upgrades, rclone (backups a OSS)"
}

# ------------------------------------------------------------------ 2. Docker
install_docker() {
  step "2/10 Docker (repositorio oficial download.docker.com)"
  if command -v docker > /dev/null && docker compose version > /dev/null 2>&1 \
     && dpkg-query -W -f='${Status}' docker-ce 2>/dev/null | grep -q 'install ok installed'; then
    ok "ya instalado: $(docker --version), $(docker compose version --short)"
  else
    # Paquetes no oficiales que chocan con los de Docker (docs.docker.com/engine/install/ubuntu).
    local pkg
    for pkg in docker.io docker-doc docker-compose docker-compose-v2 podman-docker containerd runc; do
      if dpkg-query -W -f='${Status}' "$pkg" 2>/dev/null | grep -q 'install ok installed'; then
        apt-get remove -y -q "$pkg"
      fi
    done
    install -m 0755 -d /etc/apt/keyrings
    curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc.tmp
    if ! gpg --show-keys --with-colons /etc/apt/keyrings/docker.asc.tmp 2>/dev/null | grep -q "^fpr:*${DOCKER_GPG_FINGERPRINT}:"; then
      rm -f /etc/apt/keyrings/docker.asc.tmp
      die "La clave GPG descargada de Docker no tiene la huella esperada ($DOCKER_GPG_FINGERPRINT)"
    fi
    mv /etc/apt/keyrings/docker.asc.tmp /etc/apt/keyrings/docker.asc
    chmod a+r /etc/apt/keyrings/docker.asc
    # shellcheck disable=SC1091
    local codename
    codename="$(. /etc/os-release && echo "${UBUNTU_CODENAME:-$VERSION_CODENAME}")"
    cat > /etc/apt/sources.list.d/docker.sources <<EOF
Types: deb
URIs: https://download.docker.com/linux/ubuntu
Suites: $codename
Components: stable
Architectures: $(dpkg --print-architecture)
Signed-By: /etc/apt/keyrings/docker.asc
EOF
    apt-get update -q
    apt-get install -y -q docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
    ok "instalado: $(docker --version)"
  fi
  systemctl enable --now docker > /dev/null
  # !reset en docker-compose.images.yml necesita Compose ≥ 2.24.4.
  local v
  v="$(docker compose version --short | sed 's/^v//')"
  if [[ "$(printf '%s\n2.24.4\n' "$v" | sort -V | head -n 1)" != "2.24.4" ]]; then
    die "Docker Compose $v es demasiado viejo (hace falta ≥ 2.24.4): apt-get install --only-upgrade docker-compose-plugin"
  fi
}

# ------------------------------------------------------------------ 3. swap
setup_swap() {
  step "3/10 Swap"
  if [[ -n "$(swapon --show --noheadings 2>/dev/null)" ]]; then
    ok "ya hay swap: $(swapon --show --noheadings | awk '{print $1, $3}' | head -n 1)"
    return 0
  fi
  # 2 GB de respaldo: evita que el kernel mate MySQL o Java en un pico (p. ej. al compilar en el servidor).
  if [[ ! -f /swapfile ]]; then
    fallocate -l 2G /swapfile || dd if=/dev/zero of=/swapfile bs=1M count=2048 status=none
    chmod 600 /swapfile
    mkswap /swapfile > /dev/null
  fi
  swapon /swapfile
  grep -q '^/swapfile ' /etc/fstab || echo '/swapfile none swap sw 0 0' >> /etc/fstab
  echo 'vm.swappiness=10' > /etc/sysctl.d/90-qiao-swap.conf
  sysctl -q -p /etc/sysctl.d/90-qiao-swap.conf
  ok "/swapfile de 2 GB (swappiness 10)"
}

# ------------------------------------------------------------------ 4. usuario qiao
create_user() {
  step "4/10 Usuario $QIAO_USER"
  if id "$QIAO_USER" > /dev/null 2>&1; then
    ok "ya existe"
  else
    useradd --create-home --shell /bin/bash "$QIAO_USER"
    ok "creado"
  fi
  # El grupo docker equivale a root: sólo para este usuario de operación.
  usermod -aG docker "$QIAO_USER"
  local home
  home="$(getent passwd "$QIAO_USER" | cut -d: -f6)"
  install -d -m 700 -o "$QIAO_USER" -g "$QIAO_USER" "$home/.ssh"
  # Las mismas claves SSH que root, para poder entrar como qiao (ssh qiao@IP).
  if [[ -s /root/.ssh/authorized_keys && ! -s "$home/.ssh/authorized_keys" ]]; then
    install -m 600 -o "$QIAO_USER" -g "$QIAO_USER" /root/.ssh/authorized_keys "$home/.ssh/authorized_keys"
    ok "copiadas las claves SSH de root"
  fi
}

# ------------------------------------------------------------------ 5. repositorio
repo_host() {
  local url="$1"
  if [[ "$url" =~ ^[A-Za-z0-9._-]+@([^:/]+): ]]; then echo "${BASH_REMATCH[1]}"
  elif [[ "$url" =~ ^ssh://([^@/]+@)?([^:/]+) ]]; then echo "${BASH_REMATCH[2]}"
  else echo ""; fi
}

setup_ssh_access() {
  local url="$1" host home key known fps fp
  host="$(repo_host "$url")"
  [[ -n "$host" ]] || return 0
  home="$(getent passwd "$QIAO_USER" | cut -d: -f6)"
  key="$home/.ssh/id_ed25519"
  known="$home/.ssh/known_hosts"
  if [[ ! -f "$key" ]]; then
    as_qiao ssh-keygen -q -t ed25519 -N "" -C "qiao-deploy@$(hostname)" -f "$key"
  fi
  if ! as_qiao ssh-keygen -F "$host" -f "$known" > /dev/null 2>&1; then
    local scan
    scan="$(ssh-keyscan -T 15 -t ed25519,ecdsa,rsa "$host" 2>/dev/null)" || true
    [[ -n "$scan" ]] || die "No se pudo contactar $host por SSH (puerto 22)"
    fps="$(ssh-keygen -lf - <<< "$scan" | awk '{print $2}')"
    if [[ "$host" == github.com ]]; then
      for fp in $fps; do
        [[ " $GITHUB_SSH_FINGERPRINTS " == *" $fp "* ]] || die "La clave SSH de github.com ($fp) no es la publicada por GitHub"
      done
    else
      warn "Huellas SSH de $host (compararlas con las que publica el proveedor):"
      printf '      %s\n' "$fps"
      ask TRUST_HOST "¿Confiar en $host? (si/no)" no
      [[ "$TRUST_HOST" == si ]] || die "Cancelado"
    fi
    printf '%s\n' "$scan" | as_qiao tee -a "$known" > /dev/null
  fi
  local tries=0
  until as_qiao env GIT_SSH_COMMAND="ssh -o BatchMode=yes" git ls-remote "$url" HEAD > /dev/null 2>&1; do
    tries=$((tries + 1))
    [[ $tries -le 5 ]] || die "Sin acceso al repo $url con la clave de despliegue"
    printf '\n%sEl servidor necesita permiso de LECTURA sobre el repo.%s\n' "$C_BOLD" "$C_RESET"
    echo "En GitHub: repo → Settings → Deploy keys → Add deploy key (sin marcar «Allow write access»)"
    echo "Título: qiao-$(hostname). Clave:"
    echo
    cat "$key.pub"
    echo
    ask KEY_ADDED "Escribir «listo» cuando esté agregada" listo
    unset QIAO_KEY_ADDED
  done
  ok "acceso de lectura a $url"
}

setup_repo() {
  local url="$1" branch="$2"
  step "5/10 Repositorio en $QIAO_DIR (rama $branch)"
  if [[ "$url" =~ ^https://[^/@]+@ ]]; then
    warn "La URL tiene credenciales: quedan guardadas en $QIAO_DIR/.git/config. Mejor usar una deploy key (git@...)."
  fi
  setup_ssh_access "$url"
  if [[ ! -d "$QIAO_DIR/.git" ]]; then
    [[ ! -e "$QIAO_DIR" || -z "$(ls -A "$QIAO_DIR" 2>/dev/null)" ]] || die "$QIAO_DIR existe y no es un repo git"
    install -d -o "$QIAO_USER" -g "$QIAO_USER" "$QIAO_DIR"
    as_qiao git clone --quiet --branch "$branch" "$url" "$QIAO_DIR"
    ok "clonado $(as_qiao git -C "$QIAO_DIR" log -1 --format='%h %s' | cut -c1-70)"
    return 0
  fi
  local current_url
  current_url="$(as_qiao git -C "$QIAO_DIR" remote get-url origin)"
  [[ "$current_url" == "$url" ]] || warn "El repo de $QIAO_DIR apunta a $current_url (no a $url): se usa el existente"
  as_qiao git -C "$QIAO_DIR" fetch --quiet origin "$branch"
  if [[ -n "$(as_qiao git -C "$QIAO_DIR" status --porcelain --untracked-files=no)" ]]; then
    warn "Hay cambios locales en $QIAO_DIR: no se actualiza (ver git -C $QIAO_DIR status)"
    return 0
  fi
  as_qiao git -C "$QIAO_DIR" checkout --quiet "$branch"
  if as_qiao git -C "$QIAO_DIR" merge --quiet --ff-only "origin/$branch"; then
    ok "actualizado a $(as_qiao git -C "$QIAO_DIR" log -1 --format='%h %s' | cut -c1-70)"
  else
    warn "No se pudo avanzar $branch a origin/$branch sin merge: revisar a mano"
  fi
}

# ------------------------------------------------------------------ 6. deploy/.env
GENERATED_ADMIN_PASSWORD=""

create_env() {
  local env_file="$QIAO_DIR/deploy/.env"
  step "6/10 Configuración deploy/.env"
  if [[ -f "$env_file" ]]; then
    ok "ya existe: se conserva tal cual (los secretos no se regeneran)"
    local k
    for k in DOMAIN ACME_EMAIL APP_BASE_URL JWT_SECRET DB_PASSWORD MYSQL_ROOT_PASSWORD; do
      grep -qE "^${k}=.+" "$env_file" || warn "Falta $k en $env_file"
    done
    chown "$QIAO_USER:$QIAO_USER" "$env_file"
    chmod 600 "$env_file"
    return 0
  fi

  if [[ -n "${QIAO_ENV_SOURCE:-}" ]]; then
    [[ -f "$QIAO_ENV_SOURCE" ]] || die "No existe QIAO_ENV_SOURCE=$QIAO_ENV_SOURCE"
    grep -qE '^JWT_SECRET=.+' "$QIAO_ENV_SOURCE" || die "$QIAO_ENV_SOURCE no parece un deploy/.env de Qiao"
    install -m 600 -o "$QIAO_USER" -g "$QIAO_USER" "$QIAO_ENV_SOURCE" "$env_file"
    ok "copiado desde $QIAO_ENV_SOURCE (mismos secretos que el servidor anterior; borrar ese archivo después)"
    return 0
  fi
  echo "Sólo se preguntan dominio, email del administrador, correo (SMTP) y pagos (Stripe)."
  echo "Los secretos internos (JWT, base de datos, clave del socio, contraseña del admin) se generan aquí."
  echo
  local DOMAIN ADMIN_EMAIL MAIL_MODE SMTP_HOST="" SMTP_PORT="465" SMTP_USER="" SMTP_PASSWORD="" SMTP_FROM=""
  local PAYMENT_PROVIDER STRIPE_SECRET_KEY="" STRIPE_WEBHOOK_SECRET=""
  while true; do
    ask DOMAIN "Dominio público (sin https://, p. ej. qiao.example.com)"
    DOMAIN="${DOMAIN,,}"
    if [[ "$DOMAIN" =~ ^([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z][a-z0-9-]{1,62}$ ]]; then break; fi
    echo "  dominio inválido"; unset QIAO_DOMAIN
  done
  [[ "$DOMAIN" != *.cn ]] || warn "Un dominio .cn exige registro en China (ICP): ver docs/OPERACIONES.md"
  while true; do
    ask ADMIN_EMAIL "Email del primer administrador (también recibe los avisos de Let's Encrypt)"
    if [[ "$ADMIN_EMAIL" =~ ^[^@[:space:]\']+@[^@[:space:]\']+\.[^@[:space:]\']+$ ]]; then break; fi
    echo "  email inválido"; unset QIAO_ADMIN_EMAIL
  done

  echo
  echo "Correo: «smtp» envía de verdad (Alibaba DirectMail); «log» NO envía nada (sólo para una prueba inicial:"
  echo "los códigos de acceso se ven en el back-office → Outbox)."
  while true; do
    ask MAIL_MODE "Modo de correo (smtp/log)" smtp
    [[ "$MAIL_MODE" == smtp || "$MAIL_MODE" == log ]] && break
    unset QIAO_MAIL_MODE
  done
  if [[ "$MAIL_MODE" == smtp ]]; then
    ask SMTP_HOST "Servidor SMTP" smtpdm-ap-southeast-1.aliyun.com
    ask SMTP_PORT "Puerto SMTP (SSL)" 465
    [[ "$SMTP_PORT" =~ ^[0-9]+$ ]] || die "Puerto SMTP inválido"
    ask SMTP_USER "Usuario SMTP (dirección remitente de DirectMail)" "no-reply@$DOMAIN"
    ask_secret SMTP_PASSWORD "Contraseña SMTP"
    ask SMTP_FROM "Remitente (From)" "$SMTP_USER"
  fi

  echo
  echo "Pagos: «stripe» cobra de verdad; «fake» simula los pagos (sólo para una prueba inicial)."
  while true; do
    ask PAYMENT_PROVIDER "Proveedor de pagos (stripe/fake)" stripe
    [[ "$PAYMENT_PROVIDER" == stripe || "$PAYMENT_PROVIDER" == fake ]] && break
    unset QIAO_PAYMENT_PROVIDER
  done
  if [[ "$PAYMENT_PROVIDER" == stripe ]]; then
    while true; do
      ask_secret STRIPE_SECRET_KEY "Clave secreta de Stripe (sk_live_... o rk_live_...)"
      [[ "$STRIPE_SECRET_KEY" =~ ^(sk|rk)_(live|test)_[A-Za-z0-9]+$ ]] && break
      echo "  no parece una clave de Stripe"; unset QIAO_STRIPE_SECRET_KEY
    done
    [[ "$STRIPE_SECRET_KEY" != *_test_* ]] || warn "Es una clave de PRUEBA de Stripe (_test_): no cobra de verdad"
    echo "  Webhook de Stripe: https://$DOMAIN/api/payments/stripe/webhook, evento checkout.session.completed"
    while true; do
      ask_secret STRIPE_WEBHOOK_SECRET "Secreto de firma del webhook (whsec_...)"
      [[ "$STRIPE_WEBHOOK_SECRET" =~ ^whsec_[A-Za-z0-9]+$ ]] && break
      echo "  no parece un secreto de webhook"; unset QIAO_STRIPE_WEBHOOK_SECRET
    done
  fi

  local v
  for v in DOMAIN ADMIN_EMAIL SMTP_HOST SMTP_USER SMTP_PASSWORD SMTP_FROM STRIPE_SECRET_KEY STRIPE_WEBHOOK_SECRET; do
    check_value "$v" "${!v}"
  done

  GENERATED_ADMIN_PASSWORD="$(gen_password)"
  local tag
  tag="$(as_qiao git -C "$QIAO_DIR" rev-parse --short=12 HEAD 2>/dev/null || echo local)"
  (
    umask 077
    cat > "$env_file.tmp" <<EOF
# Generado por deploy/install.sh el $(date -u +%Y-%m-%d). Contiene secretos: no copiar al repo.
# Guardar una copia en un gestor de contraseñas: sin este archivo no se puede restaurar en otro servidor.
# Formato y variables: deploy/.env.example. Tras editar: cd $QIAO_DIR/deploy && docker compose up -d

# --- Dominio y HTTPS ---
DOMAIN='$DOMAIN'
ACME_EMAIL='$ADMIN_EMAIL'
APP_BASE_URL='https://$DOMAIN'

# --- Secretos generados en este servidor ---
JWT_SECRET='$(gen_hex 32)'
DB_USER='qiao'
DB_PASSWORD='$(gen_hex 16)'
MYSQL_ROOT_PASSWORD='$(gen_hex 16)'
PARTNER_API_KEY='$(gen_hex 24)'
ADMIN_BOOTSTRAP_EMAIL='$ADMIN_EMAIL'
ADMIN_BOOTSTRAP_PASSWORD='$GENERATED_ADMIN_PASSWORD'

PLATFORM_FEE_PCT='25'
# Razones sociales de los contratos (completar antes de firmar contratos reales):
# APP_CONTRACTS_HK_ENTITY_NAME='Nombre legal de la Ltd de Hong Kong'
# APP_CONTRACTS_PARTNER_ENTITY_NAME='Nombre legal del socio en China'

# --- Correo ---
MAIL_MODE='$MAIL_MODE'
SMTP_HOST='$SMTP_HOST'
SMTP_PORT='$SMTP_PORT'
SMTP_USER='$SMTP_USER'
SMTP_PASSWORD='$SMTP_PASSWORD'
SMTP_FROM='${SMTP_FROM:-no-reply@$DOMAIN}'

# --- Pagos ---
PAYMENT_PROVIDER='$PAYMENT_PROVIDER'
STRIPE_SECRET_KEY='$STRIPE_SECRET_KEY'
STRIPE_WEBHOOK_SECRET='$STRIPE_WEBHOOK_SECRET'

# --- Archivos subidos ---
STORAGE='local'
OSS_ENDPOINT='https://oss-cn-hongkong.aliyuncs.com'
OSS_BUCKET=''
OSS_ACCESS_KEY_ID=''
OSS_ACCESS_KEY_SECRET=''

# --- Variante de despliegue (ver .env.example) ---
# COMPOSE_FILE=docker-compose.yml:docker-compose.images.yml
# COMPOSE_PROFILES=monitoring
QIAO_REGISTRY='registry-vpc.cn-hongkong.aliyuncs.com'
QIAO_NAMESPACE=''
QIAO_IMAGE_TAG='$tag'

# --- Backups (copia externa en OSS: completar BACKUP_OSS_*) ---
BACKUP_DIR='$BACKUP_ROOT'
BACKUP_KEEP='14'
BACKUP_OSS_BUCKET=''
BACKUP_OSS_ENDPOINT='oss-cn-hongkong-internal.aliyuncs.com'
BACKUP_OSS_PREFIX='qiao'
BACKUP_OSS_ACCESS_KEY_ID=''
BACKUP_OSS_ACCESS_KEY_SECRET=''
EOF
  )
  chown "$QIAO_USER:$QIAO_USER" "$env_file.tmp"
  chmod 600 "$env_file.tmp"
  mv "$env_file.tmp" "$env_file"
  ok "creado $env_file (permisos 600)"
  if [[ "$MAIL_MODE" == log || "$PAYMENT_PROVIDER" == fake ]]; then
    local msgs=("MODO DE PRUEBA: NO usar con clientes reales.")
    [[ "$MAIL_MODE" == log ]] && msgs+=("MAIL_MODE=log: no se envía NINGÚN correo (códigos de acceso sólo en back-office → Outbox).")
    [[ "$PAYMENT_PROVIDER" == fake ]] && msgs+=("PAYMENT_PROVIDER=fake: los pagos son SIMULADOS, nadie paga de verdad.")
    msgs+=("Para producción: editar $env_file (MAIL_MODE=smtp, PAYMENT_PROVIDER=stripe y sus claves) y docker compose up -d")
    loud "${msgs[@]}"
  fi
}

# ------------------------------------------------------------------ 7. firewall
setup_firewall() {
  step "7/10 Firewall (ufw)"
  local ports port
  ports="$(sshd -T 2>/dev/null | awk '$1 == "port" { print $2 }' | sort -u)"
  [[ -n "$ports" ]] || ports=22
  for port in $ports; do
    ufw allow "$port/tcp" comment 'SSH' > /dev/null
  done
  ufw allow 80/tcp comment 'HTTP (Caddy: certificado y redirección)' > /dev/null
  ufw allow 443/tcp comment 'HTTPS' > /dev/null
  ufw default deny incoming > /dev/null
  ufw default allow outgoing > /dev/null
  ufw --force enable > /dev/null
  ok "entrada permitida sólo a SSH ($(echo "$ports" | tr '\n' ' ' | sed 's/ $//')), 80 y 443"
  echo "      Ojo: los puertos que publica Docker no pasan por ufw; el grupo de seguridad de Alibaba es la"
  echo "      barrera real (abrir sólo 22 desde tu IP, 80 y 443)."
}

# ------------------------------------------------------------------ 8. actualizaciones automáticas
setup_auto_updates() {
  step "8/10 Actualizaciones automáticas de seguridad"
  cat > /etc/apt/apt.conf.d/20auto-upgrades <<'EOF'
APT::Periodic::Update-Package-Lists "1";
APT::Periodic::Unattended-Upgrade "1";
APT::Periodic::AutocleanInterval "7";
EOF
  # Sin reinicio automático: status.sh avisa cuando hace falta reiniciar (ver OPERACIONES.md).
  # Docker no se actualiza solo (su repositorio no está en los orígenes de unattended-upgrades): un reinicio
  # del demonio en mitad del día cortaría el servicio. Se actualiza a mano en la rutina mensual.
  cat > /etc/apt/apt.conf.d/52qiao-unattended <<'EOF'
Unattended-Upgrade::Remove-Unused-Kernel-Packages "true";
Unattended-Upgrade::Remove-Unused-Dependencies "true";
Unattended-Upgrade::Automatic-Reboot "false";
EOF
  systemctl enable --now unattended-upgrades > /dev/null 2>&1 || true
  ok "parches de seguridad de Ubuntu todos los días (sin reinicio automático)"
}

# ------------------------------------------------------------------ 9. backups
setup_backups() {
  step "9/10 Backups diarios"
  install -d -m 700 "$BACKUP_ROOT" "$STATE_DIR"
  cat > /etc/systemd/system/qiao-backup.service <<EOF
[Unit]
Description=Backup de Qiao (MySQL + archivos subidos, copia en OSS si está configurada)
Requires=docker.service
After=docker.service

[Service]
Type=oneshot
ExecStart=$QIAO_DIR/deploy/backup.sh
Nice=10
IOSchedulingClass=idle
EOF
  cat > /etc/systemd/system/qiao-backup.timer <<'EOF'
[Unit]
Description=Backup diario de Qiao a las 03:30 de Hong Kong

[Timer]
OnCalendar=*-*-* 03:30:00 Asia/Hong_Kong
RandomizedDelaySec=300
Persistent=true

[Install]
WantedBy=timers.target
EOF
  systemctl daemon-reload
  systemctl enable --now qiao-backup.timer > /dev/null 2>&1
  ok "qiao-backup.timer: todos los días 03:30 (HKT), 14 copias en $BACKUP_ROOT"
}

# ------------------------------------------------------------------ 10. arranque
env_value() {
  grep -E "^$1=" "$QIAO_DIR/deploy/.env" | tail -n 1 | cut -d= -f2- | sed "s/^'\(.*\)'$/\1/"
}

DNS_OK=1
check_dns() {
  local domain="$1" resolved public=""
  resolved="$(getent ahostsv4 "$domain" 2>/dev/null | awk '{ print $1 }' | sort -u | tr '\n' ' ' | sed 's/ $//')"
  # IP pública del ECS desde el servicio de metadatos de Alibaba (sólo accesible desde dentro del ECS).
  public="$(curl -fsS --noproxy '*' --max-time 3 http://100.100.100.200/latest/meta-data/eipv4 2>/dev/null \
    || curl -fsS --noproxy '*' --max-time 3 http://100.100.100.200/latest/meta-data/public-ipv4 2>/dev/null || true)"
  if [[ -z "$resolved" ]]; then
    DNS_OK=0
    loud "El dominio $domain todavía no resuelve a ninguna IP." \
      "Crear en el DNS un registro A: $domain → ${public:-<IP pública del ECS>}" \
      "Caddy reintentará el certificado solo cuando el DNS funcione."
  elif [[ -n "$public" && " $resolved " != *" $public "* ]]; then
    DNS_OK=0
    loud "El dominio $domain apunta a $resolved, pero la IP pública de este servidor es $public." \
      "Let's Encrypt no podrá emitir el certificado hasta que el registro A apunte aquí."
  else
    ok "DNS: $domain → $resolved"
  fi
}

start_stack() {
  step "10/10 Arranque"
  local domain
  domain="$(env_value DOMAIN)"
  check_dns "$domain"
  cd "$QIAO_DIR/deploy"
  local backend_id
  backend_id="$(docker compose ps -q backend 2>/dev/null || true)"
  if [[ -n "$backend_id" ]]; then
    echo "      El stack ya existe: se aplican cambios de configuración (para código nuevo usar deploy/update.sh)."
    docker compose up -d --no-build --wait --wait-timeout 600 || warn "Algún servicio no quedó sano: $QIAO_DIR/deploy/status.sh"
  else
    if docker compose config --images | grep -q '/qiao-backend:'; then
      docker compose pull
    else
      echo "      Compilando backend, web y H5 en el servidor (5-15 minutos la primera vez)..."
      docker compose build
    fi
    docker compose up -d --wait --wait-timeout 600 || warn "Algún servicio no quedó sano todavía"
    install -d -m 700 "$STATE_DIR"
    printf '%s\t%s\t%s\t%s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$(env_value QIAO_IMAGE_TAG)" OK "instalación" >> "$STATE_DIR/deploy-history"
  fi
  echo "      Esperando el certificado HTTPS de Let's Encrypt..."
  local tries=18
  [[ $DNS_OK -eq 1 ]] || tries=2
  for _ in $(seq 1 "$tries"); do
    if curl -fsS --noproxy '*' --max-time 10 --resolve "$domain:443:127.0.0.1" -o /dev/null "https://$domain/api/health" 2>/dev/null; then
      ok "https://$domain responde con un certificado válido"
      HTTPS_OK=1
      return 0
    fi
    sleep 10
  done
  warn "Todavía no hay HTTPS válido para $domain. Revisar: DNS (registro A), grupo de seguridad (80 y 443 abiertos)"
  warn "y docker compose logs caddy. Caddy sigue reintentando solo; volver a mirar con $QIAO_DIR/deploy/status.sh"
}

HTTPS_OK=0
first_backup() {
  step "Primer backup de prueba"
  if [[ "$(docker inspect --format '{{.State.Health.Status}}' "$(cd "$QIAO_DIR/deploy" && docker compose ps -q mysql)" 2>/dev/null)" != healthy ]]; then
    warn "MySQL no está sano: se omite (probar luego con sudo $QIAO_DIR/deploy/backup.sh --label prueba)"
    return 0
  fi
  "$QIAO_DIR/deploy/backup.sh" --label install || warn "El backup de prueba falló: revisar antes de abrir el servicio"
}

summary() {
  local domain
  domain="$(env_value DOMAIN)"
  printf '\n%s================ Qiao instalado ================%s\n' "$C_BOLD" "$C_RESET"
  echo "Clientes:   https://$domain/"
  echo "Ingenieros: https://$domain/m/"
  echo "Back-office https://$domain/admin   usuario: $(env_value ADMIN_BOOTSTRAP_EMAIL)"
  if [[ -n "$GENERATED_ADMIN_PASSWORD" ]]; then
    printf '%sContraseña del admin (se muestra UNA sola vez; guardarla ya en un gestor de contraseñas):%s\n' "$C_BOLD" "$C_RESET"
    echo "            $GENERATED_ADMIN_PASSWORD"
  fi
  cat <<EOF

Próximos pasos (detalle en docs/OPERACIONES.md):
  1. Guardar una copia de $QIAO_DIR/deploy/.env en el gestor de contraseñas (sin ella no hay restauración completa).
  2. Stripe: webhook https://$domain/api/payments/stripe/webhook (evento checkout.session.completed).
  3. Completar APP_CONTRACTS_HK_ENTITY_NAME y APP_CONTRACTS_PARTNER_ENTITY_NAME en deploy/.env.
  4. Entregar al socio la clave PARTNER_API_KEY (está en deploy/.env) por un canal seguro.
  5. Copia externa de backups: completar BACKUP_OSS_* en deploy/.env y probar: sudo $QIAO_DIR/deploy/backup.sh
  6. Configurar 拨测 (云拨测 o 17CE) contra https://$domain/m/ desde Telecom, Unicom y Mobile.
  7. Estado en cualquier momento: sudo $QIAO_DIR/deploy/status.sh
EOF
  if [[ $HTTPS_OK -ne 1 ]]; then
    warn "HTTPS todavía no funciona: las URL de arriba no abrirán hasta que el DNS y el certificado estén listos."
  fi
  if sshd -T 2>/dev/null | grep -qi '^passwordauthentication yes'; then
    warn "SSH acepta contraseñas: usar un par de claves de Alibaba y desactivarlas (ver OPERACIONES.md)."
  fi
  if [[ "$(env_value MAIL_MODE)" == log || "$(env_value PAYMENT_PROVIDER)" == fake ]]; then
    loud "Sigue en MODO DE PRUEBA (MAIL_MODE=log o PAYMENT_PROVIDER=fake): no abrir a clientes reales."
  fi
}

main() {
  [[ $# -ge 1 && "$1" != -h && "$1" != --help ]] || { sed -n '2,17p' "$0"; exit 1; }
  local repo_url="$1" branch="${2:-main}"
  [[ "$branch" =~ ^[A-Za-z0-9._/-]+$ ]] || die "Rama inválida: $branch"
  check_system
  install_packages
  install_docker
  setup_swap
  create_user
  setup_repo "$repo_url" "$branch"
  create_env
  setup_firewall
  setup_auto_updates
  setup_backups
  start_stack
  first_backup
  summary
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  main "$@"
fi
