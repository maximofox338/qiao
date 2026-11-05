# Qiao · web (portal de clientes + back-office)

Aplicación Vue 3 + TypeScript + Vite + Element Plus + Pinia + vue-router + vue-i18n. Implementa la parte web de `docs/SPEC.md`:

- **Portal de clientes** (`/`, `/login`, `/client/**`, `/pay/fake/:id`, `/legal/:doc`, `/account`), idiomas `en` y `es` (por defecto el del navegador, si no `en`).
- **Back-office** (`/admin/**`), idiomas `es` y `en` (por defecto `es`). El menú se muestra según los roles del administrador (SUPERADMIN ve todo).

## Requisitos

Node 22 y npm.

## Uso

```bash
npm install
npm run dev          # http://localhost:5173, proxy /api -> http://localhost:8080
npm run type-check   # vue-tsc --noEmit
npm run build        # type-check + build de producción en dist/
npm run preview      # sirve dist/ en http://localhost:4173 (también con proxy /api)
```

En producción nginx sirve `dist/` en `/` (con fallback a `index.html` para el history mode) y hace de proxy de `/api`.

## Estructura

```
src/
  api/        cliente HTTP (axios), tipos y endpoints (user.ts, admin.ts)
  i18n/       vue-i18n; locales/en.ts y locales/es.ts (es está tipado contra en)
  stores/     Pinia: sesión de usuario, sesión de admin, catálogo de categorías
  router/     rutas y guards (login de cliente / de admin, roles)
  layouts/    ClientLayout y AdminLayout
  views/      public/, client/, admin/
  components/ StatusTag, MarkdownView, EngineerCard, MoneyInput, SkillsInput, ...
  utils/      errores, formato, markdown seguro, palabras clave de cumplimiento
```

## Detalles

- **Sobre de la API**: todas las respuestas `{code, message, data}` se desenvuelven en `src/api/http.ts`. Si `code !== "OK"` se lanza `ApiError`; la interfaz traduce por `code` (`errors.<CODE>` en i18n) y usa `message` como respaldo. `VALIDATION_ERROR` se muestra campo a campo (`data.fields`).
- **Tokens separados** en `localStorage`: `qiao.user.token` (cliente) y `qiao.admin.token` (admin). Las URLs `/api/admin/**` usan el token de admin. Un 401 borra el token correspondiente y redirige a `/login` o `/admin/login`.
- **Sin peticiones externas**: fuentes del sistema, iconos de `@element-plus/icons-vue` empaquetados, sin CDN. Los países se nombran con `Intl.DisplayNames` (local).
- **Markdown seguro** (`markdown-it` con `html: false`, sin imágenes, solo enlaces http(s)/mailto/relativos) para textos legales y documentos de contrato.
- **Pagos de prueba**: «Pagar» llama a `/api/client/milestones/{id}/checkout` y navega a la `url` devuelta; con `PAYMENT_PROVIDER=fake` es `/pay/fake/{paymentId}`.
- **Tests e2e**: los `data-testid` están documentados en [`TESTIDS.md`](./TESTIDS.md).

Las decisiones de interpretación de la especificación están en `docs/DECISIONS.md` (sección «web»).
