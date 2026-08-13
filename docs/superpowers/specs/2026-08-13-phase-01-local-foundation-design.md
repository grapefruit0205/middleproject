# Phase 01 Local Application Foundation Design

- Status: Approved
- Date: 2026-08-13
- Base commit: `fb5cc0a6e0ad54fa6cd83f7b5ca881fd0c2f6a73`
- Branch: `codex/phase-01-local-foundation`
- Architecture: v1.2

## Goal

Build the smallest local foundation that proves the approved WEB/WAS boundary: Apache serves a React/PWA build, proxies `/api/*` to an external Tomcat 10.1 container, and the Spring Boot WAR reports PostgreSQL-backed readiness.

## Scope

- React, TypeScript, Vite, and PWA assets in `frontend/`
- Java 21, Spring Boot 3.5, Gradle Kotlin DSL, and Gradle Wrapper in `backend/`
- Deployable `ROOT.war` with `SpringBootServletInitializer`
- PostgreSQL 16, external Tomcat 10.1, and Apache 2.4 through Docker Compose
- GitHub Actions jobs for frontend, backend, and external-container integration
- Phase 01 result and review evidence

## Non-goals

- Reminder domain models or business endpoints
- Natural-language parsing
- AWS resources or Terraform
- Embedded-Tomcat-only deployment
- Authentication, notification delivery, or scheduling

## Repository layout

```text
frontend/
  src/
  public/
  apache/
  package.json
  package-lock.json
  vite.config.ts
  .env.example
backend/
  src/main/
  src/test/
  gradle/wrapper/
  tomcat/
  build.gradle.kts
  settings.gradle.kts
  gradlew
  gradlew.bat
  compose.yaml
  .env.example
.github/workflows/ci.yml
```

Compose lives in `backend/` and Apache configuration lives in `frontend/` so Phase 01 stays within its approved implementation paths. The workflow file is the approved CI exception.

## Components

### Frontend

The frontend uses React with TypeScript and Vite. It renders a minimal project shell and displays backend availability from `/api/actuator/health/readiness`. The PWA setup generates a web manifest and service worker during the production build.

Apache 2.4 serves `frontend/dist/`. Its proxy configuration forwards `/api/*` to Tomcat and does not contain application logic.

### Backend

The backend uses Java 21 and Spring Boot 3.5. Gradle Kotlin DSL defines the build, and the checked-in Gradle Wrapper fixes the Gradle version for local and CI runs.

The application applies the WAR plugin, names the artifact `ROOT.war`, extends `SpringBootServletInitializer`, and declares Tomcat as `providedRuntime`. Actuator exposes `/actuator/health/readiness`. The readiness group includes the application readiness state and PostgreSQL health.

The backend contains no Reminder controller, service, entity, or migration in Phase 01.

### Local external environment

Docker Compose runs three services:

- PostgreSQL 16 stores local application state.
- Tomcat 10.1 on Java 21 deploys `ROOT.war`.
- Apache 2.4 serves the frontend build and proxies `/api/*` to Tomcat.

The Docker build contexts copy previously built frontend and backend artifacts into immutable local images. Developers run the frontend and backend builds before `docker compose up --build`.

## Request and build flow

```text
npm test + npm run build
             |
             v
      frontend/dist
             |
             v
Browser -> Apache :8080 -> /api/* -> Tomcat :8080 -> PostgreSQL :5432
                                      ^
                                      |
                         Gradle test + bootWar
                                      |
                       backend/build/libs/ROOT.war
```

The integration test calls `http://localhost:8080/api/actuator/health/readiness`. A healthy PostgreSQL connection produces HTTP 200. Stopping PostgreSQL produces HTTP 503 after the health state updates.

## Configuration and secrets

- `frontend/.env.example` contains `VITE_API_BASE_URL=/api`.
- `backend/.env.example` lists database variable names with placeholders.
- Compose requires runtime values for database credentials and does not define a reusable real password.
- Git ignores `.env`, Gradle caches, `node_modules`, test reports, and build output.
- Git tracks `package-lock.json`, Wrapper scripts, Wrapper properties, and the Wrapper JAR.
- CI supplies disposable database credentials as job-scoped environment variables.

## Failure behavior

- PostgreSQL unavailability changes readiness to `DOWN` with HTTP 503.
- The integration job uses a bounded readiness poll. On failure it prints Compose status and container logs.
- The workflow shuts down Compose services after success or failure.
- Apache only proxies `/api/*`; unmatched paths remain frontend routes or static files.
- Missing required environment variables stop Compose configuration before containers start.

## Test strategy

Implementation follows red-green-refactor.

### Backend sequence

1. Add a test that requires the WAR application class and external-container initializer; confirm failure before production code exists.
2. Add the minimum application class and WAR build configuration; confirm the test and `bootWar` pass.
3. Add a readiness configuration test; confirm failure.
4. Add Actuator and PostgreSQL-backed readiness configuration; confirm the test passes.

### Frontend sequence

1. Add a component test for the project shell and initial health state; confirm failure before the component exists.
2. Add the minimum component; confirm the test passes.
3. Add tests for successful and failed readiness responses; implement the smallest fetch behavior that passes them.
4. Run the production build and verify the manifest and service worker outputs.

### Integration sequence

1. Build `frontend/dist/` and `ROOT.war`.
2. Validate the Compose model.
3. Build and start PostgreSQL, Tomcat, and Apache.
4. Verify readiness through Tomcat and through Apache.
5. Stop PostgreSQL and verify readiness returns 503.
6. Print logs and remove containers and volumes.

## Continuous integration

GitHub Actions runs three jobs:

- `frontend`: npm clean install, tests, production build
- `backend`: Java 21 setup, Gradle Wrapper validation, tests, WAR build
- `integration`: rebuild both artifacts, start Compose, verify the external Tomcat and Apache path, then clean up

The integration job depends on the frontend and backend jobs. Gradle and npm caches use their lock files as cache keys.

## Acceptance criteria

- `gradlew test bootWar` exits with code 0.
- The build produces a deployable `ROOT.war`.
- The application runs in external Tomcat 10.1 on Java 21.
- `npm test` and `npm run build` exit with code 0.
- The production frontend includes a web manifest and generated service worker.
- Tomcat readiness returns HTTP 200 while PostgreSQL is healthy.
- Apache-proxied readiness returns HTTP 200.
- Readiness returns HTTP 503 after PostgreSQL stops.
- Checked-in example environment files contain no real credential.
- GitHub Actions completes all three jobs.

## Version policy

Phase 01 uses Java 21, Spring Boot 3.5.16, Gradle 8.14.5, `tomcat:10.1.57-jdk21-temurin-noble`, `postgres:16.14-alpine`, and `httpd:2.4.68-alpine`. npm records exact frontend dependency versions in `package-lock.json`. Dependency upgrades after Phase 01 require the same build and integration checks.

## References

- [Spring Boot 3.5 system requirements](https://docs.spring.io/spring-boot/3.5/system-requirements.html)
- [Spring Boot traditional WAR deployment](https://docs.spring.io/spring-boot/how-to/deployment/traditional-deployment.html)
- [Gradle Wrapper](https://docs.gradle.org/current/userguide/gradle_wrapper.html)
- [Apache Tomcat 10.1 migration guide](https://tomcat.apache.org/migration-10.1.html)
- [Vite getting started](https://vite.dev/guide/)
