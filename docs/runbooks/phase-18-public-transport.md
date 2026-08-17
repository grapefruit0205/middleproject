# Phase 18 Public Transport Runbook

## Purpose

Enable the private Trip Copilot WAS tier to query the approved Seoul Open Data and TAGO operations. The Android app and ChatGPT MCP app call the existing application APIs; neither client receives a public-data API key.

## Security boundary

- Store the JSON object with `seoulOpenDataKey`, `dataGoKrServiceKey`, and `kakaoLocalRestApiKey` in a dedicated Secrets Manager secret in `ap-northeast-2`. The Kakao key is required for place resolution, nearby subway discovery, and public-transit route previews.
- Put only the secret ARN in ignored runtime tfvars. Never put either key value in Git, Terraform variables, plans, state, EC2 user data, logs, APKs, or MCP responses.
- Terraform grants `secretsmanager:GetSecretValue` for that exact ARN only to the WAS role. The WEB role has no permission to read it.
- The Seoul real-time subway provider uses a legacy plaintext-HTTP upstream and remains disabled by default. TAGO HTTPS operations remain available.
- When the Seoul demo flag is explicitly enabled, Terraform adds TCP/80 egress to the WAS security group only. Disable the flag after the demo to remove both the application gate and that egress rule.
- The transport APIs are read-only. Booking is an allowlisted HTTPS handoff to official applications/sites.

## Deployment inputs

Add these non-secret values to the ignored deployment tfvars:

```hcl
public_transport_enabled                = true
public_transport_secrets_arn            = "arn:aws:secretsmanager:ap-northeast-2:123456789012:secret:project/phase18/public-data-api-keys-XXXXXX"
public_transport_seoul_realtime_enabled = false
```

Do not enable the Seoul real-time provider merely to make a smoke test pass. Enable it only for an explicitly accepted, time-bounded demonstration of that HTTP upstream.

## Build and deploy

Build a new backend artifact before planning, then copy the current Terraform source into the ignored runtime directory while retaining its local state and the backend-free runtime `versions.tf`.

```powershell
Push-Location backend
.\gradlew.bat clean test bootWar --no-daemon --console=plain
Pop-Location

terraform -chdir="$tfDir" fmt -check
terraform -chdir="$tfDir" init -input=false
terraform -chdir="$tfDir" validate
terraform -chdir="$tfDir" plan -input=false -no-color -var-file="$tfvarsPath" -out="$applyPlan"
terraform -chdir="$tfDir" show -no-color "$applyPlan"
terraform -chdir="$tfDir" apply -input=false "$applyPlan"
```

The expected infrastructure delta is limited to the backend artifact object, the WAS inline IAM policy, a new WAS launch-template version, and replacement of the two WAS instances through the existing Auto Scaling Group. A plan that creates or destroys the VPC, RDS, ALBs, NAT Gateway, subnets, or unrelated resources must not be applied.

## Verification

1. Confirm both WAS target-group targets return healthy after the instance refresh.
2. Confirm public `/healthz` and `/api/actuator/health/readiness` return 200 and public `/api/mcp` remains 403.
3. Confirm unauthenticated `/api/device/transport/...` requests return 401.
4. With a valid paired device token, run one TAGO station/bus lookup and verify a typed success, empty, or sanitized provider-failure envelope.
5. Through the private MCP connector, list all 35 tools. Run one TAGO lookup, one `resolve_place` lookup, and one `preview_public_transit_route` request. Verify API keys never appear in the response or CloudWatch logs.
6. On Android, verify manual coordinates work without location permission and that foreground location is requested only after tapping the current-location action.
7. Verify official handoff links use HTTPS and unsupported KTX, SRT, and airline booking is not scraped.

## Rollback

Set `public_transport_enabled = false`, create and inspect a new saved plan, then apply it. This removes the WAS secret permission and transport environment activation without deleting the pre-existing secret. Secret deletion is a separate, explicit operation.
