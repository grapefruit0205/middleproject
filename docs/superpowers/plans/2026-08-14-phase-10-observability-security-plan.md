# Phase 10 Observability and Security Hardening Implementation Plan

## Objective

Implement the approved Phase 10 design on `codex/phase-10-observability-security`. Add request correlation, ECS JSON application logs, bounded Micrometer metrics, CloudWatch collection and Alarms, and the approved EC2, IAM, SSM, and storage controls. Codex performs the independent review. Local work stops at `AWAITING_APPROVAL` before any AWS mutation.

## Constraints

- Preserve the Public ALB to Apache WEB to Internal ALB to external Tomcat WAS to PostgreSQL path.
- Keep WEB and WAS private. Add no SSH rule, Bastion host, WAF, paid Interface Endpoint, commercial SIEM, or OpenTelemetry component.
- Use Spring Boot 3.5's built-in ECS JSON formatter. Add no third-party log encoder.
- Keep metric dimensions bounded. Never use a Reminder ID, recipient, correlation ID, exception message, or raw path as a metric tag.
- Never log authorization values, Secret values, database passwords, notification bodies, or raw personal data.
- Do not run `terraform apply`, mutate an Alarm, replace an instance, inject a live failure, or read a live Secret during local implementation.
- The restarted Command Code cycle uses `gpt-5.6-luna`, `max`, `--auto-accept`, and `--yolo`. CMDC 1.24.0 print mode needs `--yolo` to enable file-write and shell tools. The runner enforces the prompt and post-run HEAD, branch, review-file, and path-scope checks. Attempt 1 receives 50 turns. One repair may receive 30 turns. Phase 10 permits two invocations in the restarted cycle.
- Command Code may edit only `backend/**`, `infra/terraform/**`, `docs/runbooks/**`, and `docs/phases/phase-10/result.md`.
- Command Code cannot edit `docs/phases/phase-10/review.md`, create a commit, push, or change architecture records.
- Use a dedicated ignored state file, `.orchestration/phase-10-state.json`, so the completed Phase 01-09 state remains intact.

## Task 1: Extend the Orchestrator for Phase 10

### Files

- Modify: `tools/orchestration/tests/PhaseOrchestrator.Tests.ps1`
- Modify: `tools/orchestration/PhaseOrchestrator.psm1`
- Modify: `tools/orchestration/Invoke-Phase.ps1`
- Modify: `tools/orchestration/Set-PhaseReview.ps1`
- Modify: `tools/orchestration/phases.json`
- Modify: `docs/orchestration/README.md`
- Modify: `docs/phases/phase-10/implement.prompt.md`

### RED

Add focused Pester tests before changing production scripts. Each test names the production mutation that would break it. Assert that:

- the manifest contains Phases 01 through 10 in order;
- Phase 10 selects `gpt-5.6-luna`, `max`, 50 initial turns, 30 repair turns, and two invocations in the restarted cycle;
- historical phases retain `gpt-5.6-luna`, `max`, 100 turns, and three invocations;
- Phase 10 uses the approved branch, documents, allowlist, and external-approval marker;
- an initial Phase 10 run plan contains `--max-turns 50`;
- attempt 2 contains `--max-turns 30`;
- a third attempt is rejected;
- Phase 10 becomes the terminal phase instead of Phase 09;
- `Invoke-Phase.ps1 -Phase 10 -DryRun` accepts the phase and does not create state or invoke the fake Command Code process;
- PASS for every phase marked `requiresExternalApproval` requires recorded external approval instead of a hard-coded Phase 05 check.

Run:

```powershell
Invoke-Pester .\tools\orchestration\tests\PhaseOrchestrator.Tests.ps1
```

Confirm that the new assertions fail because the runner ends at Phase 09 and uses only global model and turn settings.

### GREEN

Implement per-phase manifest overrides with global fallback. Pass the next attempt number to `New-CommandCodeRunPlan` and choose the initial or repair turn limit there. Derive the terminal phase and external-approval behavior from the manifest. Change the entry point range to 1 through 10. Keep existing Phase 01-09 behavior green.

