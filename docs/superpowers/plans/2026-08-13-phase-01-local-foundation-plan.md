# Phase 01 Local Application Foundation Implementation Plan

- Spec: `docs/superpowers/specs/2026-08-13-phase-01-local-foundation-design.md`
- Base commit: `052985c2aa05750f3d8e8613066cede01c3249e1`
- Branch: `codex/phase-01-local-foundation`
- Method: red-green-refactor

## Constraints

- Keep implementation changes inside `frontend/`, `backend/`, and `.github/workflows/`.
- Keep Phase evidence inside `docs/phases/phase-01/`.
- Do not add Reminder business logic, Terraform, or AWS resources.
- Build a deployable WAR for external Tomcat. Do not make an executable JAR the deployment target.
- Stop and write an ADR proposal if implementation requires an Architecture v1.2 change.

## Task 1: Provision and verify the local toolchain

Install Temurin JDK 21 and Docker Desktop with Windows Package Manager. Keep Gradle project-local through the Wrapper.

Verify:

```powershell
java --version
docker --version
docker compose version
node --version
npm --version
```

Expected result: Java reports major version 21, Docker and Compose respond, and the existing Node 24 installation remains available.

## Task 2: Create backend build scaffolding and the first failing test

Create:

- `backend/settings.gradle.kts`
- `backend/build.gradle.kts`
- `backend/gradle/wrapper/gradle-wrapper.properties`
- `backend/gradlew`
- `backend/gradlew.bat`
- `backend/gradle/wrapper/gradle-wrapper.jar`
- `backend/src/test/java/com/middleproject/reminder/WarDeploymentContractTest.java`
- `backend/.gitignore`

Configure Java 21, Spring Boot 3.5.16, Gradle 8.14.5, WAR packaging, JUnit 5, Actuator, JDBC, PostgreSQL, and `providedRuntime` Tomcat.

The first test references `ReminderPlatformApplication`, requires it to extend `SpringBootServletInitializer`, and requires an overridden `configure` method. Run:

```powershell
cd backend
.\gradlew.bat test --tests "*WarDeploymentContractTest"
```

Expected RED: compilation fails because `ReminderPlatformApplication` does not exist.

## Task 3: Implement the minimum external-Tomcat application

Create:

- `backend/src/main/java/com/middleproject/reminder/ReminderPlatformApplication.java`

Implement `@SpringBootApplication`, `SpringBootServletInitializer`, `configure`, and `main`. Configure `bootWar` to emit `ROOT.war` and disable `bootJar`.

Run:

```powershell
.\gradlew.bat test --tests "*WarDeploymentContractTest"
.\gradlew.bat bootWar
```

Expected GREEN: the contract test passes and `backend/build/libs/ROOT.war` exists.

Commit:

```text
build: create external tomcat war foundation
```

## Task 4: Add PostgreSQL-backed readiness through TDD

Create the failing test:

- `backend/src/test/java/com/middleproject/reminder/ReadinessConfigurationTest.java`

The test loads `application.yml` and requires the readiness group to include `readinessState` and `db`. Run the test and confirm RED because the configuration file does not exist.

Then create:

- `backend/src/main/resources/application.yml`
- `backend/.env.example`

Use environment-backed datasource settings. Enable health probes and expose only `health` and `info`. Configure readiness to include the database health indicator.

Run:

```powershell
.\gradlew.bat test
.\gradlew.bat bootWar
```

Expected GREEN: all backend tests pass and the WAR builds.

## Task 5: Create the frontend test harness and first failing component test

Create the React TypeScript Vite project and install Vitest, React Testing Library, jsdom, and the Vite PWA plugin. Commit the generated `package-lock.json`.

Create:

- `frontend/src/App.test.tsx`
- `frontend/src/test/setup.ts`
- frontend TypeScript, Vite, and test configuration
- `frontend/.gitignore`

The first test requires the project title and the initial `Checking backend` state. Run:

```powershell
cd frontend
npm test -- --run
```

Expected RED: the test cannot resolve `App` because the component does not exist.

## Task 6: Implement the minimum project shell and readiness client

Create:

- `frontend/src/App.tsx`
- `frontend/src/main.tsx`
- `frontend/src/styles.css`
- `frontend/index.html`
- `frontend/public/` PWA icons
- `frontend/.env.example`

Implement the shell and initial state. Run the focused test for GREEN.

Add two failing tests: an HTTP 200 response with `{ "status": "UP" }` displays `Backend ready`, and a rejected or non-200 response displays `Backend unavailable`. Implement the minimum fetch behavior and rerun tests.

Configure the manifest and generated service worker. Add a build-output verification script that checks `dist/manifest.webmanifest` and the service worker file.

Run:

```powershell
npm test -- --run
npm run build
npm run verify:build
```

Expected result: component tests pass and the production PWA output exists.

Commit:

```text
build: create react pwa foundation
```

## Task 7: Add the external local environment

Create:

- `frontend/apache/httpd.conf`
- `frontend/apache/Dockerfile`
- `backend/tomcat/Dockerfile`
- `backend/compose.yaml`

Use these pinned images:

- `httpd:2.4.68-alpine`
- `tomcat:10.1.57-jdk21-temurin-noble`
- `postgres:16.14-alpine`

Apache serves `frontend/dist/` and proxies only `/api/*` to Tomcat. Tomcat deploys `backend/build/libs/ROOT.war` as `ROOT.war`. Compose requires database variables, waits for PostgreSQL health before starting Tomcat, and exposes Apache on 8080 and Tomcat on 8081.

Run:

```powershell
cd backend
docker compose --env-file .env config
docker compose --env-file .env up --build -d
```

Poll both endpoints with a bounded timeout:

```text
http://localhost:8081/actuator/health/readiness
http://localhost:8080/api/actuator/health/readiness
```

Expected result: both return HTTP 200 and status `UP`.

Stop PostgreSQL and poll again. Expected result: readiness returns HTTP 503 and status `DOWN`. Print logs, then remove containers and volumes.

Commit:

```text
feat: verify apache external tomcat local path
```

## Task 8: Add GitHub Actions

Create:

- `.github/workflows/ci.yml`

Define `frontend`, `backend`, and `integration` jobs. Use Java 21 and Node 24. Validate the Gradle Wrapper, run both test suites, build both artifacts, run the Compose checks, print logs on failure, and clean up with an unconditional final step.

Validate YAML syntax locally and push the branch. Inspect the GitHub Actions run until all three jobs finish.

Commit:

```text
ci: verify phase 01 application foundation
```

## Task 9: Record evidence and review

Create:

- `docs/phases/phase-01/result.md`
- `docs/phases/phase-01/review.md`

Record commands, exit codes, commit SHAs, changed files, CI URLs, external Tomcat evidence, PostgreSQL failure evidence, and limitations. Run a secret-pattern scan over the worktree and Git history.

Run the complete verification set from a clean checkout state:

```powershell
cd frontend
npm ci
npm test -- --run
npm run build
npm run verify:build

cd ..\backend
.\gradlew.bat clean test bootWar
docker compose --env-file .env config
docker compose --env-file .env up --build -d
```

PASS requires every acceptance criterion in the approved design and Phase 01 brief. Push the evidence commit but do not merge to `main` until the review records PASS.
