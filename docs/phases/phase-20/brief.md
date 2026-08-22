# Phase 20 · Daily Itinerary MVP

## Objective

Turn one natural-language day plan into a confirmed, ordered itinerary that is stored in PostgreSQL and displayed by the Android companion. The first acceptance slice is ChatGPT MCP → preview/confirmation → PostgreSQL → Android timeline data.

## Scope

- Multiple schedule items for one Asia/Seoul calendar day.
- Fixed and flexible start/end semantics.
- Explicit places and travel legs between consecutive items.
- A read-only preview followed by one explicit confirmation.
- Notification lead-time metadata for each confirmed item.
- Owner-scoped persistence and idempotent confirmation.
- Minimal Android timeline data; visual redesign is out of scope.

## Non-goals

- Full ChatGPT conversation-history capture.
- RAG, vector embeddings, or Obsidian synchronization.
- Background location tracking.
- Ticket purchase, payment, or booking guarantees.
- Multi-user organizations, fleet dispatch, or vehicle routing.
- Automatic system clock alarms except a user-initiated wake-alarm handoff.
- React frontend redesign.

## Stage gates

Each stage is implemented in the repository and independently reviewed by Codex.
Command Code with `gpt-5.6-luna` is the preferred worker when its quota is
available; this phase may be completed directly by Codex when the worker is
blocked by quota. A stage does not advance until its tests and acceptance checks
are PASS. Failed stages return to the same stage for a focused correction.

The final acceptance slice is:

`MCP preview_day_plan → explicit confirm_day_plan → PostgreSQL day_plans/items/travel_legs/reminders → paired Android timeline → versioned item cancellation`
