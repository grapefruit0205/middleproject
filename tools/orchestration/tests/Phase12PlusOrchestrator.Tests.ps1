$ErrorActionPreference = 'Stop'

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$manifestPath = Join-Path $repositoryRoot 'tools\orchestration\phases-12-plus.json'
$modulePath = Join-Path $repositoryRoot 'tools\orchestration\PhaseOrchestrator.psm1'
$invokePhasePath = Join-Path $repositoryRoot 'tools\orchestration\Invoke-Phase.ps1'
$setReviewPath = Join-Path $repositoryRoot 'tools\orchestration\Set-PhaseReview.ps1'

function New-Phase12PlusTestRepository {
    $root = Join-Path ([System.IO.Path]::GetTempPath()) ("middleproject-phase12-repo-" + [guid]::NewGuid().ToString('N'))
    [System.IO.Directory]::CreateDirectory((Join-Path $root 'docs\architecture')) | Out-Null
    [System.IO.Directory]::CreateDirectory((Join-Path $root 'docs\adr')) | Out-Null
    [System.IO.Directory]::CreateDirectory((Join-Path $root 'docs\phases\phase-12')) | Out-Null

    Copy-Item -LiteralPath (Join-Path $repositoryRoot 'docs\architecture\project-invariants.md') -Destination (Join-Path $root 'docs\architecture\project-invariants.md')
    Copy-Item -LiteralPath (Join-Path $repositoryRoot 'docs\adr\ADR-005-private-mcp-tunnel-and-device-pairing.md') -Destination (Join-Path $root 'docs\adr\ADR-005-private-mcp-tunnel-and-device-pairing.md')
    Copy-Item -LiteralPath (Join-Path $repositoryRoot 'docs\phases\phase-12\brief.md') -Destination (Join-Path $root 'docs\phases\phase-12\brief.md')
    Copy-Item -LiteralPath (Join-Path $repositoryRoot 'docs\phases\phase-12\implement.prompt.md') -Destination (Join-Path $root 'docs\phases\phase-12\implement.prompt.md')
    Set-Content -LiteralPath (Join-Path $root '.gitignore') -Value ".orchestration/`n.commandcode/" -Encoding utf8NoBOM

    git -C $root init -b codex/phase-12-trip-domain-mcp-foundation | Out-Null
    git -C $root config user.email 'phase12-orchestrator-tests@example.invalid'
    git -C $root config user.name 'Phase 12 Orchestrator Tests'
    git -C $root add .
    git -C $root commit -m 'test fixture baseline' | Out-Null
    return $root
}

Describe 'Phase 12-18 orchestration manifest' {
    It 'defines Phase 12 through Phase 18 in order' {
        (Test-Path -LiteralPath $manifestPath) | Should Be $true

        $manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
        (@($manifest.phases.phase) -join ',') | Should Be '12,13,14,15,16,17,18'
    }

    It 'uses DeepSeek V4 Flash for Phase 12 through 18' {
        Import-Module $modulePath -Force
        $definition = Get-PhaseDefinition -Phase 12 -ManifestPath $manifestPath
        $initial = New-CommandCodeRunPlan -PhaseDefinition $definition -RuntimePrompt 'initial' -CmdcPath 'cmdc' -RepositoryRoot $repositoryRoot -AttemptNumber 1
        $repair = New-CommandCodeRunPlan -PhaseDefinition $definition -RuntimePrompt 'repair' -CmdcPath 'cmdc' -RepositoryRoot $repositoryRoot -AttemptNumber 2

        foreach ($phase in 12..18) {
            (Get-PhaseDefinition -Phase $phase -ManifestPath $manifestPath).model | Should Be 'deepseek/deepseek-v4-flash'
        }
        $definition.model | Should Be 'deepseek/deepseek-v4-flash'
        $definition.effort | Should Be 'max'
        $definition.maxInvocationsPerPhase | Should Be 2
        $initial.maxTurns | Should Be 100
        $repair.maxTurns | Should Be 100
        (@($repair.arguments) -join '|') | Should Match ([regex]::Escape('--model|deepseek/deepseek-v4-flash|--effort|max'))
        (@($repair.arguments) -join '|') | Should Match ([regex]::Escape('--max-turns|100'))
    }

    It 'references committed contracts and marks only Phase 17 for external approval' {
        $manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
        $missing = @(
            foreach ($phase in $manifest.phases) {
                foreach ($property in @('briefPath', 'implementationPromptPath')) {
                    $path = Join-Path $repositoryRoot $phase.$property
                    if (-not (Test-Path -LiteralPath $path)) {
                        "$($phase.phase):$property"
                    }
                }
            }
        )

        ($missing -join ',') | Should Be ''
        (@($manifest.phases | Where-Object requiresExternalApproval | ForEach-Object phase) -join ',') | Should Be '17'
    }
}

