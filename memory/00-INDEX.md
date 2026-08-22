# memory/ — middleproject brain

Purpose: this folder is the durable memory for middleproject. Conversations forget; this folder does not. What is recorded here survives topic changes, session resets, and context compaction.

## File map

| File | What | Write rule |
|---|---|---|
| `DECISIONS.md` | Confirmed decisions | Append-only. Supersede protocol — never edit past entries |
| `OPEN-QUESTIONS.md` | Unresolved items awaiting a decision, and readings in force the user has not confirmed | Two tables. Close each row with a link to the resolving decision, or drop it |
| `SESSION-LOG.md` | What happened, per working session | Append, dated |
| `PRODUCT-TRUTH.md` | What the product actually does | Evidence + date only. Three sections: implemented / not / excluded |
| `goal/three-tier-architecture.md` | Canonical goal map, atomic evidence tree, and done-check for the three-tier decision | Update in place; preserve superseded cuts |
| `goal/mywiki.md` | Canonical goal map and ordered 32-step MyWiki implementation record | Update after every atomic step and verification |
| `knowledge/` | Verified, reusable findings | Verify-gate entries only; re-check after 90 days |
| `CHECKPOINT.md` | Current thirty-second return point | Keep current; archive the outgoing version before every update |
| `checkpoints/` | Append-only checkpoint history | Never overwrite archived checkpoints |

## Operating principles

1. **Record in-session.** Decisions and important facts are written the moment they appear, not at the end. Zero loss.
2. **User-confirmed vs AI-proposed are always distinguished.** A proposal the user hasn't confirmed is not a decision — and neither is your reading of a non-answer; that is registered in OPEN-QUESTIONS.md as `assumed`.
3. **Claims carry labels** — confirmed / observed / assumed / hearsay / unknown (see the ballast verify-gate skill).
4. **External product claims require truth-file evidence** (see the ballast proof-standard skill).
5. **Unresolved things get registered**, not remembered. If it's not in OPEN-QUESTIONS.md, it will be lost.
