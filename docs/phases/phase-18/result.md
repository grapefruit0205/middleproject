# Phase 18 Result: Official Public-Transport Integration

## Status

`PARTIAL — implementation, automated validation, AWS deployment, TAGO live query, authenticated Device API, and Terraform no-drift are complete; physical Android UI acceptance remains.`

Phase 18 code and cloud behavior are accepted. The formal end-to-end gate remains open because no Android device or emulator is connected for the location-permission and on-device handoff checks, and Phase 17 still requires physical FCM → local alarm → ACK evidence.

## Implemented

- Added one application service and a port/adapter boundary for eight allowlisted public-transport operations:
  - subway station search and station schedule through TAGO HTTPS;
  - nearby bus stops, bus arrivals, and route search through TAGO HTTPS;
  - express-bus arrival prediction and intercity-bus schedules through TAGO HTTPS;
  - Seoul real-time subway arrivals behind an explicit opt-in because its upstream is plaintext HTTP.
- Added typed `success`, `empty`, `retryable`, `failureKind`, `value`, and sanitized `errorMessage` outcomes to REST and MCP.
- Expanded the private MCP catalog from 17 to 25 tools without exposing `/api/mcp` through the public ALB.
- Added bearer-protected Android transport endpoints, location/manual-coordinate UI, and verified HTTPS handoffs for Korail, SRT, Kobus, T-money intercity bus, and Korea Airports.
- Stored only the approved secret ARN in Terraform. The WAS role receives exact-resource `secretsmanager:GetSecretValue`; WEB receives no public-data secret access.
- Added opt-in Terraform inputs and non-secret WAS environment flags. The secret values do not enter tfvars, Terraform state, user data, APKs, logs, or Git.
- Updated plugin and MCP model guidance so a destination/arrival deadline without an origin asks `어디에서 출발하시나요?` before draft creation.
- Added explicit vague-query flows: `지하철 언제 와?` asks for the departure station, while `버스 언제 와?` asks for the departure location and chains nearby-stop discovery into arrivals without asking the user for internal provider IDs.
- Updated plugin behavior so unsupported KTX/SRT/air booking uses official handoffs and never claims that a seat or booking was confirmed.

## Test-first correction

Live MCP smoke testing found that Java record-style `failureKind()` was not discovered by default Jackson getter serialization. A focused integration assertion first reproduced the missing `failureKind`, then `McpAdapterController` was changed to build the transport envelope explicitly. The focused test and full backend suite passed after the correction.

Conversation-flow tests were also added before implementation. They first failed for missing origin questions and missing bus discovery chaining, then passed after the skill and model-readable MCP descriptions were updated. A live TAGO probe established that a successful empty proximity response can be location-specific rather than an API failure: Seoul City Hall returned zero nearby stops, while Gangnam Station returned stops and live arrivals.

## Local validation

- Backend: `test bootWar` successful; 309 tests, 0 failures, 0 errors, 8 environment-gated skips; 46 suites.
- Android: `testDebugUnitTest assembleDebug -PtripCopilotBaseUrl=https://trip.tripjunseok.site` successful; 51 tests, 0 failures; debug APK produced.
- Android APK SHA-256: `98B0D18A1F4AE66075CCCEBA707B8269D67729FF3BA8C0DD615E8E3DDA8DF0C9`.
- Frontend: Vite production build and PWA build verification successful.
- Phase 17/18 Terraform plus Phase 12–18 orchestration Pester contracts: 24 passed, 0 failed.
- Plugin prompt evaluation: 13 cases / 141 checks passed.
- `git diff --check` and tracked-diff credential pattern scan passed. Checkov was not installed in this environment and was not reported as executed.

## AWS deployment evidence — 2026-08-17 KST

- Account ending `1416`, region `ap-northeast-2`.
- Initial Phase 18 apply: `0 added / 4 changed / 0 destroyed`; backend artifact, WAS IAM, WAS launch template, and WAS ASG update only.
- Corrected transport envelope apply: `0 added / 1 changed / 0 destroyed`; backend artifact only.
- Seoul real-time demo enablement: `0 added / 2 changed / 0 destroyed`; WAS launch template and ASG only.
- Root-cause correction for the legacy upstream: `0 added / 1 changed / 0 destroyed`; conditional TCP/80 egress on the WAS security group only.
- Conversation guidance artifact update: `0 added / 1 changed / 0 destroyed`; backend S3 object only.
- Latest forced rolling refresh `19415ef7-12a2-4ddb-bee5-28fd90905c68`: `Successful`, 100%.
- Current WAS instances `i-070c70a5a78e6221a` and `i-0a6ab41233f5dfff9`: InService, healthy targets, Launch Template v3.
- Public checks:
  - `/healthz`: 200
  - `/api/actuator/health/readiness`: 200
  - `/api/mcp`: 403
  - unauthenticated `/api/device/transport/handoffs`: 401
- Private MCP checks on a replacement WAS:
  - `tools/list`: 25
  - TAGO subway search for 강남: success, 3 results
  - default-disabled Seoul real-time request: `DISABLED_INSECURE`, retryable false
  - explicitly enabled Seoul real-time 강남 request: success, 5 results
  - TAGO 강남역 nearby-stop search: success, 5 results
  - first discovered stop → live bus arrivals: success, 10 results
  - deployed tool descriptions contain the trip-origin, subway-origin, bus-origin, and bus-chain guidance
- Ephemeral authenticated Device API verification, without printing the pairing code or token:
  - pairing exchange: 200
  - official handoffs: 200, 5 links
  - TAGO 강남 station search: 200, success, 3 results
  - disconnect: 204
  - reuse of revoked token: 401
- Post-deployment Terraform `plan -detailed-exitcode`: 0, no changes.

## Remaining physical acceptance

1. Connect an authorized Android device or emulator (`adb devices -l` currently lists none).
2. Install `android/app/build/outputs/apk/debug/app-debug.apk` and verify manual coordinates plus user-initiated foreground location.
3. Verify the transport result cards and official handoff buttons on-device.
4. Complete the existing Phase 17 FCM → local alarm → ACK scenario.
5. After evidence capture, create and review a destroy plan and remove the cost-bearing AWS stack.
