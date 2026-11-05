# Construye el web y el H5 y los sirve con Nginx. Contexto de build: la raíz del repo.
FROM node:22-alpine AS web
WORKDIR /src/web
COPY web/package*.json ./
RUN npm ci
COPY web/ ./
RUN npm run build

FROM node:22-alpine AS h5
WORKDIR /src
COPY scripts/ ./scripts/
WORKDIR /src/h5
COPY h5/package*.json ./
RUN npm ci
COPY h5/ ./
RUN npm run build && npm run check:external

FROM nginx:1.27-alpine
ENV BACKEND_UPSTREAM=backend:8080 NGINX_PORT=80
COPY deploy/nginx/templates/ /etc/nginx/templates/
COPY --from=web /src/web/dist/ /usr/share/nginx/html/
COPY --from=h5 /src/h5/dist/ /usr/share/nginx/html/m/
EXPOSE 80
HEALTHCHECK --interval=30s --timeout=5s CMD wget -q -O /dev/null http://127.0.0.1/ || exit 1
