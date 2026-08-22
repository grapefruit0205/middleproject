$terraformRoot = Split-Path -Parent $PSScriptRoot

Describe 'Retired MCP infrastructure boundary' {
    BeforeAll {
        $webBootstrap = Get-Content (Join-Path $terraformRoot 'templates/web.sh.tftpl') -Raw
        $variables = Get-Content (Join-Path $terraformRoot 'variables.tf') -Raw
        $tier = Get-Content (Join-Path $terraformRoot 'tier.tf') -Raw
        $outputs = Get-Content (Join-Path $terraformRoot 'outputs.tf') -Raw
    }

    It 'serves only the normal WEB reverse proxy without an MCP loopback listener' {
        $webBootstrap | Should Match 'ProxyPass /api/ http://\$\{internal_alb_dns\}/api/'
        $webBootstrap | Should Not Match '/api/mcp'
        $webBootstrap | Should Not Match 'tunnel-client'
        $webBootstrap | Should Not Match 'Listen 127\.0\.0\.1:'
    }

    It 'does not expose tunnel deployment inputs, IAM access, or outputs' {
        $variables | Should Not Match 'tunnel_client|tunnel_id|tunnel_runtime|tunnel_loopback'
        $tier | Should Not Match 'tunnel_client|tunnel_runtime|install-tunnel-client'
        $outputs | Should Not Match 'secure_mcp_tunnel|private_mcp_endpoint'
    }
}
