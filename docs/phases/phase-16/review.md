# Phase 16 Codex Review

- Decision: PASS
- Branch: `codex/phase-16-android-companion`
- Baseline: `7d994d618ab544dc365ee143fb2cff7e2dc28dff`
- Reviewer: Codex main agent

## Accepted scope

- Native Kotlin/Jetpack Compose Android companion with Gradle Kotlin DSL and Wrapper
- Five-minute, one-use pairing codes and at-most-24-hour opaque device bearer tokens
- Hash-only persistence for pairing codes and device bearer tokens; Android Keystore AES-GCM credential storage
- Owner-scoped trip, reminder, alarm-time, and typed delivery-status queries
- Idempotent trip/reminder cancellation, reminder acknowledgement, and FCM token lifecycle
- Exact AlarmManager scheduling with inexact fallback, stable reminder identity, and stale-alarm reconciliation
- Boot/time/timezone rescheduling and terminal-FCM cancellation through a shared production handler
- Debug-only local cleartext allowance; release cleartext denied

## Independent evidence

- Backend clean test: 37 suites / 259 tests / 0 failures / 0 errors / 8 skipped.
- All 8 backend skips are the environment-gated `Postgres16IntegrationTest`.
- Android clean unit/build validation: 8 suites / 45 tests / 0 failures / 0 errors / 0 skipped.
- Debug and unsigned release APKs assembled successfully.
- Debug APK SHA-256: `8D3AEC3590888D9BD1E852BD6E405F6FA450B07ADA346E0E1EB3468CEFC21E8C`.
- Unsigned release APK SHA-256: `555D00E719DA64C20C5A8787490F309319783690FBC071481973D3AB32B83F72`.
- Phase 12+ orchestration Pester suite: 8 passed / 0 failed / 0 skipped.
- `git diff --check`: passed.
- Phase path scope: 79 changed/untracked files / 0 rejected paths before this Codex review document and README update.
- Forbidden artifact and secret-pattern scans: 0 matches.

## Review corrections

Codex rejected intermediate worker results until these issues were corrected:

1. Blocking backend calls ran on the UI dispatcher and the installation identity was unstable.
2. Initial/retry FCM registration, local deregistration cleanup, delivery UI, stale-alarm reconciliation, and release network configuration were incomplete.
3. Firebase runtime/configuration errors could escape the best-effort pairing path.
4. The backend delivery response did not initially provide the Android `channel`/`status`/`attemptedAt` contract.
5. An unrelated idempotency adapter rewrite was removed and the original H2/PostgreSQL behavior restored.
6. H2 concurrent pairing-code issuance leaked a database duplicate exception instead of the safe conflict outcome.
7. Missing or expired credentials left previously scheduled alarms alive after time changes.
8. Terminal FCM messages were discarded without cancelling an existing reminder alarm.
9. Terminal parsing incorrectly required `alarmTime`, treated unknown statuses as terminal, and was initially tested through a path that could diverge from production.
10. Unknown reminder statuses could still schedule because the active-status allowlist was not enforced.

## Deferred evidence

- No emulator or physical-device execution was performed.
- No live Firebase project, `google-services.json`, FCM delivery, or notification rendering was exercised.
- No live AlarmManager timing/permission behavior was observed outside JVM contracts and APK assembly.
- No AWS deployment or live PostgreSQL 16 environment was used in this phase.
- Firebase server sending, live Android pairing, 3-tier deployment, and end-to-end evidence remain Phase 17 work.

No AWS resource or public endpoint was created in Phase 16.
