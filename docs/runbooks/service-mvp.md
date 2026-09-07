# Service MVP 실행·배포 Runbook

이 문서는 일정·마감 알림 서비스의 재현 가능한 실행 경로다. 비밀값, Terraform state, plan 파일은 Git에 넣지 않는다. AWS 생성·변경과 실제 이메일 발송은 별도 승인 후에만 수행한다.

## 로컬 WEB → WAS → PostgreSQL 실행

런타임 계층은 다음처럼 분리된다.

```text
Browser → 127.0.0.1:8088 Apache WEB → private compose network → external Tomcat WAS
                                                        └→ internal compose network → PostgreSQL 16
```

WAS와 PostgreSQL 포트는 호스트에 공개하지 않는다. PostgreSQL에는 관리자, Flyway, 애플리케이션 역할을 별도로 만들며 Tomcat 애플리케이션은 스키마 생성 권한이 없다.

사전 조건은 Docker Engine과 Docker Compose v2다. `docker compose version`이 동작하지 않으면 운영체제에 맞는 공식 Compose plugin을 먼저 설치한다.

1. 로컬 비밀을 준비한다. `.env`는 Git에서 제외된다.

   ```bash
   cp .env.example .env
   openssl rand -hex 24
   ```

   생성한 서로 다른 임의값으로 `.env`의 세 DB 암호와 `LOCAL_OWNER_TOKEN`을 채운다. 토큰은 최소 24바이트여야 한다.

2. 처음 한 번 빌드하고 실행한다.

   ```bash
   docker compose build
   docker compose up -d
   docker compose ps
   curl --fail http://127.0.0.1:8088/healthz
   ```

3. 브라우저에서 `http://127.0.0.1:8088`을 열고 `.env`의 `LOCAL_OWNER_TOKEN`을 입력한다. 토큰은 React 메모리에만 있고 새로고침하면 다시 입력해야 한다. 번들·localStorage·PostgreSQL에는 저장하지 않는다.

4. 로그와 종료:

   ```bash
   docker compose logs --tail=200 web was db
   docker compose down
   ```

   `docker compose down`은 DB volume을 보존한다. `docker compose down -v`는 로컬 일정 데이터를 삭제하므로 데이터 폐기를 명시적으로 원할 때만 사용한다.

## 설정 경계

- `APP_SECURITY_ENABLED=true`일 때 `APP_OWNER_ID`와 최소 24바이트 `APP_OWNER_TOKEN`이 없으면 WAS가 시작을 거부한다.
- `/api/**`는 Bearer 인증 대상이며 `/actuator/**`만 로드밸런서 상태 확인을 위해 인증 없이 허용한다.
- 일정 소유자는 요청 헤더나 body가 아니라 인증 principal에서 결정한다.
- `SPRING_FLYWAY_ENABLED=false`인 AWS WAS는 migration 비밀을 읽지 않는다.
- Scheduler/SQS/SES 설정이 꺼져 있으면 화면에 외부 알림 비활성 상태가 표시된다.

## AWS 배포 입력

Terraform 전에 같은 서울 리전에 다음 세 Secrets Manager secret을 운영자가 별도로 만든다. 각 DB 사용자명은 서로 달라야 한다.

- `db_runtime_secret_arn`

  ```json
  {"dbUsername":"reminder_app","dbPassword":"<random>"}
  ```

- `db_migration_secret_arn`

  ```json
  {"dbUsername":"reminder_migrator","dbPassword":"<different-random>"}
  ```

- `owner_auth_secret_arn`

  ```json
  {"ownerId":"owner","ownerToken":"<at-least-24-random-bytes>"}
  ```

RDS 관리자는 Terraform의 `manage_master_user_password`가 생성한 별도 secret을 사용한다. 권한 경계는 다음과 같다.

| 실행 주체 | 읽을 수 있는 DB 비밀 | 용도 |
|---|---|---|
| WAS instance role | runtime DB + owner auth | CRUD와 소유자 인증 |
| 일회성 migration role | RDS master + migration DB + runtime DB | 역할 생성, Flyway, runtime grant |
| WEB instance role | 없음 | 정적 파일과 reverse proxy |

