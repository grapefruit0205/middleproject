# Goal — choose the repository's three-tier architecture

- Goal: analyze the current Git repository and determine which three-tier architecture is the best fit.
- Definition of done: a repository-grounded current-state map, verified option comparison, one conditional recommendation, prioritized next changes, explicit unknowns, and a zero-context rehearsal-ready deliverable exist without changing the runtime architecture before the user's choice.
- Started: 2026-08-22
- Canonical deliverable: [`docs/architecture/three-tier-assessment-2026-08-22.md`](../../docs/architecture/three-tier-assessment-2026-08-22.md)
- Status: analysis complete; architecture selection awaits the user (Q-002).

## Mobilization

| Branch | What it needs | What was already held | Gap → first move |
|---|---|---|---|
| Goal and constraints | confirmed target, current ADRs, scope | D-002; [`project-invariants.md`](../../docs/architecture/project-invariants.md); ADR-001/002/005 | Workload/SLO/budget/operator capacity unknown → register Q-001 |
| Current implementation | code, Terraform, phase evidence, present runtime state | repository source; README; Phase 10/11 result/review | Local tools partly unavailable → inspect directly and run available frontend checks |
| Three-tier foundations | verified definitions and supported deployment forms | no prior `memory/knowledge/` entry | Open upstream Apache, Spring, and AWS primary sources; self-gate claims |
| Options and tradeoffs | alternatives evaluated against the actual goal | Architecture v1.2 already considered EC2, Fargate, mod_proxy, mod_jk | Re-evaluate current/managed/hybrid options without assuming “modern” means “better” |
| Recommendation and handoff | decision rule, priority order, limits, rehearsal | ballast goal/verify/rehearsal/checkpoint skills | Write standalone assessment, rehearse, checkpoint; user selects the baseline |

## Terrain map — questions before answers

| Terrain question | Label and finding | Source |
|---|---|---|
| What counts as the three tiers here? | `observed`: Apache/React is presentation, Tomcat/Spring is application, RDS is data. ALBs are connectors, while Scheduler/SQS/providers are supporting services. | [`architecture-v1.2.md`](../../docs/architecture/architecture-v1.2.md), Terraform |
| Is Apache plus external Tomcat accidental legacy? | `confirmed, self-gated`: both deployment forms remain supported; `observed`: the repository deliberately chose them for assignment evidence. | [`memory/knowledge/three-tier-architecture.md`](../knowledge/three-tier-architecture.md), ADR-001 |
| Is the current topology implemented or only diagrammed? | `observed`: it is implemented in Terraform and has one documented live baseline; `observed`: the stack is currently torn down. | [`README.md`](../../README.md), [`phase-11/result.md`](../../docs/phases/phase-11/result.md), Terraform |
| Does “Auto Scaling Group” mean demand elasticity here? | `confirmed, self-gated`: no; both groups are capped at two. Health replacement and rolling refresh remain available. | [`memory/knowledge/three-tier-architecture.md`](../knowledge/three-tier-architecture.md) |
| Has HA recovery been proven? | `confirmed, self-gated`: baseline placement/health was observed, but deliberate instance and RDS recovery was not executed. | [`memory/knowledge/three-tier-architecture.md`](../knowledge/three-tier-architecture.md) |
| Are the documented ingress and identity promises wired? | `observed`: public `/api/mcp` denial and a production `Principal` provider are absent from the repository; public REST mutation routes have no authentication dependency. | `infra/terraform/tier.tf`, `web.sh.tftpl`, `backend/build.gradle.kts`, controllers |
| Does the application use least-privilege DB identity? | `confirmed, self-gated`: WAS currently receives the RDS managed-master username/secret. | [`memory/knowledge/three-tier-architecture.md`](../knowledge/three-tier-architecture.md) |
| Does `development` materially shrink the stack? | `observed`: NAT differs, but WEB/WAS stay 2/2 and RDS stays Multi-AZ. | `infra/terraform/main.tf`, `tier.tf`, `variables.tf` |
| Would CloudFront/S3 and ECS Fargate still be three-tier? | `confirmed, self-gated`: yes logically; `observed`: it violates the accepted physical demonstration constraints. | [`memory/knowledge/three-tier-architecture.md`](../knowledge/three-tier-architecture.md), ADR-001/002 |
| What important thing is still missing? | `unknown`: workload, traffic shape, SLO/RTO/RPO, monthly ceiling, persistent-vs-ephemeral intent, and operator capacity. These prevent exact production sizing and cost conclusions. | Q-001 in [`OPEN-QUESTIONS.md`](../OPEN-QUESTIONS.md) |

## Skeleton v2 — conclusion carried by MECE branches

