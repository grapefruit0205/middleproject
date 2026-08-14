# Phase 11 HA Test Runbook

```powershell
$runtimeRoot = 'C:\middleproject\.orchestration\runtime\phase-11-ha'
$tfDir = Join-Path $runtimeRoot 'terraform'
$fakeTfvarsPath = Join-Path $runtimeRoot 'ha-fake-certificate.tfvars'
$approvedTfvarsPath = Join-Path $runtimeRoot 'ha-approved.tfvars'
$fakePlanPath = Join-Path $tfDir 'phase-11-ha-fake-certificate.plan'
$approvedPlanPath = Join-Path $tfDir 'phase-11-ha-approved.plan'
```

`phase-11-ha-fake-certificate.plan` is validation-only and must never be applied. It contains the fake ACM ARN. `phase-11-ha-approved.plan` is reserved for a separately generated plan after an approved real ACM certificate import and renewed Codex/user review. Never rename or substitute the fake plan for the approved plan.

## 1. Scope and hard gates

This runbook is for the approved ephemeral `ha` environment only.

Required approval values:

- AWS account: `891376981416`
- Region: `ap-northeast-2`
- Environment: `ha`
- Project name: `reminder-platform`
- Approved Terraform plan delta: `<record exact add/change/destroy counts>`
- Approved cost ceiling: `<record amount and currency>`
- Approved execution window: `<record UTC and Asia/Seoul times>`
- Rollback/teardown owner: `<record owner>`

Stop immediately when:

- AWS account or region differs from the approved values.
- Terraform plan contains unexpected resources or destroys.
- A resource is not tagged or named as created by this session.
- A command requires reading a Secret, credential, Terraform state, token, recipient, or personal data.
- A reproducible application or infrastructure defect is found.
- Cleanup fails.

Do not claim zero downtime, RTO, or RPO without an observed measurement.

## 2. Evidence rules

For every experiment record:

- experiment ID
- start and end timestamps in UTC and Asia/Seoul
- exact command
- pre-failure state
- failure injection and expected impact
- during-failure observations
- recovery command
- recovery timestamp and observed recovery duration
- post-recovery state
- log and metric evidence paths
- cost impact
- limitations and anomalies

Mask account IDs, ARNs, IPs, DNS names, email addresses, tokens, and personal data in committed evidence.

Do not commit complete raw logs. Keep only minimal redacted excerpts and command summaries.

## 3. Read-only preflight

```powershell
$env:AWS_REGION = 'ap-northeast-2'
$environment = 'ha'
$name = 'reminder-platform'
$expectedAccount = '891376981416'

aws sts get-caller-identity --query Account --output text
aws configure get region
aws ec2 describe-vpcs --region ap-northeast-2
aws autoscaling describe-auto-scaling-groups --region ap-northeast-2
aws ec2 describe-instances --region ap-northeast-2
aws elbv2 describe-load-balancers --region ap-northeast-2
aws rds describe-db-instances --region ap-northeast-2
aws ec2 describe-nat-gateways --region ap-northeast-2
aws ec2 describe-addresses --region ap-northeast-2
aws logs describe-log-groups --region ap-northeast-2
aws cloudwatch describe-alarms --region ap-northeast-2
aws scheduler list-schedule-groups --region ap-northeast-2
aws sqs list-queues --region ap-northeast-2
aws s3api list-buckets
```

Mask output before saving evidence.

## 4. Terraform plan gate

Run only after approval of the exact variables and cost window.

```powershell
terraform -chdir="$tfDir" fmt -check
terraform -chdir="$tfDir" init -input=false
terraform -chdir="$tfDir" validate

# Fake-ARN validation only; never apply this plan.
terraform -chdir="$tfDir" plan `
  -input=false `
  -refresh=false `
  -no-color `
  -var-file="$fakeTfvarsPath" `
  -out="$fakePlanPath"
```

After a real ACM certificate is imported and separately approved, create a new plan with `$approvedTfvarsPath` at `$approvedPlanPath`. Do not reuse or apply `$fakePlanPath`.

```powershell
terraform -chdir="$tfDir" plan `
  -input=false `
  -refresh=false `
  -no-color `
  -var-file="$approvedTfvarsPath" `
  -out="$approvedPlanPath"
```

Before apply, record:

- exact plan summary
- resources to add/change/destroy
- RDS Multi-AZ setting
- WEB/WAS desired capacity
- NAT mode
- S3 lifecycle and deletion behavior
- `skip_final_snapshot`
- `deletion_protection`
- expected hourly and data-processing cost

No `terraform apply -auto-approve`.

Apply requires a separate approval showing purpose, expected impact, cost, and rollback. Apply only the separately reviewed real-certificate plan:

```powershell
terraform -chdir="$tfDir" apply "$approvedPlanPath"
```

Never run `terraform apply "$fakePlanPath"`.

## 5. Baseline and Phase 10 evidence

