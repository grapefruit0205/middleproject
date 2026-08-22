# Phase 20.9 — final verification and handoff

## Acceptance checks

1. Run the complete backend suite:

   ```powershell
   cd C:\middleproject\backend
   .\gradlew.bat test --no-daemon --console=plain
   ```

2. Confirm the device integration test covers owner-scoped read, notification
   projection, item cancellation, recomputed route preview, and version increment.
3. Review the MCP tool list and annotations: preview is read-only, confirmation is
   explicit and idempotent, and cancellation is destructive and idempotent.
4. On a machine with the Android SDK configured, run:

   ```powershell
   cd C:\middleproject\android
   .\gradlew.bat test
   .\gradlew.bat assembleDebug
   ```

   Install the resulting debug APK, pair once with a short-lived pairing code,
   refresh, confirm the timeline appears, then cancel one item and refresh again.

## Knowledge scope

Phase 20 stores only the structured data required for a daily itinerary: explicit
saved origins, confirmed day plans, fixed or flexible schedule items, route-estimate
provenance, and notification metadata for items with a fixed start time. It does
not add preferred transport-mode settings, reusable trip templates, or packing-note
storage. RAG, vector embeddings, Obsidian synchronization, and silent ChatGPT-history
capture are not part of this project.
