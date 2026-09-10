# Daylight API

Daylight 화면이 사용하는 일정·예약 알림 API입니다. 기존 Deadline Companion 엔진을 재사용하며, 별도의 두 번째 백엔드를 만들지 않습니다. Spring 애플리케이션 이름은 `daylight-api`입니다.

## 연결

로컬 WEB의 `/daylight/`에서 화면을 열면 동일 출처의 `/api/deadlines`를 호출합니다. Apache가 WAS로 전달합니다. 정적 Amplify 주소에는 이 API가 없습니다. 공개 운영용 인증·팀 멤버십 연결 없이 로컬 API를 외부 공개하지 마세요.

브랜드 변경과 리소스 주소 변경은 별개입니다. 기존 클라이언트 호환성을 위해 다음 경로와 Java 패키지·DB 테이블 이름을 유지합니다.

| 메서드·경로 | 역할 |
| --- | --- |
| `GET /api/deadlines` | 현재 소유자의 일정 목록 |
| `GET /api/deadlines/{id}` | 일정 상세·버전·예약 상태 |
| `POST /api/deadlines` | 일정과 예약 Outbox 등록 |
| `PUT /api/deadlines/{id}` | 제목·시각·사전 알림 수정 |
| `POST /api/deadlines/{id}/cancel` | 취소 기록과 예약 삭제 Outbox 생성 |
| `GET /api/deadlines/{id}/history` | 예약·발송 처리 이력과 외부 연동 설정 상태 |

변경 요청에는 `Idempotency-Key`가 필요합니다. 응답이 불명확하면 같은 키와 같은 본문으로 재시도합니다. 수정에는 `expectedVersion`, `expectedEventVersion`, `expectedPolicyVersion`, 취소에는 `expectedVersion`을 전달하며 충돌은 HTTP 409입니다. 무조건 최신 버전으로 덮어쓰지 않습니다.

등록 본문 예시(시각은 실행 시점보다 미래여야 합니다):

```json
{"title":"팀 발표","startsAt":"2027-01-15T14:00:00+09:00","leadMinutes":60}
```

현재 계약은 단일 소유자의 제목·일정 시각·사전 알림 분을 지원합니다. 종료 시각, 카테고리, 팀 멤버십, 수신자 목록은 지원하지 않습니다. 화면도 지원되지 않는 값을 저장한 것처럼 표시하지 않습니다. 팀 연동은 이 계약을 확장하는 다음 작업입니다.

일정 저장과 실제 발송은 다릅니다. 로컬 외부 연동은 비활성입니다. `PROVIDER_ACCEPTED`/`DELIVERED`는 발송 요청 수락이며 도착·읽음 증거가 아닙니다. `OUTCOME_UNKNOWN`/`DELIVERY_UNKNOWN`은 불명확한 결과로 무조건 재발송하지 않습니다.
