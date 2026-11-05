# Despliegue en Alibaba Cloud Hong Kong (fase 1)

Un solo servidor (ECS, Ubuntu 24.04, 2 vCPU / 4 GB) en la región **cn-hongkong** corre todo con Docker Compose:
Caddy (HTTPS automático), Nginx (web de clientes y back-office en `/`, H5 de ingenieros en `/m/`, API en `/api/`),
backend y MySQL. No hace falta ICP porque el servidor no está en China continental.

**Guía de operación completa (primer despliegue, rutinas, actualizar, volver atrás, backups, secretos,
incidentes, 拨测, costos): [`docs/OPERACIONES.md`](../docs/OPERACIONES.md).**

## Instalación (resumen)
En un ECS nuevo, como root, con el registro A del dominio ya apuntando a su IP:
```bash
bash install.sh git@github.com:<org>/qiao.git main
```
Instala Docker (repositorio oficial), crea el usuario `qiao`, clona el repo en `/opt/qiao`, genera `deploy/.env`
con secretos aleatorios (pregunta sólo dominio, email del admin, SMTP, Stripe y las razones sociales de los contratos),
configura ufw (SSH, 80, 443), actualizaciones automáticas de seguridad, swap y backups diarios, arranca todo y muestra
la URL del admin.

## Archivos
| Archivo | Qué es |
| --- | --- |
| `docker-compose.yml` | stack base: mysql, backend, nginx, caddy; perfil opcional `monitoring` (Uptime Kuma en 127.0.0.1:3001) |
| `docker-compose.images.yml` | usa imágenes de CI desde Alibaba ACR (`QIAO_REGISTRY/QIAO_NAMESPACE/qiao-*:QIAO_IMAGE_TAG`) en lugar de compilar |
| `docker-compose.slb.yml` | alternativa con TLS en un SLB de Alibaba: sin Caddy, nginx publica el puerto 80 |
| `.env.example` | todas las variables (la variante se elige con `COMPOSE_FILE` en `.env`) |
| `caddy/Caddyfile` | HTTPS con Let's Encrypt para `DOMAIN`, HTTP → HTTPS, sin HTTP/3, sin cabeceras propias |
| `nginx/` | imagen con web y H5; **única** fuente de las cabeceras de seguridad (CSP, HSTS, X-Frame-Options…) |
| `install.sh` | instalación idempotente en un ECS nuevo |
| `update.sh` | actualizar a un tag (o compilar), esperar salud y volver atrás solo si falla; `--rollback`, `--history` |
| `backup.sh` / `restore.sh` | backup diario (mysqldump consistente + volumen de archivos, copia en OSS) y restauración con `--dry-run` |
| `status.sh` | estado: contenedores, salud de punta a punta (versión y commit del backend), cabeceras, certificado, disco, memoria, backups, rotación de la clave del socio pendiente |
| `rotate-secret.sh` | rotar `JWT_SECRET`, `PARTNER_API_KEY` (sin corte: `PARTNER_API_KEY_PREVIOUS` y `--finish-partner-rotation`), `DB_PASSWORD`, `MYSQL_ROOT_PASSWORD` |
| `lib.sh` | funciones comunes de los scripts |
| `test/` | **sólo pruebas locales**: overrides y `run.sh` (ver abajo) |

## Prueba local del despliegue
Sin dominio público ni Internet dentro de los contenedores, con artefactos ya compilados:
```bash
(cd backend && mvn -q -DskipTests package) && (cd web && npm ci && npm run build) && (cd h5 && npm ci && npm run build)
sudo apt-get install -y rclone      # cliente S3 que usa backup.sh
deploy/test/run.sh                  # KEEP=1 para dejarlo levantado; deploy/test/run.sh --down para borrarlo
```
Levanta el proyecto `qiao-deploytest` en `127.0.0.1:18080/18443` (no choca con `scripts/e2e.sh`: 3316, 8080, 8088)
usando `test/docker-compose.test.yml` (`.env` propio, puertos, CA interna de Caddy con `local_certs`) y
`test/docker-compose.test-build.yml` (imágenes desde la etapa final de los Dockerfile reales, copiando el jar y los
`dist/`). Comprueba HTTPS y redirección, cabeceras de seguridad una sola vez, healthchecks, rotación de logs,
permisos del volumen, backup → S3 local → restore (dry-run, fetch y real), `status.sh`, `update.sh` con rollback
automático contra un registro local, `rotate-secret.sh` (incluida la rotación sin corte de la clave del socio), la
versión y el commit en `/api/health` (build arg `APP_COMMIT`), que sin razones sociales el backend arranque sólo con
pagos fake, la variante SLB y Uptime Kuma. Al terminar borra
contenedores, volúmenes e imágenes de prueba.
