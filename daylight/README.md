# Daylight · Daylight API에 연결된 캘린더

HTML·CSS·JavaScript 화면은 그대로 사용하고 일정·예약·발송 데이터는 기존 Spring/Tomcat 백엔드와 PostgreSQL을 사용합니다. API 이름은 **Daylight API**입니다.

## 실행

저장소 루트에서 Docker Compose를 실행한 뒤 [로컬 Daylight](http://127.0.0.1:8088/daylight/)를 엽니다. WEB 이미지가 이 디렉터리를 제공하며, 같은 출처의 `/api/deadlines`가 WAS로 전달됩니다. [전체 실행 안내](../README.md) · [API 계약](../docs/api/daylight.md)

이 API 연결 화면은 단순 정적 서버만으로 서버 저장이 불가능합니다. Amplify에는 `preview/`의 브라우저 저장 방식 미리보기를 별도로 배포합니다. 최신 일간·주간·월간 UI를 제공하지만 API·팀 공유·실제 메일 발송은 미연결입니다. 공개 무인증 API나 브라우저에 심은 공용 Bearer 토큰으로 연결하지 마세요.

## 연결한 기능

- 서버 일정 목록, 일간·주간·월간 보기·검색·날짜 이동
- 기존 Daylight 디자인 유지: 일간 1일 시간표, 주간 7일 시간표, 월간 날짜 칸 안의 파스텔 일정 카드. 모바일 주간은 가로 스크롤, 일간·월간은 화면 너비에 맞춤
- 제목·시각·사전 알림 분 등록·수정, 버전 확인 후 취소
- 과거·취소 일정을 포함하는 전체 목록과 서버 처리 이력
- 30초 갱신, 오류 표시, 저장 응답 불명확 시 같은 요청 키로 재확인

일정과 알림을 localStorage로 대체하지 않습니다. 이전 브라우저 데이터는 자동 업로드하거나 삭제하지 않습니다. 메뉴 등 화면 선호 설정만 로컬에 남을 수 있습니다.

## 아직 구분할 것

현재 API는 단일 소유자용입니다. 팀원 정보·권한·수신자는 다음 확장이고, 미연결 프로필 컨트롤은 비활성입니다. 종료 시각·카테고리도 기존 API가 지원하지 않아 저장하지 않습니다. 카드 높이는 표시용입니다.

로컬의 Scheduler·SQS·SES 외부 연동은 비활성입니다. 발송 요청 수락과 실제 수신·열람을 구분하며, 저장을 발송 성공으로 표시하지 않습니다.

## 배포

`infra/local/web.Dockerfile`에 Daylight가 포함됩니다. 기존 정적 전용 Amplify 배포 스크립트는 API 연결 버전의 잘못된 업로드를 거부합니다. 실제 공개 배포에는 인증된 동일 출처 API와 WEB 경로 구성이 필요합니다.

UI만 배포할 때는 `scripts/deploy-daylight.py --profile <승인된 프로필> --account <대상 계정> --static-preview`를 사용합니다. `preview/index.html`, `preview/team-calendar.js`, `preview/calendar-views.js`와 공통 CSS·UI 스크립트만 업로드합니다. 기존 `daylight_team_calendar_v1` 저장 형식은 유지하며 브라우저 데이터를 배포물에 포함하지 않습니다. 로컬 확인 경로는 `/daylight/preview/`입니다.
