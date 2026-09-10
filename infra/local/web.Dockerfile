FROM node:22-bookworm-slim AS build
WORKDIR /workspace/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --include=dev
COPY frontend/ ./
RUN npm run build

FROM httpd:2.4-alpine
RUN sed -i \
      -e 's/^#LoadModule proxy_module/LoadModule proxy_module/' \
      -e 's/^#LoadModule proxy_http_module/LoadModule proxy_http_module/' \
      -e 's/^#LoadModule headers_module/LoadModule headers_module/' \
      /usr/local/apache2/conf/httpd.conf \
    && printf '\nInclude conf/extra/reminder.conf\n' >>/usr/local/apache2/conf/httpd.conf
COPY infra/local/httpd/reminder.conf /usr/local/apache2/conf/extra/reminder.conf
COPY --from=build /workspace/frontend/dist/ /usr/local/apache2/htdocs/
COPY daylight/ /usr/local/apache2/htdocs/daylight/
RUN printf 'ok\n' >/usr/local/apache2/htdocs/healthz
EXPOSE 80
