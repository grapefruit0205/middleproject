# Phase 17 AWS Well-Architected Architecture Review v3

## Deliverables

- Editable Draw.io source: `phase-17-aws-well-architected-v3.drawio`
- Presentation preview: `phase-17-aws-well-architected-v3.png`
- Canvas: 1920 × 1080, single page
- Icon source: AWS Architecture Icons release dated 2026-07-31

## Diagram scope

This diagram is an as-built view of the current Terraform configuration in
`infra/terraform`. It applies AWS reference-architecture layout conventions
without presenting future or unimplemented services as deployed resources.

Included:

- Seoul Region with two Availability Zones
- One VPC with public, WEB private, WAS private, and DB-isolated subnet tiers
- Public and internal Application Load Balancers spanning both AZs
- WEB and WAS Auto Scaling Groups spanning both AZs
- Amazon RDS for PostgreSQL Multi-AZ
- Regional NAT Gateway and Internet Gateway
- EventBridge Scheduler, SQS reminder queue and DLQ
- S3 artifacts and ALB access logs
- Secrets Manager, IAM, KMS, CloudWatch, Systems Manager, and ACM
- External Gabia DNS, OpenAI MCP tunnel, and Firebase Cloud Messaging

Intentionally excluded because they are not part of the current Terraform
deployment:

- Amazon Route 53
- Amazon CloudFront
- AWS WAF
- Amazon Cognito
- Amazon SES
- A second NAT Gateway or per-AZ NAT topology

No AWS account ID, credential, token, secret value, private key, database
password, certificate ARN, or bucket name is included in the diagram.

## AWS best-practice mapping

| Well-Architected concern | Diagram evidence |
| --- | --- |
| Reliability | Two AZs, cross-AZ ALBs, two-node WEB/WAS Auto Scaling Groups, RDS Multi-AZ, health routing, SQS DLQ |
| Security | Private WEB/WAS subnets, isolated DB subnets, security-group chain, Secrets Manager, KMS, ACM, no-SSH Systems Manager access |
| Operational excellence | CloudWatch logs and alarms, Systems Manager Session Manager, explicit synchronous and asynchronous paths |
| Performance efficiency | Layer-specific load balancing, stateless horizontal WEB/WAS capacity, queue-backed worker path |
| Cost optimization | Current demo capacity is shown explicitly; S3 lifecycle and a single Regional NAT reflect the deployed cost profile |
| Sustainability | Horizontal scaling and managed regional services are separated from instance-hosted workloads |

The two-AZ topology follows AWS guidance to avoid a single location failure.
The ALB is shown with nodes in both enabled AZs and routes to tier-local healthy
targets. RDS is shown as a primary/standby Multi-AZ deployment rather than two
application-addressable database instances.

## Visual layout decisions

- AWS Cloud → Region → VPC → Availability Zone → subnet nesting is explicit.
- Both AZ columns use the same vertical tier order and dimensions.
- Service labels have dedicated header bands, so labels do not sit underneath
  icons or connectors.
- AWS-managed regional services are outside the VPC frame in a separate rail.
- External services are outside the AWS Cloud frame.
- The primary request path is solid blue; asynchronous, MCP, FCM, and
  observability paths use distinct colors and line styles.
- Account metadata and operational secrets are absent.

## Validation record

- Draw.io XML: well-formed, one editable page
- Vertex/edge structure: 87 vertices, 16 edges, zero broken endpoints
- Embedded official AWS SVG instances: 21
- PNG render: 1920 × 1080
- Visual QA: two render-and-review passes
- Corrected during QA:
  - moved the public ALB label away from both AZ headings
  - moved left subnet labels away from boundary resources
  - split Gabia DNS → Internet Gateway → Public ALB into explicit hops
  - corrected MCP and FCM arrows to show outbound traffic
  - removed a redundant SQL label that overlapped the RDS Multi-AZ title

## Reference guidance

- [AWS Well-Architected Framework — REL10-BP01](https://docs.aws.amazon.com/wellarchitected/latest/framework/rel_fault_isolation_multiaz_region_system.html)
- [AWS Well-Architected Framework — Reliability design principles](https://docs.aws.amazon.com/wellarchitected/latest/framework/rel-dp.html)
- [Elastic Load Balancing — How Elastic Load Balancing works](https://docs.aws.amazon.com/elasticloadbalancing/latest/userguide/how-elastic-load-balancing-works.html)
- [Application Load Balancers](https://docs.aws.amazon.com/elasticloadbalancing/latest/application/application-load-balancers.html)
- [AWS Well-Architected Framework definitions](https://docs.aws.amazon.com/wellarchitected/latest/framework/definitions.html)
