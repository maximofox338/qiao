# Qiao (桥)

Marketplace curado donde empresas extranjeras contratan **proyectos por hitos** a ingenieros y equipos STEM en China, sin VPN y con pagos legales en yuanes a través de un socio chino. Este repositorio es el MVP de la fase 1 del [plan de desarrollo](https://claude.ai/artifact/3JX7QAUYaRuicxk6ZMUGMy).

## Estructura
| Carpeta | Qué es | Stack |
| --- | --- | --- |
| `backend/` | API (monolito modular) | Java 21, Spring Boot 3.5, MyBatis-Plus, MySQL 8.4, Flyway |
| `web/` | Portal de clientes (en, es) y back-office (`/admin`) | Vue 3, TypeScript, Element Plus |
| `h5/` | App móvil de ingenieros (`/m/`, zh-CN y en) | Vue 3, TypeScript, Vant 4 |
| `deploy/` | Docker Compose y Nginx para Alibaba Cloud Hong Kong | Docker |
| `e2e/` | Prueba de punta a punta del flujo completo | Playwright |
| `docs/` | Especificación (`SPEC.md`) y decisiones (`DECISIONS.md`) | |

## Reglas que el código hace cumplir
- Sólo proyectos con hitos y fecha de fin; nada de ofertas de empleo.
- Categorías prohibidas bloqueadas y palabras sensibles marcadas para revisión.
- Los clientes ven perfiles seudonimizados; el CV completo sólo con consentimiento por proyecto.
- Contador anual de transferencias de datos personales con umbrales.
- La app de ingenieros no hace ninguna solicitud a servidores externos (lo verifica `scripts/check-no-external.mjs` y la CSP de Nginx).

## Correr todo en local
Requisitos: Java 21, Maven, Node 22 y Docker.
```bash
docker run -d --name qiao-mysql-dev -e MYSQL_ROOT_PASSWORD=root -e MYSQL_DATABASE=qiao -p 3306:3306 mysql:8.4
cd backend && SPRING_PROFILES_ACTIVE=dev APP_DEMO_SEED=true mvn spring-boot:run   # API en :8080
cd web && npm install && npm run dev                                            # http://localhost:5173
cd h5 && npm install && npm run dev                                             # http://localhost:5174/m/
```
En modo `dev` los emails no se envían: el código de acceso aparece en el log y en el back-office (Outbox), y los pagos usan un checkout de prueba.

## Pruebas
```bash
./scripts/e2e.sh          # flujo completo con MySQL, backend, Nginx y Playwright
cd backend && mvn verify  # tests de integración (Testcontainers)
```

## Despliegue
Ver [`deploy/README.md`](deploy/README.md).