Update the operator runbook and Phase 10 prompt after the behavior tests pass. The prompt must point to the approved design, require red-green-refactor for Java behavior, prohibit live AWS mutation, and require truthful command evidence in `result.md`.

### Verification

```powershell
Invoke-Pester .\tools\orchestration\tests\PhaseOrchestrator.Tests.ps1
& .\tools\orchestration\Invoke-Phase.ps1 `
  -Phase 10 `
  -RepositoryRoot C:\middleproject `
  -StatePath C:\middleproject\.orchestration\phase-10-state.json `
  -DryRun | Format-List
```

The dry run must show Luna, max effort, 50 turns, auto-accept, the Phase 10 branch, and the current baseline without writing runtime state.

## Task 2: Correlate HTTP Requests

### Files

- Create: `backend/src/test/java/com/middleproject/reminder/CorrelationIdFilterTest.java`
- Create: `backend/src/main/java/com/middleproject/reminder/observability/CorrelationIdFilter.java`
- Modify: `backend/src/main/resources/application.yml`

### RED

Add one focused test per behavior:

- generate a canonical UUID when `X-Correlation-Id` is absent;
- preserve a canonical inbound UUID;
- replace malformed and oversized values;
- return the selected identifier in the response;
- extract a valid ALB `Root=1-<8 hex>-<24 hex>` from a header of at most 512 bytes;
- omit an invalid or oversized ALB trace value;
- expose the selected fields through MDC during the filter chain;
- clear every field in `finally` after both success and exception paths.

Run only this test class and confirm each new test fails for the missing filter behavior:

```powershell
Push-Location .\backend
.\gradlew.bat test --tests '*CorrelationIdFilterTest'
Pop-Location
```

### GREEN

Implement one `OncePerRequestFilter`. Parse before adding values to MDC. Log method, resolved route pattern when present, response status, and elapsed milliseconds after the chain returns. Treat both identifiers as diagnostic data. Keep field values bounded.

## Task 3: Add Structured Logs and Bounded Metrics

### Files

- Modify: `backend/build.gradle.kts`
- Modify: `backend/src/main/resources/application.yml`
- Create: `backend/src/main/resources/application-aws.yml`
- Create: `backend/src/test/java/com/middleproject/reminder/StructuredLoggingContractTest.java`
- Create: `backend/src/test/java/com/middleproject/reminder/ObservabilityMetricsTest.java`
- Modify: `backend/src/main/java/com/middleproject/reminder/application/SchedulerOutboxService.java`
- Modify: `backend/src/main/java/com/middleproject/reminder/application/ReminderDeliveryConsumer.java`
- Modify: `backend/src/main/java/com/middleproject/reminder/application/NotificationDeliveryService.java`
- Modify: `backend/src/test/java/com/middleproject/reminder/SchedulerOutboxIntegrationTest.java`
- Modify: `backend/src/test/java/com/middleproject/reminder/ReminderDeliveryConsumerTest.java`
- Modify: `backend/src/test/java/com/middleproject/reminder/NotificationDeliveryServiceTest.java`

### RED

Write log-contract tests that parse emitted lines as JSON and assert ECS service, environment, instance, correlation, and business fields. Use fixed canaries for an authorization value, database password, Secret payload, notification body, and recipient, then assert that no canary appears in the output.

Write Micrometer tests with `SimpleMeterRegistry`. Assert counters and timers for Scheduler reconciliation, SQS acceptance or duplicate or failure, and Delivery success or retryable or terminal failure. Assert that `reminder.delivery.terminal.failures` increments without a variable tag. Inspect all meter IDs and reject forbidden tag keys.

Run the focused tests and confirm failure because ECS file configuration, the CloudWatch registry, and the custom meters do not exist.

### GREEN