1. **Constraint fit — filled.** The project-specific objective requires visible Apache/Tomcat and WEB/WAS separation. Rests on ADR-001/002 and `project-invariants.md`.
2. **Current system truth — filled.** The deployment is a real three-tier implementation with historical baseline evidence, but not a live environment. Rests on Terraform, README, Phase 11 result/review, and `PRODUCT-TRUTH.md`.
3. **Fitness gaps — filled.** Remaining risks divide into architecture correctness, security, reliability, repeatability, and application maintainability. Rests on repository inspection and `memory/knowledge/three-tier-architecture.md`.
4. **Alternatives — filled.** Current unchanged, hardened current, managed logical, and hybrid forms are compared against the same constraints. Rests on the canonical assessment and upstream AWS/Apache/Spring sources.
5. **Decision and execution — partly filled.** A+ is the conditional recommendation and its work order is defined; user selection and production constraints remain named-unfilled. Rests on the canonical assessment, Q-001, and Q-002.

## Atomic leaves

### L-01 — Must the solution preserve Apache and external Tomcat? — filled (`observed`)

- Answer: yes under the currently accepted project objective; changing it requires new ADRs.
- Source: [`ADR-001`](../../docs/adr/ADR-001-ec2-apache-tomcat.md), [`ADR-002`](../../docs/adr/ADR-002-mod-proxy-internal-alb.md), [`project-invariants.md`](../../docs/architecture/project-invariants.md).
- Refutation: a managed architecture could still be logically three-tier, but it would not preserve the graded construction evidence.
- Limits: the user can retire this constraint; that would reopen the recommendation.
- sub-foundations exposed: `assignment/demo objective — atomic`.

### L-02 — Is the current network/deployment path a coherent three-tier implementation? — filled (`observed`)

- Answer: yes; responsibility, subnet, Security Group, health-check, and runtime boundaries line up with presentation/application/data tiers.
- Source: [`architecture-v1.2.md`](../../docs/architecture/architecture-v1.2.md), `infra/terraform/main.tf`, `infra/terraform/tier.tf`.
- Refutation: there are five network hops, but ALBs do not add business tiers; async services are outside the core request path.
- Limits: this evaluates separation, not whether every security promise is implemented.
- sub-foundations exposed: `business tier vs transport component — atomic`; `async supporting services — atomic`.

### L-03 — Was the topology ever deployed successfully? — filled (`observed`)

- Answer: one checked-in Phase 11 run records a healthy 2/2 WEB, 2/2 WAS, private encrypted Multi-AZ RDS baseline and no drift.
- Source: [`phase-11/result.md`](../../docs/phases/phase-11/result.md), [`phase-11/review.md`](../../docs/phases/phase-11/review.md), [`README.md`](../../README.md).
- Refutation: four bootstrap defects occurred and required fixes; the final baseline survived those fixes, but deliberate failure tests did not occur.
- Limits: historical n=1 deployment; not reproduced in the current workspace.
- sub-foundations exposed: `deployment health — atomic`; `recovery behavior — not atomic → split into L-06`.

### L-04 — Is the current capacity configuration elastic to demand? — filled (`confirmed, self-gated`)

- Answer: no; both ASGs are capped at two. They still replace unhealthy instances and support rolling refresh.
- Source: [`memory/knowledge/three-tier-architecture.md`](../knowledge/three-tier-architecture.md).
- Refutation: the broader statement “ASG provides no automation” was rejected.
- Limits: no load test exists, so target metrics and bounds remain unknown.
- sub-foundations exposed: `health replacement — atomic`; `demand scaling — atomic`.

### L-05 — Are development and HA profiles meaningfully distinct? — filled (`observed`)

- Answer: only NAT behavior differs. Compute remains 2/2 and RDS remains Multi-AZ in both supported environments, contradicting Architecture v1.2's statement that development can run one instance per server tier.
- Source: `infra/terraform/main.tf`, `infra/terraform/tier.tf`, `infra/terraform/variables.tf`, [`architecture-v1.2.md`](../../docs/architecture/architecture-v1.2.md).
- Refutation: instance type and DB class are variable, but capacity and Multi-AZ are not.
- Limits: ignored tfvars cannot override hard-coded counts.
- sub-foundations exposed: `NAT profile — atomic`; `compute profile — atomic`; `database availability profile — atomic`.

### L-06 — Is HA recovery demonstrated? — filled (`confirmed, self-gated`)

