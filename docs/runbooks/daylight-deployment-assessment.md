# Daylight 기능 포함 배포 · 비용과 선행 조건

확인일: 2026-09-10. 아직 배포 완료 문서가 아닙니다.

## 사용자 요청과 현재 상태

- `confirmed`: 모바일 일간·주간·월간 개선과 Amplify 기능 포함 배포 요청. 인증된 백엔드 연결을 선택했으며 4명용 저비용, 저장소의 Terraform 재사용을 요구했습니다.
- `observed`: 연결된 `architecture-hosting` 계정의 서울 리전에서 Daylight Amplify 앱은 정적 WEB입니다. ALB/RDS 인스턴스 목록은 비어 있습니다. 조회된 Cognito 풀은 다른 프로젝트용이라 변경하지 않았습니다.
- `observed`: `infra/terraform/`는 public ALB → private WEB → internal ALB → private WAS → RDS 구조, 아티팩트 배포, Scheduler/SQS/SES 연결을 이미 정의합니다. 새로운 도구로 대체할 필요는 없습니다.

## 기존 Terraform을 수량만 축소했을 때

`environment=development`, `web_capacity={min=1,desired=1,max=1}`, `was_capacity={min=1,desired=1,max=1}`, `rds_multi_az=false`는 기존 변수로 가능한 구성입니다. 고가용성은 포기하지만 ALB 2개와 NAT 1개는 그대로 남습니다.

AWS Price List API를 2026-09-10에 조회한 서울 On-Demand 단가, 월 730시간을 가정했습니다. 무료 크레딧·예약 할인·세금·환율은 적용하지 않았습니다.

| 리소스 | 시간당 USD | 수량 | 730시간 비용 USD |
| --- | ---: | ---: | ---: |
| Linux t3.small | 0.026 | 2 | 37.96 |
| PostgreSQL db.t4g.micro Single-AZ | 0.025 | 1 | 18.25 |
| Application Load Balancer | 0.0225 | 2 | 32.85 |
| NAT Gateway | 0.059 | 1 | 43.07 |
| **위 항목만 합계** | | | **132.13** |

이 금액은 전체 견적이 아닙니다. EC2/RDS 스토리지, IPv4, ALB LCU, NAT 데이터 처리, 전송, 로그, 비밀 저장, 백업, Amplify, 메일, 인증 등이 별도입니다. 이용자가 4명이어도 시간 요금은 계속 발생합니다.

요금 구조 참고: [VPC/NAT/IPv4](https://aws.amazon.com/vpc/pricing/) · [ALB](https://aws.amazon.com/elasticloadbalancing/pricing/). 서울 단가는 CLI `pricing get-products`의 `regionCode=ap-northeast-2`와 각각의 인스턴스 유형/사용 유형으로 확인했습니다.

## 비용 우선 대안 — 사용자 요청으로 제외 (D-022)

기존 3-Tier Terraform은 교육·발표/HA 기준으로 보존하고, 별도의 4인 실사용 Terraform 프로필에서 WEB·WAS·PostgreSQL을 소형 서버에 함께 배치하는 방안입니다. 기존 앱과 컨테이너 구성을 재사용할 수 있지만 장애 격리·DB 관리·백업 책임이 달라지고 물리적 3-Tier 이중화가 아닙니다. 아직 이 구조를 선택하거나 만들지 않았습니다.

상시 알림 처리를 원하므로 서버를 단순 절전하는 방법은 제시간 알림과 양립하지 않습니다. 구체적인 대안 비용은 리소스를 정한 뒤 별도 산정해야 합니다.

## 배포 전에 정할 것

1. **기존 물리적 3-Tier 유지로 확정(D-022).** 단일 서버 대안은 적용하지 않습니다.
2. 4명 개별 로그인·팀 멤버십과 초대 방법. 현재 API는 단일 소유자 계약이며 공용 토큰을 프론트엔드에 포함하지 않습니다.
3. HTTPS API 주소와 인증 리다이렉트/동일 출처 경로. 현재 Terraform은 ACM 인증서 ARN을 요구합니다.
4. 실제 SES 발신·수신 대상과 검증. 이메일을 임의의 주소로 발송하지 않습니다.
5. 선택한 구성의 Terraform plan, 비용 확인 후 apply와 기능 확인

현재 Amplify의 정적 배포를 연결형 화면으로 덮어쓰지 않았고 AWS 리소스 생성·권한 변경·실메일 발송도 하지 않았습니다.

직접 구매한 도메인이 필수인 것은 아닙니다. CloudFront의 기본 `cloudfront.net` 주소에는 AWS가 제공하는 HTTPS 인증서를 사용할 수 있습니다. 다만 브라우저→CloudFront HTTPS와 CloudFront→ALB 구간은 별도입니다. 도메인 없이 운영하려면 CloudFront와 보호된 origin 연결을 기존 Terraform에 추가해야 하며, 현재 ALB HTTPS 설정을 그대로 적용할 수 있다는 뜻은 아닙니다. [AWS 기본 주소 HTTPS](https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/using-https-viewers-to-cloudfront.html) · [ALB 접근 제한/VPC origins](https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/restrict-access-to-load-balancer.html)
