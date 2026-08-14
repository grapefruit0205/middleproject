# Phase 10 Observability and Security Hardening Design

Status: Approved for implementation

Date: 2026-08-14

Branch: `codex/phase-10-observability-security`

## Objective

Phase 10 gives operators enough evidence to trace requests, detect Scheduler and Delivery failures, and verify the project's minimum AWS security controls. The work keeps the approved three-tier path:

```text
User -> Public ALB -> Apache WEB -> Internal ALB -> Tomcat WAS -> PostgreSQL
                              EventBridge Scheduler -> SQS/DLQ -> WAS -> Provider
```

The phase adds no user-facing capability. It does not deploy OpenTelemetry, a commercial SIEM, WAF, or paid Interface Endpoints.

## Decisions

### Observability stack

Use AWS-native components:

- request correlation identifiers;
- Spring Boot JSON logs;
- Spring Actuator and Micrometer;
- CloudWatch Agent on WEB and WAS instances;
- CloudWatch Logs, Metrics, and Alarms;
- encrypted ALB access logs in S3.

OpenTelemetry would add a Java agent or SDK, an OTLP pipeline, Collector lifecycle management, exporter configuration, IAM, and another failure path. The current service topology does not need that portability or trace backend. A future ADR can add OpenTelemetry if the platform splits into more services or adopts a different observability backend.

### Execution budget

The restarted Command Code cycle uses `gpt-5.6-luna` with `max` effort. The first implementation attempt receives at most 50 turns. Codex reviews the result. One repair attempt receives at most 30 turns. A second failure stops the phase for user direction.

The runner does not call Sonnet automatically. Codex may recommend a separate read-only review when it finds a security question that the repository evidence cannot settle.

## Request correlation

The HTTP filter validates an inbound `X-Correlation-Id`. It accepts a canonical UUID and generates a new UUID for a missing, malformed, or oversized value. The filter returns the selected value in the response header, adds it to the logging MDC, and clears the MDC in a `finally` block.

The application accepts at most 512 bytes from `X-Amzn-Trace-Id` and extracts a valid `Root=1-<8 hex>-<24 hex>` value from the header that the Public ALB adds or updates. It omits the trace field when the header is oversized or has no valid Root. The Internal ALB can change the `Self` field but preserves the `Root` field. Apache records the Root value and forwards the header. Tomcat and Spring record the same Root value with the request method, route, response status, and elapsed time. Apache also records the application's `X-Correlation-Id` response header. The application treats both identifiers as diagnostic data and never as authentication or authorization input.

Scheduler and Delivery work continues after the originating HTTP request. Their logs use stable business identifiers already present in the system: Reminder ID, Scheduler version, Delivery key, and Notification Attempt correlation ID. These fields let an operator join an HTTP write to its Outbox row, Scheduler action, SQS delivery, and Provider attempt without storing MDC state in a WAS instance.

## Application logs and metrics

Spring Boot 3.5's built-in ECS formatter writes one JSON object per line to `/var/log/middleproject/application.json`; no additional logging encoder is added. Each record contains a timestamp, severity, logger, message, service, environment, instance identifier, correlation fields, and relevant business identifiers. In AWS, User Data obtains the EC2 instance ID through an IMDSv2 token request and exposes it to Tomcat as the `INSTANCE_ID` systemd environment value. Local and test profiles use the literal `local`. User Data creates the log directory with Tomcat-only write permission, and Spring's size and history limits bound local disk use. Logging code excludes authorization values, database passwords, Secret payloads, notification bodies, and raw personal data.

Actuator keeps readiness separate from metrics. Micrometer records bounded, low-cardinality counters and timers for:

- HTTP requests and failures;
- Scheduler reconciliation successes and failures;
- SQS acceptance, duplicate suppression, and processing failures;
- notification delivery successes, retryable failures, and terminal failures.

Metric tags use fixed categories such as operation, channel, and outcome. They do not use Reminder IDs, recipients, correlation IDs, exception messages, or request paths with unbounded values.

The dedicated `reminder.delivery.terminal.failures` counter has no variable tag and is the source for the Delivery Alarm. Detailed delivery meters may use only the fixed channel and outcome values enforced by the domain enums.

Local and test profiles use an in-memory registry. The AWS profile publishes once per 60 seconds to `MiddleProject/Reminder/${environment}`. A telemetry export failure does not roll back a Reminder business transaction.

## AWS log collection

Terraform creates these log groups:

- `/middleproject/${environment}/web/apache-access`, retained for 14 days;
- `/middleproject/${environment}/web/apache-error`, retained for 14 days;
- `/middleproject/${environment}/was/tomcat-access`, retained for 14 days;
- `/middleproject/${environment}/was/application`, retained for 14 days;
- `/middleproject/${environment}/ssm/session`, retained for 30 days.

CloudWatch Agent runs on WEB and WAS instances and collects:

- Apache access and error logs;
- Tomcat access logs;
- Spring JSON application logs;
- selected EC2 host metrics for CPU, memory, and disk.

User Data installs and configures the agent with bounded retries. A broken agent package or invalid configuration fails instance bootstrap so the Auto Scaling Group does not register an unobservable instance as healthy.

The Public and Internal ALBs retain native CloudWatch metrics. Terraform enables ALB access logging to a dedicated S3 bucket with public access blocked, encryption enabled, a 30-day deletion lifecycle, and the minimum delivery policy required by Elastic Load Balancing.