Capture before each experiment:

```powershell
aws elbv2 describe-target-health --target-group-arn <masked-web-target-group-arn>
aws elbv2 describe-target-health --target-group-arn <masked-was-target-group-arn>
aws autoscaling describe-auto-scaling-groups --auto-scaling-group-names <web-asg> <was-asg>
aws rds describe-db-instances --db-instance-identifier reminder-platform-ha
aws cloudwatch describe-alarms --alarm-name-prefix reminder-platform-ha
aws cloudwatch get-metric-data --metric-data-queries file://<approved-query>.json --start-time <utc-start> --end-time <utc-end>
aws logs describe-log-groups --log-group-name-prefix /middleproject/ha/
aws ssm describe-instance-information --region ap-northeast-2
```

Use a non-secret, non-personal read endpoint:

```powershell
curl.exe -sS -D headers.txt -o response.txt https://<masked-public-alb-dns>/api/<approved-read-endpoint>
Select-String -Path headers.txt -Pattern '^X-Correlation-Id:'
```

Join only the masked correlation ID across ALB, Apache, Tomcat, Spring application logs, and relevant metrics.

## 6. EXP-01 WEB single-instance failure

Purpose: observe Public ALB and WEB target behavior when one WEB instance is unavailable.

Before approval, show purpose, expected impact, cost impact, recovery command, and exact target instance and AZ.

Approved failure options:

```powershell
aws ec2 stop-instances --instance-ids <session-created-web-instance-id> --region ap-northeast-2
```

or, only if explicitly approved:

```powershell
aws autoscaling terminate-instance-in-auto-scaling-group `
  --instance-id <session-created-web-instance-id> `
  --should-decrement-desired-capacity false `
  --region ap-northeast-2
```

Observe target health, ALB metrics, alarms, and the approved `/healthz` endpoint. Record actual request behavior.

Recovery branch:

- If the targeted instance remains `stopped`, show purpose, expected impact, cost, and recovery command, obtain separate approval, then run:

  ```powershell
  aws ec2 start-instances --instance-ids <session-created-web-instance-id> --region ap-northeast-2
  ```

- If the ASG moves the instance to `terminating` or `terminated`, do not call `start-instances`. Observe the replacement instance creation, SSM `Online`, and target `healthy` state.
- Never call `start-instances` for an already terminated instance.
- Record observed recovery only; do not infer RTO.

## 7. EXP-02 WAS single-instance failure

Purpose: observe Internal ALB target removal and request behavior when one WAS instance is unavailable.

Before approval, show purpose, expected impact, cost impact, target instance and AZ, and recovery command.

Approved failure command:

```powershell
aws ec2 stop-instances --instance-ids <session-created-was-instance-id> --region ap-northeast-2
```

Observe Internal ALB target health, alarms, metrics, and an approved read endpoint through the public ALB.

Recovery branch:

- If the targeted instance remains `stopped`, show purpose, expected impact, cost, and recovery command, obtain separate approval, then run:

  ```powershell
  aws ec2 start-instances --instance-ids <session-created-was-instance-id> --region ap-northeast-2
  ```

- If the ASG moves the instance to `terminating` or `terminated`, do not call `start-instances`. Observe the replacement instance creation, SSM `Online`, and target `healthy` state.
- Never call `start-instances` for an already terminated instance.

Check readiness only through approved endpoints. Do not inspect database Secret values.

## 8. EXP-03 RDS Multi-AZ failover

Purpose: observe managed RDS Multi-AZ failover and application behavior.

Before approval, show purpose, expected connection impact, expected RDS state transitions, cost impact, and the managed recovery behavior. No manual rollback command is used.

Capture only non-secret metadata:

```powershell
aws rds describe-db-instances `
  --db-instance-identifier reminder-platform-ha `
  --query "DBInstances[0].[DBInstanceStatus,MultiAZ,AvailabilityZone,SecondaryAvailabilityZone,Endpoint.Address]" `
  --output text `
  --region ap-northeast-2
```

Approved failure command:

```powershell
aws rds reboot-db-instance `
  --db-instance-identifier reminder-platform-ha `
  --force-failover `
  --region ap-northeast-2
```

Observe RDS events, instance metadata, target health, application logs, and the first successful approved read. Record actual connection errors, failover timestamps, and observed recovery duration. Do not claim zero downtime or RPO from a smoke test.

## 9. EXP-04 Scheduler failure and recovery

Purpose: observe Scheduler-to-SQS failure handling and recovery without production recipients or Secrets.

Use only a session-created test schedule and test-only queue/group path. Do not mutate production reminder schedules.

Before approval, show the exact test identifiers, expected alarm/metric, cleanup command, and cost impact.

Failure injection must be limited to a test-only target with an invalid or deliberately unavailable destination and requires separate approval.

Observe Scheduler metrics, alarms, schedule metadata, and test queue attributes. Restore only the test schedule, verify the test message, then delete only the test schedule/group after preserving evidence.

