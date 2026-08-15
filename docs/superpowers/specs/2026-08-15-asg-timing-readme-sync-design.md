# ASG Timing and GitHub README Sync Design

## Context

The Phase 11 HA stack runs in `ap-northeast-2`. WEB and WAS each use a 600-second ELB health-check grace period and a 600-second rolling-refresh warmup. The observed bootstrap completed within five minutes after the deployment fixes, so the current values delay rollout completion without adding useful protection for this test environment.

GitHub uses `main` as the default branch. The Phase 11 branch contains the current deployment status and README, while `main` still shows the pre-Phase 11 state. The deployed frontend only checks backend readiness. It does not provide reminder management controls.

## Decision

Change these four values from 600 seconds to 300 seconds:

- WEB `health_check_grace_period`
- WEB `instance_warmup`
- WAS `health_check_grace_period`
- WAS `instance_warmup`

Keep the ALB health-check interval, thresholds, deregistration delay, desired capacity, and launch templates unchanged. Add a Terraform contract test that requires the four 300-second values.

Update the README to state that the deployed page is a connectivity smoke test. The text will explain that `Backend ready` proves the ALB, WEB, WAS, and readiness path. It will also state that the reminder-management UI remains unimplemented.

After verification, merge the complete Phase 11 branch into `main` and push both branches. This keeps the default GitHub README and implementation history aligned.

## Apply Safety

Generate a fresh Terraform plan from the ignored Phase 11 runtime configuration. Accept only in-place Auto Scaling Group changes with zero additions and zero destroys. Stop if Terraform plans instance, load balancer, database, network, or certificate replacement.

Apply the reviewed plan, then verify both WEB and WAS target groups report two healthy targets. Verify the frontend and backend readiness endpoints return HTTP 200 and all project alarms return `OK`.

If either tier loses healthy capacity after the change, restore the four values to 600 seconds through a reviewed Terraform plan. Do not terminate instances by hand for this timing change.

## Test and Documentation Scope

- Run the Terraform Pester suite and demonstrate the new assertion fails before implementation and passes afterward.
- Run Terraform format, validation, and the pinned Checkov policy scan.
- Run a post-apply no-drift plan.
- Update README and Phase 11 evidence with the approved 300-second setting and observed AWS result.
- Keep Terraform state, plans, tfvars, certificate material, account identifiers, ARNs, DNS names, and credentials outside Git.

## Success Criteria

- Source and runtime Terraform contain 300 seconds for the four approved settings.
- The reviewed plan has no additions or destroys and no resource replacement.
- WEB and WAS retain two healthy targets each after apply.
- HTTPS root and readiness requests return HTTP 200.
- GitHub `main` shows the current README and contains the Phase 11 implementation history.
