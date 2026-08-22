# DECISIONS — append-only ledger

Rules: only user-confirmed decisions are recorded. Nothing is edited or deleted. A changed decision gets a **new** entry that `supersedes D-xxx`, and the old entry receives exactly one added line: `→ superseded by D-yyy (date)`. Sequential ids, never reused. (Full protocol: ballast decision-ledger skill.)

---

## D-001 · Adopt the ballast memory structure — 2026-08-22 (user, project setup)

This project uses `memory/` as its durable brain: decisions in this ledger, unresolved items in OPEN-QUESTIONS, per-session notes in SESSION-LOG. Standing decisions are followed without relitigating; changes go through the supersede protocol.

## D-002 · Use a three-tier architecture — 2026-08-22 (user, repository architecture analysis)

The target architecture will use a three-tier structure. The concrete WEB, WAS, data, availability, and deployment choices remain open until the repository-grounded options are compared.

## D-003 · Build MyWiki in ordered, atomic Git steps — 2026-08-23 (user, implementation workflow)

Start MyWiki from the first ordered step in the supplied master prompt. Complete and verify one atomic step at a time, record it in the repository, and create a Git commit for each completed record. This decision does not choose whether MyWiki replaces the current reminder product, lives beside it, or moves to a separate repository.
