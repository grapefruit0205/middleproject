# Daylight

**4명이 함께 일정을 관리하고, 필요한 팀원에게 알림을 보내는 팀 캘린더를 만들고 있습니다.**

**Daylight는 하나의 서비스입니다.** HTML·CSS·JavaScript 캘린더 화면이 **Daylight API**를 호출하고, 기존에 구현한 일정·예약 알림 엔진이 PostgreSQL 저장과 비동기 알림을 처리합니다. 백엔드의 이전 이름은 Deadline Companion입니다.

[📖 아키텍처 쉽게 읽기](https://app.notion.com/p/3d7d9d5f6b3a81359f18c9aeec3376ed) · [🎤 15분 발표](https://app.notion.com/p/3d7d9d5f6b3a813090b5cbeb9af817b2) · [API 안내](docs/api/daylight.md)

> 현재 API 연결은 **로컬 단일 소유자용**입니다. 4인 팀 인증·공유 권한·팀원별 이메일 수신자는 다음 구현입니다. [Amplify 미리보기](https://main.d1za53r0rfy3x6.amplifyapp.com/)에는 최신 일간·주간·월간 UI를 별도로 배포하며, 일정은 각 브라우저에만 저장합니다. 아래 API 연결 작업이 배포된 사이트는 아닙니다.

## 1. 화면·데이터·알림은 이렇게 연결됩니다

| 구성 | Daylight에서 사용하는 방식 |
| --- | --- |
| 화면 | [daylight/](daylight) — HTML·CSS·JavaScript 캘린더 |
| API | [Daylight API](docs/api/daylight.md) — 기존 Spring/Tomcat 업무 처리 재사용 |
| 데이터 | [backend/](backend)를 통한 PostgreSQL 저장. 일정·알림의 기준은 서버 |
| 일정 관리 | 기존 API의 등록·조회·수정·취소·처리 이력을 화면에서 사용 |
| 예약 알림 | 기존 Outbox → Scheduler → SQS → Consumer → SES 엔진 사용 |
| 팀 확장 | 단일 소유자 계약에 팀·권한·수신자 모델 추가 예정 |

**화면을 React로 다시 만들 필요는 없습니다.** 화면 기술과 데이터 저장 방식은 독립적입니다. 기존 [frontend/](frontend)의 React 대시보드는 비교·호환용으로 남겨 두며, 별개의 제품으로 운영하려는 것이 아닙니다.

예를 들어 **내일 14시 마감 → 60분 전인 13시 알림**을 등록하면 화면은 API에 저장을 요청합니다. 서버는 일정과 예약할 일을 함께 기록하고, 이후 예약·발송 과정을 관리합니다.

## 2. 지금 어디까지 되나요?

기준: **2026-09-10**. 소스 구현, 로컬 확인, 공개 배포는 구분합니다.

| 기능 | 현재 상태 |
| --- | --- |
| 현재 날짜, 연·월·일 선택, 일간·주간·월간·검색 | 모바일 일간 카드·주간 목록·월간 날짜/일정 수, PC 주간 시간표 제공 |
| 서버 일정 등록·조회·수정·취소 | 기존 API 연결 구현 |
| 예약·발송 처리 이력 | 일정 상세와 전체 일정 메뉴에서 서버 이력 조회 |
| 자동 갱신·오류 처리 | 30초 갱신, 충돌 안내, 불명확한 저장 응답은 동일 요청 키로 재확인 |
| 브라우저 일정 저장 | 연결 화면에서는 사용하지 않음. 예전 로컬 데이터는 자동 이전·삭제하지 않음 |
| 종료 시각·카테고리·수신자 | 기존 API 계약에 없어 입력 비활성/제외. 카드 높이는 시각 표시용 |
| 4인 팀 인증·프로필 수정·공유 권한 | **미연결**. 팀원 선택을 인증으로 사용하지 않음 |
| 예약 생성·큐 소비·메일 요청 | 백엔드 코드 구현. 로컬 외부 연동은 비활성 |
| 실제 팀원 이메일 발송 | **미연결·미검증** |
| CloudWatch → SNS 운영자 통보 | 알람 정의는 있으나 **SNS 연결 미구현** |
| AWS 3-Tier 운영·장애 조치 검증 | 현재 서비스의 운영 검증은 남아 있음 |

API 장애 시 브라우저에 저장해 성공한 것처럼 표시하지 않습니다. 마지막 조회 내용과 연결 오류를 표시하며, 저장 결과가 불명확하면 같은 요청을 재확인하도록 안내합니다.

공개 Amplify 사이트는 **수동 정적 배포**입니다. GitHub 푸시로 갱신되지 않으며 이 API를 제공하지 않습니다. 실제 운영에는 인증된 API로 연결되는 WEB 배포가 필요합니다.

공개 UI 소스는 `daylight/preview/`이며 `daylight/styles.css`와 `daylight/script.js`를 함께 사용합니다. 이번에는 UI만 배포하고, 도메인 준비 후 기존 3-Tier Terraform 기반의 백엔드·인증 연결을 이어갑니다. 팀원 선택은 로그인 기능이 아니며 기기 간 공유·실제 이메일 발송은 없습니다.

## 3. 아키텍처는 두 가지 길로 이해합니다

아래는 **Daylight API와 Terraform이 정의하는 구조**입니다. Daylight 정적 사이트가 이 모든 서비스를 사용 중이라는 뜻은 아닙니다.

### 길 ① 지금 일정을 저장하는 길

```mermaid
flowchart TD
    U["사용자: 일정 등록"] -->|"HTTPS"| P["공개 ALB: WEB으로 분산"]
    P --> W["WEB · Apache<br/>화면 제공 / API 요청 전달"]
    W --> I["내부 ALB: WAS로 분산"]
    I --> A["WAS · Spring / Tomcat<br/>입력 확인 / 업무 처리"]
    A --> D[("RDS PostgreSQL<br/>일정 / 후속 작업 / 처리 이력")]
```

- **WEB은 전달합니다.** 화면 파일을 제공하고 업무 요청을 WAS로 넘깁니다.
- **WAS는 판단합니다.** 입력과 소유자를 확인하고 알림 시각을 계산합니다.
- **DB는 기억합니다.** 일정과 처리 상태를 저장합니다.

저장 결과는 WAS·WEB과 ALB를 거쳐 사용자에게 돌아옵니다. 사용자가 DB에 직접 접속하지 않습니다. 화면 파일 요청은 WEB에서 응답할 수 있어 모든 요청이 DB까지 내려가는 것도 아닙니다.

ALB는 요청을 나누는 장치입니다. ALB가 두 개라고 업무 계층이 5개가 되는 것은 아닙니다.

### 길 ② 나중에 알림을 처리하는 길

```mermaid
flowchart TD
    D[("DB: 일정과 예약할 일 함께 저장")] -->|"후속 코드가 조회"| W["WAS Worker"]
    W -->|"예약 API 호출"| S["EventBridge Scheduler<br/>언제 실행할지 관리"]
    S -->|"예약 시각에 메시지 전송"| Q["SQS<br/>처리할 일 보관"]
    Q -->|"WAS가 가져감"| C["WAS Consumer<br/>버전·중복·현재 상태 확인"]
    C -->|"이메일 발송 요청"| E["SES"]
    C -->|"결과 기록"| R[("DB: 발송 시도·처리 상태")]
    R --> V["조회 API를 통해 화면에서 확인"]
    Q -.->|"반복 소비 실패"| L["DLQ: 조사할 메시지 격리"]
```

| 이름 | 쉬운 설명 |
| --- | --- |
| Outbox | 일정과 함께 DB에 적어 두는 **후속 작업 목록** |
| Worker / Consumer | 목록·큐를 처리하는 **WAS 안의 코드**. 별도 서버가 아님 |
| Scheduler | 미래 시각에 작업을 실행하도록 예약 |
| SQS | 서버가 가져갈 작업을 보관하는 대기 줄 |
| SES | 이메일 발송을 요청하는 서비스 |
| DLQ | 반복 실패한 메시지를 분리해 조사하는 큐. 자동 수리 기능은 아님 |

일정 등록 응답은 미래의 메일 발송 완료를 기다리지 않습니다. 외부 연동과 서버가 정상 작동하는 조건에서 후속 처리가 진행됩니다.

**저장 완료 ≠ 예약 완료 ≠ 발송 요청 수락 ≠ 메일 수신·열람**입니다. 사용자는 서버에 기록된 상태를 조회하며, 실제 메일 도착·읽음까지 확인하는 연동은 없습니다.

SQS 메시지는 읽었다고 바로 삭제되지 않습니다. 처리 결과가 재시도 대상인지에 따라 삭제 여부를 정합니다. 외부 메일 발송과 DB 기록은 하나의 원자적 작업이 아니므로 모든 장애에서 정확히 한 번 발송을 보장하지는 않습니다.

## 4. 보안·장애·운영은 어떻게 받쳐 주나요?

| 관점 | 현재 코드가 준비한 구조 | 주의할 점 |
| --- | --- | --- |
| 통신 제한 | 공개 ALB → 사설 WEB·WAS → 격리 DB, 계층별 Security Group | Private 배치만으로 사용자 인증이 해결되지는 않음 |
| AWS 권한·비밀 | IAM 역할, Secrets Manager, DB 계정 분리 | 코드·Git에 실제 비밀값을 넣지 않음 |
| 고가용성 | 2개 AZ의 WEB·WAS, ALB·ASG, RDS Multi-AZ 설정 | 실제 전환 시간·남은 서버 용량은 시험 필요 |
| 실패 추적 | Outbox 기록, 큐·DLQ, 발송 시도 이력 | 불명확한 발송을 무조건 재시도하면 중복 위험 |
| 운영 관찰 | CloudWatch 로그·지표·알람 정의 | SNS 통보 연결과 수신 확인은 별도 |
| 재현성 | Terraform, Docker Compose, Flyway | 정적 검사 통과가 운영 배포 완료를 뜻하지 않음 |

현재 WAS는 API와 Worker를 포함한 **단일 Spring 애플리케이션**입니다. 독립 마이크로서비스나 전체 서버리스 구조는 아닙니다. 외부 HTTPS와 달리 내부 WEB–WAS 구간은 현재 HTTP이며, ASG의 기본 수량 유지와 부하 기반 자동 증설 정책도 구분해야 합니다.

자세한 포트·서브넷·장애 경로는 [아키텍처 안내](docs/architecture/middleproject-aws-3tier-architecture.md)와 [다이어그램 원본](docs/architecture/middleproject-aws-3tier-architecture.drawio)에 있습니다.

## 5. 내 PC에서 실행하기

### 서버에 연결된 Daylight 실행

필요한 것: Docker Engine, Docker Compose v2.

1. `.env`가 없을 때만 `.env.example`을 복사합니다. 기존 파일은 덮어쓰지 마세요.
2. `LOCAL_DB_ADMIN_PASSWORD`, `LOCAL_DB_MIGRATION_PASSWORD`, `LOCAL_DB_APP_PASSWORD`에 서로 다른 암호를 설정합니다. 기존 DB를 사용한다면 `LOCAL_OWNER_ID`를 유지합니다.
3. 저장소 루트에서 실행합니다.

```bash
docker compose up -d --build
docker compose ps
curl --fail http://127.0.0.1:8088/healthz
```

- **[Daylight 열기](http://127.0.0.1:8088/daylight/)** — 새 캘린더 + Daylight API + PostgreSQL
- [기존 React 대시보드](http://127.0.0.1:8088/) — 같은 API·DB를 사용하는 기존 화면

Apache는 `/daylight/`의 정적 파일을 제공하고, 같은 출처의 `/api/` 요청을 WAS로 전달합니다. API 리소스 주소 `/api/deadlines`는 호환성을 위해 유지합니다. API의 애플리케이션 이름은 `daylight-api`입니다.

단순 Python 정적 서버나 Amplify 정적 호스팅만으로는 API가 연결되지 않습니다. 브라우저에서 API를 찾지 못하면 오류를 표시하며 로컬 저장으로 전환하지 않습니다.

로컬은 접속 코드 없이 사용하며 AWS 예약·큐·메일은 기본 비활성입니다. **무인증 로컬 API를 외부 공개하거나 터널링하지 마세요.**

종료는 `docker compose down`입니다. DB 볼륨은 유지됩니다. `-v`를 추가하면 데이터가 삭제되므로 일반 종료에는 사용하지 마세요. 자세한 설정은 [실행·배포 런북](docs/runbooks/service-mvp.md)을 확인합니다.

## 6. 발표·학습 자료

README는 전체 흐름을 파악하는 입구입니다. 세부 설명은 아래 문서에서 이어집니다.

- [노션 전체 목차](https://app.notion.com/p/3d7d9d5f6b3a81359f18c9aeec3376ed): Phase 1~9 아래에 쉬운 설명 13개와 발표 대본 2개
- [Phase 9 · 15분 발표](https://app.notion.com/p/3d7d9d5f6b3a813090b5cbeb9af817b2): 시간 배분, 전반부·후반부 대본, 그림을 가리킬 순서
- [아키텍처 아틀라스 HTML](docs/architecture/architecture-atlas.html): 전체 구조와 구간별 설명
- [구현 사실·제약](memory/PRODUCT-TRUTH.md): 소스 구현과 운영 검증의 구분
- [기존 서비스 진행 기록](progress.json): 백엔드 구현 트랙. Daylight 팀 연동 완료표는 아님

15분 발표는 **목적 → 등록 → 예약·발송 → 보안 → 장애 대응 → 현재 한계** 순서로 구성합니다. 설정값을 전부 읽기보다 화살표마다 “무엇을 넘기고, 왜 다음 단계가 필요한지” 설명합니다.

## 7. 팀원이 코드를 찾는 곳

```text
middleproject/
├── daylight/               Daylight HTML·CSS·JavaScript 화면
├── frontend/               기존 React 대시보드
├── backend/                Spring 업무·예약·발송 코드와 DB 마이그레이션
├── infra/local/            Apache·Tomcat·PostgreSQL 로컬 구성
├── infra/terraform/        AWS 3-Tier 인프라 정의
├── scripts/                Daylight 수동 배포 도구
├── docs/architecture/      아키텍처 그림·설명
└── docs/runbooks/           실행·배포·복구 절차
```

현재 작업 브랜치는 `codex/service-mvp-pastel-dashboard`입니다. 기본 `main`에 최신 변경이 모두 병합된 상태는 아닙니다. 팀원은 작업 브랜치를 확인하고 각자 브랜치에서 변경한 뒤 PR로 공유하세요.

## 8. 다음에 연결할 것

1. **팀 인증·권한:** 화면의 팀원 선택과 실제 로그인·멤버십을 분리
2. **공유 저장:** 팀·멤버십·알림 수신자를 기존 서버 DB·API 계약에 추가
3. **이메일 알림:** 등록 알림과 사전 예약 알림을 구분하고, 수신자별 비동기 처리·결과 기록 연결
4. **운영 확인:** AWS 사용자 흐름·실메일 수신·실패 대응·운영자 통보 검증

Bedrock·SageMaker 같은 AI 기능은 위의 핵심 흐름을 대신하지 않으며 현재 연결되어 있지 않습니다.

<details>
<summary>개발자용 검사 명령</summary>

변경할 때마다 아래 전체 검사를 반복할 필요는 없습니다. 변경 범위에 맞는 검사를 선택합니다.

```bash
# 기존 React 프론트엔드
cd frontend
npm ci --include=dev
npm run typecheck
npm test
npm run build
```

```bash
# 저장소 루트에서 시작, JDK 21 필요
cd backend
./gradlew test bootWar --no-daemon
```

```bash
# 저장소 루트에서 Terraform 정적 검사
terraform -chdir=infra/terraform fmt -check
terraform -chdir=infra/terraform init -backend=false
terraform -chdir=infra/terraform validate
```

실제 AWS 생성·변경, DNS 변경, 외부 메일 발송은 정적 검사와 다른 작업입니다. 대상·권한·비용과 실행 범위를 확인한 뒤 진행합니다.

</details>
