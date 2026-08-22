# Stage 20.7 — MCP itinerary tools and conversation contract

## Tools

- `preview_day_plan(draftJson)` — validates and resolves a draft without writes.
- `confirm_day_plan(draftJson, confirmationId, idempotencyKey)` — explicit user-approved,
  atomic persistence and notification scheduling.
- `cancel_day_plan_item(planId, sequence, expectedPlanVersion, idempotencyKey)` — destructive,
  explicit item cancellation with notification deletion and route recomputation.

The draft is transported as a bounded JSON string so the MCP schema remains closed and compatible
with clients that do not support nested record schemas. The service still validates every field and
never trusts an owner supplied by the model; the deployment-fixed owner context is used.

## Conversation rules

1. Collect one missing value at a time using the validation result.
2. Call `preview_day_plan` only after origin, fixed times, places, durations and travel modes are
   known or a place candidate is explicitly selected.
3. Show the complete timeline and notification times, then ask for explicit confirmation.
4. Call `confirm_day_plan` once with a fresh confirmation and idempotency key.
5. On a cancellation request, repeat the affected item and notification time, ask for confirmation,
   then call `cancel_day_plan_item` with the latest plan version.

## Safety annotations

The preview is read-only; confirmation is repeat-safe; cancellation is destructive and repeat-safe.
External route/place results are marked open-world. MCP errors do not leak another owner's plan.