Describe 'Phase 12-18 custom manifest behavior' {
    BeforeEach {
        Import-Module $modulePath -Force
    }

    It 'resolves Phase 12 and composes the approved private-access prompt' {
        $definition = Get-PhaseDefinition -Phase 12 -ManifestPath $manifestPath
        $prompt = New-PhaseRuntimePrompt -PhaseDefinition $definition -RepositoryRoot $repositoryRoot -BaselineCommit '0123456789abcdef0123456789abcdef01234567'

        $definition.model | Should Be 'deepseek/deepseek-v4-flash'
        $definition.maxTurns | Should Be 100
        $definition.repairMaxTurns | Should Be 100
        $prompt | Should Match 'Cognito(?:/|, )OIDC'
        $prompt | Should Match ([regex]::Escape('공개 MCP Endpoint'))
        $prompt | Should Match ([regex]::Escape('Do not run terraform apply or mutate live AWS resources.'))
    }

    It 'moves Phase 18 to COMPLETE after independent PASS' {
        $definition = Get-PhaseDefinition -Phase 18 -ManifestPath $manifestPath
        $state = New-OrchestrationState -PhaseDefinition $definition -BaselineCommit '0123456789abcdef0123456789abcdef01234567'
        $state = Move-OrchestrationState -State $state -ToStatus IMPLEMENTING -Reason 'test start' -ManifestPath $manifestPath
        $state = Move-OrchestrationState -State $state -ToStatus REVIEWING -Reason 'test review' -ManifestPath $manifestPath
        $state = Move-OrchestrationState -State $state -ToStatus PASS -Reason 'test pass' -ManifestPath $manifestPath

        $state.status | Should Be 'COMPLETE'
        $state.phase | Should Be 18
    }

    It 'counts only completed Command Code results toward the two-invocation limit' {
        $definition = Get-PhaseDefinition -Phase 12 -ManifestPath $manifestPath
        $state = New-OrchestrationState -PhaseDefinition $definition -BaselineCommit '0123456789abcdef0123456789abcdef01234567'

        $state = Move-OrchestrationState -State $state -ToStatus IMPLEMENTING -Reason 'interrupted start' -ManifestPath $manifestPath
        $state.attempt | Should Be 0
        $state = Move-OrchestrationState -State $state -ToStatus READY -Reason 'process ended without a result' -ManifestPath $manifestPath
        $state.attempt | Should Be 0

        $state = Move-OrchestrationState -State $state -ToStatus IMPLEMENTING -Reason 'effective attempt 1' -ManifestPath $manifestPath
        $state = Move-OrchestrationState -State $state -ToStatus READY -Reason 'cmdc returned exit 8' -ManifestPath $manifestPath -InvocationCompleted
        $state.attempt | Should Be 1

        $state = Move-OrchestrationState -State $state -ToStatus IMPLEMENTING -Reason 'effective attempt 2' -ManifestPath $manifestPath
        $state = Move-OrchestrationState -State $state -ToStatus READY -Reason 'cmdc returned exit 8' -ManifestPath $manifestPath -InvocationCompleted
        $state.attempt | Should Be 2
        $thirdAttemptRejected = $false
        try {
            Move-OrchestrationState -State $state -ToStatus IMPLEMENTING -Reason 'third effective attempt' -ManifestPath $manifestPath | Out-Null
        }
        catch {
            $thirdAttemptRejected = $true
        }
        $thirdAttemptRejected | Should Be $true
    }
}

Describe 'Phase 12-18 runner entry points' {
    It 'dry-runs Phase 12 with the custom manifest and exact CMDC arguments' {
        $testRoot = New-Phase12PlusTestRepository
        try {
            $result = & $invokePhasePath `
                -Phase 12 `
                -RepositoryRoot $testRoot `
                -ManifestPath $manifestPath `
                -StatePath (Join-Path $testRoot '.orchestration\phase-12-18-state.json') `
                -DryRun

            $result.status | Should Be 'DRY_RUN'
            $result.model | Should Be 'deepseek/deepseek-v4-flash'
            $result.effort | Should Be 'max'
            $result.maxTurns | Should Be 100
            (@($result.arguments) -join '|') | Should Match ([regex]::Escape('--model|deepseek/deepseek-v4-flash|--effort|max'))
            (@($result.arguments) -join '|') | Should Match ([regex]::Escape('--max-turns|100'))
        }
        finally {
            Remove-Item -LiteralPath $testRoot -Recurse -Force
        }
    }

    It 'uses the same custom manifest when PASS advances to Phase 13' {
        $testRoot = New-Phase12PlusTestRepository
        try {
            Import-Module $modulePath -Force
            $baseline = (git -C $testRoot rev-parse HEAD).Trim()
            $definition = Get-PhaseDefinition -Phase 12 -ManifestPath $manifestPath
            $state = New-OrchestrationState -PhaseDefinition $definition -BaselineCommit $baseline
            $state = Move-OrchestrationState -State $state -ToStatus IMPLEMENTING -Reason 'test start' -ManifestPath $manifestPath
            $state = Move-OrchestrationState -State $state -ToStatus REVIEWING -Reason 'test review' -ManifestPath $manifestPath
            $statePath = Join-Path $testRoot '.orchestration\phase-12-18-state.json'
            Write-OrchestrationState -State $state -StatePath $statePath
            Set-Content -LiteralPath (Join-Path $testRoot 'docs\phases\phase-12\review.md') -Value "# Phase 12 Review`n`n- Reviewer: Codex Desktop`n- Verdict: PASS`n" -Encoding utf8NoBOM

            $result = & $setReviewPath `
                -RepositoryRoot $testRoot `
                -StatePath $statePath `
                -ManifestPath $manifestPath `
                -Decision PASS `
                -Reason 'independent test pass' `
                -NextBaselineCommit $baseline

            $result.status | Should Be 'READY'
            $result.phase | Should Be 13
            $result.branch | Should Be 'codex/phase-13-private-car-vertical-slice'
        }
        finally {
            Remove-Item -LiteralPath $testRoot -Recurse -Force
        }
    }
}
