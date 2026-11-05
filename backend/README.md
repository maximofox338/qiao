# Qiao — backend

Monolito modular en Java 21 + Spring Boot 3.5 + MyBatis-Plus + MySQL 8.4. Implementa la API de `docs/SPEC.md`
(contrato con `web/` y `h5/`); las interpretaciones y desviaciones están en `docs/DECISIONS.md` (sección *backend*).

## Estructura

Paquete `com.qiao`, un subpaquete por módulo, cada uno con `domain` (entidades, enums, mappers), `service` y `web`
(controladores y DTOs):

| Módulo | Responsabilidad |
|---|---|
| `common` | sobre de respuesta, errores, seguridad JWT, rate limit, almacenamiento (local / OSS), dinero |
| `account` | usuarios, OTP, `/api/me`, exportación y borrado de cuenta |
| `profile` | perfil del ingeniero |
| `client` | empresa cliente y declaraciones |
| `project` | categorías, proyectos, hitos y postulaciones |
| `contract` | contratos, documentos (plantillas en `resources/contracts`) e hitos del contrato |
| `delivery` | mensajes, adjuntos y entrega de hitos |
| `billing` | pagos (fake / Stripe), liquidaciones, API del socio, ingresos |
| `compliance` | consentimientos, `transfer_event`, auditoría, textos legales (`resources/legal`), palabras clave |
| `moderation` | revisiones del back-office (empresas, ingenieros, proyectos, disputas) |
| `notify` | correo y `mail_outbox` |
| `admin` | administradores, login, TOTP (alta con QR, desactivación, reinicio por SUPERADMIN), panel, arranque del primer admin y datos demo |
| `team` | equipos de ingenieros, invitaciones por alias (las postulaciones y contratos de equipo viven en `project` y `contract`) |
| `review` | valoraciones tras un contrato completado (una por lado) y su listado en el back-office |

Reglas: cada módulo es dueño de sus tablas; nada de JOIN entre módulos (se llama al servicio del otro módulo).
Las comprobaciones de propiedad se hacen en los servicios.

## Arrancar en local

Requisitos: Java 21, Maven 3.9, Docker.

```bash
# 1. MySQL de desarrollo
docker run -d --name qiao-mysql-dev -e MYSQL_ROOT_PASSWORD=root -e MYSQL_DATABASE=qiao -p 3306:3306 mysql:8.4

# 2. Backend con datos demo (perfil dev: todos los valores por defecto ya apuntan a ese MySQL)
SPRING_PROFILES_ACTIVE=dev APP_DEMO_SEED=true mvn spring-boot:run
# o bien: mvn -DskipTests package && SPRING_PROFILES_ACTIVE=dev APP_DEMO_SEED=true java -jar target/qiao-backend.jar

curl localhost:8080/api/health
curl localhost:8080/api/public/categories
```

- Flyway crea el esquema (`db/migration/V1__init.sql`) y las categorías; `V2__teams.sql`, `V3__reviews.sql` y
  `V4__admin_totp.sql` añaden equipos, valoraciones y TOTP de administradores.
- Admin de desarrollo: `admin@qiao.local` / `QiaoAdmin-dev-2026` (SUPERADMIN).
- Datos demo (`APP_DEMO_SEED=true`, solo perfil `dev`, idempotente): cliente `client@demo.qiao` con empresa aprobada,
  ingeniero `engineer@demo.qiao` aprobado (`partnerRef` `PARTNER-DEMO-001`) y un proyecto OPEN con dos hitos.
- Login por OTP: `POST /api/auth/otp/request` y luego `GET /api/dev/last-otp?email=...` (solo `dev` y `test`)
  para leer el código; con `MAIL_MODE=log` los correos quedan en la tabla `mail_outbox`.
- Pagos con `PAYMENT_PROVIDER=fake`: el checkout devuelve `{APP_BASE_URL}/pay/fake/{id}` y se confirma con
  `POST /api/payments/fake/{id}/confirm`.
- Swagger UI (dev): <http://localhost:8080/api/docs> · OpenAPI: `/api/openapi`.

## Perfiles

| Perfil | Uso |
|---|---|
| `dev` | desarrollo local con valores por defecto seguros, `/api/dev/last-otp`, datos demo opcionales |
| `test` | tests de integración (Testcontainers), límites de rate relajados |
| `prod` | sin valores por defecto para secretos, Swagger desactivado, `PAYMENT_PROVIDER=stripe` por defecto; no se puede combinar con `dev` y nunca expone `/api/dev/**` |

Sin perfil se usa la configuración base (`application.yml`), que exige todas las variables obligatorias.

## Variables de entorno

