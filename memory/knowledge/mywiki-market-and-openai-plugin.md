# Verified knowledge — MyWiki market and OpenAI plugin feasibility

Scope: focused foundation scan for MyWiki Step 1, checked 2026-08-23. OpenAI documentation claims are protocol/product contracts and are marked `confirmed, self-gated`. Competitor capability statements are marked `observed, vendor-published`: their official pages were opened, but the products were not independently exercised. Re-check after 90 days because these products change quickly.

## An installed OpenAI plugin can be invoked with `@` in ChatGPT Work — confirmed (self-gated) — 2026-08-23

- Claim: OpenAI's current plugin quickstart tells the user to install a personal plugin, start a ChatGPT Work chat, type `@`, select the plugin, and issue the request. This supports an explicit `@MyWiki` invocation concept on that documented surface.
- Refutation attempted: the stronger claim that the same invocation works in every ChatGPT chat, client, or plan was not supported by the reviewed page and was rejected.
- Source: OpenAI, [Plugins quickstart](https://developers.openai.com/plugins/quickstart), especially “Test the plugin”.
- Sample: n=1 current official end-to-end quickstart, fetched 2026-08-23.
- Limits: no MyWiki plugin, chosen plugin name, publication approval, mobile client, or non-Work surface was tested. OpenAI's current product term is **plugin**, which can contain an MCP server, skills, and optional UI; older “ChatGPT App / Apps SDK” wording in the supplied specification should not be treated as the current contract.

## The MCP server receives declared tool arguments, not automatic raw-chat access — confirmed (self-gated) — 2026-08-23

- Claim: in OpenAI's documented MCP flow, the client discovers tools, the model selects one and supplies arguments matching its input schema, then the server validates and executes it. A MyWiki server can therefore work from a structured Knowledge Candidate supplied by the model without storing the raw conversation by default.
- Refutation attempted: the statement “the server can directly read the user's whole conversation” was not present in the reviewed contract and was rejected. A client may deliberately include text in tool arguments, but that is different from implicit transcript access.
- Sources: OpenAI, [MCP server concept](https://developers.openai.com/plugins/concepts/mcp-server) and [Build an MCP server](https://developers.openai.com/plugins/build/mcp-server).
- Sample: n=2 official documentation contracts, fetched 2026-08-23.
- Limits: this establishes the server boundary, not what private host context the model may use when forming arguments. The input schema and retention policy must explicitly bound what text is transmitted.

## A knowledge commit is a write action that needs explicit contracts and server-side safeguards — confirmed (self-gated) — 2026-08-23

- Claim: OpenAI requires tools to separate reads from writes, define authorization and side effects, mark accurate safety hints, validate inputs on the server, and retain confirmation for consequential actions. Authenticated MCP servers are expected to implement the MCP OAuth 2.1 flow and verify tokens on every request.
- Refutation attempted: accurate `readOnlyHint`, `destructiveHint`, and `openWorldHint` metadata does not itself authorize or make a write safe; OpenAI explicitly says annotations do not replace authorization, validation, or confirmation.
- Sources: OpenAI, [Define tools](https://developers.openai.com/plugins/plan/tools), [Authentication](https://developers.openai.com/plugins/build/auth), [Security & Privacy](https://developers.openai.com/plugins/guides/security-privacy), and [MCP server review requirements](https://developers.openai.com/plugins/deploy/app-review).
- Sample: n=4 official planning, auth, security, and review contracts, fetched 2026-08-23.
- Limits: an additive, reversible version proposal is not automatically “destructive”; the exact MyWiki annotation and confirmation policy must follow its implemented side effects and will be decided in later steps.

## Generic AI Second Brain capabilities are already common — observed (vendor-published) — 2026-08-23

- Claim: the reviewed official pages collectively cover workspace/connected-source search with citations (Notion), capture plus automatic surfacing, vague-memory search, and organization (Mem), summaries, smart tags, knowledge graphs, chat, and read-only MCP access (Recall), and a structured connected PKM with backlinks and AI (Tana). “Capture, organize, search/chat, and connect” is therefore not a defensible uniqueness claim for MyWiki.
- Refutation attempted: the four products differ in audience and depth, but those differences do not make the generic bundle unique. The surviving conclusion is limited to feature-category overlap, not product equivalence.
- Sources: Notion, [Enterprise Search](https://www.notion.com/help/enterprise-search); Mem, [Welcome to Mem](https://help.mem.ai/); Recall, [product overview](https://www.getrecall.ai/) and [MCP Server](https://docs.recall.it/developer/mcp); Tana, [About Tana Outliner](https://outliner.tana.inc/help/knowledge-graph-concepts).
- Sample: n=4 vendor documentation/product pages, fetched 2026-08-23.
- Limits: vendor claims were not hands-on verified; the scan is not exhaustive. An unmentioned capability is `unknown`, not absent.

## Tana's published workflow directly overlaps “self-maintaining canonical knowledge” — observed (vendor-published) — 2026-08-23

- Claim: Tana currently publishes that meetings and chats feed structured memory; extraction creates proposals a person accepts; rerunning extraction updates existing outcomes instead of creating duplicates; records can remain current or immutable; external agents can retrieve and write through MCP; writes return as proposals for approval. This substantially overlaps MyWiki's proposed maintenance narrative.
- Refutation attempted: MyWiki additionally specifies an explicit five-state decision model, provenance, versions, rollback, and conflict handling, but the reviewed Tana page was not a full feature audit. Those may be a narrower differentiation hypothesis, not an established market fact.
- Sources: Tana, [How to build AI agent memory with a knowledge graph](https://tana.inc/blog/how-to-build-ai-agent-memory-with-a-knowledge-graph) and [About Tana Outliner](https://outliner.tana.inc/help/knowledge-graph-concepts).
- Sample: n=2 current official Tana pages, fetched 2026-08-23.
- Limits: vendor-published behavior, not an independent benchmark. Tana's provenance, version history, rollback, and conflict semantics remain `unknown` until a deeper audit.
