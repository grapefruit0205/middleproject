# Verified knowledge — three-tier architecture

Scope: `middleproject` at Git commit `a013e1b`, checked 2026-08-22. No external verifier is configured, so confirmed entries are marked `self-gated`.

## Fixed WEB/WAS ASGs are replacement-capable but not demand-elastic — confirmed (self-gated) — 2026-08-22

- Claim: both Auto Scaling Groups are fixed at `min = max = desired = 2`. They can replace unhealthy instances and roll a launch-template refresh, but they cannot add capacity above two and have no target-tracking policy.
- Refutation attempted: an Auto Scaling Group can still provide health replacement without a scaling policy, so the broader claim “there is no autoscaling behavior” was rejected. The surviving claim is limited to demand elasticity.
- Sources: [`infra/terraform/tier.tf`](../../infra/terraform/tier.tf) (`aws_autoscaling_group.web`, `aws_autoscaling_group.was`); AWS, [Target tracking scaling policies for Amazon EC2 Auto Scaling](https://docs.aws.amazon.com/autoscaling/ec2/userguide/as-scaling-target-tracking.html).
- Sample: n=2 ASG definitions and n=1 official AWS service contract.
- Limits: repository snapshot only; a future capacity variable or scaling-policy resource would supersede this entry. No load test was run, so no scaling threshold is recommended here.

## The application runtime currently connects as the RDS master user — confirmed (self-gated) — 2026-08-22

- Claim: Terraform creates the RDS instance with `var.db_username`, passes that same username and the RDS managed-master secret to WAS, and Tomcat retrieves that secret at startup. AWS strongly recommends that applications use a minimally privileged database account instead of the master user.
- Refutation attempted: the username defaults to `reminder_app` and the password is safely managed by Secrets Manager, but neither creates a separate database principal; the value is still assigned to the RDS `username` field that defines the master user.
- Sources: [`infra/terraform/tier.tf`](../../infra/terraform/tier.tf) and [`infra/terraform/templates/was.sh.tftpl`](../../infra/terraform/templates/was.sh.tftpl); AWS, [Master user account privileges](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/UsingWithRDS.MasterAccounts.html).
- Sample: n=1 database definition, n=1 WAS bootstrap path, and n=1 official AWS database guidance page.
- Limits: this establishes the deployed credential path, not that the application has exercised every master privilege. A separate migration/runtime role design has not yet been implemented.

## An ALB listener can deny a path before its default forward action — confirmed (self-gated) — 2026-08-22

- Claim: an Application Load Balancer listener rule can match a `path-pattern` such as `/api/mcp` or `/api/mcp/*` and return a fixed 4xx response before the default forward action.
- Refutation attempted: the default listener rule cannot have conditions, but a higher-priority non-default rule can; therefore the path block must be a separate listener rule, not a modification of the default action.
- Sources: AWS, [Condition types for listener rules](https://docs.aws.amazon.com/elasticloadbalancing/latest/application/rule-condition-types.html); AWS, [Action types for listener rules](https://docs.aws.amazon.com/elasticloadbalancing/latest/application/rule-action-types.html).
- Sample: n=2 independent ALB documentation contracts.
- Limits: applies to Application Load Balancers and HTTP(S) request paths. It does not create the private MCP tunnel or authenticate a caller.

## Apache reverse proxy plus a Spring Boot WAR remains a supported deployment form — confirmed (self-gated) — 2026-08-22

- Claim: Apache HTTP Server 2.4 supports HTTP reverse-proxy operation through `mod_proxy`/`mod_proxy_http`, and Spring Boot supports deployable WAR files for an external servlet container.
- Refutation attempted: Spring Boot also supports more modern deployment forms, and compatibility does not prove this is the lowest-operations production choice. The narrower compatibility claim survived.
- Sources: Apache Software Foundation, [`mod_proxy` documentation](https://httpd.apache.org/docs/2.4/mod/mod_proxy.html); Spring, [Traditional Deployment](https://docs.spring.io/spring-boot/how-to/deployment/traditional-deployment.html).
- Sample: n=2 independent upstream project documentation sets.
- Limits: this is a support/compatibility finding, not a performance, cost, or security benchmark.

## A managed logical three-tier alternative is technically viable — confirmed (self-gated) — 2026-08-22

- Claim: a presentation tier can be served from private S3 through CloudFront, while an application tier runs behind an ALB on ECS Fargate and persists to RDS. This remains three logical tiers even though it removes Apache and self-managed Tomcat hosts.
- Refutation attempted: this alternative does not preserve the repository's accepted requirement to demonstrate Apache installation, external Tomcat, and WEB-to-WAS proxying. “Viable” survived; “better for this project” did not.
- Sources: AWS Well-Architected, [Create network layers](https://docs.aws.amazon.com/wellarchitected/latest/framework/sec_network_protection_create_layers.html); AWS, [Get started with a secure static website](https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/getting-started-secure-static-website-cloudformation-template.html); AWS, [Amazon ECS launch types](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/capacity-launch-type-comparison.html).
- Sample: n=3 official architecture/service documentation sets.
- Limits: no project migration, benchmark, or price calculation was performed. Operational burden and cost effects are conditional on workload and team practices.

## The repository proves a deployed baseline, not HA recovery behavior — confirmed (self-gated) — 2026-08-22

- Claim: the checked-in evidence records a healthy two-AZ deployment with two WEB and two WAS targets and Multi-AZ RDS, but explicitly records that deliberate WEB/WAS failure, RDS failover, RTO/RPO measurement, and final rehearsal were not executed.
- Refutation attempted: multi-AZ placement and healthy target counts are meaningful availability preparation, so “no HA work exists” was rejected. AWS reliability guidance separately treats recovery testing as required evidence, leaving the narrower “recovery is not demonstrated” claim.
- Sources: [`README.md`](../../README.md), [`docs/phases/phase-11/result.md`](../../docs/phases/phase-11/result.md), and [`docs/phases/phase-11/review.md`](../../docs/phases/phase-11/review.md); AWS Well-Architected, [Reliability design principles](https://docs.aws.amazon.com/wellarchitected/latest/framework/rel-dp.html).
- Sample: n=1 documented deployment run, n=3 repository status records, and n=1 official reliability framework.
- Limits: no live AWS environment exists now, so the historical observations were not reproduced on 2026-08-22.
