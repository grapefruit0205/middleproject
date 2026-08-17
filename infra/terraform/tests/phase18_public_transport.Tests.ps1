$terraformRoot = Split-Path -Parent $PSScriptRoot

Describe 'Phase 18 public transport deployment boundary' {
    BeforeAll {
        $variables = Get-Content (Join-Path $terraformRoot 'variables.tf') -Raw
        $main = Get-Content (Join-Path $terraformRoot 'main.tf') -Raw
        $tier = Get-Content (Join-Path $terraformRoot 'tier.tf') -Raw
        $wasBootstrap = Get-Content (Join-Path $terraformRoot 'templates/was.sh.tftpl') -Raw
        $repositoryRoot = Split-Path -Parent (Split-Path -Parent $terraformRoot)
        $applicationConfig = Get-Content (Join-Path $repositoryRoot 'backend/src/main/resources/application.yml') -Raw
    }

    It 'keeps public transport and the Seoul plaintext provider opt-in' {
        $variables | Should Match '(?ms)variable "public_transport_enabled".*?default\s*=\s*false'
        $variables | Should Match '(?ms)variable "public_transport_seoul_realtime_enabled".*?default\s*=\s*false'
        $variables | Should Match '(?ms)variable "public_transport_seoul_realtime_enabled".*?!var\.public_transport_seoul_realtime_enabled\s*\|\|\s*var\.public_transport_enabled'
    }

    It 'requires an exact Seoul Secrets Manager ARN when transport is enabled' {
        $variables | Should Match '(?ms)variable "public_transport_secrets_arn".*?default\s*=\s*""'
        $variables | Should Match '(?ms)variable "public_transport_secrets_arn".*?!var\.public_transport_enabled.*?arn:aws:secretsmanager:ap-northeast-2:'
    }

    It 'grants only the WAS role read access to the configured transport secret' {
        $wasPolicy = [regex]::Match($tier, '(?ms)^resource "aws_iam_role_policy" "was" \{.*?^\}').Value
        $webPolicy = [regex]::Match($tier, '(?ms)^resource "aws_iam_role_policy" "web" \{.*?^\}').Value

        $wasPolicy | Should Match '(?s)var\.public_transport_enabled.*?secretsmanager:GetSecretValue.*?var\.public_transport_secrets_arn'
        $webPolicy | Should Not Match 'public_transport_secrets_arn'
    }

    It 'injects only non-secret transport configuration into the WAS service' {
        $wasBootstrap | Should Match 'Environment=APP_TRANSPORT_ENABLED=\$\{public_transport_enabled\}'
        $wasBootstrap | Should Match 'Environment=APP_TRANSPORT_SECRETS_SECRET_ID=\$\{public_transport_secrets_arn\}'
        $wasBootstrap | Should Match 'Environment=APP_TRANSPORT_SEOUL_REALTIME_ENABLED=\$\{public_transport_seoul_realtime_enabled\}'
        $wasBootstrap | Should Not Match 'seoulOpenDataKey'
        $wasBootstrap | Should Not Match 'dataGoKrServiceKey'
        $wasBootstrap | Should Not Match '(?s)get-secret-value.*public_transport'
        $applicationConfig | Should Match 'enabled:\s*\$\{APP_TRANSPORT_ENABLED:false\}'
        $applicationConfig | Should Match 'secrets-secret-id:\s*\$\{APP_TRANSPORT_SECRETS_SECRET_ID:'
        $applicationConfig | Should Match 'seoul-realtime-enabled:\s*\$\{APP_TRANSPORT_SEOUL_REALTIME_ENABLED:false\}'
    }

    It 'passes all transport deployment inputs to the WAS launch template' {
        $tier | Should Match 'public_transport_enabled\s*=\s*var\.public_transport_enabled'
        $tier | Should Match 'public_transport_secrets_arn\s*=\s*var\.public_transport_secrets_arn'
        $tier | Should Match 'public_transport_seoul_realtime_enabled\s*=\s*var\.public_transport_seoul_realtime_enabled'
    }

    It 'rolls the WAS launch template when the backend artifact changes' {
        $tier | Should Match 'backend_artifact_hash\s*=\s*filemd5\(var\.backend_artifact_path\)'
        $wasBootstrap | Should Match '# Backend artifact hash: \$\{backend_artifact_hash\}'
    }

    It 'keeps the WAS bootstrap portable to Linux cloud-init' {
        $wasBootstrap | Should Not Match "`r`n"
    }

    It 'opens plaintext HTTP egress only for an explicitly enabled Seoul realtime demo' {
        $wasSecurityGroup = [regex]::Match($main, '(?ms)^resource "aws_security_group" "was" \{.*?^\}').Value

        $wasSecurityGroup | Should Match '(?s)dynamic "egress".*?for_each\s*=\s*var\.public_transport_enabled\s*&&\s*var\.public_transport_seoul_realtime_enabled.*?from_port\s*=\s*80.*?to_port\s*=\s*80.*?protocol\s*=\s*"tcp".*?cidr_blocks\s*=\s*\["0\.0\.0\.0/0"\]'
    }
}
