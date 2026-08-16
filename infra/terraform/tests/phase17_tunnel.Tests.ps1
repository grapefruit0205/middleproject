$terraformRoot = Split-Path -Parent $PSScriptRoot

Describe 'Phase 17 private MCP tunnel behavior' {
    BeforeAll {
        $bash = 'C:\Program Files\Git\bin\bash.exe'
        if (-not (Test-Path $bash)) {
            throw 'Git Bash is required for the tunnel bootstrap behavior tests.'
        }

        $webBootstrap = Get-Content (Join-Path $terraformRoot 'templates/web.sh.tftpl') -Raw
        $tunnelInstaller = Get-Content (Join-Path $terraformRoot 'templates/install-tunnel-client.sh') -Raw
        $wasBootstrap = Get-Content (Join-Path $terraformRoot 'templates/was.sh.tftpl') -Raw
        $variables = Get-Content (Join-Path $terraformRoot 'variables.tf') -Raw
        $tier = Get-Content (Join-Path $terraformRoot 'tier.tf') -Raw
        $repositoryRoot = Split-Path -Parent (Split-Path -Parent $terraformRoot)
        $androidRootBuild = Get-Content (Join-Path $repositoryRoot 'android/build.gradle.kts') -Raw
        $androidBuild = Get-Content (Join-Path $repositoryRoot 'android/app/build.gradle.kts') -Raw
        $androidIgnore = Get-Content (Join-Path $repositoryRoot 'android/.gitignore') -Raw
        $backendConfig = Get-Content (Join-Path $repositoryRoot 'android/app/src/main/java/com/middleproject/tripcopilot/data/BackendConfig.kt') -Raw
    }

    It 'renders separate public and loopback Apache paths so only the tunnel can proxy MCP' {
        $match = [regex]::Match(
            $webBootstrap,
            '(?ms)^cat >/etc/httpd/conf\.d/reminder\.conf <<EOF\r?\n.*?^EOF\r?$'
        )
        $match.Success | Should Be $true

        $outputPath = Join-Path $TestDrive 'reminder.conf'
        $outputPathForBash = $outputPath.Replace('\', '/')
        $fragment = $match.Value.Replace(
            'cat >/etc/httpd/conf.d/reminder.conf',
            "cat >'$outputPathForBash'"
        )
        $fragment = $fragment.Replace('${internal_alb_dns}', 'internal.example')
        $fragment = $fragment.Replace('${tunnel_loopback_port}', '8090')
        $fragment = [regex]::Replace($fragment, '\$\{[^}]+\}', 'fixture')

        $scriptPath = Join-Path $TestDrive 'render-phase17-apache.sh'
        [System.IO.File]::WriteAllText($scriptPath, $fragment, [System.Text.UTF8Encoding]::new($false))
        $scriptPathForBash = $scriptPath.Replace('\', '/')

        & $bash -u $scriptPathForBash 2>&1 | Out-Null
        $LASTEXITCODE | Should Be 0

        $config = Get-Content $outputPath -Raw
        $public = [regex]::Match($config, '(?ms)<VirtualHost \*:80>.*?</VirtualHost>').Value
        $loopback = [regex]::Match($config, '(?ms)<VirtualHost 127\.0\.0\.1:8090>.*?</VirtualHost>').Value

        $public | Should Match 'ProxyPass\s+/api/mcp\s+!'
        $public | Should Match '(?ms)<LocationMatch.+api/mcp.+Require all denied.+</LocationMatch>'
        $public | Should Match 'ProxyPass\s+/api/\s+http://internal\.example/api/'
        $loopback | Should Match 'ProxyPass\s+/api/mcp\s+http://internal\.example/api/mcp'
        $loopback | Should Not Match 'Require all denied'
    }

    It 'installs the published Linux ZIP as an executable tunnel-client binary' {
        $installerPath = Join-Path $terraformRoot 'templates/install-tunnel-client.sh'
        (Test-Path $installerPath) | Should Be $true

        $fixtureDirectory = Join-Path $TestDrive 'release-fixture'
        New-Item -ItemType Directory -Path $fixtureDirectory -Force | Out-Null
        $fixtureBinary = Join-Path $fixtureDirectory 'tunnel-client'
        [System.IO.File]::WriteAllText(
            $fixtureBinary,
            "#!/usr/bin/env bash`nprintf 'tunnel-ok\n'`n",
            [System.Text.UTF8Encoding]::new($false)
        )

        $fixtureArchive = Join-Path $TestDrive 'tunnel-client-linux-amd64.zip'
        Compress-Archive -LiteralPath $fixtureBinary -DestinationPath $fixtureArchive
        $expectedSha256 = (Get-FileHash -Algorithm SHA256 $fixtureArchive).Hash.ToLowerInvariant()
        $installedBinary = Join-Path $TestDrive 'installed-tunnel-client'

        $installerForBash = $installerPath.Replace('\', '/')
        $archiveUrl = 'file:///' + $fixtureArchive.Replace('\', '/')
        $installedForBash = $installedBinary.Replace('\', '/')

        & $bash $installerForBash $archiveUrl $expectedSha256 $installedForBash 2>&1 | Out-Null
        $LASTEXITCODE | Should Be 0
        (Test-Path $installedBinary) | Should Be $true

        $output = & $bash $installedForBash 2>&1
        $LASTEXITCODE | Should Be 0
        (($output -join "`n").Trim()) | Should Be 'tunnel-ok'
    }

    It 'keeps the tunnel opt-in and validates every deployment input' {
        $variables | Should Match '(?ms)variable "tunnel_client_enabled".*?default\s*=\s*false'
        $variables | Should Match '(?ms)variable "tunnel_id".*?tunnel_[0-9A-Za-z_-]'
        $variables | Should Match '(?ms)variable "tunnel_runtime_api_key_secret_arn".*?arn:aws:secretsmanager:ap-northeast-2:'
        $variables | Should Match '(?ms)variable "tunnel_client_download_url".*?github.*?openai.*?tunnel-client.*?releases'
        $variables | Should Match '(?ms)variable "tunnel_client_sha256".*?\[0-9A-Fa-f\].*?64'
        $variables | Should Match '(?ms)variable "tunnel_loopback_port".*?>=\s*1024.*?<=\s*65535'
    }

    It 'grants only the WEB role exact secret access and injects the key at service start' {
        $wasPolicy = [regex]::Match(
            $tier,
            '(?ms)^resource "aws_iam_role_policy" "was" \{.*?^\}'
        ).Value

        $tier | Should Match '(?ms)aws_iam_role_policy" "web".*?secretsmanager:GetSecretValue.*?var\.tunnel_runtime_api_key_secret_arn'
        $wasPolicy | Should Not Match 'tunnel_runtime_api_key_secret_arn'
        $webBootstrap | Should Match '%\{ if tunnel_client_enabled \}'
        $webBootstrap | Should Match '(?s)install-tunnel-client\.sh.+\$\{tunnel_client_download_url\}.+\$\{tunnel_client_sha256\}'
        $tunnelInstaller | Should Match '(?s)curl.+archive_url'
        $tunnelInstaller | Should Match '(?s)expected_sha256.+sha256sum'
        $tunnelInstaller | Should Match '(?s)unzip -p.+tunnel-client'
        $webBootstrap | Should Match '(?s)aws secretsmanager get-secret-value.+\$\{tunnel_runtime_api_key_secret_arn\}'
        $webBootstrap | Should Match 'CONTROL_PLANE_API_KEY'
        $webBootstrap | Should Match '(?s)tunnel-client init.+--tunnel-id.+\$\{tunnel_id\}'
        $webBootstrap | Should Match '(?s)--mcp-server-url.+127\.0\.0\.1:\$\{tunnel_loopback_port\}/api/mcp'
        $webBootstrap | Should Match 'ExecStart=.*tunnel-client-start'
        $webBootstrap | Should Not Match 'tunnel_runtime_api_key\s*='
    }

    It 'allows the tunnel-client service account to execute its root-owned launcher' {
        $webBootstrap | Should Match '(?ms)^chown root:tunnel-client /usr/local/sbin/tunnel-client-start\r?\nchmod 0750 /usr/local/sbin/tunnel-client-start$'
    }

    It 'wires the fixed demo owner and opt-in Firebase credential without embedding a secret' {
        $variables | Should Match '(?ms)variable "trip_demo_owner_id".*?default\s*=\s*"demo-owner"'
        $variables | Should Match '(?ms)variable "notification_push_enabled".*?default\s*=\s*false'
        $variables | Should Match '(?ms)variable "notification_push_project_id".*?notification_push_enabled'
        $variables | Should Match '(?ms)variable "notification_push_service_account_secret_arn".*?arn:aws:secretsmanager:ap-northeast-2:'

        $wasPolicy = [regex]::Match($tier, '(?ms)^resource "aws_iam_role_policy" "was" \{.*?^\}').Value
        $webPolicy = [regex]::Match($tier, '(?ms)^resource "aws_iam_role_policy" "web" \{.*?^\}').Value
        $wasPolicy | Should Match '(?s)var\.notification_push_enabled.*?secretsmanager:GetSecretValue.*?var\.notification_push_service_account_secret_arn'
        $webPolicy | Should Not Match 'notification_push_service_account_secret_arn'

        $wasBootstrap | Should Match 'Environment=TRIP_DEMO_OWNER_ID=\$\{trip_demo_owner_id\}'
        $wasBootstrap | Should Match 'Environment=NOTIFICATION_PUSH_ENABLED=\$\{notification_push_enabled\}'
        $wasBootstrap | Should Match 'Environment=NOTIFICATION_PUSH_PROJECT_ID=\$\{notification_push_project_id\}'
        $wasBootstrap | Should Match 'Environment=NOTIFICATION_PUSH_SERVICE_ACCOUNT_SECRET_ARN=\$\{notification_push_service_account_secret_arn\}'
        $wasBootstrap | Should Not Match 'FIREBASE_PRIVATE_KEY'
    }

    It 'builds deployable Android APKs with an explicit non-secret backend URL override' {
        $androidBuild | Should Match 'gradleProperty\("tripCopilotBaseUrl"\)'
        $androidBuild | Should Match 'buildConfigField\("String",\s*"BACKEND_BASE_URL"'
        $backendConfig | Should Match 'BuildConfig\.BACKEND_BASE_URL'
        $androidBuild | Should Not Match 'reminder-platform-de-public'
    }

    It 'activates Android Firebase only when the ignored google-services config is supplied' {
        $androidRootBuild | Should Match 'com\.google\.gms\.google-services'
        $androidBuild | Should Match 'file\("google-services\.json"\)\.isFile'
        $androidBuild | Should Match 'apply\(plugin\s*=\s*"com\.google\.gms\.google-services"\)'
        $androidIgnore | Should Match 'app/google-services\.json'
    }
}
