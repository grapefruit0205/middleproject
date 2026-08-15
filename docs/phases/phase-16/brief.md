# Phase 16 Brief: Android Companion

## Goal

ChatGPT에서 확정한 출장을 조회하고 FCM 알림과 로컬 정확한 알람을 처리하는 Android Companion을 만든다.

## Scope

- Native Kotlin과 Jetpack Compose
- Gradle Kotlin DSL과 프로젝트 Gradle Wrapper
- 5분 만료 일회용 Pairing Code 교환
- 최대 24시간의 폐기 가능한 Opaque Device Token
- Android Keystore Credential 보관
- Trip, Reminder, Delivery 조회와 취소·ACK
- FCM Token 등록·갱신·해제
- AlarmManager 정확한 알람과 권한 fallback
- 재부팅·시간대 변경 후 알람 재등록

## Non-goals

- Android AI 채팅과 MCP Client
- Cognito/OIDC 로그인
- OpenAI 또는 Provider Credential 포함
- 자동 내비게이션 조작

## Definition of Done

- [ ] Pairing Code 만료와 재사용을 거부한다.
- [ ] 서버가 Pairing Code와 Device Token의 hash만 저장한다.
- [ ] 미페어링·만료·폐기된 Device 요청을 거부한다.
- [ ] 동일 Reminder 재수신이 중복 알람을 만들지 않는다.
- [ ] 재부팅과 시간대 변경 후 유효 알람만 등록한다.
- [ ] 기기 연결 해제가 서버 Token과 Keystore Credential을 제거한다.
- [ ] Gradle Wrapper clean build와 Android 테스트가 통과한다.

## Recommended commits

- `feat: add android trip companion`
- `test: secure device pairing and alarms`
