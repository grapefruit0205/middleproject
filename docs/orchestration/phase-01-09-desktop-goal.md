# Codex Desktop Goal: Supervise Phase 01 Through Phase 09

Copy the prompt below into a Codex Desktop long-running Goal after closing the existing interactive Command Code session.

```text
Work in C:\middleproject and supervise application implementation from Phase 01 through Phase 09.

You are the sole reviewer and state-machine owner. Command Code is an implementation worker. Invoke it once per attempt through tools/orchestration/Invoke-Phase.ps1. The manifest at tools/orchestration/phases.json fixes the model at gpt-5.6-luna, effort at max, auto-accept on, and a maximum of three invocations per phase.

Before the first paid invocation:
1. Read docs/orchestration/README.md, tools/orchestration/phases.json, docs/architecture/project-invariants.md, and the active phase brief and implementation prompt.
2. Confirm the expected Git branch and baseline commit.
3. Run Invoke-Phase.ps1 with -DryRun and verify the model, effort, autoAccept, working directory, and arguments.
4. Confirm no other Command Code or coding-agent writer is active. If one is active, stop and ask me to close the exact session.

For each phase:
1. Run exactly one implementation attempt with tools/orchestration/Invoke-Phase.ps1.
2. Read the persisted state, stdout, stderr, and phase result file.
3. Inspect every working-tree change and untracked file. Preserve unrelated user changes.
4. Run the phase's relevant tests, builds, static checks, and acceptance checks yourself. Do not accept Command Code's result.md as proof.
5. Compare the observed evidence with the brief, implementation prompt, project invariants, ADRs, and allowlist.
6. Write docs/phases/phase-NN/review.md. Command Code must not edit that file or choose the verdict.

If review finds a repairable defect, write exact file, line, expected behavior, and failing command evidence. Record REVISE with Set-PhaseReview.ps1, then rerun the same phase. The next prompt treats review.md as read-only repair input. Stop at the three-invocation limit.

If review passes, stage only verified active-phase files and review.md. Do not stage .commandcode, .orchestration, credentials, build output, or unrelated user files. Commit locally, pass the new commit SHA to Set-PhaseReview.ps1, create the next manifest branch from that commit, and continue. Do not push or merge. Phase 09 PASS ends the loop in COMPLETE.

Treat any Command Code HEAD change, branch switch, out-of-scope edit, review.md edit, architecture conflict, credential exposure, or concurrent writer as BLOCKED. Do not reset, clean, discard, or silently repair those changes.

Phase 04 permits Terraform generation, fmt, validate, plan, and static checks. Do not run terraform apply or mutate AWS.

For Phase 05, prepare and review the exact live AWS action first. Record AWAITING_APPROVAL before any mutation and show me the account, region, resources, command, rollback/deletion plan, and cost boundary. Wait for my explicit approval of those exact details. After approval, record RESUME_AFTER_APPROVAL with -ExternalApproval. Command Code never receives permission to perform the live action. Run only the approved command, collect evidence, then finish independent review. Use mocks or recorded fixtures for Phases 06 and 07 unless I grant another limited approval.

After a restart, resume from .orchestration/state.json. READY may run, REVIEWING requires review, AWAITING_APPROVAL waits for me, BLOCKED stops, and COMPLETE ends the goal. Never hand-edit state to skip a gate.

Report each phase with the branch, baseline, invocation count, changed paths, verification commands, verdict, and local commit. Continue to the next phase only after PASS. Pause for my input only at an approval gate or a blocker that requires authority or a material product decision.
```
