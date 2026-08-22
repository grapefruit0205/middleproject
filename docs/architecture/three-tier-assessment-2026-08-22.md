# 3-Tier Architecture Repository Assessment

- Date: 2026-08-22
- Repository snapshot: `main` at `a013e1b`
- Scope: repository architecture, backend/frontend boundaries, Terraform, checked-in verification evidence, and viable AWS alternatives
- Purpose and limit: this is an architecture decision record and prioritized backlog, not a command-by-command deployment runbook. It must support choosing a target and separating pre-deployment gates from evidence collected during an approved run. Actual AWS actions still require the linked runbook, success criteria, credentials, cost/time approval, and the user's explicit approval.
- Evidence labels: `confirmed` passed the local verify gate; `observed` was directly inspected or run once; `assumed` is a conditional judgment; `unknown` needs user or runtime evidence.

## Recommendation

**Keep the current EC2-based physical three-tier topology and adopt the hardened “A+” design as the next implementation baseline.** This is the best fit for this repository because Apache installation, external Tomcat, WEB/WAS separation, and their request path are explicit project requirements and already have deployment evidence. A wholesale move to CloudFront/S3 plus ECS Fargate is technically viable, but it would replace accepted ADR-001/002 and discard much of the evidence this project was designed to present.

“A+ selected” means the target and backlog are approved. “A+ implemented” additionally requires the pre-deployment code gates below. “A+ HA-verified” is earned only after an approved `ha` run collects the missing recovery evidence. These are separate states; an HA test is not a prerequisite for creating the environment in which that test runs.

This recommendation is `assumed` to optimize for the repository's stated assignment/demo objective. If the real priority has changed to a long-running public production service with minimum host operations, reassess the managed alternative after workload, SLO, budget, and operator capacity are known.

## What the repository has today

The five network hops form three **logical** tiers; the two ALBs are traffic-control components, not additional business tiers.

```text
Presentation tier
  Internet -> Public ALB -> private Apache WEB ASG -> React/PWA
                               |
Application tier              v
  private Internal ALB -> private external-Tomcat WAS ASG -> Spring Boot WAR
                                                                  |
Data tier                                                         v
  isolated RDS PostgreSQL Multi-AZ

Supporting asynchronous path
  EventBridge Scheduler -> SQS/DLQ -> WAS -> SES/push provider
```

- `observed`: the intended request path and responsibility split are explicit in [Architecture v1.2](architecture-v1.2.md), [project invariants](project-invariants.md), and ADR-001/002.
- `observed`: Terraform implements two-AZ public, WEB, WAS, and DB subnet groups; chained Security Groups; private WEB/WAS instances; an isolated RDS ingress boundary; SSM administration; IMDSv2; encrypted EBS/RDS; and separate health checks.
- `observed`: the two ASGs are fixed at two instances each. They can replace unhealthy instances and perform rolling refreshes, but cannot grow past two and have no demand-scaling policy. This is an HA baseline, not elastic capacity.
- `observed`: RDS is always Multi-AZ, regardless of `development` versus `ha`; only the NAT topology changes with that variable.
- `observed`: the checked-in Phase 11 evidence records a healthy 2/2 WEB, 2/2 WAS, private encrypted Multi-AZ RDS baseline and no Terraform drift. It also explicitly records that failure injection, RDS failover, RTO/RPO measurement, and the final rehearsal were not run.
- `observed`: the AWS stack was torn down on 2026-08-15. The architecture is implemented as code and historically deployed, but it is not currently operational.

## What is already good

### 1. The boundaries are explainable and enforceable

`observed`: presentation, business logic, and persistent state have distinct runtimes and subnet/Security Group boundaries. Apache knows only the Internal ALB DNS, not WAS instance addresses. RDS accepts PostgreSQL only from the WAS Security Group. This is a clean physical demonstration of the three-tier principle.

### 2. WEB and WAS can fail and roll independently

`observed`: each server tier has its own Launch Template, target group, readiness/health check, ASG, and rolling-refresh behavior. A change to the React/Apache artifact need not change Tomcat, and vice versa.

### 3. Application state is kept out of the compute tiers

`observed`: the architecture declares RDS as the source of truth and keeps WAS stateless. Transactional outbox, idempotency lease/fencing, SQS/DLQ, and durable notification-attempt records address failures that a simple synchronous three-tier diagram would miss.

### 4. The deployment choice is deliberate, not accidental legacy

`confirmed`: Apache `mod_proxy_http` reverse proxying and a Spring Boot WAR in an external servlet container remain supported deployment forms. The repository chose them because the assignment evaluates visible Apache/Tomcat construction. Compatibility does not make them the universal modern default, but it makes the choice coherent for this project.

