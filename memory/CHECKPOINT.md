# Checkpoint — service-mvp local completion — 2026-09-07 KST

## Current task

The user started the continuous implementation run (D-007). S01–S04 and the local portion of S05 are implemented and verified. `progress.json` is now `blocked` only because AWS/HTTPS deployment and a real SES delivery require external authority and configuration.

## Scope and boundaries

- `confirmed`: local implementation and proportional verification are authorized; AWS apply/destroy, DNS, live email, Git commit, and push are not authorized.
- `observed`: the final local Apache WEB → external Tomcat WAS → PostgreSQL 16 stack is running at `http://127.0.0.1:8088`; only WEB is host-exposed.
- `verified`: frontend 11 tests/build/PWA/audit, backend 106 tests with no failures/errors and 9 environment-gated skips, WAR build, authentication 401, aggregate lifecycle, idempotency, 409 conflict, restart persistence, and browser lifecycle all passed.
- `observed`: S05 browser testing found stale open history after cancellation; `App.tsx` now refreshes it and the regression test passes.
- `unknown`: current deployable AWS/SES inputs. Q-005 and `progress.json` S05-B1/B2 are the only remaining completion boundary.
- Keep legacy Phase 00–18 intact. Do not resume the MyWiki or Trip Copilot tracks.

## Next action

Do not repeat local S01–S05 checks. If the user explicitly authorizes the external scope and securely prepares prerequisites, resolve S05-B1/B2: present the exact Terraform plan, apply only the approved AWS scope, verify HTTPS and 401, then send one approved test email and distinguish provider acceptance from inbox receipt.

## Previous checkpoint

The pre-implementation service handoff is archived at `memory/checkpoints/2026-09-07-service-mvp-implementation-start.md`. MyWiki remains a separate repository (D-004).
