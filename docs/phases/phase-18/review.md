# Phase 18 Codex Review

- Verdict: BLOCKED — only physical Android acceptance remains
- Reviewer: Codex main agent
- Date: 2026-08-17 KST

## Review conclusion

The Phase 18 implementation, automated tests, least-privilege secret boundary, two-step AWS deployment, replacement-instance health, live TAGO lookup, authenticated Device API lifecycle, public MCP denial, and Terraform no-drift state are accepted.

The verdict is not formal PASS because no Android device or emulator is connected. Current-location permission behavior, transport result rendering, official app/browser handoffs, and the pre-existing Phase 17 FCM → local alarm → ACK flow cannot be physically observed from this environment.

## Independent evidence

- Backend: 309 tests, 0 failures/errors, 8 environment-gated skips; WAR built.
- Android: 51 tests, 0 failures; debug APK built for `https://trip.tripjunseok.site`.
- Frontend build/verification: pass.
- Terraform/orchestration Pester: 24 passed, 0 failed.
- Plugin evaluation: 13 cases / 141 checks passed.
- Live MCP: 25 tools; TAGO 강남 station search succeeded with 3 results.
- Seoul plaintext real-time endpoint: disabled by default; the explicitly enabled live demo returned 5 강남 arrival results.
- Live city-bus chain: Gangnam Station returned 5 nearby stops and the selected stop returned 10 arrivals; a zero-result proximity response is handled as an empty location-specific result rather than a provider failure.
- Deployed model guidance asks for the origin before creating an underspecified trip and before vague subway/bus arrival lookups, then performs the bus discovery-to-arrivals chain without exposing internal IDs to the user.
- Authenticated Device API: exchange 200, handoffs 200, station search 200, disconnect 204, revoked token 401.
- Public boundary: health/readiness 200, MCP 403, unauthenticated device transport 401.
- Latest WAS rolling replacement `19415ef7-12a2-4ddb-bee5-28fd90905c68`: successful; current instances `i-070c70a5a78e6221a` and `i-0a6ab41233f5dfff9` are healthy targets on Launch Template v3.
- Terraform post-apply detailed-exitcode: 0.
- Diff credential scan: pass. Checkov unavailable and therefore not claimed.

## Security and architecture assessment

- Clients never receive Seoul or data.go.kr keys.
- Only the WAS role can read the exact Phase 18 secret ARN.
- Provider hosts and operations are source-defined rather than user-controlled, preventing arbitrary outbound URL selection.
- Query failures are sanitized and do not contain request URIs or keys.
- Unsupported booking is an allowlisted HTTPS handoff; no scraping, payment, or booking claim was introduced.
- The legacy Seoul HTTP provider remains separately opt-in. It is temporarily enabled in the live demo environment, with TCP/80 egress present only while both transport and Seoul real-time flags are true.

## Resume condition

Connect an authorized device/emulator, install the generated APK, and capture on-device location, result, handoff, FCM, local-alarm, and ACK evidence. If those checks pass, change this verdict and the Phase 17 verdict to PASS, then perform the reviewed teardown.