### 5. Infrastructure evidence is unusually explicit

`observed`: phase results distinguish implementation, live baseline verification, missing HA experiments, and teardown. That makes it possible to avoid claiming more availability than has actually been demonstrated.

## Implementation and evidence gates

### P0 — before any AWS deployment: architecture and security code gates

1. **Make deployment profiles real.** Parameterize WEB/WAS `min`, `max`, and `desired` capacity plus `db_multi_az`. Keep the existing Terraform environment names: `development` defaults to 1 WEB, 1 WAS, Single-AZ RDS, and its current single-zonal NAT; `ha` defaults to 2/2, Multi-AZ RDS, and its current Regional NAT. Use `development` for short wiring/smoke checks and `ha` for the missing failure-recovery evidence; never cite a development run as HA proof. Exact production sizes remain `unknown` until load and SLO evidence exists.
2. **Implement the promised public MCP block.** The current public listener has only a default forward action, while Apache proxies all `/api/`. Therefore `/api/mcp` is not blocked in the checked-in Terraform even though README, ADR-005, and the project invariants say it is. Add a higher-priority ALB listener rule for both `/api/mcp` and `/api/mcp/*`, defaulting to a fixed 404 response so the private endpoint is not advertised, plus a Terraform contract test. Use 403 instead only if an explicit client/diagnostic contract requires it.
3. **Fail closed until the authentication boundary is wired.** The public REST controllers expose read and write routes without an authentication dependency. The MCP controller requires a `Principal`, but the repository contains no production wiring that supplies one. Before the planned pairing/identity mechanism exists, expose only the UI and readiness path publicly; deny other `/api/*` routes at the public boundary and use private/controlled probes for deeper smoke checks. Selecting device pairing versus another identity mechanism is a product decision, but anonymous API exposure is not the fallback.
4. **Separate database principals.** Terraform currently gives WAS the RDS managed-master secret and uses the master username as the application username. Rename the master role for what it is, run Flyway through a controlled migration identity, and give the running application a separate least-privilege secret. Derive exact schema/table grants from the migrations and integration tests during implementation; this assessment decides the identity separation, not every SQL grant.
5. **Build reproducible server images.** Use a versioned EC2 Image Builder pipeline for WEB/WAS, pin the base AMI and package inputs, and verify the Tomcat archive checksum during the image build. Keep application artifacts separately versioned so a release does not silently inherit a different base image.

P0 completion permits an explicitly approved AWS run; it does not yet prove recovery or production suitability.

### P1 — during and after an approved `ha` run: evidence gates

6. **Finish the HA evidence.** First define the pass/fail recovery targets from the user-owned SLO/RTO/RPO decision. Then use the checked-in [Phase 11 HA Test Runbook](../runbooks/phase-11-ha-test.md) to run the approved WEB/WAS replacement and RDS failover procedures, record recovery time and data behavior, and perform the 15-minute rehearsal. Two instances and Multi-AZ configuration prepare for failure; they do not prove recovery behavior by themselves.
7. **Add demand scaling only from measured need.** If this becomes a real variable-load service, run a load test and choose a target-tracking metric such as CPU or `ALBRequestCountPerTarget`, with bounded min/max values. For a short, controlled HA demonstration, fixed 2/2 is simpler and honest when labeled as fixed capacity. This item is conditional and is not a gate for the bounded HA demonstration.

### P2 — later maintainability work, not a gate for the next bounded run

8. **Make backend boundaries consistent.** The package structure has domain, application, port, infrastructure, and web layers, but several application services directly depend on `JdbcTemplate`. This does not break deployment three-tier architecture, yet it weakens the replaceable data-access boundary needed by later Trip phases. Move persistence details behind the existing repository ports as those features are changed.
9. **Build the actual presentation tier.** The React application currently checks only backend readiness. Event, policy, reminder, and notification workflows exist primarily as APIs; the user-facing presentation tier is not feature-complete.
10. **Keep WAF and VPC endpoints as explicit later work.** They are already listed as later scope. Do not let them block the P0 correctness items, but reassess them before a durable public deployment.

## Options considered

| Option | Shape | Fit with accepted project requirements | Change/evidence impact | Operations profile | Judgment |
|---|---|---|---|---|---|
| A · Current unchanged | Public ALB → Apache EC2 → Internal ALB → Tomcat EC2 → RDS | High | None, but known promises remain unwired | Two server fleets; mutable boot | Reject as final baseline |
| **A+ · Hardened current** | Same path, with real environment profiles, path denial, identity boundary, least-privilege DB roles, reproducible images, followed by HA tests | **Very high** | Low-to-medium; reuses historical evidence | Host operations remain, but are explicit | **Recommended now** |
| B · Managed logical 3-tier | CloudFront/private S3 → ALB/ECS Fargate → RDS | Low under ADR-001/002; high only if those requirements are retired | High; new ADRs and new deployment/HA evidence required | `assumed` lower host-management surface; not measured here | Reconsider after a goal change |
| C · Hybrid | Apache EC2 → ALB → containerized Tomcat/ECS → RDS | Medium | High; combines two deployment models and weakens the external-server demonstration | Mixed EC2/container operations | No clear advantage; do not choose |

