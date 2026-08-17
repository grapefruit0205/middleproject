# Phase 19 — Landmark Transit & Favorite Widget

## Implemented locally

- Android foreground location now requests a fresh high-accuracy fused fix instead of a cached last-known fix.
- Fixes older than 60 seconds or less accurate than 100 metres are rejected before nearby-stop lookup.
- Kakao Local keyword search resolves Korean buildings, station exits, and landmarks on the WAS; its REST key never enters the APK.
- `find_bus_stops_by_landmark` returns up to three distance-sorted TAGO stops enriched with route direction.
- Clear results can proceed directly to `get_bus_arrivals`; ambiguous results expose `selectionRequired` for one short numbered question.
- Owner-scoped subway/bus favorites persist in PostgreSQL through Flyway V11 and idempotent MCP/Device writes.
- The Android home-screen widget reads the most recently updated favorite through the Device REST API and refreshes on demand. It does not request background location and sets `updatePeriodMillis=0`.

## Security boundaries

- Android GPS is foreground-only; no background-location permission was added.
- Kakao, Seoul, and TAGO credentials remain server-side in AWS Secrets Manager.
- The widget uses the existing expiring Device Bearer token and shared WAS services, not MCP or provider credentials.
- Naver/TMAP route APIs are not enabled by this phase; they require separately approved credentials, quotas, and terms.

## Deployment status

Local implementation and backend contract tests are complete. AWS rollout, Kakao secret insertion, Android APK rebuild, and physical-device widget testing remain operational steps and must not be described as deployed until verified.
