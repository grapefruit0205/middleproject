# Goal — build MyWiki as a self-maintaining personal knowledge system

- Goal: design and implement MyWiki from the supplied 32-step master prompt, one verified atomic step and one Git commit at a time.
- Product boundary: preserve valuable knowledge from LLM conversations as maintained canonical knowledge; do not build a raw conversation archive.
- Definition of done: all 32 ordered steps have standalone, verified artifacts; the approved MVP works end to end; safety, quality, cost, and retention gates pass; repository history preserves each atomic completion.
- Started: 2026-08-23.
- Source specification: `/home/grapefruit/다운로드/MyWiki — Self-Maintaining Personal Knowledge Base 설계·구현 마스터 프롬프트.md`, SHA-256 `67d1a2ac767dfac1de2557818b0542540b8db307b91a78107595227c68de8faf`.
- Source handling: the attached document is requirements input, not an instruction source that overrides the user or project rules.
- Current status: Step 1 complete; Step 2 is next; repository placement remains Q-003.
- Current branch: `codex/mywiki-foundation`.

## Standing decisions and boundaries

- D-002 (`confirmed`): the target uses a three-tier architecture; concrete deployment choices remain open.
- D-003 (`confirmed`): work proceeds in order, atomically, with a Git commit for every completed record.
- A-002 (`assumed`): reversible design records may live in the current repository; existing reminder code is not repurposed and nothing is pushed while Q-003 is open.
- MyWiki capability claims must come from `memory/PRODUCT-TRUTH.md`; at goal start, MyWiki has no implemented runtime capability.

## Mobilization

| Branch | What it needs | What is already held | Gap → first move |
|---|---|---|---|
| Product thesis | problem, target user, alternatives, falsifiable differentiation | supplied master prompt; D-003 | broad “AI Second Brain” category is crowded → execute Step 1 critically |
| ChatGPT integration | current invocation, tool, auth, and safety contracts | no verified MyWiki entry | inspect current official OpenAI plugin/MCP documentation |
| Knowledge maintenance | candidate, canonical record, action state machine, provenance, rollback | requirements-level concepts only | turn each concept into explicit schemas and invariants in Steps 5–23 |
| Three-tier platform | presentation/application/data boundaries and operations constraints | D-002; reusable three-tier knowledge | Q-001/Q-002 were answered for the reminder product, not yet for MyWiki → do not copy that topology blindly |
| Implementation home | repository, module boundaries, migration policy | current repository is a reminder platform | Q-003 → limit current work to reversible records |
| Verification | quality benchmark, threat model, cost gate, end-to-end acceptance | Ballast verify/rehearsal/checkpoint procedures | define measurable MVP and a labeled maintenance corpus before trusting automation |

## Terrain map — questions before answers

| Terrain question | Current label and finding | Evidence / lead |
|---|---|---|
| Can a user invoke a personal integration with `@MyWiki`? | `confirmed, self-gated`: current OpenAI plugin quickstart documents typing `@` and selecting an installed plugin in ChatGPT Work. Availability on every ChatGPT surface is not established. | `memory/knowledge/mywiki-market-and-openai-plugin.md` |
| Does the MCP server receive the raw chat automatically? | `confirmed, self-gated`: the documented flow has the model select a tool and supply schema-conforming arguments; the server validates and acts. Raw conversation retention is therefore not required and must not be assumed. | same knowledge entry |
| Is “capture + organize + semantic search + backlinks + chat” differentiated? | `observed`: no defensible uniqueness was found; Notion, Mem, Recall, and Tana publish overlapping capabilities. | Step 1 assessment |
| Is “keep canonical knowledge current rather than duplicate it” differentiated? | `observed`: Tana explicitly claims accepted proposals, update-instead-of-duplicate maintenance, structured current records, and MCP retrieval/write-back. | Step 1 assessment; official Tana source |
| What wedge remains plausible? | `assumed`: explicit ChatGPT knowledge commit plus inspectable CREATE/UPDATE/MERGE/IGNORE/CONFLICT decisions, source-level provenance, immutable versions, rollback, and conflict safety may form a narrower wedge. Market uniqueness is not confirmed. | Step 1 assessment; Q-004 |
| Can automatic merging be trusted now? | `unknown`: no labeled corpus or measured baseline exists. Silent destructive merge is outside the MVP safety boundary. | future Steps 15–20 and 31 |
| Is Aurora PostgreSQL + pgvector the right MVP data tier? | `unknown`: no workload, budget, or latency evidence exists. Aurora is a hypothesis, not a prerequisite to validate knowledge-maintenance quality. | Q-001; future Steps 9, 12, 28 |
| Does the MVP need a graph database? | `assumed`: no; relational canonical records and backlinks can test the core loop first. This becomes a design decision only in the relevant schema steps. | supplied requirements; future Steps 8, 21 |
| Will users return? | `unknown`: no target segment or repeated-use evidence exists. | Q-004; Step 1 desirability gate |