- Add Micrometer's CloudWatch 2 registry through the Spring dependency management already in use.
- Use Spring Boot's ECS formatter for `/var/log/middleproject/application.json` in the AWS profile.
- Set the service environment from `ENVIRONMENT` and ECS node name from `INSTANCE_ID`; use `local` defaults outside AWS.
- Publish to `MiddleProject/Reminder/${environment}` every 60 seconds in AWS.
- Inject `MeterRegistry` into the three application paths and record only enum-backed operation, channel, and outcome values.
- Emit business identifiers as structured log fields, not metric dimensions.
- Keep telemetry exceptions outside business transaction outcomes.
- Keep the existing public Actuator exposure limited to health and info. CloudWatch export does not require a public metrics endpoint.

Rerun the focused tests after each minimal change, then run the complete backend suite.

## Task 4: Configure Cross-Layer Log Collection

### Files

- Create: `infra/terraform/observability.tf`
- Modify: `infra/terraform/tier.tf`
- Modify: `infra/terraform/templates/web.sh.tftpl`
- Modify: `infra/terraform/templates/was.sh.tftpl`
- Modify: `infra/terraform/outputs.tf`
- Create: `infra/terraform/tests/phase10_observability.Tests.ps1`

### RED

Create Pester contract tests before Terraform and User Data changes. Assert exact log group names and retention, CloudWatch Agent package and configuration, Apache and Tomcat access formats, IMDSv2 instance-ID retrieval, Tomcat log-directory ownership, and the absence of credential or Secret values from rendered bootstrap content.

Confirm the focused test file fails because the Phase 10 resources and bootstrap configuration do not exist.

### GREEN

Terraform creates the five approved log groups. WEB ships Apache access and error logs. WAS ships Tomcat access and ECS application logs. Both tiers publish CPU, memory, and disk host metrics. User Data installs and validates the CloudWatch Agent with five bounded attempts and fails bootstrap when installation or configuration fails.

Configure Apache to record the ALB trace header and backend `X-Correlation-Id` response header. Configure Tomcat to record the same trace header and request details. Retrieve `INSTANCE_ID` with an IMDSv2 token and pass `ENVIRONMENT`, `INSTANCE_ID`, and the AWS Spring profile into Tomcat.

Enable Public and Internal ALB access logs in a dedicated private encrypted S3 bucket with a 30-day expiration lifecycle and the minimum ELB delivery policy.

## Task 5: Add Alarms and Security Controls

### Files

- Modify: `infra/terraform/observability.tf`
- Create: `infra/terraform/security.tf`
- Modify: `infra/terraform/tier.tf`
- Modify: `infra/terraform/tests/phase10_observability.Tests.ps1`
- Create: `docs/runbooks/phase-10-observability.md`

### RED

Extend the contract tests to assert:

- separate unhealthy-host and target-5xx Alarms for both target groups;
- Scheduler `TargetErrorCount` and `InvocationDroppedCount` Alarms scoped to the project Schedule Group;
- source queue age and visible-message Alarms;
- the existing DLQ Alarm uses the approved 60-second threshold;
- a terminal Delivery Alarm uses the dedicated application counter;
- root EBS uses encrypted `gp3` on both launch templates;
- WEB cannot read the database Secret;
- WAS reads only the existing RDS-managed Secret;
- CloudWatch Logs and metric IAM permissions use project resources or namespace conditions;
- no port 22 rule, public WEB or WAS address, or Bastion resource exists;
- a project Session Manager document streams to the 30-day session log group.

Confirm the new tests fail for the missing resources or controls. Add the smallest Terraform changes that pass. Do not create an SNS subscription.

Write the runbook with exact inspection commands, safe Alarm test steps, original-state capture, rollback, and teardown. Mark every live command as gated.

## Task 6: Command Code Attempt 1

### Preflight

Commit and push the verified orchestrator extension before the paid run. Confirm:

```powershell
cmdc --version
cmdc status
cmdc --list-models
git status --short --branch
& .\tools\orchestration\Invoke-Phase.ps1 `
  -Phase 10 `
  -RepositoryRoot C:\middleproject `
  -StatePath C:\middleproject\.orchestration\phase-10-state.json `
  -DryRun | Format-List
```

