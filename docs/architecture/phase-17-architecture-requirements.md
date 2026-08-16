# Phase 17 AWS Architecture Diagram Requirements

## 1. 목적

이 문서는 `infra/terraform`을 단일 사실 원천으로 삼아 Phase 17 Trip Copilot 배포 아키텍처를 Draw.io로 표현하기 위한 요구사항 계약이다. 다이어그램 생성 전에 구조와 흐름을 먼저 명시하고, 생성 후 같은 문서로 누락·오연결·잘못된 가정을 검증한다.

참고한 작성 방식: [AI-Assisted AWS Architecture Diagram Generation](https://medium.com/@monali16296/ai-assisted-aws-architecture-diagram-generation-fea0a8f4cfd9)

## 2. 산출물

- 편집 가능한 원본: `docs/architecture/phase-17-aws-three-tier.drawio`
- Draw.io 파일은 유효한 비압축 `mxfile` XML이어야 한다.
- 모든 AWS 리소스는 AWS가 배포한 2026-07-31 공식 Architecture/Resource SVG 아이콘을 파일 내부에 포함한다.
- 외부 시스템은 중립적인 박스로 표시하고 AWS 리소스처럼 오인시키지 않는다.
- 첫 페이지는 런타임 3-Tier 및 네트워크 배치를, 둘째 페이지는 MCP·알림·운영 흐름을 설명한다.

## 3. 범위와 경계

### 외부 시스템

- Browser/PWA 및 Android 앱
- Gabia DNS: `trip.tripjunseok.site`
- ChatGPT/OpenAI Secure MCP Tunnel
- Google Firebase Cloud Messaging
- Public Internet

### AWS 범위

- 계정 번호나 비밀값은 표기하지 않는다.
- Region: `ap-northeast-2` (Seoul)
- VPC: `10.20.0.0/16`
- Availability Zones: `ap-northeast-2a`, `ap-northeast-2c`
- AWS Cloud, Region, VPC, AZ, subnet 경계를 중첩 구조로 명확히 구분한다.

## 4. 네트워크와 3-Tier 배치

| 계층 | AZ A | AZ C | 인터넷 경로 |
|---|---|---|---|
| Public | `10.20.0.0/24` | `10.20.1.0/24` | Internet Gateway, Regional NAT Gateway, public ALB |
| WEB private | `10.20.10.0/24` | `10.20.11.0/24` | Regional NAT Gateway를 통한 outbound only |
| WAS private | `10.20.20.0/24` | `10.20.21.0/24` | Regional NAT Gateway를 통한 outbound only |
| DB isolated | `10.20.30.0/24` | `10.20.31.0/24` | local route only, 인터넷/NAT route 없음 |

- Public Application Load Balancer는 양쪽 public subnet에 걸쳐 있고 ACM 인증서로 HTTPS `:443`을 종료한다.
- WEB Auto Scaling Group은 Apache EC2 두 대를 양쪽 WEB subnet에 한 대씩 둔다. min/desired/max는 `2/2/2`다.
- WEB은 정적 PWA를 제공하고 `/api/*`를 internal ALB로 reverse proxy한다.
- Internal Application Load Balancer는 양쪽 WAS subnet에 걸쳐 있고 WAS target `:8080`으로 전달한다.
- WAS Auto Scaling Group은 Spring Boot/Tomcat EC2 두 대를 양쪽 WAS subnet에 한 대씩 둔다. min/desired/max는 `2/2/2`다.
- Amazon RDS for PostgreSQL 16은 암호화된 gp3 Multi-AZ DB다. 애플리케이션은 RDS endpoint를 통해 active primary에 접속하며 standby에 직접 접속하지 않는다.

## 5. 필수 데이터 흐름

### 5.1 Web/Android REST

1. Browser 또는 Android → Gabia DNS
2. Gabia CNAME → public ALB
3. public ALB HTTPS `:443` → WEB Apache `:80`
4. WEB `/api/*` → internal ALB `:80`
5. internal ALB → Spring Boot/Tomcat `:8080`
6. WAS → RDS endpoint/PostgreSQL `:5432`

### 5.2 Private MCP

1. WEB EC2의 `tunnel-client`가 OpenAI로 outbound TLS 연결을 수립한다.
2. ChatGPT의 tool call은 이미 수립된 Secure MCP Tunnel을 통해 WEB으로 전달된다.
3. `tunnel-client`는 `127.0.0.1:8090/api/mcp` loopback Apache listener로 요청한다.
4. Apache → internal ALB → WAS MCP adapter로 전달한다.
5. public virtual host의 `/api/mcp`는 `403`이며 인터넷에 MCP listener를 공개하지 않는다.

### 5.3 일정과 알림

1. WAS는 확정된 출장 계획, 알림 정책, Android device token을 PostgreSQL에 저장한다.
2. WAS는 project-scoped EventBridge Scheduler group에 schedule을 생성·수정·삭제한다.
3. EventBridge Scheduler → encrypted reminder SQS queue
4. WAS consumer가 long polling 후 알림을 idempotent하게 처리하고 상태를 DB에 기록한다.
5. 다섯 번 처리하지 못한 메시지는 encrypted DLQ로 이동한다.
6. WAS는 Firebase Admin credential을 Secrets Manager에서 runtime에 읽어 FCM HTTPS 요청을 보낸다.
7. Firebase Cloud Messaging → Android push notification

## 6. Regional AWS 서비스

- Amazon S3 bucket 2개
  - artifact bucket: `frontend.zip`, `ROOT.war`
  - ALB access-log bucket
- EventBridge Scheduler schedule group
- Amazon SQS reminder queue 및 DLQ (`maxReceiveCount = 5`)
- AWS Secrets Manager
  - RDS managed DB secret
  - Firebase Admin JSON secret
  - OpenAI tunnel runtime API key secret
- AWS Certificate Manager: `trip.tripjunseok.site`
- Amazon CloudWatch: log group 5개, alarm 10개
- AWS Systems Manager Session Manager: SSH 없이 관리
- AWS IAM: WEB/WAS/Scheduler 최소 범위 역할
- AWS KMS 및 서비스별 암호화-at-rest

## 7. 보안 관계

- Internet → public ALB `:443`
- public ALB security group → WEB `:80`
- WEB security group → internal ALB `:80`
- internal ALB security group → WAS `:8080`
- WAS security group → RDS `:5432`
- 인바운드 SSH 없음
- DB subnet에는 인터넷 route 없음
- Firebase service-account JSON, OpenAI API key, DB password를 다이어그램·Git·tfvars·user data에 넣지 않는다.
- Secret 접근은 정확한 ARN과 tier별 IAM role 관계로 표현한다.

## 8. 관측성과 운영

- CloudWatch Agent가 WEB/WAS host 및 애플리케이션 로그/메트릭을 전송한다.
- 로그 범주: Apache access/error, Tomcat access, JSON application, SSM session.
- 알람 범주: ALB unhealthy/5xx, Scheduler error/drop, SQS age/visible/DLQ, terminal delivery failure.
- S3 artifact를 WEB/WAS가 부팅 시 읽는 관계와 ALB access log가 별도 S3 bucket으로 기록되는 관계를 구분한다.
- Session Manager와 WEB/WAS managed-node 관계를 표현한다.

## 9. 명시적 비범위

현재 Terraform에 없으므로 다음 리소스를 다이어그램에 추가하지 않는다.

- Amazon Route 53 (DNS는 Gabia)
- Amazon CloudFront, AWS WAF
- Amazon Cognito 또는 별도 OIDC 로그인
- Amazon ECS/Fargate, EKS, Lambda
- Amazon Aurora, DynamoDB
- OpenTelemetry collector

## 10. Draw.io 배치 규칙

- 요청 흐름은 가능한 한 왼쪽에서 오른쪽, 위에서 아래로 읽힌다.
- AWS Cloud → Region → VPC → AZ → subnet 계층을 시각적으로 유지한다.
- Public, WEB private, WAS private, DB isolated 계층은 색과 라벨로 구분한다.
- 주 흐름은 실선, 제어·관리·관측·secret 관계는 점선으로 구분한다.
- 모든 화살표에 프로토콜, 포트 또는 목적을 의미 있게 표시한다.
- 선 교차와 긴 대각선을 줄이고 관련 컴포넌트끼리 정렬한다.
- 세부 운영 흐름이 런타임 네트워크를 가리지 않도록 두 페이지로 분리한다.

## 11. 생성 후 검증 체크리스트

- [ ] Terraform의 필수 AWS 서비스가 모두 있다.
- [ ] 존재하지 않는 AWS 서비스나 추정 리소스가 없다.
- [ ] VPC/AZ/subnet 경계와 CIDR이 정확하다.
- [ ] public/private/isolated 배치가 정확하다.
- [ ] RDS standby에 애플리케이션이 직접 연결되지 않는다.
- [ ] Web/Android REST, private MCP, Scheduler/SQS/DLQ, FCM 흐름이 모두 있다.
- [ ] public `/api/mcp = 403` 경계가 명확하다.
- [ ] S3 두 bucket의 역할이 구분된다.
- [ ] IAM, Secrets Manager, KMS, SSM, CloudWatch 관계가 모호하지 않다.
- [ ] 모든 AWS 이미지가 공식 SVG로 내장되어 오프라인에서도 보인다.
- [ ] Draw.io XML의 모든 edge source/target이 존재한다.
- [ ] 계정 ID, API key, private key, secret value가 없다.
