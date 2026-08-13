# Phase 6 result

Phase 6 scheduler/outbox and SQS delivery implementation includes deterministic concurrent claim-order evidence and a PostgreSQL 16 compatibility path within the allowed paths. Terraform deployment defaults enable the AWS Scheduler and SQS workers, while backend application defaults remain disabled for local development.

## Safety and operations

- DLQ alarm threshold: **1** visible message.
- Source queue `maxReceiveCount`: **5**.
- Scheduler and delivery workers are property-gated Spring `SmartLifecycle` components with bounded retry backoff, interruption-aware shutdown, and long-poll SQS receive.
- AWS clients are Spring-owned beans with `destroyMethod = "close"`; adapters and consumers do not close them.
- IAM allows only scheduler CreateSchedule/UpdateSchedule/DeleteSchedule for the scheduler group and `reminder-*` names, and `iam:PassRole` only for the scheduler role with `iam:PassedToService=scheduler.amazonaws.com`.

Safe DLQ procedure: inspect the message body and attributes first; validate JSON schema, reminder ID, scheduler version, idempotency key, and target queue before any replay. Validate the corresponding reminder/outbox state and payload contract, then replay only a validated message through the approved queue operation. Do not delete or replay before validation; retain the original until successful processing is confirmed.

## Verification evidence

The following evidence was recorded in the Phase 6 review and is retained here as review-attributed evidence:

- `cmd /c gradlew.bat test` and `cmd /c gradlew.bat bootWar` — exit **0**; clean full test suite and WAR build.
- `cmd /c gradlew.bat test --rerun-tasks --tests com.middleproject.reminder.SchedulerOutboxIntegrationTest --tests com.middleproject.reminder.AwsSchedulerAdapterTest` — exit **0** on each of five consecutive runs.
- PostgreSQL 16.15 integration execution — exit **0** on server version `160015`; two Flyway migrations completed successfully. This is independent evidence recorded by the Phase 6 review, not evidence from the current revision's environment-gated run. Credentials were supplied through environment variables and were not printed or stored.
- `terraform fmt -check`, Terraform initialization, and `terraform validate -no-color` — exit **0**.
- Backendless `terraform plan -refresh=false` for the development profile — exit **0**, **65 add**, 0 update, 0 delete.
- Backendless `terraform plan -refresh=false` for the HA profile — exit **0**, **64 add**, 0 update, 0 delete.
- Backendless local-profile validation probe — exit **1** with the expected environment validation rejection; `infra/terraform/variables.tf` allows only the `development` and `ha` profiles.
- Backendless validation probes: invalid `/24` VPC, duplicate AZ, and non-Seoul AZ — exit **1** each with the expected variable validation rejection; alternate valid `/16` probe — exit **0**.
- No Terraform apply, AWS mutation, commit, push, merge, rebase, or reset was performed.

Commands run during this revision:

- `cmd /c gradlew.bat test --tests com.middleproject.reminder.Postgres16IntegrationTest` — exit **1** initially because the newly edited test used `.set()` on a boolean; corrected and rerun — exit **0**. With PostgreSQL environment variables unset, the environment-gated test was cleanly skipped; this run does not claim a PostgreSQL execution. The strengthened concurrent test was not executed against PostgreSQL in this revision because the required environment variables were absent.
- `cmd /c gradlew.bat test --tests com.middleproject.reminder.SchedulerOutboxIntegrationTest --tests com.middleproject.reminder.AwsSchedulerAdapterTest` — exit **0** sequentially. A concurrent attempt encountered a locked Gradle `output.bin` and exited **1** before execution.
- `cmd /c gradlew.bat test && cmd /c gradlew.bat bootWar` — exit **0** for both commands.
- `terraform fmt -check && terraform validate -no-color` in `infra/terraform` — exit **0**.
- `git diff --check -- backend infra/terraform docs/phases/phase-06/result.md` — exit **0**; Git reported only existing LF/CRLF normalization warnings.
- A PostgreSQL environment check reported `POSTGRES_TEST_URL`, `POSTGRES_TEST_USERNAME`, and `POSTGRES_TEST_PASSWORD` unset; no secret values were output.

## Implementation evidence

`Postgres16IntegrationTest` is environment-gated, dynamically configures Spring datasource credentials without printing or persisting the password, requires `server_version_num` to start with `16`, relies on Flyway, and cleans rows before and after each test. Its concurrent test creates two workers using the existing `JdbcTemplate`, autowired `PlatformTransactionManager`/`TransactionTemplate`, and recording `SchedulerPort`. Deterministic latches hold the first scheduler claim; the second worker uses PostgreSQL `FOR UPDATE SKIP LOCKED` to claim an unlocked independent reminder without waiting, while the newer row for the held reminder remains pending. Bounded `Future` waits and executor shutdown/termination assertions provide cleanup.

Terraform defaults for `scheduler_aws_enabled` and `delivery_sqs_enabled` are enabled for deployment. Backend application defaults remain disabled for local development.

DLQ alarm threshold remains **1** visible message, source queue `maxReceiveCount` remains **5**, and the safe inspect/validate/replay procedure remains in force. No Terraform apply, AWS/live-resource mutation, or commit was performed.