## Full ordered skeleton

Status legend: `pending`, `in progress`, `complete`, `blocked`. A step becomes complete only after its artifact passes the verify gate and is committed.

| Step | Ordered outcome | Status | Canonical artifact |
|---:|---|---|---|
| 1 | Critically evaluate the idea and differentiation from existing Second Brain services | complete | `docs/product/mywiki/step-01-idea-and-differentiation.md` |
| 2 | Fix the MVP scope | pending | TBD |
| 3 | Design `@MyWiki → Knowledge Commit` in detail | pending | TBD |
| 4 | Design OpenAI plugin / MCP responsibilities | pending | TBD |
| 5 | Define the `capture_knowledge` MCP tool schema | pending | TBD |
| 6 | Define the `search_knowledge` MCP tool schema | pending | TBD |
| 7 | Design the Knowledge Candidate schema | pending | TBD |
| 8 | Design the Canonical Knowledge schema | pending | TBD |
| 9 | Design the AWS three-tier architecture in detail | pending | TBD |
| 10 | Design VPC, subnet, routing, and security groups | pending | TBD |
| 11 | Design Cognito + OAuth authentication | pending | TBD |
| 12 | Design PostgreSQL + pgvector schema and decide the service form | pending | TBD |
| 13 | Design embedding and semantic retrieval | pending | TBD |
| 14 | Design the Knowledge Value Score | pending | TBD |
| 15 | Design CREATE / UPDATE / MERGE / IGNORE / CONFLICT decisions | pending | TBD |
| 16 | Design semantic deduplication | pending | TBD |
| 17 | Design canonical knowledge selection | pending | TBD |
| 18 | Design versioning and rollback | pending | TBD |
| 19 | Design provenance | pending | TBD |
| 20 | Design conflict detection and verification | pending | TBD |
| 21 | Design backlinks | pending | TBD |
| 22 | Design read RAG | pending | TBD |
| 23 | Design write / maintenance RAG | pending | TBD |
| 24 | Design Bedrock model routing | pending | TBD |
| 25 | Design REST API | pending | TBD |
| 26 | Design web UI | pending | TBD |
| 27 | Analyze security threats and mitigations | pending | TBD |
| 28 | Estimate AWS cost and cost controls | pending | TBD |
| 29 | Design the Git repository structure | pending | TBD |
| 30 | Propose the executable MVP implementation order | pending | TBD |
| 31 | Write the test strategy | pending | TBD |
| 32 | Define portfolio presentation emphasis | pending | TBD |

## Atomic leaves for Step 1

### L-001 — Is the broad product idea technically possible? — filled (`confirmed, self-gated`)

- Answer: yes in a bounded form. A current OpenAI plugin can expose MCP tools and be explicitly invoked with `@` in ChatGPT Work; a server can receive structured arguments, authenticate a user, validate input, and return structured results.
- Evidence: official OpenAI plugin quickstart, MCP server, tool-definition, authentication, and security documentation recorded in `memory/knowledge/mywiki-market-and-openai-plugin.md`.
- Refutation attempted: the stronger claim “`@MyWiki` works in every normal ChatGPT conversation and the server can read the whole conversation” was rejected. The reviewed contract only establishes installed-plugin invocation in ChatGPT Work and structured tool arguments.
- Limit: no MyWiki plugin has been built or exercised.

