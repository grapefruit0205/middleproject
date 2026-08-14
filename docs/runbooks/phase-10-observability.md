# Phase 10 Observability and Security Runbook

## Scope and approval gate

All commands in this runbook inspect or change live AWS resources. Run them only after the external approval gate names the AWS account, `ap-northeast-2` region, Terraform plan delta, cost limit, execution window, and rollback owner. Do not run `terraform apply`, inject failures, or change Alarm state during local implementation.

Set only non-secret identifiers in the operator shell:

```powershell
$env:AWS_REGION = 'ap-northeast-2'
$environment = '<approved-environment>'
$name = 'reminder-platform'
```

Confirm the selected account and region before any gated action:

```powershell
aws sts get-caller-identity
aws configure get region
```

## Inspect request correlation

After deployment and an approved request, use the response header as the join key. Do not put authorization values or personal data in the request.

```powershell
curl.exe -sS -D headers.txt -o NUL https://<public-alb-dns>/api/<approved-read-endpoint>
Select-String -Path headers.txt -Pattern '^X-Correlation-Id:'
aws logs filter-log-events --log-group-name "/middleproject/$environment/web/apache-access" --filter-pattern '<correlation-id>'
aws logs filter-log-events --log-group-name "/middleproject/$environment/was/tomcat-access" --filter-pattern '<correlation-id>'
aws logs filter-log-events --log-group-name "/middleproject/$environment/was/application" --filter-pattern '<correlation-id>'
```

The ALB `trace_id` field, Apache `trace`, Tomcat `trace`, and Spring `albTraceRoot` must share the `Root=1-<8 hex>-<24 hex>` value. This is gated live evidence; local verification does not assert it.

## Inspect metrics and alarms

```powershell
aws cloudwatch describe-alarms --alarm-name-prefix "$name-$environment"
aws cloudwatch get-metric-data --metric-data-queries file://<approved-query-file>.json --start-time <utc-start> --end-time <utc-end>
aws logs describe-log-groups --log-group-name-prefix "/middleproject/$environment/"
aws s3api get-bucket-encryption --bucket <alb-access-log-bucket>
aws s3api get-public-access-block --bucket <alb-access-log-bucket>
```

Inspect the ALB access-log bucket lifecycle and the Session Manager document. Session records use the CloudWatch Logs default AES-GCM encryption at rest; this low-cost design does not add a customer-managed KMS key:

```powershell
aws s3api get-bucket-lifecycle-configuration --bucket <alb-access-log-bucket>
aws ssm get-document --name "$name-$environment-session-logging" --document-format JSON
```

## Checkov exception policy

Run the pinned scanner with no network and a read-only Terraform mount. The raw scan remains part of the evidence. The policy scan excludes only the check IDs in this section and must finish with zero failures.

```powershell
$phase10CheckovExclusions = @(
  'CKV_AWS_2','CKV_AWS_18','CKV_AWS_103','CKV_AWS_112','CKV_AWS_113',
  'CKV_AWS_118','CKV_AWS_129','CKV_AWS_144','CKV_AWS_145','CKV_AWS_150',
  'CKV_AWS_158','CKV_AWS_161','CKV_AWS_338','CKV_AWS_353','CKV_AWS_378',
  'CKV2_AWS_11','CKV2_AWS_19','CKV2_AWS_20','CKV2_AWS_28','CKV2_AWS_30',
  'CKV2_AWS_61','CKV2_AWS_62'
) -join ','

docker run --rm --network none `
  -v "${PWD}/infra/terraform:/tf:ro" `
  docker.io/bridgecrew/checkov@sha256:c64ffb6d6fc8087c896341a2c697770a04a1cf558db04fa7b8129d8ca6bce336 `
  --directory /tf --framework terraform --compact --skip-download `
  --skip-check $phase10CheckovExclusions
```

The exclusions have these approved reasons:

| Checks | Reason |
|---|---|
| `CKV_AWS_2`, `CKV_AWS_103`, `CKV_AWS_378`, `CKV2_AWS_20` | The accepted three-tier architecture terminates TLS at the public ALB and uses HTTP only on private WEB-to-internal-ALB-to-WAS paths. |
| `CKV2_AWS_28` | WAF is an explicit Phase 10 non-goal. |
| `CKV_AWS_338` | The approved development retention is 14 days for service logs and 30 days for SSM logs, not one year. |
| `CKV_AWS_112`, `CKV_AWS_113`, `CKV_AWS_145`, `CKV_AWS_158` | TLS protects Session Manager transport. CloudWatch Logs and S3 use AWS default encryption at rest. Customer-managed KMS keys require a separate cost and design approval. |
| `CKV_AWS_150` | ALB deletion protection conflicts with the approved bounded ephemeral teardown. |
| `CKV_AWS_118`, `CKV_AWS_129`, `CKV_AWS_161`, `CKV_AWS_353`, `CKV2_AWS_30` | RDS enhanced monitoring, exported query logs, IAM database authentication, and Performance Insights are outside the approved Phase 10 scope. |
| `CKV2_AWS_11` | VPC Flow Logs are outside the approved log set. |
| `CKV_AWS_18`, `CKV_AWS_144`, `CKV2_AWS_61`, `CKV2_AWS_62` | Recursive bucket access logging, cross-region replication, artifact lifecycle, and event notifications are outside the bounded artifact and ALB-log design. |
| `CKV2_AWS_19` | The EIP is attached to a NAT Gateway; this check expects an EC2 attachment and is a false positive for this resource. |

Any new Checkov ID is a failure until Codex reviews its resource and records a separate decision.

## Safe Alarm test procedure

This procedure is gated and must run only inside the approved time window. Capture original state first; do not mutate production workloads or send notification payloads.

1. Record the initial alarm configuration and state:

   ```powershell
   aws cloudwatch describe-alarms --alarm-name-prefix "$name-$environment" | Tee-Object -FilePath phase-10-alarm-before.json
   ```

2. In an isolated approved environment, generate one bounded failure signal that maps to one Alarm only. Examples are a test Scheduler target with a deliberately invalid test-only target or a single controlled message sent to the isolated DLQ. Never use a production reminder, recipient, Secret, or arbitrary payload.
3. Observe the matching metric and Alarm state with `describe-alarms`; record timestamps, metric dimensions, and the source test identifier.
4. Remove the test-only signal and wait for the documented recovery evaluation period. Record the recovered state.
5. Compare `phase-10-alarm-before.json` with the final `describe-alarms` output. Escalate if any unrelated Alarm configuration or state changed.

Local implementation makes no claim that this procedure has run or that any Alarm transitioned.

## Rollback and teardown

For an approved ephemeral environment only, preserve evidence, then run the reviewed rollback command from the approved Terraform working directory:

```powershell
terraform -chdir=infra/terraform plan -destroy -refresh=false -input=false -lock=false -no-color -var-file=<approved.tfvars>
terraform -chdir=infra/terraform destroy -input=false -var-file=<approved.tfvars>
```

Use the plan output to confirm deletion includes the ALB log bucket only when its lifecycle and retention requirements permit it. Never use `-auto-approve`, never force-delete an unexpected bucket, and never tear down a shared environment without an explicit owner approval.