- Answer: no. Redundancy was provisioned and observed healthy, but recovery experiments, RDS failover, RTO/RPO, and final rehearsal remain unexecuted.
- Source: [`memory/knowledge/three-tier-architecture.md`](../knowledge/three-tier-architecture.md).
- Refutation: “no HA preparation exists” was rejected because multi-AZ placement, ASG replacement, health checks, alarms, and a runbook do exist.
- Limits: conclusion concerns evidence, not a prediction that recovery would fail.
- sub-foundations exposed: `redundancy configuration — atomic`; `tested recovery — atomic`; `measured objectives — atomic`.

### L-07 — Is public `/api/mcp` denied as documented? — filled (`observed`)

- Answer: no in the current Terraform. The listener defaults to forwarding all paths and Apache proxies all `/api/`; no listener rule implements the documented denial.
- Source: `infra/terraform/tier.tf`, `infra/terraform/templates/web.sh.tftpl`, [`ADR-005`](../../docs/adr/ADR-005-private-mcp-tunnel-and-device-pairing.md).
- Refutation: ADR/README prose says it is blocked, but executable Terraform is the stronger source for wired state.
- Limits: an out-of-repository manual ALB rule is unknown and would not be reproducible; the documented stack is currently destroyed.
- sub-foundations exposed: `public path denial — atomic`; `private tunnel existence — atomic, not implemented`.

### L-08 — Is caller identity wired for the public API and MCP? — filled (`observed`)

- Answer: public REST controllers have no authentication boundary; MCP rejects a missing `Principal`, but no repository dependency/configuration supplies a production principal.
- Source: `backend/build.gradle.kts`, `backend/src/main/java/com/middleproject/reminder/web/*Controller.java`, `infra/terraform/templates/was.sh.tftpl`.
- Refutation: tests can inject a principal, but test injection is not production wiring.
- Limits: an external, uncommitted container realm is unknown; it was not found in the reproducible bootstrap.
- sub-foundations exposed: `authentication — atomic`; `ownership authorization — atomic`; `device pairing — atomic, planned`.

### L-09 — Does WAS use a least-privilege database role? — filled (`confirmed, self-gated`)

- Answer: no; it receives the RDS master user's managed secret.
- Source: [`memory/knowledge/three-tier-architecture.md`](../knowledge/three-tier-architecture.md).
- Refutation: a safe secret store and an application-like username do not turn the master account into a separate runtime role.
- Limits: actual SQL privilege exercise was not traced; the credential scope itself is established.
- sub-foundations exposed: `master/admin identity — atomic`; `migration identity — atomic, named-unfilled`; `runtime identity — atomic, named-unfilled`.

### L-10 — Is instance construction reproducible? — filled (`observed`)

- Answer: only partially. Application artifacts and Tomcat version are pinned, but latest AL2023 is resolved dynamically, packages are installed at boot, and the Tomcat archive is downloaded without a checksum.
- Source: `infra/terraform/tier.tf`, `infra/terraform/templates/web.sh.tftpl`, `infra/terraform/templates/was.sh.tftpl`; AWS immutable-infrastructure guidance cited in the assessment.
- Refutation: retries and pinned `tomcat_version` reduce some variability, so “nothing is pinned” was rejected.
- Limits: no launch-time benchmark was run.
- sub-foundations exposed: `base image identity — atomic`; `package resolution — atomic`; `application artifact identity — atomic`; `archive integrity — atomic`.

### L-11 — Are backend logical boundaries as strict as the package names imply? — filled (`observed`)

- Answer: no; multiple application services directly depend on `JdbcTemplate` even though repository ports exist.
- Source: `backend/src/main/java/com/middleproject/reminder/application/ReminderService.java`, `NotificationDeliveryService.java`, `ReminderDeliveryService.java`, `SchedulerOutboxService.java`, and `port/`.
- Refutation: domain/application/infrastructure packages and several adapters are genuinely separated; the surviving claim is “partial”, not “absent”.
- Limits: this is maintainability analysis, not a failure of deployment three-tier architecture.
- sub-foundations exposed: `deployment tiers — atomic`; `in-process layers — atomic and distinct`.

### L-12 — Is a managed logical three-tier form technically valid? — filled (`confirmed, self-gated`)

- Answer: yes: CloudFront/private S3 presentation, ALB/ECS Fargate application, and RDS data are viable logical tiers.
- Source: [`memory/knowledge/three-tier-architecture.md`](../knowledge/three-tier-architecture.md).
- Refutation: “valid” does not mean “best for this repository”; it loses the accepted Apache/external-Tomcat evidence and requires a new verification baseline.
- Limits: no project migration or cost/performance benchmark exists.
- sub-foundations exposed: `logical tier identity — atomic`; `project constraint fit — atomic, answered by L-01`.

### L-13 — Which option best fits the known objective? — filled (`assumed`)

