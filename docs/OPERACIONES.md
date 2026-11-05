# Operaciones de Qiao (fase 1)

Guía para poner en marcha y operar Qiao en **un solo servidor ECS de Alibaba Cloud en Hong Kong** (región
`cn-hongkong`). Está escrita para una persona sola que no es especialista en servidores: cada tarea tiene los
comandos exactos para copiar y pegar.

- Todos los comandos se ejecutan **en el servidor, como root** (`ssh root@IP` o `sudo -i`), salvo que se diga otra cosa.
- El código vive en `/opt/qiao`; la configuración y los secretos en `/opt/qiao/deploy/.env`.
- Ante cualquier duda, lo primero es siempre: `sudo /opt/qiao/deploy/status.sh`.

## Índice
1. [Cómo está armado](#1-cómo-está-armado)
2. [Primer despliegue paso a paso](#2-primer-despliegue-paso-a-paso)
3. [Rutinas diarias, semanales y mensuales](#3-rutinas)
4. [Actualizar](#4-actualizar)
5. [Volver atrás (rollback)](#5-volver-atrás-rollback)
6. [Backups y restauración](#6-backups-y-restauración)
7. [Rotar secretos](#7-rotar-secretos)
8. [Incidentes: listas de verificación](#8-incidentes-listas-de-verificación)
9. [Monitoreo y 拨测 desde China](#9-monitoreo-y-拨测-desde-china)
10. [Alternativa: HTTPS en un SLB de Alibaba](#10-alternativa-https-en-un-slb-de-alibaba)
11. [CI y GitHub](#11-ci-y-github)
12. [Costos (estimaciones)](#12-costos-estimaciones)
13. [Referencia rápida](#13-referencia-rápida)

---

## 1. Cómo está armado

```
Internet ──80/443──> caddy ──> nginx ──> backend ──> mysql
                     HTTPS     web /      API Java    base de datos
                     Let's     H5 /m/     (Spring)    (volumen mysql-data)
                     Encrypt   /api/ ─────┘ archivos subidos (volumen files)
```

| Servicio | Qué hace | Puerto |
| --- | --- | --- |
| `caddy` | Pide y renueva solo el certificado HTTPS (Let's Encrypt) y redirige HTTP → HTTPS | 80 y 443 del servidor (los únicos abiertos) |
| `nginx` | Sirve la web de clientes y el back-office (`/`, `/admin`), la app de ingenieros (`/m/`) y pasa `/api/` al backend. **Pone las cabeceras de seguridad** (CSP sin recursos externos, HSTS…) una sola vez | sólo interno |
| `backend` | API (Java). Al arrancar aplica las migraciones de base de datos y crea el primer administrador | sólo interno |
| `mysql` | MySQL 8.4 | sólo interno |
| `uptime-kuma` | (opcional) monitoreo propio | `127.0.0.1:3001`, sólo por túnel SSH |

| Dónde | Qué hay |
| --- | --- |
| `/opt/qiao` | el repositorio (dueño: usuario `qiao`) |
| `/opt/qiao/deploy/.env` | **toda la configuración y los secretos** (permisos 600). Guardar una copia en un gestor de contraseñas |
| `/var/backups/qiao/daily`, `/manual` | backups locales (sólo root) |
| `/var/lib/qiao/deploy-history` | historial de versiones desplegadas (lo usa `update.sh --rollback`) |
| `/var/lib/qiao/env-backups/` | copias del `.env` anterior a cada cambio automático |
| volúmenes Docker `qiao_mysql-data`, `qiao_files`, `qiao_caddy-data` | base de datos, archivos subidos, certificados |

Scripts en `/opt/qiao/deploy/`: `install.sh` (instalación), `status.sh` (estado), `update.sh` (actualizar y
volver atrás), `backup.sh`, `restore.sh`, `rotate-secret.sh`.

Los logs de cada contenedor rotan solos: como máximo 5 archivos de 10 MB por servicio (`docker compose logs`).

---

## 2. Primer despliegue paso a paso

### 2.1 Antes de empezar
- Cuenta de Alibaba Cloud internacional (alibabacloud.com) verificada, con medio de pago.
- Un dominio que **no** sea `.cn` (un `.cn` exige registro ICP en China; con el servidor en Hong Kong no hace falta ICP).
- Acceso al repositorio de GitHub (para crear una *deploy key*).
- Un gestor de contraseñas (Bitwarden, 1Password…): ahí van la contraseña del admin y la copia del `.env`.
- Para producción real (se puede dejar para después de una prueba): cuenta de Stripe de la Ltd de Hong Kong
  y Alibaba **DirectMail** con el dominio verificado (SPF, DKIM y DMARC).

### 2.2 Crear el servidor (ECS)
En la consola: **Elastic Compute Service → Instances → Create Instance**.
1. **Billing**: *Subscription* (mensual, más barato) o *Pay-as-you-go* para la prueba.
2. **Region**: *China (Hong Kong)*. Zona: cualquiera.
3. **Instance type**: 2 vCPU y 4 GiB (por ejemplo familias `u1`, `c7` o `g7`, «2 vCPU 4 GiB»).
4. **Image**: *Ubuntu 24.04 64-bit*.
5. **System disk**: ESSD (Entry o PL0), **40 GB** (60 GB si se va a compilar en el servidor; ver 4.2).
6. **Public IP**: asignar IP pública (o una EIP), facturación **por tráfico** (*pay-by-traffic*), pico 100 Mbps.
   Si el 拨测 muestra mala conexión desde China, ver «BGP (Multi-ISP) Pro» en 8.1.
7. **Security group**: crear uno nuevo (siguiente punto).
8. **Logon credentials**: *Key pair* (crear uno y guardar el `.pem`). Evitar contraseña.
9. Nombre `qiao-prod`. Activar **Release protection** (evita borrarla por error).
10. Después de crearla: **Snapshots → Automatic snapshot policy**: una diaria, conservar 7, y asociarla al disco.
    Es una red de seguridad extra; los backups de la sección 6 son la principal.

### 2.3 Grupo de seguridad
Reglas de **entrada** (*Inbound*); salida: todo permitido.

| Puerto | Origen | Para qué |
| --- | --- | --- |
| 22/TCP | **tu IP**/32 (no 0.0.0.0/0) | SSH |
| 80/TCP | 0.0.0.0/0 | certificado de Let's Encrypt y redirección a HTTPS |
| 443/TCP | 0.0.0.0/0 | el sitio |
| ICMP | 0.0.0.0/0 (opcional) | que el 拨测 pueda hacer ping |

Nada más. Importante: el firewall del propio servidor (ufw) **no** filtra los puertos que publica Docker; la
barrera real es este grupo de seguridad.

### 2.4 DNS
En el proveedor del dominio, crear un registro **A**: `tu-dominio.com → <IP pública del ECS>` (TTL 600).
Comprobar desde tu computadora: `nslookup tu-dominio.com` debe devolver esa IP. Opcional: registro CAA
`0 issue "letsencrypt.org"`. Hacerlo **antes** de instalar: Let's Encrypt valida el dominio contra el servidor.

### 2.5 Ejecutar la instalación
1. Entrar al servidor: `ssh -i clave.pem root@IP`.
2. Llevar `install.sh` al servidor, de una de estas formas:
   - repo público: `curl -fsSL https://raw.githubusercontent.com/<org>/qiao/main/deploy/install.sh -o install.sh`
   - desde tu computadora (repo privado): `scp -i clave.pem deploy/install.sh root@IP:/root/`
   - o pegarlo: `cat > install.sh`, pegar el contenido, Enter y `Ctrl-D`.
3. Ejecutarlo con la URL SSH del repo y la rama:
   ```bash
   bash install.sh git@github.com:<org>/qiao.git main
   ```
4. El script muestra una **clave pública** y pide agregarla en GitHub: repo → *Settings → Deploy keys →
   Add deploy key*, título `qiao-<servidor>`, **sin** marcar *Allow write access*. Escribir `listo`.
5. Responde las preguntas: dominio, email del administrador (también recibe los avisos de Let's Encrypt),
   correo (`smtp` con usuario y contraseña de DirectMail, o `log` para una prueba), pagos (`stripe` con la
   clave secreta y el secreto del webhook, o `fake` para una prueba) y las **razones sociales** que se imprimen
   en cada contrato: la de la Ltd de Hong Kong y la del socio en China continental, tal como figuran en el
   registro de empresas. Con `stripe` son obligatorias; con `fake` se pueden dejar vacías (ver 2.8). Todo lo
   demás (JWT, contraseñas de la base, clave del socio, contraseña del admin) lo genera el script.
6. Espera: instala Docker, configura firewall, swap, actualizaciones automáticas y backups, compila y arranca
   (10–20 minutos la primera vez).
7. Al final muestra las URL y **la contraseña del administrador una sola vez**: guardarla ya en el gestor.

El script se puede volver a ejecutar sin riesgo: no toca un `.env` existente ni regenera secretos.

### 2.6 Comprobar
```bash
sudo /opt/qiao/deploy/status.sh
```
Debe terminar en «Todo en orden» (con avisos si se usó `fake`/`log`). Abrir `https://tu-dominio.com/admin` y
entrar con el email y la contraseña del admin. Abrir `https://tu-dominio.com/m/` desde un teléfono **en China
continental sin VPN** (o con el 拨测 de la sección 9).

### 2.7 Después del primer arranque
1. **Copia del `.env`**: `sudo cat /opt/qiao/deploy/.env` y guardarlo completo en el gestor de contraseñas
   (nota segura). Sin él no se puede restaurar en otro servidor con los mismos secretos.
2. **Stripe**: en el panel de Stripe, *Developers → Webhooks → Add endpoint*:
   `https://tu-dominio.com/api/payments/stripe/webhook`, evento `checkout.session.completed`.
3. **Razones sociales de los contratos**: comprobar en `.env` `APP_CONTRACTS_HK_ENTITY_NAME` y
   `APP_CONTRACTS_PARTNER_ENTITY_NAME` (las pregunta `install.sh`). Con `PAYMENT_PROVIDER=stripe` el backend **no
   arranca** si están vacías o todavía tienen un marcador entre corchetes (`[…]`, `[dev]`).
4. **Cambiar la contraseña del admin** que mostró la instalación por una propia: back-office → *Mi perfil* →
   *Contraseña* (ver 7.1), y activar ahí mismo la verificación en dos pasos.
5. **Socio chino**: entregarle `PARTNER_API_KEY` (está en `.env`) por un canal seguro.
6. **Backups fuera del servidor**: configurar OSS (6.2).
7. **拨测** desde China (9.3).
8. **SSH sólo con clave**: si `status.sh`/`install.sh` avisan que SSH acepta contraseñas, en
   `/etc/ssh/sshd_config` poner `PasswordAuthentication no` y `systemctl restart ssh` (antes comprobar que
   entrás con la clave en otra ventana).

### 2.8 Pasar de modo prueba a producción
Si se instaló con `MAIL_MODE=log` o `PAYMENT_PROVIDER=fake`, **no abrir a clientes reales**: los correos no
salen (los códigos de acceso sólo se ven en back-office → Outbox) y los pagos son simulados (la página de pago dice
«modo de prueba»). Con `fake`, además, se pudo instalar sin razones sociales: el backend arranca igual, con un aviso
`TEST MODE` en sus logs, y los contratos salen con marcadores `[…]`. Para pasar a real:
```bash
sudo nano /opt/qiao/deploy/.env     # MAIL_MODE='smtp', SMTP_*; PAYMENT_PROVIDER='stripe', STRIPE_*;
                                    # APP_CONTRACTS_HK_ENTITY_NAME y APP_CONTRACTS_PARTNER_ENTITY_NAME (razones sociales)
cd /opt/qiao/deploy && sudo docker compose up -d
sudo /opt/qiao/deploy/status.sh
```
Con `stripe` y sin razones sociales el backend se niega a arrancar (`docker compose logs backend` lo dice:
«APP_CONTRACTS_… must hold the registered legal name…»): completarlas y repetir `docker compose up -d`.
Después de cualquier cambio en `.env` hace falta `docker compose up -d` (`docker compose restart` **no** relee
el archivo). Actualizar la copia del `.env` en el gestor.

---

## 3. Rutinas

### Diaria (2 minutos)
- `sudo /opt/qiao/deploy/status.sh` → «Todo en orden». Revisa contenedores, salud de punta a punta (con la versión
  y el commit del backend en marcha), que la CSP y HSTS salgan una vez, certificado, disco, memoria, edad del último
  backup y de la última subida a OSS, y avisa de una rotación de la clave del socio sin terminar.
- Mirar si llegaron alertas del 拨测 o de Uptime Kuma.

### Semanal (15 minutos)
- Errores del backend de la semana:
  `cd /opt/qiao/deploy && sudo docker compose logs --since 168h backend | grep -E ' ERROR | WARN ' | tail -50`
- Backups de la semana: `sudo journalctl -u qiao-backup --since -7d | grep -E 'OK|FALLA|AVISO'`
- Back-office → Outbox: correos con error de envío.
- Uso de disco en `status.sh` (avisa al 80 %).

### Mensual (1 hora, siempre el mismo día; anotar el resultado)
1. **Prueba de restauración** (sin tocar producción):
   ```bash
   sudo /opt/qiao/deploy/restore.sh --dry-run          # último backup diario
   ```
   Verifica las sumas SHA-256, que el volcado esté completo, lo carga en un MySQL temporal y muestra la
   versión de Flyway y las filas por tabla; termina con «PRUEBA DE RESTAURACIÓN CORRECTA». Comparar a ojo las
   filas con lo esperado (p. ej. cantidad de usuarios y contratos). Una vez por trimestre, hacerlo con un
   backup **traído de OSS**: `restore.sh --list`, `restore.sh --fetch <nombre>`, `restore.sh --dry-run <nombre>`.
   Anotar en el registro de operaciones: fecha, backup probado, resultado, tiempo.
2. **Reinicio por parches del kernel** si `status.sh` lo pide, en la hora de menos uso (p. ej. 04:00 HKT):
   `sudo reboot`; a los 3 minutos `sudo /opt/qiao/deploy/status.sh`. Los contenedores arrancan solos.
3. **Actualizar Docker y las imágenes base** (MySQL 8.4.x, Caddy 2.10.x, Uptime Kuma), después de un backup:
   ```bash
   sudo /opt/qiao/deploy/backup.sh --label pre-mensual
   sudo apt-get update && sudo apt-get install -y --only-upgrade docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
   cd /opt/qiao/deploy && sudo docker compose pull mysql caddy && sudo docker compose up -d
   sudo /opt/qiao/deploy/status.sh
   ```
4. Revisar la factura de Alibaba Cloud (sección 12) y que la política de snapshots siga activa.
5. Revisar en RAM que no haya AccessKeys sin uso o de personas que ya no trabajan en el proyecto.

### Anual
- Rotar `JWT_SECRET`, `PARTNER_API_KEY` (en dos pasos, sin corte para el socio) y las contraseñas de la base
  (sección 7).
- Revisar en back-office → *Administradores* que no queden cuentas activas de personas que ya no trabajan en el
  proyecto (desactivarlas, 7.1).
- Ensayo completo de recuperación en un ECS nuevo (6.5) y borrarlo al terminar.

---

## 4. Actualizar

### 4.1 Con imágenes de CI (recomendado)
Flujo: cambio en `main` → CI en verde → el job `images` publica `qiao-backend:<sha>` y `qiao-nginx:<sha>` en
Alibaba Container Registry y muestra en su resumen el comando exacto. En el servidor:
```bash
sudo /opt/qiao/deploy/update.sh <sha-del-commit>
```
Qué hace `update.sh`: descarga las imágenes (si falla, no cambia nada) → guarda las imágenes en marcha para
poder volver → backup de la base y los archivos (`manual/<fecha>-pre-<sha>`) → despliega backend y nginx →
espera los healthchecks y que `/api/health` y `/m/` respondan por HTTPS → si algo falla **vuelve sola a la
versión anterior**. La API queda sin servicio 1–2 minutos mientras el backend arranca (la web y `/m/` siguen
cargando). Cada paso queda en `update.sh --history`. Qué versión quedó en marcha: `status.sh` («backend 0.1.0,
commit <sha>») o `https://tu-dominio.com/api/health` (`version` = versión de Maven, `commit` = commit del build).

Si el commit cambió archivos de `deploy/` (compose, Caddyfile, scripts), traerlos antes:
`sudo -u qiao git -C /opt/qiao pull --ff-only`.

### 4.2 Compilando en el servidor (modo por defecto tras la instalación)
```bash
sudo /opt/qiao/deploy/update.sh          # git pull + compila con tag = commit + despliega (+ vuelta atrás)
```
El commit compilado queda dentro de la imagen y lo muestran `/api/health` y `status.sh`.
La compilación tarda 5–15 minutos y usa mucha CPU y memoria (la versión actual sigue atendiendo mientras
tanto). Por eso conviene pasar al modo de imágenes (4.3) en cuanto CI esté configurado.

### 4.3 Activar el modo de imágenes (una vez)
1. **ACR**: consola → *Container Registry* → región *China (Hong Kong)* → instancia *Personal Edition*.
   Crear un *namespace* (p. ej. `qiao`), y en él los repositorios **privados** `qiao-backend` y `qiao-nginx`
   (tipo *local repository*). En *Access Credentials* fijar una contraseña de registro.
2. **GitHub**: variables y secrets de la sección 11. Hacer un push a `main` y esperar el job `images`.
3. **Servidor**: iniciar sesión en el registro por la red interna (tráfico gratis):
   ```bash
   sudo docker login --username=<usuario de ACR> registry-vpc.cn-hongkong.aliyuncs.com
   ```
4. En `/opt/qiao/deploy/.env`: `COMPOSE_FILE=docker-compose.yml:docker-compose.images.yml` y
   `QIAO_NAMESPACE='qiao'` (`QIAO_REGISTRY` ya viene con `registry-vpc.cn-hongkong.aliyuncs.com`).
5. `sudo /opt/qiao/deploy/update.sh <sha>` (el sha del último job `images`).

### 4.4 Cambiar configuración
Editar `/opt/qiao/deploy/.env` y `cd /opt/qiao/deploy && sudo docker compose up -d`. Actualizar la copia del
gestor de contraseñas.

---

## 5. Volver atrás (rollback)
- **Automático**: si una versión nueva no queda sana, `update.sh` vuelve sola a la anterior y termina con error.
- **Manual** (la versión nueva arrancó pero tiene un error funcional):
  ```bash
  sudo /opt/qiao/deploy/update.sh --history      # versiones desplegadas (OK / FALLA)
  sudo /opt/qiao/deploy/update.sh --rollback     # vuelve a la anterior que quedó OK
  sudo /opt/qiao/deploy/update.sh <sha>          # o a una versión concreta
  ```
- **Cuidado con la base de datos**: las migraciones (Flyway) sólo avanzan. Volver atrás la imagen no deshace un
  cambio de esquema. Si la versión nueva migró (`update.sh` lo avisa: «aplicó migraciones») y la anterior no
  funciona con el esquema nuevo, restaurar el backup previo a la actualización (se pierde lo escrito desde entonces):
  ```bash
  sudo /opt/qiao/deploy/restore.sh --list                       # buscar manual/<fecha>-pre-<sha>
  sudo /opt/qiao/deploy/restore.sh --db-only <fecha>-pre-<sha>
  ```
  Por eso las migraciones nuevas deben ser compatibles con la versión anterior (ver 5.1).

### 5.1 Migraciones compatibles hacia atrás (expandir / contraer)
`update.sh` vuelve atrás la **imagen**, nunca el **esquema**: después de un rollback la versión anterior corre contra
la base ya migrada por la nueva. Toda migración tiene que dejar la base usable por la versión anterior. Regla para
quien escribe `V<n>__*.sql` (y se revisa en cada PR que toque `db/migration/`):

| Paso | Versión N (expandir) | Versión N+1 o posterior (contraer) |
| --- | --- | --- |
| Columna nueva | `ADD COLUMN … NULL` o `NOT NULL DEFAULT …` (la versión anterior no la conoce y sus `INSERT` siguen valiendo) | si hace falta, quitar el `DEFAULT` |
| Tabla nueva | `CREATE TABLE` (la versión anterior la ignora) | — |
| Renombrar columna/tabla | agregar la nueva, el código escribe en las dos y lee de la nueva; copiar los datos (`UPDATE … SET nueva = vieja`) | dejar de escribir la vieja; borrarla en una versión todavía posterior |
| Borrar columna/tabla | el código deja de usarla (sin migración) | `DROP` recién cuando ninguna versión a la que se pueda volver la use |
| Cambiar tipo o hacer `NOT NULL` | columna nueva con el tipo nuevo, como un renombre | — |
| Índice único / restricción nueva | primero limpiar los datos que la violarían, en la misma migración | — |

- Nada de `DROP`, `RENAME` ni cambios de tipo en la misma versión que deja de usar la columna: si esa versión falla
  y `update.sh` vuelve atrás, la anterior se rompe.
- Una migración publicada no se edita nunca (Flyway compara sumas); los arreglos van en una `V<n+1>`.
- Las migraciones corren dentro del arranque del backend: que sean rápidas (tablas chicas en fase 1); un `UPDATE`
  masivo o un índice sobre una tabla grande se hace en lotes o en una ventana de mantenimiento.
- Ejemplo real: `V5__admin_token_version.sql` sólo agrega `admin_user.token_version INT NOT NULL DEFAULT 0`; la
  versión anterior sigue funcionando con la columna puesta.
- Si igual hace falta un cambio incompatible: anunciar la ventana, `backup.sh --label pre-<cambio>` y, si la versión
  nueva falla, restaurar ese backup además de volver atrás la imagen (comandos de arriba).

---

## 6. Backups y restauración

### 6.1 Qué se guarda
| Capa | Qué | Cuándo / cuánto |
| --- | --- | --- |
| Backup diario (`backup.sh`) | `db.sql.gz` (mysqldump `--single-transaction`: copia consistente sin cortar el servicio), `files.tar.gz` (volumen de archivos subidos), `meta.txt`, `SHA256SUMS` | 03:30 hora de Hong Kong (timer `qiao-backup.timer`); 14 copias en `/var/backups/qiao/daily` |
| Backups manuales | igual, antes de cada actualización y restauración | 10 copias en `/var/backups/qiao/manual` |
| Copia externa en OSS | cada backup diario, subido a un bucket de Hong Kong | lo que diga la regla de ciclo de vida del bucket (recomendado 90 días) |
| Snapshot del disco del ECS | el disco entero | política automática diaria, 7 copias |
| `.env` | secretos y configuración | **no** va en los backups: copia en el gestor de contraseñas |

Si `STORAGE=oss` (archivos subidos en OSS en lugar del volumen), los archivos no están en `files.tar.gz`:
activar el **versionado** de ese bucket en la consola de OSS.

Ver los backups: `sudo /opt/qiao/deploy/restore.sh --list`. Hacer uno a mano: `sudo /opt/qiao/deploy/backup.sh --label antes-de-algo`.

### 6.2 Copia externa en Alibaba OSS (configurar una vez)
Se usa la **API compatible con S3 de OSS mediante `rclone`** del repositorio de Ubuntu (lo instala
`install.sh`). Por qué: se instala y se parchea con `apt` como el resto del sistema (sin descargar binarios a
mano), se configura sólo con variables del `.env`, funciona con una clave que **no puede borrar** (no necesita
listar ni crear buckets) y, si algún día se cambia de proveedor, sirve igual con cualquier almacenamiento S3.
La herramienta oficial `ossutil` es una alternativa válida para mirar o descargar backups a mano desde una
computadora; los scripts no la necesitan.

1. **Bucket**: consola → *Object Storage Service* → *Create Bucket*: región *China (Hong Kong)*, nombre p. ej.
   `qiao-backups-<algo>`, almacenamiento *Standard*, ACL **privado**, cifrado del lado del servidor *OSS-managed*.
   Opcional y recomendado: *Versioning* activado.
2. **Ciclo de vida**: *Data Management → Lifecycle* → regla para el prefijo `qiao/`: borrar objetos de más de
   90 días (y versiones anteriores de más de 30 si se activó el versionado).
3. **Usuario RAM sólo para backups**: *RAM → Users → Create User* `qiao-backup`, acceso *OpenAPI* (crea un
   AccessKey: guardarlo en el gestor). *Permissions → Grant*, política personalizada (cambiar el nombre del bucket):
   ```json
   {
     "Version": "1",
     "Statement": [
       { "Effect": "Allow", "Action": ["oss:PutObject", "oss:GetObject"],
         "Resource": ["acs:oss:*:*:qiao-backups-xxx/qiao/*"] },
       { "Effect": "Allow", "Action": ["oss:ListObjects"],
         "Resource": ["acs:oss:*:*:qiao-backups-xxx"] }
     ]
   }
   ```
   Sin `DeleteObject`: si alguien entra al servidor, no puede borrar las copias externas.
4. En `/opt/qiao/deploy/.env`:
   ```
   BACKUP_OSS_BUCKET='qiao-backups-xxx'
   BACKUP_OSS_ENDPOINT='oss-cn-hongkong-internal.aliyuncs.com'
   BACKUP_OSS_PREFIX='qiao'
   BACKUP_OSS_ACCESS_KEY_ID='LTAI...'
   BACKUP_OSS_ACCESS_KEY_SECRET='...'
   ```
   El endpoint `-internal` sólo funciona desde un ECS de Hong Kong y no cobra tráfico.
5. Probar: `sudo /opt/qiao/deploy/backup.sh` (debe terminar con «Subido a oss://…») y
   `sudo /opt/qiao/deploy/restore.sh --list` (debe listar también los backups en OSS).

### 6.3 Restaurar en producción
Casos típicos: alguien borró o corrompió datos; una migración falló; se perdieron archivos.
```bash
sudo /opt/qiao/deploy/restore.sh --list
sudo /opt/qiao/deploy/restore.sh --dry-run <nombre>     # primero, siempre: verifica y prueba la carga
sudo /opt/qiao/deploy/restore.sh <nombre>               # pide escribir RESTAURAR
#   --db-only     sólo la base           --files-only   sólo los archivos subidos
```
El script hace un backup de seguridad del estado actual (`manual/<fecha>-pre-restore`), detiene el backend,
reemplaza la base `qiao` y/o el volumen de archivos, vuelve a arrancar y espera a que todo esté sano. Todo lo
escrito después del backup elegido se pierde (queda en el backup de seguridad). Si la base está tan rota que
el backup de seguridad falla: agregar `--skip-safety-backup`.

Un backup que sólo está en OSS (el disco se perdió o ya no está en local): `restore.sh --fetch <nombre>` y
luego `restore.sh <nombre>`.

### 6.4 Prueba mensual
Ver la rutina mensual (sección 3). Una prueba que no se anota no cuenta.

### 6.5 Servidor perdido: recuperar en un ECS nuevo
1. Crear el ECS, grupo de seguridad y apuntar el DNS a la IP nueva (2.2–2.4).
2. Copiar al servidor nuevo el `.env` guardado en el gestor como `/root/qiao.env` (`nano /root/qiao.env`, pegar).
3. Instalar reutilizando esos secretos:
   ```bash
   QIAO_ENV_SOURCE=/root/qiao.env bash install.sh git@github.com:<org>/qiao.git main
   rm /root/qiao.env
   ```
4. Traer el último backup de OSS y restaurarlo:
   ```bash
   sudo /opt/qiao/deploy/restore.sh --list
   sudo /opt/qiao/deploy/restore.sh --fetch <nombre>
   sudo /opt/qiao/deploy/restore.sh <nombre>
   ```
5. Si se usaban imágenes de CI: `docker login` (4.3) y `update.sh <sha>` con la versión que figura en
   `meta.txt` del backup (`version_desplegada`).
6. Alternativa más rápida si el disco viejo existe: crear el ECS desde el último **snapshot** del disco.

---

## 7. Rotar secretos
Después de cualquier rotación: actualizar la copia del `.env` en el gestor de contraseñas.

| Secreto | Cómo | Efecto |
| --- | --- | --- |
| `JWT_SECRET` | `sudo /opt/qiao/deploy/rotate-secret.sh JWT_SECRET` | todas las sesiones se cierran; los códigos de acceso pendientes dejan de valer |
| `PARTNER_API_KEY` | en dos pasos, sin corte (ver abajo): `rotate-secret.sh PARTNER_API_KEY`, entregar la clave nueva al socio y, cuando ya la use, `rotate-secret.sh --finish-partner-rotation` | entre los dos pasos valen la nueva y la anterior; después, sólo la nueva |
| `DB_PASSWORD` | `rotate-secret.sh DB_PASSWORD` (cambia la contraseña dentro de MySQL y en `.env`) | MySQL y el backend se reinician (~1 minuto de API caída) |
| `MYSQL_ROOT_PASSWORD` | `rotate-secret.sh MYSQL_ROOT_PASSWORD` | MySQL se reinicia; el backend se reconecta solo |
| Contraseña SMTP | consola de DirectMail → la dirección remitente → *Set SMTP password*; copiar en `SMTP_PASSWORD`; `docker compose up -d` | — |
| Claves de Stripe | panel de Stripe → *Developers → API keys → Roll key* (y en el webhook *Roll secret*); copiar en `STRIPE_SECRET_KEY` / `STRIPE_WEBHOOK_SECRET`; `docker compose up -d` | al rotar, Stripe deja elegir cuánto tiempo sigue valiendo la clave vieja: elegir unas horas y cambiar `.env` en ese margen |
| AccessKey de OSS (archivos de la app o backups) | RAM → el usuario → crear un AccessKey nuevo; copiar en `.env`; `docker compose up -d` (los backups lo leen solos); probar; **desactivar y borrar** el viejo | — |
| Contraseña de ACR | consola de ACR → *Access Credentials*; en el servidor `docker login` de nuevo; en GitHub actualizar el secret `ACR_PASSWORD` | — |
| Deploy key de GitHub | borrar la vieja en GitHub; `sudo -u qiao ssh-keygen -t ed25519 -f /home/qiao/.ssh/id_ed25519` (reemplazar) y agregar la pública nueva | — |

Importante: cambiar `DB_PASSWORD` o `MYSQL_ROOT_PASSWORD` **a mano en `.env` no alcanza** (MySQL guarda la
contraseña en su propia base y sólo lee esas variables la primera vez): usar siempre `rotate-secret.sh`.

**Clave del socio, paso a paso** (sin que el socio quede sin servicio):
1. `sudo /opt/qiao/deploy/rotate-secret.sh PARTNER_API_KEY`: genera la clave nueva, la clave actual pasa a
   `PARTNER_API_KEY_PREVIOUS` y el backend se reinicia (~1 minuto). Desde ahí **valen las dos**.
2. Entregar la clave nueva al socio por un canal seguro: `sudo grep ^PARTNER_API_KEY= /opt/qiao/deploy/.env`.
3. Mientras el socio siga usando la anterior, cada llamada suya queda en back-office → *Cumplimiento → Auditoría*
   (actor `PARTNER`) con `partnerKey: PREVIOUS` y un aviso, y en los logs del backend como `WARN`. `status.sh`
   recuerda que la rotación está en curso.
4. Cuando el socio confirme el cambio: `sudo /opt/qiao/deploy/rotate-secret.sh --finish-partner-rotation`. Antes de
   cortar mira la auditoría: si el socio usó la clave anterior después de la nueva (o todavía no usó la nueva), se
   niega; `--force` corta igual. Al terminar, la clave anterior responde 401.
- No se puede empezar otra rotación sin terminar la que está en curso.
- **Clave filtrada**: no hace falta esperar al socio: paso 1 y enseguida paso 4 (si se niega, con `--force`).
  La clave filtrada deja de valer en ~2 minutos; el socio queda sin acceso hasta recibir la nueva.
- A mano en `.env`: `PARTNER_API_KEY_PREVIOUS` vacía, o de 32+ caracteres y distinta de `PARTNER_API_KEY`; si no, el
  backend no arranca.

### 7.1 Administradores: contraseñas y bajas
- **Cambiar la propia contraseña** (cualquier admin): back-office → *Mi perfil* → *Contraseña*: la actual y la nueva
  (12+ caracteres, distinta de la actual). Al cambiarla se cierran **todas las demás sesiones** de ese admin (otros
  navegadores, otro dispositivo); la sesión desde la que se cambió sigue abierta. Queda en la auditoría
  (`ADMIN_PASSWORD_CHANGED`; los intentos con la contraseña actual equivocada, `ADMIN_PASSWORD_CHANGE_FAILED`).
  Hay como máximo 5 intentos cada 15 minutos.
- **Desactivar a un admin** (SUPERADMIN; p. ej. alguien deja el proyecto o se sospecha de su cuenta): back-office →
  *Administradores* → *Desactivar*. No puede volver a entrar y sus sesiones abiertas dejan de funcionar en su próxima
  acción. Nadie puede desactivarse a sí mismo ni desactivar al último SUPERADMIN activo. *Reactivar* le devuelve el
  acceso (con su contraseña de siempre; las sesiones viejas no reviven). Auditoría: `ADMIN_DISABLED`, `ADMIN_ENABLED`.
- `ADMIN_BOOTSTRAP_PASSWORD` sólo se usa cuando no existe ningún admin; después del primer arranque se puede
  vaciar en `.env` (y conviene: la contraseña real ya se cambió desde *Mi perfil*).
- **Contraseña olvidada**: no hay recuperación por correo. Si hay otro SUPERADMIN, puede crear una cuenta nueva
  para esa persona (con otro email) y desactivar la vieja. Sólo si nadie puede entrar (el único SUPERADMIN olvidó la
  suya), poner una contraseña nueva directamente en la base; esto también cierra todas sus sesiones:
  ```bash
  sudo apt-get install -y apache2-utils
  read -rs -p 'Contraseña nueva (12+ caracteres): ' P; echo      # no se muestra ni queda en el historial
  HASH="$(htpasswd -nbBC 12 '' "$P" | tr -d ':\n' | sed 's/^\$2y/\$2a/')"; unset P
  cd /opt/qiao/deploy && printf "UPDATE admin_user SET password_hash='%s', failed_attempts=0, locked_until=NULL, status='ACTIVE', token_version=token_version+1 WHERE email='%s';\n" "$HASH" 'admin@tu-dominio.com' \
    | sudo docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot qiao'
  unset HASH
  ```
  Después, entrar y cambiarla otra vez desde *Mi perfil* para que quede en la auditoría.

---

## 8. Incidentes: listas de verificación
Siempre primero: `sudo /opt/qiao/deploy/status.sh` y anotar la hora. `cd /opt/qiao/deploy` para los comandos
de `docker compose`.

### 8.1 El sitio no carga desde China continental (pero sí desde Hong Kong o fuera de China)
1. `status.sh` en verde → el servidor está bien; el problema es la red hacia China.
2. 拨测 (sección 9.3) o una prueba puntual en **17CE** / **ITDOG** de `https://tu-dominio.com/m/`: ¿falla en
   todos los operadores o sólo en uno (China Telecom, China Unicom, China Mobile)? ¿Falla el ping o sólo HTTPS?
3. Sólo un operador o sólo lento → problema de rutas: esperar unas horas; si se repite, considerar la IP
   **BGP (Multi-ISP) Pro** de Alibaba en Hong Kong (optimizada para China continental, más cara) o
   Global Accelerator.
4. Prueba de DNS de 17CE/ITDOG: si desde China el dominio resuelve a IPs que no son la tuya → contaminación de
   DNS; probar con otro proveedor de DNS autoritativo.
5. El ping funciona pero TCP 443 se corta en todas partes de China → posible bloqueo del dominio o de la IP:
   probar `https://<IP>/` (fallará el certificado, pero indica si la IP responde). Si la IP responde y el
   dominio no, el bloqueo es del nombre (SNI): hace falta un dominio nuevo. Si no responde la IP: pedir una
   EIP nueva.
6. Revisar que nada en `/m/` pida recursos externos (lo garantizan `npm run check:external` y la CSP; un
   recurso bloqueado en China deja la página en blanco).

### 8.2 El sitio no carga desde ningún lado
1. `docker compose ps`: ¿algún servicio no está `running (healthy)`? → `docker compose logs --tail 200 <servicio>`.
2. `docker compose up -d` (levanta lo que esté caído).
3. ¿Fue justo después de una actualización? → `update.sh --rollback`.
4. Consola de Alibaba: ¿la instancia está *Running*? ¿hay una alerta de *overdue payment*? ¿el grupo de
   seguridad sigue con 80/443 abiertos?
5. Disco lleno → 8.4. Memoria: `free -m`, `dmesg -T | grep -i -E 'killed process|out of memory'`.

### 8.3 Base de datos caída
1. `docker compose ps mysql` y `docker compose logs --tail 200 mysql`.
2. Si se cayó por memoria (mensaje *Killed* en `dmesg`) o por disco lleno: resolver eso y `docker compose up -d mysql`.
3. Arranca y se vuelve a caer con errores de InnoDB (*corruption*, *crash recovery failed*):
   **no borrar el volumen**. Restaurar el último backup: `restore.sh --dry-run <último>` y
   `restore.sh --skip-safety-backup <último>` (se pierde lo escrito desde las 03:30). Si el volumen está
   dañado del todo: `docker compose stop mysql backend`, `docker volume rm qiao_mysql-data`,
   `docker compose up -d mysql` (crea una base vacía con las contraseñas del `.env`) y restaurar.
4. El backend se reconecta solo cuando MySQL vuelve; si no, `docker compose up -d backend`.

### 8.4 Disco lleno
```bash
df -h /                            # cuánto queda
sudo docker system df              # imágenes, contenedores, volúmenes, caché de compilación
sudo du -sh /var/backups/qiao/* /var/lib/docker/volumes/* 2>/dev/null | sort -h | tail
```
1. Imágenes viejas y caché: `sudo docker image prune -af --filter until=168h` y `sudo docker builder prune -af`
   (no toca datos).
2. Diario del sistema: `sudo journalctl --vacuum-size=200M`.
3. Backups manuales viejos: borrar los más antiguos de `/var/backups/qiao/manual/` (si OSS está configurado,
   también están ahí).
4. Si los datos crecieron de verdad: ampliar el disco en la consola (*Disks → Resize*, en caliente para ESSD) y
   en el servidor `sudo growpart /dev/vda 3 && sudo resize2fs /dev/vda3` (comprobar el nombre con `lsblk`).
5. Los logs de Docker ya están limitados (5 × 10 MB por servicio).

### 8.5 Falló la renovación del certificado
Caddy renueva solo unos 30 días antes del vencimiento; `status.sh` avisa con menos de 20 días y falla con menos de 7.
1. `docker compose logs --tail 300 caddy | grep -i -E 'error|challenge|acme'`.
2. Causas típicas: el puerto **80** cerrado en el grupo de seguridad (Let's Encrypt lo necesita), el registro A
   apunta a otra IP (`nslookup tu-dominio.com`), un registro CAA que no permite `letsencrypt.org`, o límite de
   intentos de Let's Encrypt (esperar 1 hora; Caddy reintenta solo).
3. Corregido eso: `docker compose restart caddy` y mirar los logs. Caddy también puede usar ZeroSSL como
   alternativa automática.
4. Si el certificado ya venció y no hay forma rápida: pasar temporalmente a la alternativa SLB con un
   certificado del *Certificate Management Service* de Alibaba (sección 10).

### 8.6 La versión nueva no arranca
`update.sh` ya volvió a la anterior. Ver el motivo: `docker compose logs --tail 300 backend`. Errores típicos:
una variable obligatoria nueva que falta en `.env`, o una migración que falló (ver sección 5). El control de
arranque de producción se niega, con un mensaje que nombra la variable, si:
- `APP_CONTRACTS_HK_ENTITY_NAME` o `APP_CONTRACTS_PARTNER_ENTITY_NAME` están vacías o tienen un marcador `[…]` y
  `PAYMENT_PROVIDER=stripe` (servidores instalados antes de esta comprobación: completarlas antes de actualizar);
- `JWT_SECRET`, `PARTNER_API_KEY`, `PARTNER_API_KEY_PREVIOUS` o `ADMIN_BOOTSTRAP_PASSWORD` tienen un valor de ejemplo
  del repositorio, o una clave del socio tiene menos de 32 caracteres, o la anterior es igual a la actual;
- `STRIPE_API_BASE` apunta a otro lugar que `https://api.stripe.com`.

### 8.7 Los backups fallan
`sudo journalctl -u qiao-backup -n 100`. Causas: MySQL caído, menos de 1 GB libre, credenciales de OSS
(el backup local igual queda hecho). Probar a mano: `sudo /opt/qiao/deploy/backup.sh`.

### 8.8 Sospecha de acceso indebido o secreto filtrado
1. Rotar el secreto afectado (sección 7); ante la duda, todos los de `rotate-secret.sh` y las claves de Stripe, SMTP y OSS.
   La clave del socio, terminando la rotación enseguida (sección 7, «Clave filtrada»). Una cuenta del back-office:
   desactivarla desde *Administradores* (sus sesiones se cortan) o, si es la propia, cambiar la contraseña desde
   *Mi perfil* (cierra las demás sesiones).
2. Revisar en el back-office → Auditoría y en `docker compose logs nginx` los accesos.
3. Hacer y conservar un backup (`backup.sh --label incidente`) y un snapshot del disco antes de cambiar más cosas.
4. Evaluar la obligación de notificar según la política de privacidad (datos de ingenieros y clientes).

---

## 9. Monitoreo y 拨测 desde China
La regla de la app de ingenieros es que `/m/` **no hace ninguna solicitud a servidores externos**: nada de
scripts de analítica, monitoreo de navegador (RUM) ni similares en `/m/`. Todo el monitoreo de esta sección se
hace desde fuera hacia el servidor, sin tocar la página.

### 9.1 `status.sh`
Sólo hace pedidos al propio servidor. Código de salida 0 si todo está bien, 1 si hay alguna falla (sirve para
automatizar más adelante). Muestra la versión y el commit que informa el backend (`/api/health`) y avisa si ese commit
no coincide con `QIAO_IMAGE_TAG`.

### 9.2 Uptime Kuma (opcional, en el propio servidor)
Detecta caídas del servicio y vencimientos de certificado; **no** dice nada de la conectividad desde China
(para eso, 9.3).
1. Activar: en `.env` `COMPOSE_PROFILES=monitoring`, luego `cd /opt/qiao/deploy && sudo docker compose up -d`.
2. Abrir desde tu computadora con un túnel SSH (sólo escucha en 127.0.0.1 del servidor):
   `ssh -i clave.pem -L 3001:127.0.0.1:3001 root@IP` y en el navegador `http://localhost:3001`. Crear el usuario.
3. Monitores sugeridos (cada 60 s):
   - *HTTP(s) – Keyword* `https://tu-dominio.com/api/health`, palabra `UP`.
   - *HTTP(s)* `https://tu-dominio.com/m/`, con *Certificate Expiry Notification* activado.
   - *HTTP(s)* `http://nginx/` (red interna de Docker: separa fallos de nginx de los de Caddy/DNS).
4. Notificaciones: *SMTP* con DirectMail, o *DingTalk* / *WeCom* / *Feishu* (webhooks de un grupo).

### 9.3 拨测 desde China Telecom, Unicom y Mobile
**Alibaba Cloud CloudMonitor (云监控) → Site Monitoring / 云拨测 (Cloud Dial Test)** — los nombres del menú
cambian según la versión de la consola; buscar «Site Monitoring» o «拨测».
1. Crear una tarea **HTTP(S)**: URL `https://tu-dominio.com/m/`, método GET, frecuencia **5 minutos**
   (1 minuto si el plan lo permite).
2. Puntos de prueba: **China continental**, al menos dos provincias por operador — China Telecom (电信),
   China Unicom (联通) y China Mobile (移动), p. ej. Beijing, Shanghai, Guangdong, Sichuan. Agregar uno de Hong Kong
   como referencia.
3. Condición de éxito: código 200 y, si la consola lo permite, que la respuesta contenga `id="app"`; tiempo de
   respuesta máximo **2 s**.
4. Alarma: **2 fallas seguidas** en el mismo operador → SMS/email/DingTalk al contacto de guardia.
5. Una segunda tarea igual contra `https://tu-dominio.com/api/health` con palabra clave `UP` (separa «la red
   falla» de «la API falla»).
6. Antes de cada lanzamiento o campaña, mirar el mapa de tiempos por provincia: la página debe cargar en < 2 s.

**17CE (17ce.com)** e **ITDOG (itdog.cn)**: pruebas puntuales gratuitas (HTTP, ping, DNS, traceroute) desde
cientos de nodos de los tres operadores; útiles para diagnosticar un incidente (8.1). 17CE también ofrece
monitoreo continuo de pago. Ninguna de estas herramientas agrega nada a la página.

### 9.4 Logs
`docker compose logs [--since 24h] [-f] <servicio>`. Se conservan hasta 50 MB por servicio (rota sola). Si más
adelante hace falta retención larga (p. ej. 180 días), enviar los logs a **Log Service (SLS)** de Alibaba con
Logtail leyendo los logs de Docker (servicio de pago; ver costos).

---

## 10. Alternativa: HTTPS en un SLB de Alibaba
Para usar un certificado administrado por Alibaba en lugar de Let's Encrypt (o más adelante varios servidores
o WAF), el TLS lo termina un balanceador y Caddy se apaga. Las cabeceras de seguridad (CSP, HSTS) las sigue
poniendo nginx, una sola vez.
1. **Certificado**: *Certificate Management Service* → comprar o pedir uno DV (o subir uno propio) para el dominio.
2. **Balanceador**: *Server Load Balancer* → CLB (o ALB) en *China (Hong Kong)*, de acceso público.
   - Escucha **HTTPS:443** con el certificado → grupo de servidores: el ECS, puerto **80**. Activar las
     cabeceras **X-Forwarded-For** y **X-Forwarded-Proto**.
   - Escucha **HTTP:80** → redirigir a HTTPS:443.
   - Health check HTTP, ruta `/`.
3. **DNS**: el registro A pasa a la IP del balanceador.
4. **Servidor**: en `.env`
   `COMPOSE_FILE=docker-compose.yml:docker-compose.slb.yml` (con imágenes de CI:
   `docker-compose.yml:docker-compose.images.yml:docker-compose.slb.yml`), y:
   ```bash
   cd /opt/qiao/deploy && sudo docker compose rm -sf caddy && sudo docker compose up -d
   ```
5. **Grupo de seguridad**: el puerto 80 del ECS sólo desde el balanceador (CLB: `100.64.0.0/10`; ALB: el
   rango de sus vSwitches); quitar la regla de 443.
6. `status.sh` comprueba el certificado contra el dominio público.

Volver a Caddy: quitar `docker-compose.slb.yml` de `COMPOSE_FILE`, reabrir 80/443 en el grupo de seguridad,
apuntar el DNS al ECS y `docker compose up -d`.

---

## 11. CI y GitHub
`.github/workflows/ci.yml`, en cada push y pull request:

| Job | Qué hace |
| --- | --- |
| `backend` | `mvn verify` (tests de integración con MySQL real vía Testcontainers) |
| `web` | build (incluye type-check) |
| `h5` | build + `check:external` (ninguna URL externa en `/m/`) |
| `deploy` | ShellCheck de los scripts, `docker compose config` de las variantes, `nginx -t`, `caddy validate` |
| `e2e` | `./scripts/e2e.sh`: flujo completo con Playwright. En el runner de GitHub hay Docker; como no existe `/opt/pw-browsers`, el script instala Chromium con `npx playwright install --with-deps chromium` |
| `images` | sólo en `main` y si están configuradas las variables: construye y publica `qiao-backend:<sha>` (con `APP_COMMIT=<sha>`, que muestra `/api/health`) y `qiao-nginx:<sha>` en ACR, y muestra el comando de despliegue en el resumen |

Configuración en GitHub → repo → *Settings → Secrets and variables → Actions*:

| Tipo | Nombre | Valor |
| --- | --- | --- |
| Variable | `ACR_REGISTRY` | `registry.cn-hongkong.aliyuncs.com` (dirección **pública**: los runners de GitHub están fuera de Alibaba) |
| Variable | `ACR_NAMESPACE` | el namespace de ACR, p. ej. `qiao` |
| Secret | `ACR_USERNAME` | el usuario que muestra ACR en *Access Credentials* (el nombre de la cuenta o del usuario RAM) |
| Secret | `ACR_PASSWORD` | la contraseña de registro fijada en *Access Credentials* (no la de la consola) |

- Sin las variables, `images` se salta y el resto del CI funciona igual.
- Con las variables pero sin los secrets, `images` falla con un mensaje claro.
- El servidor descarga las mismas imágenes por la dirección VPC (`registry-vpc.cn-hongkong.aliyuncs.com`).
- Recomendado: *Settings → Branches → Branch protection* en `main` exigiendo `backend`, `web`, `h5`, `deploy`
  y `e2e` (la regla del proyecto: e2e en verde antes de cada commit a `main`).
- Versiones de las acciones (noviembre de 2026): `actions/checkout@v7`, `actions/setup-java@v6`,
  `actions/setup-node@v7`, `actions/upload-artifact@v7`, `docker/login-action@v4` (corren en Node 24: los runners
  de GitHub ya lo tienen; un runner propio necesita la versión 2.327.1 o posterior).

### Prueba local del despliegue (para desarrolladores)
`deploy/test/run.sh` levanta todo el stack con artefactos ya compilados, como proyecto `qiao-deploytest` en
`127.0.0.1:18080/18443` (no choca con `scripts/e2e.sh`), y prueba HTTPS con la CA interna de Caddy, cabeceras,
backups, OSS (con un S3 local), restore, `status.sh`, `update.sh` con rollback, `rotate-secret.sh`, la variante
SLB y Uptime Kuma, la rotación sin corte de la clave del socio, la versión y el commit en `/api/health` y que el
backend no arranque sin razones sociales con pagos reales. Ver la cabecera del script.

---

## 12. Costos (estimaciones)
**Todos los montos son estimaciones** (noviembre de 2026, precios de lista de Alibaba Cloud internacional en
Hong Kong, en US$, sin impuestos). Cambian seguido y dependen de descuentos y del plan: verificar en la
calculadora de precios de Alibaba Cloud antes de decidir.

| Recurso | Configuración | Estimación mensual |
| --- | --- | --- |
| ECS | 2 vCPU / 4 GiB, suscripción mensual | US$ 25–60 (familias compartidas/económicas abajo, `c7`/`g7` arriba) |
| Disco del sistema | ESSD 40 GB | US$ 3–5 |
| Tráfico de salida | por tráfico, ~50 GB/mes en la fase 1 | US$ 5–10 (≈ US$ 0,10–0,20 por GB) |
| IP «BGP (Multi-ISP) Pro» (sólo si hace falta, 8.1) | mejor ruta a China continental | varias veces el precio por GB del tráfico normal |
| Snapshots | diarios, 7 copias (incrementales) | US$ 1–4 |
| OSS para backups | Standard, < 10 GB con ciclo de vida de 90 días | < US$ 1 (≈ US$ 0,02–0,03 por GB/mes; la subida por la red interna no se cobra) |
| Container Registry | Personal Edition | sin costo (la Enterprise Edition es de pago y no hace falta en fase 1) |
| DirectMail | miles de correos al mes | pocos dólares |
| CloudMonitor / 云拨测 | 2 tareas, ~8 puntos de prueba, cada 5 min | desde la cuota gratuita hasta ~US$ 10–40 según puntos y frecuencia |
| 17CE / ITDOG | pruebas puntuales | gratis |
| Certificado | Let's Encrypt vía Caddy | gratis |
| Alternativa SLB (sección 10) | CLB/ALB de acceso público | US$ 15–30 más tráfico |
| Log Service (SLS), opcional | retención larga de logs | según volumen; pocos dólares con poco tráfico |
| Dominio (no .cn) | | US$ 10–20 por **año** |
| GitHub Actions | repo privado: 2.000 min/mes gratis en el plan Free; cada corrida completa ≈ 20–30 min | gratis hasta ~70 corridas/mes; luego ≈ US$ 0,008 por minuto |

Orden de magnitud total de la fase 1: **US$ 40–90 al mes** de infraestructura (estimación), sin contar las
comisiones de Stripe ni del socio en China.

---

## 13. Referencia rápida
```bash
sudo /opt/qiao/deploy/status.sh                         # estado de todo
cd /opt/qiao/deploy && sudo docker compose ps           # contenedores
sudo docker compose logs --tail 200 -f backend          # logs (Ctrl-C para salir)
sudo /opt/qiao/deploy/update.sh <sha>                   # actualizar (imágenes de CI)
sudo /opt/qiao/deploy/update.sh                         # actualizar (compilando en el servidor)
sudo /opt/qiao/deploy/update.sh --rollback              # volver a la versión anterior
sudo /opt/qiao/deploy/update.sh --history               # historial
sudo /opt/qiao/deploy/backup.sh --label motivo          # backup a mano
sudo /opt/qiao/deploy/restore.sh --list                 # backups locales y en OSS
sudo /opt/qiao/deploy/restore.sh --dry-run              # prueba de restauración (no toca producción)
sudo /opt/qiao/deploy/restore.sh <nombre>               # restaurar (pide confirmación)
sudo /opt/qiao/deploy/rotate-secret.sh JWT_SECRET       # rotar un secreto
sudo /opt/qiao/deploy/rotate-secret.sh PARTNER_API_KEY  # clave del socio, paso 1 (las dos valen)
sudo /opt/qiao/deploy/rotate-secret.sh --finish-partner-rotation   # paso 2: sólo la nueva
curl -s https://tu-dominio.com/api/health               # {"status":"UP","version":…,"commit":…}
sudo docker compose up -d                               # aplicar cambios del .env / levantar lo caído
sudo systemctl list-timers qiao-backup.timer            # próximo backup automático
```