| Variable | Obligatoria en prod | Descripción |
|---|---|---|
| `DB_URL` | sí | JDBC de MySQL. Incluir `connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true` (todo se guarda en UTC) |
| `DB_USER`, `DB_PASSWORD` | sí | credenciales de MySQL |
| `JWT_SECRET` | sí | secreto HS256, mínimo 32 caracteres (también firma los hashes de OTP); los valores de ejemplo del repo se rechazan al arrancar en prod |
| `APP_BASE_URL` | sí | URL pública del portal web (enlaces de pago y correos) |
| `MAIL_MODE` | no (`log`) | `log` = solo `mail_outbox`; `smtp` = envío real (Alibaba DirectMail) |
| `SMTP_HOST`, `SMTP_PORT`, `SMTP_USER`, `SMTP_PASSWORD`, `SMTP_FROM` | con `smtp` | servidor SMTP (SSL, puerto 465 por defecto) |
| `PAYMENT_PROVIDER` | no (`fake`; `stripe` en prod) | `fake` o `stripe` |
| `STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET` | con `stripe` | sin ellas el arranque falla si el proveedor es `stripe` |
| `STRIPE_API_BASE` | no (vacía) | solo para pruebas contra `stripe/stripe-mock`; en producción debe quedar vacía (o `https://api.stripe.com`) o el arranque falla |
| `PLATFORM_FEE_PCT` | no (`25`) | comisión de la plataforma en % |
| `STORAGE` | no (`local`) | `local` u `oss` |
| `STORAGE_LOCAL_DIR` | no (`/data/uploads`) | carpeta de adjuntos con `STORAGE=local` |
| `OSS_ENDPOINT`, `OSS_BUCKET`, `OSS_ACCESS_KEY_ID`, `OSS_ACCESS_KEY_SECRET` | con `oss` | Aliyun OSS |
| `PARTNER_API_KEY` | para la API del socio | cabecera `X-Partner-Key`; vacía = API del socio desactivada; en prod mínimo 32 caracteres |
| `ADMIN_BOOTSTRAP_EMAIL`, `ADMIN_BOOTSTRAP_PASSWORD` | primer arranque | crea el primer SUPERADMIN si no hay ninguno (contraseña ≥ 12; los valores de ejemplo se rechazan en prod) |
| `APP_DEMO_SEED` | no (`false`) | solo perfil `dev`: crea los datos demo |
| `APP_CONTRACTS_HK_ENTITY_NAME`, `APP_CONTRACTS_PARTNER_ENTITY_NAME` | sí | razones sociales que aparecen en los contratos |

## Tests

```bash
mvn verify        # unitarios (surefire, *Test) + integración (failsafe, *IT)
```

Los tests de integración usan MockMvc contra un MySQL 8.4 real levantado con Testcontainers, así que necesitan
Docker. Cubren registro/login OTP de ambos roles, bloqueo de OTP, declaraciones y aprobación de la empresa,
reglas de proyecto (categoría prohibida, suma de hitos, duración > 12 meses, palabras de empleo marcadas),
aprobación de proyecto e ingeniero, consentimientos, postulación, perfil seudonimizado y `transfer_event`, CV con
consentimiento, contratación, aceptación de documentos, pago fake, entrega y aceptación, liquidación + CSV + API del
socio, borrado de cuenta y bloqueo del admin tras 5 contraseñas erróneas. Además:

- `TeamFlowIT`: equipos de punta a punta (invitación, reparto, consentimiento por miembro, tarjeta de equipo con un
  `transfer_event` por miembro, CV por miembro, un acuerdo por miembro, entrega del líder, una línea de liquidación
  por miembro) y la confidencialidad entre miembros.
- `ReviewIT`: valoraciones de ambos lados, promedio en el perfil seudonimizado, textos solo para el back-office.
- `AdminTotpIT` / `TotpVerifierTest`: alta de TOTP, login sin reutilizar el mismo paso de 30 s, desactivación y
  reinicio por SUPERADMIN.
- `StripePaymentIT`: `PAYMENT_PROVIDER=stripe` contra la imagen oficial `stripe/stripe-mock:v0.197.0`
  (Testcontainers, se descarga de Docker Hub la primera vez): creación de la sesión de checkout, webhook
  `checkout.session.completed` firmado como documenta Stripe, firmas inválidas y reembolso. Arranca un segundo
  contexto de Spring (y un segundo MySQL).

## Docker

```bash
docker build -t qiao-backend .
# desde China continental: --build-arg MAVEN_MIRROR=https://maven.aliyun.com/repository/public
docker run -p 8080:8080 -e DB_URL=... -e DB_USER=... -e DB_PASSWORD=... -e JWT_SECRET=... \
  -e APP_BASE_URL=https://... -e PAYMENT_PROVIDER=fake -v qiao-uploads:/data/uploads qiao-backend
```

La imagen corre con el usuario no root `qiao`, perfil `prod` y healthcheck sobre `/api/health`.

## Convenciones de la API

- Todas las respuestas JSON: `{ "code": "OK" | "<ERROR>", "message": "...", "data": ... }`.
- Errores de validación: `VALIDATION_ERROR` con `data.fields = { campo: código }` (nombre de la restricción de Bean
  Validation, p. ej. `NotBlank`, o `REQUIRED`/`INVALID`/... desde los servicios).
- Fechas `YYYY-MM-DD`, timestamps ISO-8601 UTC, dinero en USD con 2 decimales.
- Los textos legales y las plantillas de contrato son **borradores pendientes de revisión legal**.
