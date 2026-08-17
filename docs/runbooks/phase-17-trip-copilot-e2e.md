# Phase 17 Trip Copilot E2E Runbook

## Purpose

Deploy the private Trip Copilot stack for a short AWS verification window, connect the OpenAI Secure MCP Tunnel and Firebase Cloud Messaging only when their external configuration is available, verify the Android companion, and remove every project-scoped cost resource.

The public ALB must never expose `/api/mcp`. ChatGPT reaches MCP only through the outbound Secure MCP Tunnel client running against the WEB loopback listener. Android and the dashboard use the public ALB.

## Required external configuration

- A trusted DNS name and matching public ACM certificate in `ap-northeast-2`. The imported `phase-11-ha.test` test certificate is not valid for Android or production browsers.
- An OpenAI Secure MCP Tunnel ID and runtime API key. Store the key as a dedicated AWS Secrets Manager secret; never put it in tfvars, Terraform state, user data, logs, or Git.
- An official `openai/tunnel-client` release asset URL and its verified SHA-256.
- A Firebase project ID.
- Firebase Admin service-account JSON stored as a dedicated Secrets Manager secret in `ap-northeast-2`.
- Android `android/app/google-services.json`. It is ignored by Git and activates the Google Services Gradle plugin only when present.
- A connected Android device or emulator with `adb devices` showing `device`.

## Build artifacts

Use a real trusted HTTPS application URL. The Gradle property is non-secret and is compiled into the APK.

```powershell
$env:ANDROID_HOME = 'C:\path\to\android-sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME

Push-Location android
.\gradlew.bat clean testDebugUnitTest assembleDebug assembleRelease `
  -PtripCopilotBaseUrl=https://trip.example.com `
  --no-daemon --console=plain
Pop-Location

Push-Location frontend
npm ci --include=dev
npm run build
npm run verify:build
Pop-Location

Push-Location backend
.\gradlew.bat clean test bootWar --no-daemon --console=plain
Pop-Location
```

The release APK is unsigned by design. Sign it with a project-owned keystore before installing on a physical device. Never commit the keystore or passwords.

## Terraform inputs

Keep tfvars, artifacts, state, and plans under an ignored runtime directory. In addition to the normal HA inputs, enable the integrations only when all corresponding values exist.

```hcl
trip_demo_owner_id = "demo-owner"

tunnel_client_enabled                  = true
tunnel_id                              = "tunnel_..."
tunnel_runtime_api_key_secret_arn      = "arn:aws:secretsmanager:ap-northeast-2:...:secret:..."
tunnel_client_download_url             = "https://github.com/openai/tunnel-client/releases/download/.../..."
tunnel_client_sha256                   = "...64 hexadecimal characters..."

notification_push_enabled                    = true
notification_push_project_id                 = "your-firebase-project"
notification_push_service_account_secret_arn = "arn:aws:secretsmanager:ap-northeast-2:...:secret:..."
```

When an integration is disabled, its IAM secret permission is absent and its non-secret Terraform output reports `enabled = false`.

## Plan, apply, and verify

```powershell
terraform -chdir="$tfDir" fmt -check
terraform -chdir="$tfDir" init -input=false -reconfigure -backend-config="$backendConfig"
terraform -chdir="$tfDir" validate
terraform -chdir="$tfDir" plan -input=false -no-color -var-file="$tfvarsPath" -out="$applyPlan"
terraform -chdir="$tfDir" show -no-color "$applyPlan"
terraform -chdir="$tfDir" apply -input=false "$applyPlan"
```

Verify all of the following:

1. Public `/healthz`, dashboard, and `/api/actuator/health/readiness` return 200.
2. Public `/api/mcp` returns 403.
3. Both WEB and WAS target groups have two healthy targets.
4. ChatGPT developer mode can initialize the private MCP app through the configured Tunnel and list all 25 tools after Phase 18 (17 tools on the Phase 17 baseline).
5. `create_device_pairing_code` returns one five-minute code; an Android device exchanges it once, lists trips, and an unauthenticated device receives 401.
6. Firebase registration reaches the backend, a test reminder produces FCM data, the local Android alarm appears, and ACK is reflected in delivery state.
7. The same UUID correlation ID appears in WEB access, WAS access, and JSON application logs.
8. `terraform plan -detailed-exitcode` returns 0 after the test.

Install a debug APK only on an authorized test device:

```powershell
adb devices -l
adb install -r .\android\app\build\outputs\apk\debug\app-debug.apk
```

## Time and cost guard

- Record UTC and KST apply timestamps.
- At three hours, start cleanup and do not begin a new experiment.
- Four hours is the absolute end time, not a target duration.
- Cost Explorer is delayed evidence; determine cleanup from live resource inventory.

## Destroy and inventory

Create and inspect a fresh destroy plan. Apply only that saved plan.

```powershell
terraform -chdir="$tfDir" plan -destroy -input=false -no-color `
  -var-file="$tfvarsPath" -out="$destroyPlan"
terraform -chdir="$tfDir" show -no-color "$destroyPlan"
terraform -chdir="$tfDir" apply -input=false "$destroyPlan"
terraform -chdir="$tfDir" state list
```

Confirm zero project-scoped EC2/EBS, ASG, launch templates, ALB/target groups, NAT/EIP, RDS/manual snapshots, S3 buckets, SQS/DLQ, Scheduler groups, CloudWatch log groups/alarms, IAM roles/profiles, SSM documents, and VPC resources. Do not delete unrelated `saa-*` resources or a pre-existing ACM certificate unless it has a separate approved lifecycle.
