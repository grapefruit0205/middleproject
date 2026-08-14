# Codex Desktop Goal: Supervise Phase 01 Through Phase 10

Copy the prompt below into a Codex Desktop long-running Goal after closing any interactive Command Code session.

```text
Work in C:\middleproject and supervise application implementation through Phase 10.

You own review and state transitions. Command Code implements one attempt at a time through tools/orchestration/Invoke-Phase.ps1. Read each phase's model, effort, turn budget, invocation limit, branch, and allowlist from tools/orchestration/phases.json. Do not replace manifest values with global assumptions.

Before a paid invocation:
1. Read docs/orchestration/README.md, tools/orchestration/phases.json, docs/architecture/project-invariants.md, and the active phase documents.
2. Confirm the Git branch and baseline commit.
3. Run Invoke-Phase.ps1 with -DryRun and verify the model, effort, maxTurns, attemptNumber, autoAccept, working directory, and arguments.
4. Confirm that no other Command Code or coding-agent writer is active.

For each attempt:
1. Run Invoke-Phase.ps1 once.
2. Read state, stdout, stderr, result.md, and every working-tree change.
3. Preserve unrelated user changes.
4. Run the relevant tests, builds, static checks, and acceptance checks yourself. Treat result.md as a claim that requires independent evidence.
5. Compare the evidence with the brief, design, implementation plan, project invariants, ADRs, and allowlist.
6. Write docs/phases/phase-NN/review.md. Command Code cannot edit that file or choose the verdict.

For a repairable defect, record exact file, line, expected behavior, and failing command evidence. Record REVISE with Set-PhaseReview.ps1, then rerun the same phase. The next prompt treats review.md as read-only input. Respect the manifest invocation limit. Phase 10 permits a 50-turn initial attempt and one 30-turn repair.

After local evidence passes, stage only verified phase files and review.md. Exclude .commandcode, .orchestration, credentials, Terraform state, build output, and unrelated files. Commit and push the verified phase branch under the project's backup instruction. Do not merge into main without an explicit review decision. Phases 01 through 09 advance to the next manifest phase. Phase 10 is terminal only after its external gate and live evidence pass.

Treat a Command Code HEAD change, branch switch, out-of-scope edit, review.md edit, architecture conflict, credential exposure, or concurrent writer as BLOCKED. Do not reset, clean, discard, or hide those changes.

Command Code may generate Terraform and run fmt, validate, read-only plan, and static checks. It cannot run terraform apply or mutate AWS.

For a manifest phase with requiresExternalApproval, record AWAITING_APPROVAL before any external mutation. Show the user the account, region, resources, exact command, rollback and teardown plan, time limit, and cost boundary. Wait for approval of those exact details. Record RESUME_AFTER_APPROVAL with -ExternalApproval only after approval. Command Code never receives live AWS authority.

Phase 10 uses .orchestration/phase-10-state.json so the completed Phase 01-09 state remains intact. It stops before deployment, log injection, Alarm changes, instance replacement, or Secret access.

After a restart, read the supplied state path. READY may run, REVIEWING requires independent review, AWAITING_APPROVAL waits for the user, BLOCKED stops, and COMPLETE ends the goal. Never hand-edit state to skip a gate.

Report the branch, baseline, invocation count, changed paths, verification commands, verdict, commit, and push. Continue only after the current gate passes.
```