## Recommended A+ target

### Presentation tier

- Public ALB terminates TLS and forwards ordinary dashboard/API traffic to private Apache WEB targets.
- A listener rule rejects public `/api/mcp` before traffic reaches Apache.
- Apache serves the React/PWA artifact and reverse-proxies only approved API paths.
- The future private MCP tunnel enters through its separately controlled private route; it is not inferred to exist until Phase 15/17 implements and tests it.

### Application tier

- Internal ALB routes only from the WEB Security Group to stateless external-Tomcat targets.
- Spring Boot remains a `ROOT.war`, preserving the current assignment evidence.
- Authentication/ownership, business rules, scheduling/outbox, notification delivery, and audit stay in this tier.
- Repository ports become the only application-to-persistence boundary when adjacent code is changed.

### Data tier

- RDS PostgreSQL stays isolated and remains the source of truth.
- `ha` uses Multi-AZ; `development` uses Single-AZ and must be labeled non-HA.
- Administrative/migration credentials and runtime credentials are separate, independently rotated Secrets Manager secrets.

### Supporting services

- EventBridge Scheduler, SQS/DLQ, SES/push, CloudWatch, and SSM remain supporting services around the three core tiers; they do not create a fourth business tier.

## Decision rule

Choose **A+** if any of these remain true: the assignment must show Apache/Tomcat installation, the presentation must demonstrate WEB-to-WAS proxying, or the existing Phase 5/11 evidence should be reused.

Choose **B** only if all are true: those demonstration constraints are formally retired by new ADRs; the service is intended to run persistently; reducing self-managed host work is more important than preserving current evidence; and migration plus new load/failure/security tests are funded.

The exact production topology remains `unknown` because expected request rate, traffic shape, availability target, recovery target, monthly infrastructure ceiling, and operator capacity were not supplied. No precise AWS cost or capacity claim is made in this assessment.

## Verification performed on 2026-08-22

| Check | Result | Evidence/limit |
|---|---|---|
| Repository, architecture, ADR, phase evidence, backend, frontend, Terraform inspection | pass | Direct inspection at `a013e1b` |
| Frontend unit tests | pass | Vitest: 1 file, 4 tests |
| Frontend production build | pass | Vite/PWA build completed |
| Frontend build contract | pass | `npm run verify:build` completed |
| Production dependency audit | pass | `npm audit --omit=dev --audit-level=high`: 0 vulnerabilities reported |
| Backend tests/build | unavailable | Java runtime is not installed in this workspace; historical checked-in evidence is not treated as a current rerun |
| Terraform/Pester validation | unavailable | Terraform and PowerShell are not installed in this workspace |
| Live AWS/HA validation | unavailable | Phase 11 stack was torn down on 2026-08-15 |

## Primary external references

- AWS Well-Architected, [Create network layers](https://docs.aws.amazon.com/wellarchitected/latest/framework/sec_network_protection_create_layers.html)
- AWS Well-Architected, [Reliability design principles](https://docs.aws.amazon.com/wellarchitected/latest/framework/rel-dp.html)
- AWS, [Target tracking scaling policies](https://docs.aws.amazon.com/autoscaling/ec2/userguide/as-scaling-target-tracking.html)
- AWS, [ALB listener path conditions](https://docs.aws.amazon.com/elasticloadbalancing/latest/application/rule-condition-types.html) and [fixed-response actions](https://docs.aws.amazon.com/elasticloadbalancing/latest/application/rule-action-types.html)
- AWS, [RDS master user account privileges](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/UsingWithRDS.MasterAccounts.html)
- AWS, [ECS launch types and capacity providers](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/capacity-launch-type-comparison.html)
- AWS, [Secure static website with CloudFront and private S3](https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/getting-started-secure-static-website-cloudformation-template.html)
- AWS Well-Architected, [Deploy using immutable infrastructure](https://docs.aws.amazon.com/wellarchitected/2023-10-03/framework/rel_tracking_change_management_immutable_infrastructure.html)
- Apache, [`mod_proxy`](https://httpd.apache.org/docs/2.4/mod/mod_proxy.html)
- Spring Boot, [Traditional deployment](https://docs.spring.io/spring-boot/how-to/deployment/traditional-deployment.html)
