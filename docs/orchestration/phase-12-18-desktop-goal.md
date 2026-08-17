# Codex Desktop Goal: Supervise Phase 12 Through Phase 18

아래 내용을 Codex Desktop 장기 Goal에 사용합니다. 실행 전 오케스트레이션 준비 커밋이 `main`에 병합되고 Phase 12 branch가 그 commit에서 생성되어야 합니다.

```text
Work in C:\middleproject and supervise Trip Copilot implementation from Phase 12 through Phase 18.

Use tools/orchestration/phases-12-plus.json as the only Phase 12+ manifest and .orchestration/phase-12-18-state.json as the only Phase 12+ state. Do not use or rewrite the completed Phase 01-10 state.

Command Code implements one attempt at a time through tools/orchestration/Invoke-Phase.ps1. The manifest keeps deepseek/deepseek-v4-flash/max for completed Phase 12-17 history and selects google/gemini-3.7-flash/high for Phase 18. Every implementation or correction attempt uses 100 turns and each phase permits at most ten effective invocations. If an attempt is incomplete or Codex finds a defect, Codex records concrete evidence and starts a fresh focused attempt while budget remains. An invocation counts only after the runner receives a process result. Zero, nonzero, max-turn, Git-guard, and path-scope results count. Startup refusal, a pre-execution exception, or an interrupted process with no result does not count. Before recovering an interrupted state, confirm that no CMDC writer remains active and that the interrupted process created no new working-tree changes. Before the first paid call, run cmdc --list-models and stop if the exact model id is absent.

Worker liveness uses evidence, not stdout alone. Require the first working-tree edit or child validation process within 60 seconds. After progress starts, interrupt and refocus an attempt only when 60 consecutive seconds pass with no new stdout, no working-tree/file-timestamp change, and no active test/build child process. A quiet but active test/build does not count as idle. Record an interrupted no-result attempt as ineffective and resume with a smaller evidence-based task.

Before each attempt:
1. Read docs/orchestration/README.md, tools/orchestration/phases-12-plus.json, docs/architecture/project-invariants.md, ADR-005, and the active Phase documents.
2. Confirm the current branch equals the manifest branch and record HEAD as the baseline.
3. Run Invoke-Phase.ps1 with -ManifestPath, -StatePath, and -DryRun. Verify model, effort, maxTurns, attemptNumber, branch, baseline, --auto-accept, and --yolo.
4. Confirm no other Command Code or coding-agent writer is active.

Run one CMDC attempt. Then read state, stdout, stderr, result.md, and every working-tree change. Preserve unrelated user changes. Run the relevant tests, builds, contract checks, and acceptance checks yourself. Treat result.md as an unverified claim. The main Codex Desktop task owns the final review and verdict; subagents may collect focused evidence but cannot issue PASS.

Write docs/phases/phase-NN/review.md with PASS, REVISE, BLOCKED, or AWAITING_APPROVAL. Command Code cannot edit review.md, create commits, push, merge, run terraform apply, or mutate AWS.

For REVISE, record the exact file, line, expected behavior, and failing command. Call Set-PhaseReview.ps1 with the same -ManifestPath and -StatePath, then run a fresh focused 100-turn correction attempt. Repeat the Codex review/correction loop only while the phase's ten-invocation budget remains.

For PASS, stage only the manifest allowlist plus review.md. Exclude credentials, .commandcode, .orchestration, Terraform state/plan, and build output. Commit and push the verified Phase branch. Pass the commit SHA as -NextBaselineCommit, create the next manifest branch from that SHA, and continue. Do not merge into main without an explicit review decision.

Use Codex Terra/high for routine Phase 18 diff, build, API-contract, and security verification. Escalate the final or affected review to Codex Sol/high only when an unexpected Terraform change or destroy, IAM/KMS/security-group risk, architecture-contract conflict, or provider authentication/retry/idempotency uncertainty remains. Do not use max by default.

Phase 12-18 is a single-owner private demo. Do not add Cognito or OIDC. ChatGPT reaches /api/mcp only through Secure MCP Tunnel. Public ALB must reject /api/mcp. Android uses one-time Pairing Codes and revocable Device Tokens.

Phase 17 requires external approval. CMDC may prepare Terraform and run fmt, validate, read-only plan, and static checks. Before apply, failure injection, RDS failover, Alarm mutation, or destroy, record AWAITING_APPROVAL and show the exact account, region, command, resource list, rollback, cost boundary, and absolute end time. Record RESUME_AFTER_APPROVAL only after approval for those exact actions. Codex or the user runs the approved AWS command and preserves evidence.

After a restart, read the Phase 12+ state. READY may run, REVIEWING requires Codex review, AWAITING_APPROVAL waits for the exact external approval, BLOCKED stops, and COMPLETE ends after Phase 18. Do not hand-edit state or skip a gate.

Report the active Phase, branch, baseline, attempt count, changed paths, verification commands, verdict, commit, and push after each gate.
```
