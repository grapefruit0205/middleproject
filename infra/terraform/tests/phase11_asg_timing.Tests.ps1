$terraformRoot = Split-Path -Parent $PSScriptRoot

Describe 'Phase 11 Auto Scaling timing contract' {
    BeforeAll {
        $tier = Get-Content (Join-Path $terraformRoot 'tier.tf') -Raw
    }

    It 'gives both application tiers a five-minute ELB health grace period' {
        $matches = [regex]::Matches($tier, '(?m)^\s*health_check_grace_period\s*=\s*300\s*$')
        $matches.Count | Should Be 2
    }

    It 'uses a five-minute warmup for both rolling refreshes' {
        $matches = [regex]::Matches($tier, '(?m)^\s*instance_warmup\s*=\s*300\s*$')
        $matches.Count | Should Be 2
    }
}