The process scan must show no other active Command Code writer.

### Run

```powershell
& .\tools\orchestration\Invoke-Phase.ps1 `
  -Phase 10 `
  -RepositoryRoot C:\middleproject `
  -StatePath C:\middleproject\.orchestration\phase-10-state.json
```

The runner invokes one Luna max process with 50 turns and moves to `REVIEWING` only when Command Code exits zero, keeps HEAD and branch unchanged, and edits only allowed paths.

## Task 7: Codex Independent Verification

Codex reads every changed file and the saved stdout, stderr, and `result.md`. Codex does not accept Command Code's completion claim as evidence.

Run fresh:

```powershell
Push-Location .\backend
.\gradlew.bat test
.\gradlew.bat clean bootWar
Pop-Location

Push-Location .\frontend
npm ci
npm run build
Pop-Location

Invoke-Pester .\infra\terraform\tests\phase10_observability.Tests.ps1
terraform -chdir=infra/terraform fmt -check
terraform -chdir=infra/terraform init -backend=false -input=false -reconfigure
terraform -chdir=infra/terraform validate -no-color
git diff --check
git status --short
```

Create ignored plan fixtures under `.orchestration/phase-10-plan`: compress `frontend/dist` to `frontend.zip` and copy `backend/build/libs/ROOT.war`. Read the current account number with `aws sts get-caller-identity`, without printing credentials. Run a read-only plan with `probe-valid-alternate.tfvars`, the two fixture paths, a syntactically valid Seoul ACM ARN for that account, and an account-qualified plan-only artifact bucket name. Use `-refresh=false -input=false -lock=false -no-color`. Save the plan output under `.orchestration`; do not save a Terraform binary plan or state file in Git.

Start a temporary `postgres:16.15-alpine` container bound to `127.0.0.1:55432`, provide its test-only password through a temporary ignored environment file, wait for `pg_isready`, and run `Postgres16IntegrationTest` with the three `POSTGRES_TEST_*` environment variables. Remove the container and temporary environment file in a `finally` block.

Run artifact, generated-file, secret, and scope checks. Run Checkov 3.3.8 as `docker.io/bridgecrew/checkov@sha256:c64ffb6d6fc8087c896341a2c697770a04a1cf558db04fa7b8129d8ca6bce336`. Mount only `infra/terraform` at `/tf` as read-only and pass `--directory /tf --framework terraform --skip-download --compact` plus the documented Phase 10 skip list. Do not mount AWS credentials, Terraform state, or the Docker socket. Record the version, digest, command, exit code, passed checks, failed checks, and skipped checks.

Compare the evidence line by line with the approved design and Phase 10 brief. Write `docs/phases/phase-10/review.md` with `PASS`, `REVISE`, or `BLOCKED` findings.

## Task 8: One Bounded Repair When Required

If Codex records `REVISE`, move the dedicated state back to READY and run the same entry point once. The orchestrator passes the review as read-only input and invokes Luna max with 30 turns. It rejects a third attempt in the restarted cycle.

Rerun `CorrelationIdFilterTest`, `StructuredLoggingContractTest`, `ObservabilityMetricsTest`, `phase10_observability.Tests.ps1`, and the complete Task 7 verification after repair. Do not use a repair attempt for documentation polish that Codex can perform inside its review-owned files.

## Task 9: Commit, Push, and Stop at the AWS Gate

When local evidence passes:

1. Update the root README with local Phase 10 status and the explicit AWS evidence gap.
2. Commit verified Phase 10 implementation files, the root README, and the Codex review.
3. Push `codex/phase-10-observability-security`.
4. Record `AWAITING_APPROVAL` in `.orchestration/phase-10-state.json`.
5. Present the AWS account, Seoul region, exact plan delta, cost and time limit, Alarm test steps, rollback, and teardown commands.

Do not merge to `main`, mark Phase 10 complete, or run any live AWS command until the user approves that exact external action package.
