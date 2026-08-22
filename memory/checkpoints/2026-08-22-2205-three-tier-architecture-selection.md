# Checkpoint — three-tier architecture selection — 2026-08-22 22:05 KST

## The story so far

The repository is already a coherent physical three-tier implementation: Public ALB → private Apache WEB → Internal ALB → private external-Tomcat WAS → isolated RDS. Its strongest fit is the assignment/demo requirement recorded in accepted ADRs, so the evidence-backed recommendation is the hardened A+ form of the same topology, not a wholesale ECS migration. The analysis, verified knowledge, product truth, option matrix, prioritized gates, and three-round rehearsal are complete. No runtime architecture was changed; selection awaits the user.

## Decided

- D-001: use the ballast memory structure.
- D-002: the target uses a three-tier architecture; the exact baseline remains open.
- No new user-confirmed architecture decision was recorded. A+ is an AI recommendation, not D-003.

## Waiting on the user

- Q-002: adopt A+ as the implementation baseline, or retire the Apache/external-Tomcat evidence constraint and reopen the managed alternative?
- Q-001: if this will be more than an ephemeral demonstration, provide workload/traffic shape, availability and recovery targets, monthly AWS ceiling, and operator capacity.
- A-001 was relied on: the request was treated as analysis-first, with no runtime architecture implementation before the user's selection.

## Next first action

Ask the user to confirm A+ as the baseline and, only for a persistent production goal, answer Q-001 before creating an implementation plan.

## Tried

- Backend test/build rerun: unavailable because no Java runtime is installed; retained historical evidence with that limit instead of treating it as a current pass.
- Terraform and Pester rerun: unavailable because Terraform and PowerShell are not installed.
- Initial npm dependency install/audit network access: sandbox DNS failed; approved network retry succeeded, followed by passing tests/build/audit.
- Rehearsal round 2: broader execution framing exposed circular pre-run/in-run gates; the assessment was recut into selected/implemented/HA-verified states and passed round 3 cleanly.
