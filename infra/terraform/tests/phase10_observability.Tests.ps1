$terraformRoot = Split-Path -Parent $PSScriptRoot

Describe 'Phase 10 observability and security contract' {
    BeforeAll {
        $observability = Get-Content (Join-Path $terraformRoot 'observability.tf') -Raw
        $security = Get-Content (Join-Path $terraformRoot 'security.tf') -Raw
        $tier = Get-Content (Join-Path $terraformRoot 'tier.tf') -Raw
        $webBootstrap = Get-Content (Join-Path $terraformRoot 'templates/web.sh.tftpl') -Raw
        $wasBootstrap = Get-Content (Join-Path $terraformRoot 'templates/was.sh.tftpl') -Raw
    }

    It 'creates the approved log groups with retention' {
        foreach ($group in @('/middleproject/${var.environment}/web/apache-access', '/middleproject/${var.environment}/web/apache-error', '/middleproject/${var.environment}/was/tomcat-access', '/middleproject/${var.environment}/was/application')) {
            $observability | Should Match ([regex]::Escape($group))
        }
        $observability | Should Match 'retention_in_days\s*=\s*14'
        $observability | Should Match ([regex]::Escape('/middleproject/${var.environment}/ssm/session'))
        $observability | Should Match 'retention_in_days\s*=\s*30'
    }

    It 'ships cross-layer logs and host metrics with bounded bootstrap retries' {
        $webBootstrap | Should Match 'amazon-cloudwatch-agent'
        $wasBootstrap | Should Match 'amazon-cloudwatch-agent'
        $webBootstrap | Should Match 'for attempt in 1 2 3 4 5'
        $wasBootstrap | Should Match 'for attempt in 1 2 3 4 5'
        $webBootstrap | Should Match 'X-Amzn-Trace-Id'
        $webBootstrap | Should Match 'X-Correlation-Id'
        $wasBootstrap | Should Match 'X-Amzn-Trace-Id'
        $wasBootstrap | Should Match 'INSTANCE_ID'
        $wasBootstrap | Should Match 'chown tomcat:tomcat /var/log/middleproject'
        $wasBootstrap | Should Not Match 'secret-payload-canary'
    }

    It 'defines all required alarms with valid dimensions and thresholds' {
        foreach ($alarm in @('public_unhealthy_hosts', 'internal_unhealthy_hosts', 'public_target_5xx', 'internal_target_5xx', 'scheduler_target_error', 'scheduler_invocation_dropped', 'reminder_queue_age', 'reminder_queue_visible', 'reminder_dlq', 'delivery_terminal_failures')) {
            $observability | Should Match ('aws_cloudwatch_metric_alarm" "' + $alarm + '"')
        }
        $observability | Should Match 'UnHealthyHostCount'
        $observability | Should Match 'HTTPCode_Target_5XX_Count'
        $observability | Should Match 'TargetErrorCount'
        $observability | Should Match 'InvocationDroppedCount'
        $observability | Should Match 'ApproximateAgeOfOldestMessage'
        $observability | Should Match 'reminder.delivery.terminal.failures'
    }

    It 'keeps compute private and hardened with tier-specific IAM' {
        $tier | Should Match 'associate_public_ip_address\s*=\s*false'
        $tier | Should Match 'http_tokens\s*=\s*"required"'
        $tier | Should Match 'volume_type\s*=\s*"gp3"'
        $tier | Should Match 'encrypted\s*=\s*true'
        $tier | Should Not Match 'from_port\s*=\s*22'
        $tier | Should Not Match 'bastion'
        $security | Should Match 'aws_iam_role_policy" "web_observability"'
        $security | Should Match 'aws_iam_role_policy" "was_observability"'
        $security | Should Not Match 'secretsmanager:GetSecretValue'
        $tier | Should Match 'aws_db_instance.this.master_user_secret\[0\].secret_arn'
    }

    It 'streams project SSM sessions and stores encrypted ALB logs privately' {
        $security | Should Match 'aws_ssm_document" "session_logging"'
        $security | Should Match 'cloudWatchLogGroupName'
        $observability | Should Match 'aws_s3_bucket" "alb_access_logs"'
        $observability | Should Match 'aws_s3_bucket_public_access_block" "alb_access_logs"'
        $observability | Should Match 'aws_s3_bucket_server_side_encryption_configuration" "alb_access_logs"'
        $observability | Should Match 'expiration\s*\{\s*days\s*=\s*30'
        $tier | Should Match 'access_logs\s*\{'
    }

    It 'scopes SSM session logging and WEB metrics to approved resources' {
        $security | Should Match 'cloudWatchEncryptionEnabled\s*=\s*false'
        $security | Should Not Match 'cloudWatchEncryptionEnabled\s*=\s*true'
        ([regex]::Matches($security, 'aws_cloudwatch_log_group\.ssm_session\.arn')).Count | Should Be 4
        ([regex]::Matches($security, 'logs:DescribeLogGroups')).Count | Should Be 2

        $webPolicyStart = $security.IndexOf('resource "aws_iam_role_policy" "web_observability"')
        $wasPolicyStart = $security.IndexOf('resource "aws_iam_role_policy" "was_observability"')
        $webPolicy = $security.Substring($webPolicyStart, $wasPolicyStart - $webPolicyStart)
        $webPolicy | Should Match 'MiddleProject/Host/\$\{var\.environment\}'
        $webPolicy | Should Not Match 'MiddleProject/Reminder/\$\{var\.environment\}'
    }
}