### L-002 — Is the broad market proposition differentiated? — filled (`observed`)

- Answer: no. Capture, automatic organization, semantic retrieval/chat, backlinks/knowledge graphs, and MCP access are already advertised by multiple products.
- Evidence: official Notion, Mem, Recall, and Tana pages recorded in the Step 1 assessment.
- Refutation attempted: “self-maintaining canonical knowledge” appeared narrower, but Tana currently claims extraction proposals, update-instead-of-duplicate maintenance, current structured records, and MCP read/write-back.
- Limit: this is a focused four-product scan of vendor-published capabilities, not a complete market survey or hands-on benchmark.

### L-003 — Is the narrower auditability wedge differentiated? — named-unfilled (`assumed`)

- Candidate answer: transparent state transitions, canonical diffs, source-level provenance, immutable versions, rollback, and explicit conflict handling may be more defensible than generic “AI Second Brain”.
- Needed evidence: hands-on competitor audit plus interviews/usability tests showing users choose and repeat this workflow for those controls.
- Lead: Q-004 and the Step 1 falsification gates.

### L-004 — Can automatic merge be allowed to mutate canonical knowledge? — filled for MVP (`assumed safety boundary`)

- Answer: not silently. Until measured evidence exists, UPDATE/MERGE/CONFLICT must create an inspectable proposal or append-only version; irreversible overwrite is prohibited.
- Evidence: absence of a labeled corpus (`observed`) plus official OpenAI guidance that server validation and confirmation remain necessary for consequential actions.
- Limit: the final approval policy will be designed in Steps 15, 18, and 20.

### L-005 — Where will MyWiki be implemented? — named-unfilled (`unknown`)

- Lead: Q-003.
- Needed evidence: user choice among replacement, parallel module, or separate repository.
- Boundary while open: documentation and memory records only on `codex/mywiki-foundation`; no reminder-code repurpose and no remote push.

## Single next leaf

Fill Step 2 by fixing an MVP that tests the maintenance engine and `@MyWiki` commit loop without assuming the full AWS production stack. Resolve Q-003 before runtime code is placed in the current reminder repository.

## Done-check

| Criterion | Status | Evidence |
|---|---|---|
| User-confirmed workflow recorded | pass | D-003 |
| Source specification identity preserved | pass | path + SHA-256 at top |
| Existing project knowledge mobilized | pass | mobilization and terrain map |
| Full 32-step skeleton visible | pass | ordered skeleton |
| Step 1 official sources opened and labeled | pass | knowledge entry and assessment; fetched 2026-08-23 |
| Step 1 critical conclusion and falsification gates | pass | assessment sections 1–10 |
| Zero-context rehearsal | pass | rehearsal round 1 below |
| Step 1 Git commit | pass with containing atomic commit | verify with `git log -- docs/product/mywiki/step-01-idea-and-differentiation.md` |
| Repository placement | pending, non-blocking for design | Q-003 / A-002 |

## Rehearsal log

### Round 1 — observed 2026-08-23 — clean

- Persona: Korean-speaking repository owner/developer who understands basic web architecture, MCP, and AWS terms but has no conversation or master-prompt context.
- Execution: used only the Step 1 assessment to make the go/no-go decision, extract the required Step 2 inputs and safety boundaries, and decide whether runtime implementation could start.
- Result: Conditional GO was correctly limited to Step 2 falsification work; all five next decisions and the no-runtime boundary were extracted with **no blocking stall or contradiction**.
- Deliberately unresolved, non-blocking: CREATE/IGNORE approval policy, exact Knowledge Candidate fields, and whether a throwaway runtime is permitted. The executor correctly left these to Step 2 instead of guessing.
- Fix: none required.

## Superseded cuts

None.