## Alarms

Terraform defines these development thresholds with standard-resolution metrics:

- Public and Internal ALB `UnHealthyHostCount >= 1` for two consecutive 60-second periods;
- Public and Internal ALB `HTTPCode_Target_5XX_Count >= 5` in one 5-minute period, with a separate Alarm for each target group;
- `AWS/Scheduler` `TargetErrorCount >= 1` or `InvocationDroppedCount >= 1` in one 5-minute period for the project Schedule Group;
- source SQS `ApproximateAgeOfOldestMessage >= 300` seconds for two consecutive 60-second periods;
- source SQS `ApproximateNumberOfMessagesVisible >= 10` for two consecutive 60-second periods;
- DLQ `ApproximateNumberOfMessagesVisible >= 1` in one 60-second period;
- `MiddleProject/Reminder/${environment}` `reminder.delivery.terminal.failures >= 1` in one 5-minute period.

Phase 10 creates no notification subscription. Operators can inspect alarm state in CloudWatch without sending email or SMS. A later change can attach an approved topic.

## Security hardening

Both launch templates require IMDSv2 and encrypt their root EBS volumes with `gp3`. WEB and WAS remain private and receive no public IPv4 address. Security groups contain no SSH port 22 rule, and the project creates no Bastion host.

Terraform grants CloudWatch log writes only to the project log groups. It grants application metric writes only to the project namespace. WEB cannot read the database Secret. WAS can read only the RDS-managed Secret it already uses. User Data contains the Secret ARN but never the Secret value.

Terraform creates a project-scoped Session Manager document and log group. Operators start project sessions with that document so Session Manager streams the session record to CloudWatch. The phase does not replace account-wide Session Manager preferences.

## Failure handling

- The HTTP filter replaces invalid correlation identifiers instead of rejecting valid business requests.
- The filter clears all MDC fields after success or failure.
- Metric emission cannot change a business outcome.
- Logging serializes bounded fields and never logs full request or Provider payloads.
- CloudWatch Agent bootstrap failures prevent an instance from reaching the healthy pool.
- Alarm tests use bounded, reversible failure signals and record the original state before changing anything.

## Tests and evidence

Command Code follows red-green-refactor for application behavior:

1. Add failing correlation-filter tests for generation, preservation, response propagation, invalid input replacement, and MDC cleanup.
2. Add failing Micrometer tests for Scheduler and Delivery outcomes and bounded tags.
3. Add failing log-contract tests for required JSON fields and secret exclusion.
4. Implement the smallest production changes that make each test pass.

Codex independently runs:

- focused application tests;
- the full Gradle test and `bootWar` build;
- PostgreSQL 16 integration tests;
- Terraform format, offline initialization, validation, and profile plans;
- Checkov through a pinned Docker image digest, without mounting AWS credentials, Terraform state, or the Docker socket into the scanner; the result records both the Checkov version and image digest;
- static assertions for IAM scope, IMDSv2, encrypted EBS, private WEB/WAS, no SSH, log retention, and alarm dimensions;
- WAR, secret, generated-artifact, path-scope, and whitespace checks.

The implementation result records exact commands, exit codes, test counts, scanner findings, and limitations. It does not claim live Alarm transitions or cross-layer AWS logs until Codex observes them in the approved account.

## External approval boundary

Local implementation and static verification do not authorize `terraform apply`, AWS resource creation, log injection, Alarm manipulation, instance replacement, or Secret access. After local review passes, the phase stops at `AWAITING_APPROVAL` and presents:

- AWS account and region;
- exact Terraform plan and resource delta;
- expected cost and time limit;
- safe Alarm test procedure;
- rollback and teardown commands.

Before that approval, Codex may push the phase branch for backup. It does not update `main` or mark Phase 10 `PASS`.

## Acceptance criteria

- One HTTP request exposes its `X-Correlation-Id` in the response, Apache response log, and Tomcat/Spring log. The same request exposes the ALB trace Root in ALB access evidence, Apache, and Tomcat/Spring logs.
- Scheduler and Delivery logs and metrics contain stable business identifiers and bounded outcome dimensions.
- CloudWatch collects the approved logs and metrics with explicit retention.
- Each required Alarm has a valid metric, dimension, threshold, and runbook test.
- IAM remains tier-specific and excludes wildcard access where AWS supports exact scoping or conditions.
- IMDSv2, encrypted EBS, private WEB/WAS, no SSH/Bastion, and Secret non-disclosure checks pass.
- Local Gradle, PostgreSQL, Terraform, security scanner, artifact, scope, and secret checks pass.
- Live AWS evidence exists only after explicit approval, and teardown leaves no Phase 10 resource behind.

## Primary references

- AWS ALB request tracing: https://docs.aws.amazon.com/elasticloadbalancing/latest/application/load-balancer-request-tracing.html
- AWS ALB access logs: https://docs.aws.amazon.com/elasticloadbalancing/latest/application/load-balancer-access-logs.html
- EventBridge Scheduler metrics: https://docs.aws.amazon.com/scheduler/latest/UserGuide/monitoring-cloudwatch.html
- CloudWatch Agent configuration: https://docs.aws.amazon.com/AmazonCloudWatch/latest/monitoring/CloudWatch-Agent-Configuration-File-Details.html
- Spring Boot 3.5 structured logging: https://docs.spring.io/spring-boot/reference/features/logging.html#features.logging.structured
