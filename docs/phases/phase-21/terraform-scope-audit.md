# Terraform Scope Audit for the 15-Minute Presentation

## Audit basis

- Working directory: `infra/terraform`
- Reviewed files: `main.tf`, `tier.tf`, `security.tf`, `observability.tf`,
  `variables.tf`, `outputs.tf`, and bootstrap templates.
- Validation on 2026-08-17:
  - `terraform fmt -check` — PASS
  - `terraform init -backend=false -input=false` — PASS
  - `terraform validate` — PASS
- No Terraform source, plan, state, or live AWS resource was changed by this audit.

## Core architecture to keep and explain

| Area | Terraform evidence | Presentation meaning |
| --- | --- | --- |
| Network boundary | One VPC, public/WEB/WAS/DB subnets across two AZs, separate route tables | The tiers are network-separated; DB routes are local-only. |
| Public entry | Public HTTPS ALB, ACM certificate, WEB target group | Android/browser traffic has one trusted public entry point. |
| WEB tier | Two private Apache instances in an ASG | Reverse proxy, static frontend, health boundary, and private MCP tunnel client. |
| Private MCP | Public Apache denies `/api/mcp`; loopback-only listener serves the tunnel client | ChatGPT does not reach the MCP endpoint through the public ALB path. |
| WAS tier | Internal ALB and two private Tomcat/Spring Boot instances in an ASG | Business rules, MCP/REST tools, provider calls, and notification worker remain private. |
| Data tier | Isolated encrypted PostgreSQL RDS Multi-AZ with AWS-managed master password | Persistent state is reachable only from the WAS security boundary. |
| Async delivery | EventBridge Scheduler group and role, encrypted SQS queue, DLQ | A confirmed schedule is durable before an external notification is attempted. |
| Deployment artifacts | Private encrypted/versioned S3 artifact bucket | WEB/ROOT.WAR deployment does not require public artifacts. |
| Secrets and operations | Exact-resource Secrets Manager permissions, SSM Session Manager | No SSH ingress or credential in source/bootstrap arguments. |
| Observability | Five log groups, ALB access logs, ten alarms, correlation-aware application logs | Failures can be detected across ingress, application, Scheduler, queue, and delivery. |

This is not overengineering for this project because the project’s primary subject is
the AWS 3-Tier platform. Removing the internal ALB, isolated DB tier, Outbox/SQS path,
or two-AZ deployment would weaken the main learning and presentation claim.

## Explain as one 3-Tier path

```text
Android / Browser
  → Public ALB :443
  → private WEB (Apache)
  → Internal ALB :80
  → private WAS (Tomcat/Spring Boot) :8080
  → isolated RDS PostgreSQL :5432

ChatGPT
  → OpenAI Secure MCP Tunnel
  → WEB loopback-only Apache listener
  → Internal ALB
  → WAS MCP adapter
```

The asynchronous side path is explained separately:

```text
WAS database transaction
  → schedule_outbox
  → EventBridge Scheduler
  → SQS / DLQ
  → WAS worker
  → FCM or optional email provider
```

## Keep in code but move to the appendix

The following controls are useful but should not receive individual presentation
slides:

- S3 ownership controls, public-access blocks, encryption, versioning, and lifecycle.
- Every IAM action and conditional feature policy.
- The ten individual CloudWatch alarm definitions; group them as ingress,
  asynchronous delivery, and terminal delivery alarms.
- CloudWatch Agent installation details and log file paths.
- Launch Template bootstrap, Tomcat/Apache installation, artifact hashes, and tunnel
  binary SHA-256 pinning.
- Development versus HA NAT implementation details.
- Public-transport provider feature flags and the explicitly accepted legacy HTTP
  egress exception.
- Probe tfvars and Terraform contract-test mechanics.

Moving these details to the appendix reduces explanation time without weakening the
actual implementation.

## Genuine cleanup candidates

These are code-quality cleanups, not presentation blockers. Remove them only in a
separate Terraform change with a reviewed plan.

1. **Unused legacy SSM role/profile** — `aws_iam_role.ssm`, its managed-policy
   attachment, `aws_iam_instance_profile.ssm`, and `ssm_instance_profile_name` are
   retained for compatibility but are not used by the WEB/WAS Launch Templates.
2. **Unused local** — `local.instance_log_group_arns` is declared in `security.tf`
   but is not referenced by a policy.
3. **Redundant NAT branch expression** — `nat_enabled` tests for a `local`
   environment that `variables.tf` no longer permits. It can be simplified when the
   environment contract is next revised.
4. **Historical output descriptions** — several outputs still name earlier phases.
   Rename descriptions to current product terms without changing resource behavior.

## Accuracy constraints for the talk

- The WEB and WAS ASGs are configured `min=2`, `desired=2`, `max=2`; describe them
  as fixed two-AZ capacity with managed replacement and rolling refresh, not
  load-driven elasticity.
- Public `/api/mcp` is denied by the public Apache virtual host. The Public ALB
  forwards to WEB; it is not itself performing an MCP path rule.
- Internal ALB traffic is HTTP inside the VPC. Public client traffic is HTTPS.
- The RDS instance is Multi-AZ and encrypted, but the current demo does not claim a
  tested application-level regional disaster-recovery plan.
- The Terraform configuration supports optional SES, FCM, public transport, and
  tunnel integrations. Only enabled and verified integrations should be shown as
  live evidence.

## No new Terraform resources for Phase 21 by default

Do not add Bedrock, AgentCore, ECS, EKS, OpenSearch, pgvector infrastructure,
another queue, another load balancer, or an analytics database. Use the existing
Micrometer, structured logs, PostgreSQL audit data, and CloudWatch resources to
produce a small outcome scorecard. A new resource requires a concrete missing
acceptance criterion, cost estimate, and removal plan.
