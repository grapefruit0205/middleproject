# Codex Desktop Goal: Supervise Phase 12 Through Phase 18

아래 내용을 Codex Desktop 장기 Goal에 사용합니다. 실행 전 오케스트레이션 준비 커밋이 `main`에 병합되고 Phase 12 branch가 그 commit에서 생성되어야 합니다.

```text
Work in C:\middleproject and supervise Trip Copilot implementation from Phase 12 through Phase 18.

Use tools/orchestration/phases-12-plus.json as the only Phase 12+ manifest and .orchestration/phase-12-18-state.json as the only Phase 12+ state. Do not use or rewrite the completed Phase 01-10 state.

Command Code implements one attempt at a time through tools/orchestration/Invoke-Phase.ps1. The manifest requires model deepseek/deepseek-v4-pro, effort max, 100 turns for the initial attempt, 100 turns for one repair, and at most two invocations per phase. Before the first paid call, run cmdc --list-models and stop if the exact model id is absent.

Before each attempt:
1. Read docs/orchestration/README.md, tools/orchestration/phases-12-plus.json, docs/architecture/project-invariants.md, ADR-005, and the active Phase documents.
2. Confirm the current branch equals the manifest branch and record HEAD as the baseline.
3. Run Invoke-Phase.ps1 with -ManifestPath, -StatePath, and -DryRun. Verify model, effort, maxTurns, attemptNumber, branch, baseline, --auto-accept, and --yolo.
4. Confirm no other Command Code or coding-agent writer is active.

Run one CMDC attempt. Then read state, stdout, stderr, result.md, and every working-tree change. Preserve unrelated user changes. Run the relevant tests, builds, contract checks, and acceptance checks yourself. Treat result.md as an unverified claim.

Write docs/phases/phase-NN/review.md with PASS, REVISE, BLOCKED, or AWAITING_APPROVAL. Command Code cannot edit review.md, create commits, push, merge, run terraform apply, or mutate AWS.

For REVISE, record the exact file, line, expected behavior, and failing command. Call Set-PhaseReview.ps1 with the same -ManifestPath and -StatePath, then run the one allowed repair attempt.

For PASS, stage only the manifest allowlist plus review.md. Exclude credentials, .commandcode, .orchestration, Terraform state/plan, and build output. Commit and push the verified Phase branch. Pass the commit SHA as -NextBaselineCommit, create the next manifest branch from that SHA, and continue. Do not merge into main without an explicit review decision.

Use Codex Sol/xhigh for Phase 12-16 and 18 verification. Use Sol/max for the Phase 17 Terraform, IAM, security, and evidence gate.

Phase 12-18 is a single-owner private demo. Do not add Cognito or OIDC. ChatGPT reaches /api/mcp only through Secure MCP Tunnel. Public ALB must reject /api/mcp. Android uses one-time Pairing Codes and revocable Device Tokens.

Phase 17 requires external approval. CMDC may prepare Terraform and run fmt, validate, read-only plan, and static checks. Before apply, failure injection, RDS failover, Alarm mutation, or destroy, record AWAITING_APPROVAL and show the exact account, region, command, resource list, rollback, cost boundary, and absolute end time. Record RESUME_AFTER_APPROVAL only after approval for those exact actions. Codex or the user runs the approved AWS command and preserves evidence.

After a restart, read the Phase 12+ state. READY may run, REVIEWING requires Codex review, AWAITING_APPROVAL waits for the exact external approval, BLOCKED stops, and COMPLETE ends after Phase 18. Do not hand-edit state or skip a gate.

Report the active Phase, branch, baseline, attempt count, changed paths, verification commands, verdict, commit, and push after each gate.
```
