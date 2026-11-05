# Qiao H5 — app móvil de ingenieros

App web móvil (H5) para los ingenieros en China continental: proyectos, pujas, contratos, entregas y cobros.
Vue 3 + TypeScript + Vite + Vant 4 + Pinia + vue-router + vue-i18n. Idioma por defecto `zh-CN`, también `en`.
Se sirve en `/m/` (nginx en `deploy/`) y habla con la API en `/api` según `docs/SPEC.md`.

## Regla de oro: cero hosts externos

Los ingenieros entran **sin VPN**, así que el build no puede pedir nada fuera de nuestro dominio
(ni CDN, ni fuentes web, ni analítica). Cómo se garantiza:

- Fuentes del sistema; iconos de Vant con la woff2 embebida (`data:`). Un plugin de `vite.config.ts` quita el
  respaldo remoto de la fuente de iconos (`//at.alicdn.com/...`) y el enlace de error de Vue.
- Markdown (textos legales y contratos) con un renderizador propio y estricto (`src/utils/markdown.ts`):
  escapa todo el HTML, no carga imágenes y solo permite enlaces relativos, `http(s)` y `mailto`.
- `npm run check:external` (`../scripts/check-no-external.mjs dist`) falla si aparece cualquier URL
  `http(s)://` o `ws(s)://` (o `//host` en HTML/CSS) que no sea un namespace XML/SVG de `www.w3.org`.
- `vite preview` envía la misma CSP que nginx, así que cualquier intento de salir se ve en la consola.

## Comandos

```bash
npm ci
npm run dev             # http://localhost:5174/m/  (proxy /api -> http://localhost:8080)
npm run type-check      # vue-tsc
npm run build           # type-check + build en dist/
npm run check:external  # control de URLs externas sobre dist/
npm run preview         # http://localhost:4174/m/ con la CSP de producción
npm run smoke           # Playwright contra preview con la API simulada (requiere build y playwright instalado)
```

`npm run smoke [carpeta]` levanta `vite preview`, simula toda la API con `page.route`, recorre landing, login,
perfil, proyectos + puja, pujas, contrato (aceptar documento, entregar hito con archivo, mensajes), ingresos y
«我的»; bloquea y cuenta cualquier petición a un host que no sea `localhost`, falla si hay errores de consola y
guarda capturas (por defecto en `test-results/smoke/`). También comprueba que no hay scroll horizontal a 360 px.

## Estructura

- `src/api/` cliente `fetch` (desenvuelve `{code, message, data}`, 401 → login) y endpoints de la spec.
- `src/i18n/` textos `zh-CN` y `en`, incluidos todos los códigos de error de la spec.
- `src/views/` pantallas; `src/components/` piezas comunes; `src/stores/` Pinia.
- `TESTIDS.md` lista de `data-testid` para los tests e2e.
- Interpretaciones de la spec: sección «h5» de `docs/DECISIONS.md`.
