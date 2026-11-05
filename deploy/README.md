# Despliegue en Alibaba Cloud Hong Kong (fase 1)

Un solo servidor (ECS) en la región **cn-hongkong** corre todo con Docker Compose: MySQL, backend y Nginx (web de clientes y back-office en `/`, H5 de ingenieros en `/m/`, API en `/api/`). No hace falta ICP porque el servidor no está en China continental.

## 1. Recursos
| Recurso | Configuración inicial |
| --- | --- |
| ECS | 2 vCPU, 4 GB, Ubuntu 24.04, región cn-hongkong, 40 GB de disco |
| IP pública | Ancho de banda por tráfico; abrir sólo los puertos 80 y 443 (SSH sólo desde tu IP) |
| Dominio | Uno que no sea .cn (un .cn exige registro en China) |
| Certificado | Servicio de certificados de Alibaba o Let's Encrypt |
| DirectMail | Región Singapur o Hong Kong; dominio verificado con SPF, DKIM y DMARC |
| Backups | Snapshot automático diario del disco; más adelante, pasar MySQL a RDS |

## 2. Instalación
```bash
curl -fsSL https://get.docker.com | sh
git clone <repo> qiao && cd qiao/deploy
cp .env.example .env   # completar todos los valores
docker compose up -d --build
docker compose logs -f backend
```
El backend aplica las migraciones de base de datos al arrancar y crea el primer administrador con `ADMIN_BOOTSTRAP_EMAIL` y `ADMIN_BOOTSTRAP_PASSWORD`.

Si las imágenes se construyen en CI (recomendado), reemplazar `build:` por `image: registry.cn-hongkong.aliyuncs.com/<namespace>/qiao-backend:<tag>` y lo mismo para `qiao-nginx`.

## 3. HTTPS
Poner el certificado delante del puerto 80: un balanceador SLB de Alibaba con el certificado, o un Nginx/Caddy adicional en el host. El Nginx del proyecto ya envía HSTS y una política de seguridad (CSP) que bloquea cualquier recurso externo.

## 4. Stripe
1. Cuenta de Stripe a nombre de la Ltd de Hong Kong.
2. Webhook a `https://tu-dominio.com/api/payments/stripe/webhook` con el evento `checkout.session.completed`.
3. Cargar `STRIPE_SECRET_KEY` y `STRIPE_WEBHOOK_SECRET` en `.env` y poner `PAYMENT_PROVIDER=stripe`.

## 5. Pruebas desde China (拨测)
Configurar sondas de Alibaba Cloud 云拨测 (o 17CE) cada 5 minutos contra `https://tu-dominio.com/m/` desde China Telecom, China Unicom y China Mobile, con alerta tras dos fallas seguidas. Antes de cada lanzamiento, revisar que la página cargue en menos de 2 segundos.

## 6. Operación
- Logs: `docker compose logs`; en producción, enviar a Log Service (SLS) con retención de 180 días.
- Backups: snapshot diario y una restauración de prueba por mes.
- Actualizar: `git pull && docker compose up -d --build` (o cambiar el tag de imagen).