- Answer: A+ — retain the physical topology and fix profile, ingress, identity, DB role, repeatability, and HA-evidence gaps.
- Source: [`three-tier-assessment-2026-08-22.md`](../../docs/architecture/three-tier-assessment-2026-08-22.md), L-01 through L-12.
- Refutation: managed services can reduce the self-managed host surface, but that advantage was not measured and currently conflicts with the stronger project constraint.
- Limits: becomes invalid if the user's real objective is persistent production with minimum server operations rather than assignment/demo evidence.
- sub-foundations exposed: `objective priority — atomic, user-owned and named-unfilled in L-14`; `implementation order — atomic, filled in assessment`.

### L-14 — Has the user selected A+ and supplied production constraints? — named-unfilled (`unknown`)

- Lead: Q-001 and Q-002 in [`OPEN-QUESTIONS.md`](../OPEN-QUESTIONS.md).
- Needed evidence: user confirmation of baseline plus workload, SLO/RTO/RPO, budget, persistence window, and operator capacity.
- sub-foundations exposed: `none until user evidence arrives`.

## Single next leaf

Fill **L-14** by asking the user whether to adopt A+ as the implementation baseline and, if this is more than an ephemeral demonstration, collecting the Q-001 production constraints.

## Known gaps by name

- Production workload and traffic shape — `unknown`, Q-001.
- Availability and recovery objectives — `unknown`, Q-001.
- Monthly AWS ceiling and operations capacity — `unknown`, Q-001.
- Final baseline choice — `unknown`, Q-002.
- Exact managed-alternative cost/performance — intentionally not researched until the objective selects that branch.

## Done-check

| Criterion | Status | Evidence |
|---|---|---|
| Goal restatement and definition of done | pass | top of this file |
| Existing assets and decisions mobilized | pass | mobilization table |
| Current repository and runtime truth mapped | pass | terrain, atomic leaves, `PRODUCT-TRUTH.md` |
| External architectural claims verified/refuted | pass | [`memory/knowledge/three-tier-architecture.md`](../knowledge/three-tier-architecture.md) |
| Options compared against one decision rule | pass | canonical assessment |
| Conditional recommendation and work order | pass | canonical assessment, L-13 |
| Available local tests | pass | frontend 4 tests/build/build-contract/audit; canonical assessment |
| Backend/Terraform local rerun | unavailable | Java, Terraform, and PowerShell absent |
| Live AWS/HA verification | unavailable | stack torn down; historical evidence and limits recorded |
| Zero-context rehearsal | pass | round 3 completed with no blocking stall; log below |
| User architecture selection | pending | L-14 / Q-002; not required to complete analysis, required before implementation |

## Rehearsal log

### Round 1 — observed 2026-08-22

- Persona: Korean-speaking repository owner/developer with basic AWS/Java/Terraform knowledge and no conversation context.
- Execution: selected the architecture, extracted the first three tasks, and applied the managed-alternative decision rule.
- Result: completed with no blocking stall.
- Non-blocking guesses: whether the next run was a development smoke or HA run; 403 versus 404; location of the HA procedure.
- Fix: made profile purpose explicit, selected 404 as the default, and linked the Phase 11 HA runbook.

### Round 2 — observed 2026-08-22

- Persona: fresh executor with the same recipient profile; asked to classify a pre-deployment sequence more broadly.
- Result: decision completed, but execution framing exposed blocking ambiguity: `ha-demo` did not match the existing `ha` variable, “before the next run” mixed pre-run work with evidence that can only be collected during a run, and the document could be mistaken for the deployment runbook.
- Fix: preserved `development`/`ha` names and NAT behavior; separated “A+ selected”, “A+ implemented”, and “A+ HA-verified”; split pre-deployment code gates, approved-run evidence gates, and later work; stated the document's decision-record boundary.

### Round 3 — observed 2026-08-22 — clean

- Persona: fresh executor with the same recipient profile, executing only the assessment's stated decision/backlog purpose.
- Execution: selected A+, distinguished all three states, classified P0/P1/P2, extracted managed-alternative conditions and user-owned decisions.
- Result: completed with **no blocking stall**.
- Non-blocking implementation details: exact acceptance evidence per P0 item, the future authenticated API allowlist, and whether image/database provisioning needs a bounded preparation run. These are implementation-spec questions, not blockers to the architecture decision, and remain outside this assessment's declared scope.

## Superseded cuts

### Skeleton v1 — superseded 2026-08-22

The first cut was “current architecture / alternatives / recommendation”. It hid security identity, evidence quality, environment profiles, and in-process boundaries inside a single current-state branch. It was superseded by v2, which separates constraint fit, system truth, fitness gaps, alternatives, and user-owned decision inputs. No finding was discarded.
