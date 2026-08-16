# Phase 17 Architecture Diagram Validation

검증 대상:

- 요구사항: `docs/architecture/phase-17-architecture-requirements.md`
- Draw.io: `docs/architecture/phase-17-aws-three-tier.drawio`
- Terraform: `infra/terraform/*.tf`, `infra/terraform/templates/*`
- AWS 아이콘: AWS Architecture Icons 2026-07-31 공식 SVG package

## 자동 구조 검증

| 항목 | 결과 |
|---|---:|
| Draw.io XML parse | PASS |
| 페이지 | 2 |
| 전체 `mxCell` | 187 |
| 연결선 | 50 |
| 존재하지 않는 source/target을 참조하는 연결선 | 0 |
| 공식 AWS SVG image cell | 40 |
| 외부 링크에 의존하는 AWS image cell | 0 |
| 요구 라벨 누락 | 0 |
| 12자리 AWS 계정 ID 포함 | 0 |
| private-key marker 포함 | 0 |

## 의미 검증

- PASS — AWS Cloud, Region, VPC, 두 AZ, 여덟 subnet 경계와 CIDR을 표시한다.
- PASS — Public ALB → WEB ASG → Internal ALB → WAS ASG → RDS endpoint의 3-Tier 흐름을 표시한다.
- PASS — 두 WAS instance는 RDS endpoint/active primary를 사용하며 standby에 직접 연결하지 않는다.
- PASS — WEB outbound tunnel → OpenAI → loopback MCP → internal ALB → WAS MCP adapter 흐름을 표시한다.
- PASS — public `/api/mcp = 403` 경계를 표시한다.
- PASS — WAS → EventBridge Scheduler → reminder SQS → consumer와 DLQ 흐름을 표시한다.
- PASS — Secrets Manager → WAS → Firebase Cloud Messaging → Android 흐름을 표시한다.
- PASS — Browser와 Android의 DNS/REST 진입 경로를 표시한다.
- PASS — artifact bucket과 ALB access-log bucket을 별도 역할로 명시한다.
- PASS — CloudWatch, SSM, IAM, ACM, KMS와 관리 관계를 표시한다.
- PASS — Gabia를 외부 DNS로 표시하고 Route 53을 추가하지 않는다.
- PASS — Terraform에 없는 CloudFront, WAF, Cognito, ECS/Fargate, Aurora를 추가하지 않는다.

## 편집 및 열기

Draw.io에서 XML 텍스트를 캔버스에 붙여넣지 않는다. `파일 → 다음에서 열기 → 기기`에서 `.drawio` 파일 자체를 선택하거나 파일을 캔버스로 드래그한다. 파일은 `compressed="false"` 형식이므로 열고 난 뒤 모든 도형, 라벨, 연결선을 편집할 수 있다.