WAS는 RDS master·migration secret을 읽을 수 없다. 일회성 migration role도 owner 인증 secret을 읽지 못한다.

## Terraform과 일회성 Flyway 순서

1. 프런트 ZIP과 `ROOT.war`를 만들고 SHA-256을 고정한다.
2. `service-mvp.tfvars.example`을 Git 밖으로 복사해 실제 ARN·artifact 경로를 넣는다.
3. 승인 전에는 `fmt`, `init -backend=false`, `validate`, `plan`까지만 수행한다.
4. 승인된 plan을 적용하면 WEB/WAS/RDS와 `db_migration` launch template가 만들어진다. 공개 ALB는 `/api/mcp`와 하위 경로를 404로 차단한다.
5. 서비스 공개 전, Terraform output의 migration launch template를 이용해 WAS subnet 중 하나에 일회성 instance를 한 대 실행한다.

   ```bash
   aws ec2 run-instances \
     --launch-template LaunchTemplateId="$(terraform -chdir=infra/terraform output -raw db_migration_launch_template_id)" \
     --subnet-id "<approved-was-subnet-id>" \
     --tag-specifications 'ResourceType=instance,Tags=[{Key=Purpose,Value=ephemeral-database-migration}]'
   ```

   cloud-init은 세 DB 역할을 분리하고 `ROOT.war`의 Flyway V1 이후 migration을 적용한 뒤 `/var/log/middleproject-migration-success`를 만들고 instance를 중지한다. SSM으로 성공 marker와 Flyway 로그를 확인한 다음 그 **정확한 instance ID만** 종료한다. 실패 시 instance를 유지해 로그를 확인하며, 성공으로 가장하지 않는다.

6. WAS readiness와 인증된 CRUD를 확인한다. 실메일을 켜기 전에는 `notification_email_enabled=false`를 유지한다.

## HA와 비용 축소

기본값은 WEB 2대, WAS 2대, RDS Multi-AZ다. `web_capacity`, `was_capacity`, `rds_multi_az`를 1/1/1·false로 낮춘 구성은 짧은 실습 비용을 줄일 뿐 HA 증거가 아니다. 실제 apply, DNS, 이메일 발송, teardown은 승인된 대상·비용 상한·종료 시각이 있어야 한다.

## 최종 로컬 확인 기록

2026-09-07에 격리된 로컬 구성으로 다음을 확인했다.

- React 테스트 11개와 production/PWA 빌드, production dependency audit가 통과했다.
- Java 21 전체 backend 테스트 106개는 실패·오류 0개였다. 환경변수 의존 PostgreSQL 테스트 9개는 해당 실행에서 skip됐고, 아래 실제 PostgreSQL 16 기동 시나리오로 DB 경로를 별도 확인했다.
- Apache WEB, 외장 Tomcat WAS, PostgreSQL 16을 함께 빌드·기동했다. WEB만 loopback에 공개되고 보호 API 미인증 요청은 `401`이었다.
- 동일 생성 요청 재전송은 업무 행 하나를 유지했고, 정상 수정은 `200`, 오래된 버전 수정은 `409`, 취소는 `CANCELLED`였다. WAS 재시작 후에도 PostgreSQL에서 취소 상태를 조회했다.
- 브라우저에서 인증 → 등록 → 새로고침·재인증 → 수정 → 이력 → 취소 → 최신 이력 확인 흐름을 완료했다. 외부 연동이 꺼진 상태는 발송 성공이 아니라 `외부 알림 비활성화`로 표시됐다.

AWS apply·HTTPS/DNS 변경과 실제 SES 메일 발송은 수행하지 않았다. 이는 소스 결함이 아니라 비용·외부 전송이 수반되는 별도 승인 항목이며 `progress.json`의 S05 차단 항목에서 이어간다.
