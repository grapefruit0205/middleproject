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

   생성한 서로 다른 임의값으로 `.env`의 세 DB 암호를 채운다. `LOCAL_OWNER_ID=local-owner`가 기본 소유자다. 기존 DB volume을 사용한다면 예전 `LOCAL_OWNER_ID`를 그대로 유지한다. 로컬에서는 `LOCAL_OWNER_TOKEN`을 더 이상 요구하지 않으며 기존 `.env`에 남아 있어도 사용하지 않는다.

2. 처음 한 번 빌드하고 실행한다.

   ```bash
   docker compose build
   docker compose up -d
   docker compose ps
   curl --fail http://127.0.0.1:8088/healthz
   ```

3. 브라우저에서 `http://127.0.0.1:8088`을 열면 접속 코드 입력 없이 일정을 사용한다. 로컬 WAS는 `APP_SECURITY_ENABLED=false`, `APP_DEMO_OWNER_ID=${LOCAL_OWNER_ID}`로 실행한다. 토큰을 프런트엔드 번들이나 localStorage에 삽입하지 않는다. 이전 인증형 로컬 구성을 사용했다면 `docker compose up -d --build web was`로 새 설정을 반영한다. DB volume을 삭제할 필요는 없다.

4. 로그와 종료:

   ```bash
   docker compose logs --tail=200 web was db
   docker compose down
   ```

   `docker compose down`은 DB volume을 보존한다. `docker compose down -v`는 로컬 일정 데이터를 삭제하므로 데이터 폐기를 명시적으로 원할 때만 사용한다.

## 설정 경계

- **로컬 전용:** WEB 포트는 계속 `127.0.0.1`에만 공개한다. Apache는 `localhost` 또는 `127.0.0.1` Host만 허용하며 `/api/` 요청에 다른 Origin 또는 `cross-site`·`same-site` Fetch Metadata가 있으면 거부한다. 따라서 브라우저의 다른 사이트에서 로컬 무인증 API를 호출하는 것을 제한한다. Origin/Fetch Metadata가 없는 로컬 CLI 요청은 허용한다. 이 경계는 동일 PC의 다른 프로세스·사용자를 인증하는 수단이 아니므로 신뢰하는 개인 PC에서만 사용하고, 포트를 외부에 공개하거나 터널링하지 않는다.
- **AWS/공개 환경:** 기존 `application-aws.yml`의 인증 활성 기본값을 유지하며 `APP_SECURITY_ENABLED=false`로 덮어쓰지 않는다. 인증 활성 환경에서는 `APP_OWNER_ID`와 최소 24바이트 `APP_OWNER_TOKEN`이 없으면 WAS가 시작을 거부한다. 로컬의 인증 비활성 구성을 AWS로 옮기지 않는다.
- 인증 활성 환경의 `/api/**`는 Bearer 인증 대상이며 `/actuator/**`만 로드밸런서 상태 확인을 위해 인증 없이 허용한다.
- 일정 소유자는 요청 헤더나 body가 아니라 인증 principal에서 결정한다. 인증이 없는 로컬 구성에서만 서버의 `APP_DEMO_OWNER_ID`를 사용한다.
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

2026-09-07에 **접속 코드 인증을 사용하던 당시의** 격리된 로컬 구성으로 다음을 확인했다. 아래 인증 관련 기록은 과거 검증이며, 현재 로컬 실행법은 위의 코드 없는 구성을 따른다.

- React 테스트 11개와 production/PWA 빌드, production dependency audit가 통과했다.
- Java 21 전체 backend 테스트 106개는 실패·오류 0개였다. 환경변수 의존 PostgreSQL 테스트 9개는 해당 실행에서 skip됐고, 아래 실제 PostgreSQL 16 기동 시나리오로 DB 경로를 별도 확인했다.
- Apache WEB, 외장 Tomcat WAS, PostgreSQL 16을 함께 빌드·기동했다. WEB만 loopback에 공개되고 보호 API 미인증 요청은 `401`이었다.
- 동일 생성 요청 재전송은 업무 행 하나를 유지했고, 정상 수정은 `200`, 오래된 버전 수정은 `409`, 취소는 `CANCELLED`였다. WAS 재시작 후에도 PostgreSQL에서 취소 상태를 조회했다.
- 브라우저에서 인증 → 등록 → 새로고침·재인증 → 수정 → 이력 → 취소 → 최신 이력 확인 흐름을 완료했다. 외부 연동이 꺼진 상태는 발송 성공이 아니라 `외부 알림 비활성화`로 표시됐다.

AWS apply·HTTPS/DNS 변경과 실제 SES 메일 발송은 수행하지 않았다. 이는 소스 결함이 아니라 비용·외부 전송이 수반되는 별도 승인 항목이며 `progress.json`의 S05 차단 항목에서 이어간다.

### 2026-09-08 로컬 무로그인·자동 갱신 확인

- React/Vitest 23개 테스트와 TypeScript 검사, production/PWA build가 통과했다. 자동 갱신은 표시 중인 탭에서 30초마다 실행하고 탭·네트워크 복귀 시 즉시 확인하며, 중복 요청·저장 충돌·작성 중 입력·오래된 결과 표시를 테스트했다.
- 기존 PostgreSQL volume과 `LOCAL_OWNER_ID`를 유지한 채 WEB/WAS만 재생성했다. 브라우저는 접속 코드 없이 기존 일정 두 건을 조회했고 마지막 확인 시각과 수동 새로고침을 표시했다.
- Apache `httpd -t`는 `Syntax OK`였다. 인증 헤더 없는 로컬 API와 same-origin 브라우저 요청은 `200`, 잘못된 Host·외부 Origin·다른 localhost 포트·`cross-site` 요청은 각각 `403`이었다. 응답 내용이나 비밀값은 검사 출력에 포함하지 않았다.
- 이 검증은 loopback 개인 PC 범위다. AWS/공개 환경의 Bearer 인증을 해제하거나 OAuth를 구현한 증거가 아니다.
