# Qiao — reglas del proyecto

Marketplace curado: empresas extranjeras contratan **proyectos por hitos** a ingenieros en China continental. Leer `docs/SPEC.md` (contrato de la API) y `docs/DECISIONS.md` antes de cambiar algo.

## Reglas que no se rompen
- Nunca ofertas de empleo: sólo proyectos con hitos, entregables y fecha de fin (≤ 12 meses).
- La app de ingenieros (`h5/`) no hace ninguna solicitud a hosts externos. `npm run check:external` debe pasar; nada de Google, CDNs, analítica SaaS ni fuentes web.
- El cliente ve sólo perfiles seudonimizados; el CV completo requiere consentimiento `SHARE_FULL_CV` por proyecto. Cada exposición de datos de un ingeniero escribe un `transfer_event`.
- Cada parte ve sólo su propio documento de contrato. El ingeniero nunca ve el precio del cliente ni el nombre de la empresa (sólo el país).
- Datos sensibles (documento, cuenta bancaria, rostro) nunca se guardan en la fase 1; los maneja el socio chino.
- Nada de APIs de IA occidentales (OpenAI, Anthropic, Gemini) en código que corra para usuarios en China.

## Stack y comandos
- `backend/`: Java 21, Spring Boot 3.5, MyBatis-Plus, MySQL 8.4, Flyway. `mvn -q verify` (Testcontainers, necesita Docker). Migraciones nuevas: `V<n>__descripcion.sql`, nunca editar una ya publicada.
- `web/` (clientes + `/admin`) y `h5/` (ingenieros, `/m/`): Vue 3 + TypeScript. `npm run build` incluye el type-check.
- `./scripts/e2e.sh`: flujo completo con MySQL, backend, Nginx y Playwright. Debe quedar en verde antes de cada commit a `main`.
- Textos de interfaz siempre por i18n (web: en, es; admin: es, en; h5: zh-CN, en). Cada elemento del flujo principal lleva `data-testid`.

## Estilo
- Monolito modular: cada módulo es dueño de sus tablas; nada de SQL entre módulos.
- Respuestas `{code, message, data}`; los frontends traducen por `code`.
- Commits en español, descriptivos.