## 10. EXP-05 Provider failure and recovery

Purpose: observe Provider failure, persisted delivery attempt state, retry, and terminal/DLQ behavior.

Use only a test fixture or Provider boundary already present in the deployed build. Do not add code, modify Terraform, read provider credentials, send to a real recipient, or use a production Secret.

Before approval, show the provider test mechanism, test identifier, expected state transitions, expected metric/alarm, recovery command, cost impact, and recipient safety.

If no safe test-only Provider failure mechanism exists, stop and report the evidence gap. Do not modify code to create one.

## 11. Alarm evidence

Do not call `set-alarm-state` unless separately approved for the exact alarm and purpose. Prefer natural metric transitions from isolated test signals.

```powershell
aws cloudwatch describe-alarms `
  --alarm-name-prefix reminder-platform-ha `
  --query "MetricAlarms[].{Name:AlarmName,State:StateValue,Reason:StateReason,Updated:StateUpdatedTimestamp,Metric:MetricName,Namespace:Namespace}" `
  --region ap-northeast-2
```

Any unrelated Alarm configuration or state change is a failure.

## 12. Cost and time controls

At the actual apply start, record local UTC and Asia/Seoul timestamps:

```powershell
$applyStartUtc = (Get-Date).ToUniversalTime().ToString('o')
$applyStartKst = (Get-Date).ToString('o')
"apply_start_utc=$applyStartUtc"
"apply_start_kst=$applyStartKst"
```

The USD 5 ceiling is an operator limit, not an AWS automatic control. Cost Explorer is post-run evidence only and must not be used as a real-time stop mechanism. At three hours after apply start, begin no new experiment and immediately start the cleanup approval procedure. Four hours is the absolute operational end time, not a resource-running target.

Capture Cost Explorer after the run and after cleanup:

```powershell
aws ce get-cost-and-usage `
  --time-period Start=<YYYY-MM-DD>,End=<YYYY-MM-DD> `
  --granularity DAILY `
  --metrics UnblendedCost `
  --group-by Type=SERVICE `
  --region us-east-1
```

Record EC2, RDS, ALB, NAT Gateway, EBS, CloudWatch Logs, S3, SQS, Scheduler, and RDS snapshot costs where available. Determine cleanup completion from read-only AWS resource inventory, not Cost Explorer.

The ALB access-log bucket and artifact bucket have independent `force_destroy` controls. Review both values in the destroy plan; deletion is allowed only for session-created resources.

## 13. Cleanup approval gate

Before cleanup, show the exact Terraform destroy plan, resources to be removed, final snapshot behavior, S3 deletion behavior, expected post-teardown cost, and confirmation that every resource was created by this session.

```powershell
terraform -chdir="$tfDir" plan -destroy `
  -input=false `
  -no-color `
  -var-file="$approvedTfvarsPath"
```

Cleanup requires separate approval. Never use `-auto-approve`.

```powershell
terraform -chdir="$tfDir" destroy `
  -input=false `
  -var-file="$approvedTfvarsPath"
```

The destroy plan must show both `artifact_force_destroy = true` and `alb_access_log_force_destroy = true` for this approved ephemeral session. If cleanup fails, report the exact remaining resource and removal procedure. Do not report success.

## 14. Post-cleanup read-only verification

```powershell
aws ec2 describe-instances --region ap-northeast-2
aws ec2 describe-nat-gateways --region ap-northeast-2
aws ec2 describe-addresses --region ap-northeast-2
aws elbv2 describe-load-balancers --region ap-northeast-2
aws rds describe-db-instances --region ap-northeast-2
aws autoscaling describe-auto-scaling-groups --region ap-northeast-2
aws ec2 describe-vpcs --region ap-northeast-2
aws logs describe-log-groups --region ap-northeast-2
aws cloudwatch describe-alarms --region ap-northeast-2
aws scheduler list-schedule-groups --region ap-northeast-2
aws sqs list-queues --region ap-northeast-2
aws s3api list-buckets
```

Filter by session tags and approved names. Never delete or alter resources that cannot be attributed to this session.

## 15. Deliverables

Allowed files only:

- `README.md`
- `docs/runbooks/phase-11-ha-test.md`
- `docs/phases/phase-11/result.md`
- `docs/phases/phase-11/evidence-index.md`
- `docs/phases/phase-11/demo-script.md`
- `docs/phases/phase-11/portfolio.md`
- `docs/phases/phase-11/evidence/**`

Do not create or modify `docs/phases/phase-11/review.md`.

Final evidence must include observed WEB/WAS recovery behavior, observed RDS failover behavior, observed Scheduler/Provider failure and recovery, Phase 10 logs/metrics/alarms/SSM evidence, actual observed RTO/RPO or explicit evidence gaps, cost snapshot, cleanup result, unresolved risks, and final Git diff summary.
