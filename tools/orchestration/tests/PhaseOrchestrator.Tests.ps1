$ErrorActionPreference = 'Stop'

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$manifestPath = Join-Path $repositoryRoot 'tools\orchestration\phases.json'
$modulePath = Join-Path $repositoryRoot 'tools\orchestration\PhaseOrchestrator.psm1'
$fakeCmdcPath = Join-Path $PSScriptRoot 'fixtures\fake-cmdc.ps1'
$invokePhasePath = Join-Path $repositoryRoot 'tools\orchestration\Invoke-Phase.ps1'
$setReviewPath = Join-Path $repositoryRoot 'tools\orchestration\Set-PhaseReview.ps1'

function Test-Throws {
    param([Parameter(Mandatory)][scriptblock]$ScriptBlock)

    try {
        & $ScriptBlock
        return $false
    }
    catch {
        return $true
    }
}

function New-OrchestratorTestRepository {
    $root = Join-Path ([System.IO.Path]::GetTempPath()) ("middleproject-repo-" + [guid]::NewGuid().ToString('N'))
    [System.IO.Directory]::CreateDirectory((Join-Path $root 'docs\architecture')) | Out-Null
    [System.IO.Directory]::CreateDirectory((Join-Path $root 'docs\phases\phase-01')) | Out-Null

    Copy-Item -LiteralPath (Join-Path $repositoryRoot 'docs\architecture\project-invariants.md') -Destination (Join-Path $root 'docs\architecture\project-invariants.md')
    Copy-Item -LiteralPath (Join-Path $repositoryRoot 'docs\phases\phase-01\brief.md') -Destination (Join-Path $root 'docs\phases\phase-01\brief.md')
    Copy-Item -LiteralPath (Join-Path $repositoryRoot 'docs\phases\phase-01\implement.prompt.md') -Destination (Join-Path $root 'docs\phases\phase-01\implement.prompt.md')
    Set-Content -LiteralPath (Join-Path $root '.gitignore') -Value ".orchestration/`n.commandcode/" -Encoding utf8NoBOM

    git -C $root init -b codex/phase-01-local-foundation | Out-Null
    git -C $root config user.email 'orchestrator-tests@example.invalid'
    git -C $root config user.name 'Orchestrator Tests'
    git -C $root add .
    git -C $root commit -m 'test fixture baseline' | Out-Null
    return $root
}

Describe 'Phase orchestration manifest' {
    It 'defines exactly Phase 01 through Phase 10 in order' {
        $exists = Test-Path -LiteralPath $manifestPath
        $exists | Should Be $true

        if ($exists) {
            $manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
            @($manifest.phases).Count | Should Be 10
            (@($manifest.phases.phase) -join ',') | Should Be '1,2,3,4,5,6,7,8,9,10'
        }
        else {
            0 | Should Be 10
        }
    }

    It 'references existing phase briefs and implementation prompts' {
        $missing = @('manifest is missing')
        if (Test-Path -LiteralPath $manifestPath) {
            $manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
            $missing = @(
                foreach ($phase in $manifest.phases) {
                    foreach ($property in @('briefPath', 'implementationPromptPath')) {
                        $relativePath = $phase.$property
                        if (-not (Test-Path -LiteralPath (Join-Path $repositoryRoot $relativePath))) {
                            "$($phase.phase):${property}:$relativePath"
                        }
                    }
                }
            )
        }

        ($missing -join ',') | Should Be ''
    }

    It 'uses phase-numbered result and review paths' {
        $mismatches = @('manifest is missing')
        if (Test-Path -LiteralPath $manifestPath) {
            $manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
            $mismatches = @(
                foreach ($phase in $manifest.phases) {
                    $number = '{0:D2}' -f [int]$phase.phase
                    if ($phase.resultPath -ne "docs/phases/phase-$number/result.md") {
                        "phase-$number result"
                    }
                    if ($phase.reviewPath -ne "docs/phases/phase-$number/review.md") {
                        "phase-$number review"
                    }
                }
            )
        }

        ($mismatches -join ',') | Should Be ''
    }

    It 'marks infrastructure phases for external-action handling' {
        $actual = 'manifest is missing'
        if (Test-Path -LiteralPath $manifestPath) {
            $manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
            $actual = (@($manifest.phases | Where-Object requiresExternalApproval | ForEach-Object phase) -join ',')
        }

        $actual | Should Be '4,5,10'
    }
}

Describe 'Runtime Git exclusions' {
    It 'ignores Command Code metadata' {
        Push-Location $repositoryRoot
        try {
            git check-ignore --quiet -- '.commandcode/taste/taste.md'
            $LASTEXITCODE | Should Be 0
        }
        finally {
            Pop-Location
        }
    }

    It 'ignores orchestration runtime state' {
        Push-Location $repositoryRoot
        try {
            git check-ignore --quiet --no-index -- '.orchestration/state.json'
            $LASTEXITCODE | Should Be 0
        }
        finally {
            Pop-Location
        }
    }
}

Describe 'Phase orchestration module boundary' {
    It 'loads as a PowerShell module' {
        (Test-Path -LiteralPath $modulePath) | Should Be $true

        if (Test-Path -LiteralPath $modulePath) {
            $loadError = $null
            try {
                Import-Module $modulePath -Force -ErrorAction Stop
            }
            catch {
                $loadError = $_
            }
            [bool]$loadError | Should Be $false
        }
    }
}

Describe 'Phase lookup and runtime prompt composition' {
    BeforeEach {
        Import-Module $modulePath -Force
    }

    It 'resolves Phase 01 from the manifest' {
        $definition = Get-PhaseDefinition -Phase 1 -ManifestPath $manifestPath

        $definition.phase | Should Be 1
        $definition.branch | Should Be 'codex/phase-01-local-foundation'
        (@($definition.allowedPaths) -join ',') | Should Be 'frontend/**,backend/**,.github/workflows/**,docs/phases/phase-01/result.md'
    }

    It 'resolves the Phase 10 Terra policy without changing historical defaults' {
        $historical = Get-PhaseDefinition -Phase 1 -ManifestPath $manifestPath
        $phaseTen = Get-PhaseDefinition -Phase 10 -ManifestPath $manifestPath

        $historical.model | Should Be 'gpt-5.6-luna'
        $historical.effort | Should Be 'max'
        $historical.maxTurns | Should Be 100
        $historical.maxInvocationsPerPhase | Should Be 3

        $phaseTen.branch | Should Be 'codex/phase-10-observability-security'
        $phaseTen.model | Should Be 'gpt-5.6-terra'
        $phaseTen.effort | Should Be 'xhigh'
        $phaseTen.maxTurns | Should Be 50
        $phaseTen.repairMaxTurns | Should Be 30
        $phaseTen.maxInvocationsPerPhase | Should Be 2
        $phaseTen.requiresExternalApproval | Should Be $true
        (@($phaseTen.allowedPaths) -join ',') | Should Be 'backend/**,infra/terraform/**,docs/runbooks/**,docs/phases/phase-10/result.md'
    }

    It 'rejects phases outside the application implementation range' {
        foreach ($candidate in @(0, 11, 99)) {
            $thrown = $false
            try {
                Get-PhaseDefinition -Phase $candidate -ManifestPath $manifestPath
            }
            catch {
                $thrown = $true
            }
            $thrown | Should Be $true
        }
    }

    It 'composes a Phase 02 prompt with the baseline and committed phase documents' {
        $definition = Get-PhaseDefinition -Phase 2 -ManifestPath $manifestPath
        $baseline = '0123456789abcdef0123456789abcdef01234567'

        $prompt = New-PhaseRuntimePrompt -PhaseDefinition $definition -RepositoryRoot $repositoryRoot -BaselineCommit $baseline

        $prompt | Should Match ([regex]::Escape("Baseline commit: $baseline"))
        $prompt | Should Match ([regex]::Escape('# Phase 02 Brief: Reminder Core and Calendar'))
        $prompt | Should Match ([regex]::Escape('# Phase 02 DeepSeek Implementation Prompt'))
        $prompt | Should Match ([regex]::Escape('docs/architecture/project-invariants.md'))
        $prompt | Should Match ([regex]::Escape('backend/**'))
        $prompt | Should Match ([regex]::Escape('docs/phases/phase-02/result.md'))
    }

    It 'records the selected Command Code model and effort in the prompt' {
        $definition = Get-PhaseDefinition -Phase 2 -ManifestPath $manifestPath
        $prompt = New-PhaseRuntimePrompt -PhaseDefinition $definition -RepositoryRoot $repositoryRoot -BaselineCommit '0123456789abcdef0123456789abcdef01234567'

        $prompt | Should Match ([regex]::Escape('Model: gpt-5.6-luna'))
        $prompt | Should Match ([regex]::Escape('Reasoning effort: max'))
    }

    It 'tells Phase 01 to preserve and continue the existing implementation' {
        $definition = Get-PhaseDefinition -Phase 1 -ManifestPath $manifestPath
        $prompt = New-PhaseRuntimePrompt -PhaseDefinition $definition -RepositoryRoot $repositoryRoot -BaselineCommit '0123456789abcdef0123456789abcdef01234567'

        $prompt | Should Match 'Preserve.*existing.*backend'
        $prompt | Should Match 'frontend'
        $prompt | Should Match 'first incomplete acceptance criterion'
    }

    It 'uses an existing Codex review as read-only repair input' {
        $definition = Get-PhaseDefinition -Phase 2 -ManifestPath $manifestPath
        $prompt = New-PhaseRuntimePrompt -PhaseDefinition $definition -RepositoryRoot $repositoryRoot -BaselineCommit '0123456789abcdef0123456789abcdef01234567'

        $prompt | Should Match ([regex]::Escape('If docs/phases/phase-02/review.md exists with a non-PASS verdict, read it and address only its unresolved findings.'))
        $prompt | Should Match ([regex]::Escape('Do not edit docs/phases/phase-02/review.md.'))
    }

    It 'prohibits remote Git, architecture, and live AWS mutations' {
        $definition = Get-PhaseDefinition -Phase 5 -ManifestPath $manifestPath
        $prompt = New-PhaseRuntimePrompt -PhaseDefinition $definition -RepositoryRoot $repositoryRoot -BaselineCommit '0123456789abcdef0123456789abcdef01234567'

        $prompt | Should Match ([regex]::Escape('Do not push, merge, rebase, reset, or rewrite Git history.'))
        $prompt | Should Match ([regex]::Escape('Do not create Git commits. Codex Desktop commits only after independent verification.'))
        $prompt | Should Match ([regex]::Escape('Do not modify Architecture, ADRs, project invariants, phase briefs, or phase implementation prompts.'))
        $prompt | Should Match ([regex]::Escape('Do not run terraform apply or mutate live AWS resources.'))
    }
}

Describe 'Durable phase orchestration state' {
    BeforeEach {
        Import-Module $modulePath -Force
        $phaseOne = Get-PhaseDefinition -Phase 1 -ManifestPath $manifestPath
        $baseline = '0123456789abcdef0123456789abcdef01234567'
    }

    It 'advances only through implementation and review before the next phase' {
        $state = New-OrchestrationState -PhaseDefinition $phaseOne -BaselineCommit $baseline
        $state.status | Should Be 'READY'
        $state.attempt | Should Be 0

        $state = Move-OrchestrationState -State $state -ToStatus 'IMPLEMENTING' -Reason 'initial run' -ManifestPath $manifestPath
        $state.status | Should Be 'IMPLEMENTING'
        $state.attempt | Should Be 1

        $state = Move-OrchestrationState -State $state -ToStatus 'REVIEWING' -Reason 'cmdc exited zero' -ManifestPath $manifestPath
        $state.status | Should Be 'REVIEWING'

        $nextBaseline = 'fedcba9876543210fedcba9876543210fedcba98'
        $state = Move-OrchestrationState -State $state -ToStatus 'PASS' -Reason 'independent verification passed' -ManifestPath $manifestPath -NextBaselineCommit $nextBaseline
        $state.phase | Should Be 2
        $state.status | Should Be 'READY'
        $state.attempt | Should Be 0
        $state.branch | Should Be 'codex/phase-02-reminder-core-calendar'
        $state.baselineCommit | Should Be $nextBaseline
        @($state.history | Where-Object decision -eq 'PASS').Count | Should Be 1
    }

    It 'advances Phase 09 to Phase 10 after independent review' {
        $phaseNine = Get-PhaseDefinition -Phase 9 -ManifestPath $manifestPath
        $state = New-OrchestrationState -PhaseDefinition $phaseNine -BaselineCommit $baseline
        $state = Move-OrchestrationState -State $state -ToStatus 'IMPLEMENTING' -Reason 'run' -ManifestPath $manifestPath
        $state = Move-OrchestrationState -State $state -ToStatus 'REVIEWING' -Reason 'implemented' -ManifestPath $manifestPath
        $nextBaseline = 'fedcba9876543210fedcba9876543210fedcba98'
        $state = Move-OrchestrationState -State $state -ToStatus 'PASS' -Reason 'review passed' -ManifestPath $manifestPath -NextBaselineCommit $nextBaseline

        $state.phase | Should Be 10
        $state.status | Should Be 'READY'
        $state.branch | Should Be 'codex/phase-10-observability-security'
        $state.baselineCommit | Should Be $nextBaseline
    }

    It 'ends the application loop when Phase 10 passes' {
        $phaseTen = Get-PhaseDefinition -Phase 10 -ManifestPath $manifestPath
        $state = New-OrchestrationState -PhaseDefinition $phaseTen -BaselineCommit $baseline
        $state = Move-OrchestrationState -State $state -ToStatus 'IMPLEMENTING' -Reason 'run' -ManifestPath $manifestPath
        $state = Move-OrchestrationState -State $state -ToStatus 'REVIEWING' -Reason 'implemented' -ManifestPath $manifestPath
        $state = Move-OrchestrationState -State $state -ToStatus 'PASS' -Reason 'review passed' -ManifestPath $manifestPath

        $state.phase | Should Be 10
        $state.status | Should Be 'COMPLETE'
    }

    It 'shares one three-invocation limit across failures and revisions' {
        $state = New-OrchestrationState -PhaseDefinition $phaseOne -BaselineCommit $baseline

        $state = Move-OrchestrationState -State $state -ToStatus 'IMPLEMENTING' -Reason 'attempt 1' -ManifestPath $manifestPath
        $state = Move-OrchestrationState -State $state -ToStatus 'READY' -Reason 'transient process failure' -ManifestPath $manifestPath
        $state.attempt | Should Be 1

        $state = Move-OrchestrationState -State $state -ToStatus 'IMPLEMENTING' -Reason 'attempt 2' -ManifestPath $manifestPath
        $state = Move-OrchestrationState -State $state -ToStatus 'REVIEWING' -Reason 'implemented' -ManifestPath $manifestPath
        $state = Move-OrchestrationState -State $state -ToStatus 'REVISE' -Reason 'tests failed' -ManifestPath $manifestPath
        $state.status | Should Be 'READY'
        $state.attempt | Should Be 2

        $state = Move-OrchestrationState -State $state -ToStatus 'IMPLEMENTING' -Reason 'attempt 3' -ManifestPath $manifestPath
        $state = Move-OrchestrationState -State $state -ToStatus 'READY' -Reason 'another failure' -ManifestPath $manifestPath
        $state.attempt | Should Be 3

        (Test-Throws { Move-OrchestrationState -State $state -ToStatus 'IMPLEMENTING' -Reason 'attempt 4' -ManifestPath $manifestPath }) | Should Be $true
    }

    It 'limits Phase 10 to an initial attempt and one repair' {
        $phaseTen = Get-PhaseDefinition -Phase 10 -ManifestPath $manifestPath
        $state = New-OrchestrationState -PhaseDefinition $phaseTen -BaselineCommit $baseline

        $state = Move-OrchestrationState -State $state -ToStatus 'IMPLEMENTING' -Reason 'attempt 1' -ManifestPath $manifestPath
        $state = Move-OrchestrationState -State $state -ToStatus 'READY' -Reason 'process failure' -ManifestPath $manifestPath
        $state = Move-OrchestrationState -State $state -ToStatus 'IMPLEMENTING' -Reason 'attempt 2' -ManifestPath $manifestPath
        $state = Move-OrchestrationState -State $state -ToStatus 'READY' -Reason 'repair failure' -ManifestPath $manifestPath

        $state.attempt | Should Be 2
        (Test-Throws { Move-OrchestrationState -State $state -ToStatus 'IMPLEMENTING' -Reason 'attempt 3' -ManifestPath $manifestPath }) | Should Be $true
    }

    It 'requires an explicit flag to leave external approval state' {
        $state = New-OrchestrationState -PhaseDefinition $phaseOne -BaselineCommit $baseline
        $state = Move-OrchestrationState -State $state -ToStatus 'IMPLEMENTING' -Reason 'run' -ManifestPath $manifestPath
        $state = Move-OrchestrationState -State $state -ToStatus 'REVIEWING' -Reason 'implemented' -ManifestPath $manifestPath
        $state = Move-OrchestrationState -State $state -ToStatus 'AWAITING_APPROVAL' -Reason 'live AWS gate' -ManifestPath $manifestPath

        (Test-Throws { Move-OrchestrationState -State $state -ToStatus 'REVIEWING' -Reason 'resume' -ManifestPath $manifestPath }) | Should Be $true

        $state = Move-OrchestrationState -State $state -ToStatus 'REVIEWING' -Reason 'approved plan' -ManifestPath $manifestPath -ExternalApproval
        $state.status | Should Be 'REVIEWING'
        $state.externalApprovalGranted | Should Be $true
    }

    It 'rejects skipped or invalid state transitions' {
        $state = New-OrchestrationState -PhaseDefinition $phaseOne -BaselineCommit $baseline
        (Test-Throws { Move-OrchestrationState -State $state -ToStatus 'REVIEWING' -Reason 'skip implementation' -ManifestPath $manifestPath }) | Should Be $true
        (Test-Throws { Move-OrchestrationState -State $state -ToStatus 'PASS' -Reason 'self approval' -ManifestPath $manifestPath }) | Should Be $true
    }

    It 'does not mutate review state when a required next baseline is missing' {
        $state = New-OrchestrationState -PhaseDefinition $phaseOne -BaselineCommit $baseline
        $state = Move-OrchestrationState -State $state -ToStatus IMPLEMENTING -Reason 'run' -ManifestPath $manifestPath
        $state = Move-OrchestrationState -State $state -ToStatus REVIEWING -Reason 'implemented' -ManifestPath $manifestPath
        $historyCount = @($state.history).Count

        (Test-Throws { Move-OrchestrationState -State $state -ToStatus PASS -Reason 'invalid pass' -ManifestPath $manifestPath }) | Should Be $true

        $state.status | Should Be 'REVIEWING'
        @($state.history).Count | Should Be $historyCount
    }

    It 'writes and reads atomic JSON state without leaving a temporary file' {
        $tempRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("middleproject-state-" + [guid]::NewGuid().ToString('N'))
        $statePath = Join-Path $tempRoot 'state.json'
        try {
            $state = New-OrchestrationState -PhaseDefinition $phaseOne -BaselineCommit $baseline
            Write-OrchestrationState -State $state -StatePath $statePath
            $readBack = Read-OrchestrationState -StatePath $statePath

            $readBack.phase | Should Be 1
            $readBack.status | Should Be 'READY'
            @(Get-ChildItem -LiteralPath $tempRoot -Filter '*.tmp' -ErrorAction SilentlyContinue).Count | Should Be 0
        }
        finally {
            if (Test-Path -LiteralPath $tempRoot) {
                Remove-Item -LiteralPath $tempRoot -Recurse -Force
            }
        }
    }
}

Describe 'Phase change scope guard' {
    BeforeEach {
        Import-Module $modulePath -Force
    }

    It 'accepts only Phase 01 implementation roots and its result file' {
        $definition = Get-PhaseDefinition -Phase 1 -ManifestPath $manifestPath
        $paths = @(
            'frontend/src/App.tsx',
            'backend/build.gradle.kts',
            '.github/workflows/frontend.yml',
            'docs/phases/phase-01/result.md'
        )

        $result = Test-PhaseChangeScope -PhaseDefinition $definition -ChangedPaths $paths

        $result.isAllowed | Should Be $true
        @($result.rejectedPaths).Count | Should Be 0
        (@($result.allowedPaths) -join ',') | Should Be ($paths -join ',')
    }

    It 'rejects review, architecture, agent metadata, runtime state, and unrelated files' {
        $definition = Get-PhaseDefinition -Phase 1 -ManifestPath $manifestPath
        $paths = @(
            'docs/phases/phase-01/review.md',
            'docs/architecture/project-invariants.md',
            '.commandcode/taste/taste.md',
            '.orchestration/state.json',
            'README.md'
        )

        $result = Test-PhaseChangeScope -PhaseDefinition $definition -ChangedPaths $paths

        $result.isAllowed | Should Be $false
        (@($result.rejectedPaths) -join ',') | Should Be ($paths -join ',')
    }

    It 'uses each phase manifest allowlist instead of a global root list' {
        $definition = Get-PhaseDefinition -Phase 4 -ManifestPath $manifestPath
        $result = Test-PhaseChangeScope -PhaseDefinition $definition -ChangedPaths @(
            'infra/terraform/network/main.tf',
            'docs/phases/phase-04/result.md',
            'backend/src/main/java/Unexpected.java'
        )

        $result.isAllowed | Should Be $false
        (@($result.allowedPaths) -join ',') | Should Be 'infra/terraform/network/main.tf,docs/phases/phase-04/result.md'
        (@($result.rejectedPaths) -join ',') | Should Be 'backend/src/main/java/Unexpected.java'
    }

    It 'normalizes Windows separators and path casing' {
        $definition = Get-PhaseDefinition -Phase 2 -ManifestPath $manifestPath
        $result = Test-PhaseChangeScope -PhaseDefinition $definition -ChangedPaths @(
            'BACKEND\src\main\java\Example.java',
            'Docs\Phases\Phase-02\Result.md'
        )

        $result.isAllowed | Should Be $true
        @($result.rejectedPaths).Count | Should Be 0
    }

    It 'rejects absolute paths and traversal paths before allowlist matching' {
        $definition = Get-PhaseDefinition -Phase 2 -ManifestPath $manifestPath
        $paths = @(
            'C:\middleproject\backend\build.gradle.kts',
            '../backend/build.gradle.kts',
            'backend/../docs/architecture/project-invariants.md'
        )

        $result = Test-PhaseChangeScope -PhaseDefinition $definition -ChangedPaths $paths

        $result.isAllowed | Should Be $false
        (@($result.rejectedPaths) -join ',') | Should Be ($paths -join ',')
    }
}

Describe 'Command Code process boundary' {
    BeforeEach {
        Import-Module $modulePath -Force
        $phaseOne = Get-PhaseDefinition -Phase 1 -ManifestPath $manifestPath
    }

    It 'builds the exact paid invocation policy without executing it' {
        $plan = New-CommandCodeRunPlan -PhaseDefinition $phaseOne -RuntimePrompt 'implement phase one' -CmdcPath $fakeCmdcPath -RepositoryRoot $repositoryRoot

        $plan.command | Should Be $fakeCmdcPath
        $plan.workingDirectory | Should Be $repositoryRoot
        (@($plan.arguments) -join '|') | Should Be '-p|implement phase one|--model|gpt-5.6-luna|--effort|max|--auto-accept|--yolo|--max-turns|100|--output-format|json|--name|phase-01-implementation'
    }

    It 'uses the Phase 10 initial and repair turn budgets' {
        $phaseTen = Get-PhaseDefinition -Phase 10 -ManifestPath $manifestPath

        $initial = New-CommandCodeRunPlan -PhaseDefinition $phaseTen -RuntimePrompt 'initial' -CmdcPath $fakeCmdcPath -RepositoryRoot $repositoryRoot -AttemptNumber 1
        $repair = New-CommandCodeRunPlan -PhaseDefinition $phaseTen -RuntimePrompt 'repair' -CmdcPath $fakeCmdcPath -RepositoryRoot $repositoryRoot -AttemptNumber 2

        (@($initial.arguments) -join '|') | Should Be '-p|initial|--model|gpt-5.6-terra|--effort|xhigh|--auto-accept|--yolo|--max-turns|50|--output-format|json|--name|phase-10-implementation'
        (@($repair.arguments) -join '|') | Should Be '-p|repair|--model|gpt-5.6-terra|--effort|xhigh|--auto-accept|--yolo|--max-turns|30|--output-format|json|--name|phase-10-implementation'
    }

    It 'captures stdout stderr exit code timestamps and baseline metadata' {
        $tempRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("middleproject-run-" + [guid]::NewGuid().ToString('N'))
        try {
            $plan = New-CommandCodeRunPlan -PhaseDefinition $phaseOne -RuntimePrompt 'successful fake run' -CmdcPath $fakeCmdcPath -RepositoryRoot $repositoryRoot -BaselineCommit '0123456789abcdef0123456789abcdef01234567'
            $result = Invoke-CommandCodeAttempt -Plan $plan -LogDirectory $tempRoot -ActiveWriterProbe { @() }

            $result.invoked | Should Be $true
            $result.status | Should Be 'SUCCEEDED'
            $result.exitCode | Should Be 0
            $result.baselineCommit | Should Be '0123456789abcdef0123456789abcdef01234567'
            (Test-Path -LiteralPath $result.stdoutPath) | Should Be $true
            (Test-Path -LiteralPath $result.stderrPath) | Should Be $true
            (Get-Content -LiteralPath $result.stdoutPath -Raw) | Should Match ([regex]::Escape('gpt-5.6-luna'))
            (Get-Content -LiteralPath $result.stdoutPath -Raw | ConvertFrom-Json).workingDirectory | Should Be $repositoryRoot
            (Get-Content -LiteralPath $result.stderrPath -Raw) | Should Match 'fake cmdc stderr'
            ([DateTimeOffset]::Parse($result.finishedAt) -ge [DateTimeOffset]::Parse($result.startedAt)) | Should Be $true
        }
        finally {
            if (Test-Path -LiteralPath $tempRoot) {
                Remove-Item -LiteralPath $tempRoot -Recurse -Force
            }
        }
    }

    It 'returns the real nonzero child exit code as a failed attempt' {
        $tempRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("middleproject-run-" + [guid]::NewGuid().ToString('N'))
        try {
            $plan = New-CommandCodeRunPlan -PhaseDefinition $phaseOne -RuntimePrompt '__FAKE_EXIT_7__' -CmdcPath $fakeCmdcPath -RepositoryRoot $repositoryRoot
            $result = Invoke-CommandCodeAttempt -Plan $plan -LogDirectory $tempRoot -ActiveWriterProbe { @() }

            $result.status | Should Be 'FAILED'
            $result.exitCode | Should Be 7
        }
        finally {
            if (Test-Path -LiteralPath $tempRoot) {
                Remove-Item -LiteralPath $tempRoot -Recurse -Force
            }
        }
    }

    It 'does not invoke the child or create logs during dry-run' {
        $tempRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("middleproject-run-" + [guid]::NewGuid().ToString('N'))
        $markerPath = Join-Path $tempRoot 'invoked.marker'
        $env:MIDDLEPROJECT_FAKE_CMDC_MARKER = $markerPath
        try {
            $plan = New-CommandCodeRunPlan -PhaseDefinition $phaseOne -RuntimePrompt 'dry run' -CmdcPath $fakeCmdcPath -RepositoryRoot $repositoryRoot
            $result = Invoke-CommandCodeAttempt -Plan $plan -LogDirectory $tempRoot -DryRun -ActiveWriterProbe { @() }

            $result.invoked | Should Be $false
            $result.status | Should Be 'DRY_RUN'
            (Test-Path -LiteralPath $markerPath) | Should Be $false
            (Test-Path -LiteralPath $tempRoot) | Should Be $false
        }
        finally {
            Remove-Item Env:\MIDDLEPROJECT_FAKE_CMDC_MARKER -ErrorAction SilentlyContinue
            if (Test-Path -LiteralPath $tempRoot) {
                Remove-Item -LiteralPath $tempRoot -Recurse -Force
            }
        }
    }

    It 'blocks a non-dry run while another agent writer is active' {
        $tempRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("middleproject-run-" + [guid]::NewGuid().ToString('N'))
        try {
            $plan = New-CommandCodeRunPlan -PhaseDefinition $phaseOne -RuntimePrompt 'blocked run' -CmdcPath $fakeCmdcPath -RepositoryRoot $repositoryRoot
            $throws = Test-Throws {
                Invoke-CommandCodeAttempt -Plan $plan -LogDirectory $tempRoot -ActiveWriterProbe {
                    @([pscustomobject]@{ processId = 19708; name = 'node.exe'; commandLine = 'command-code --model gpt-5.6-luna' })
                }
            }

            $throws | Should Be $true
            (Test-Path -LiteralPath $tempRoot) | Should Be $false
        }
        finally {
            if (Test-Path -LiteralPath $tempRoot) {
                Remove-Item -LiteralPath $tempRoot -Recurse -Force
            }
        }
    }
}

Describe 'Phase execution and review entry points' {
    BeforeEach {
        Import-Module $modulePath -Force
    }

    It 'dry-runs Phase 01 without invoking cmdc or changing repository state' {
        $testRepository = New-OrchestratorTestRepository
        $statePath = Join-Path $testRepository '.orchestration\state.json'
        $runtimeDirectory = Join-Path $testRepository '.orchestration\runs'
        $markerPath = Join-Path $testRepository 'invoked.marker'
        $env:MIDDLEPROJECT_FAKE_CMDC_MARKER = $markerPath
        try {
            $before = git -C $testRepository status --porcelain=v1
            $result = & $invokePhasePath -Phase 1 -RepositoryRoot $testRepository -CmdcPath $fakeCmdcPath -StatePath $statePath -RuntimeDirectory $runtimeDirectory -DryRun
            $after = git -C $testRepository status --porcelain=v1

            $result.status | Should Be 'DRY_RUN'
            $result.phase | Should Be 1
            $result.model | Should Be 'gpt-5.6-luna'
            $result.effort | Should Be 'max'
            $result.autoAccept | Should Be $true
            ($before -join "`n") | Should Be ($after -join "`n")
            (Test-Path -LiteralPath $statePath) | Should Be $false
            (Test-Path -LiteralPath $runtimeDirectory) | Should Be $false
            (Test-Path -LiteralPath $markerPath) | Should Be $false
        }
        finally {
            Remove-Item Env:\MIDDLEPROJECT_FAKE_CMDC_MARKER -ErrorAction SilentlyContinue
            if (Test-Path -LiteralPath $testRepository) {
                Remove-Item -LiteralPath $testRepository -Recurse -Force
            }
        }
    }

    It 'dry-runs Phase 10 with Terra xhigh and no runtime mutation' {
        $tempRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("middleproject-phase10-dryrun-" + [guid]::NewGuid().ToString('N'))
        $statePath = Join-Path $tempRoot 'state.json'
        $runtimeDirectory = Join-Path $tempRoot 'runs'
        try {
            $before = git -C $repositoryRoot status --porcelain=v1
            $result = & $invokePhasePath -Phase 10 -RepositoryRoot $repositoryRoot -CmdcPath $fakeCmdcPath -StatePath $statePath -RuntimeDirectory $runtimeDirectory -DryRun
            $after = git -C $repositoryRoot status --porcelain=v1

            $result.status | Should Be 'DRY_RUN'
            $result.phase | Should Be 10
            $result.model | Should Be 'gpt-5.6-terra'
            $result.effort | Should Be 'xhigh'
            (@($result.arguments) -join '|') | Should Match ([regex]::Escape('--max-turns|50'))
            ($before -join "`n") | Should Be ($after -join "`n")
            (Test-Path -LiteralPath $statePath) | Should Be $false
            (Test-Path -LiteralPath $runtimeDirectory) | Should Be $false
        }
        finally {
            if (Test-Path -LiteralPath $tempRoot) {
                Remove-Item -LiteralPath $tempRoot -Recurse -Force
            }
        }
    }

    It 'moves a successful implementation attempt to independent review' {
        $testRepository = New-OrchestratorTestRepository
        $statePath = Join-Path $testRepository '.orchestration\state.json'
        $runtimeDirectory = Join-Path $testRepository '.orchestration\runs'
        try {
            $result = & $invokePhasePath -Phase 1 -RepositoryRoot $testRepository -CmdcPath $fakeCmdcPath -StatePath $statePath -RuntimeDirectory $runtimeDirectory -ActiveWriterProbe { @() }
            $state = Read-OrchestrationState -StatePath $statePath

            $result.status | Should Be 'REVIEWING'
            $result.exitCode | Should Be 0
            $state.status | Should Be 'REVIEWING'
            $state.attempt | Should Be 1
            (Test-Path -LiteralPath $result.stdoutPath) | Should Be $true
            (Get-Content -LiteralPath $result.stdoutPath -Raw | ConvertFrom-Json).workingDirectory | Should Be $testRepository
        }
        finally {
            if (Test-Path -LiteralPath $testRepository) {
                Remove-Item -LiteralPath $testRepository -Recurse -Force
            }
        }
    }

    It 'records a failed implementation attempt without entering review' {
        $testRepository = New-OrchestratorTestRepository
        $statePath = Join-Path $testRepository '.orchestration\state.json'
        $runtimeDirectory = Join-Path $testRepository '.orchestration\runs'
        $env:MIDDLEPROJECT_FAKE_CMDC_EXIT_CODE = '7'
        try {
            $result = & $invokePhasePath -Phase 1 -RepositoryRoot $testRepository -CmdcPath $fakeCmdcPath -StatePath $statePath -RuntimeDirectory $runtimeDirectory -ActiveWriterProbe { @() }
            $state = Read-OrchestrationState -StatePath $statePath

            $result.status | Should Be 'READY'
            $result.exitCode | Should Be 7
            $state.status | Should Be 'READY'
            $state.attempt | Should Be 1
        }
        finally {
            Remove-Item Env:\MIDDLEPROJECT_FAKE_CMDC_EXIT_CODE -ErrorAction SilentlyContinue
            if (Test-Path -LiteralPath $testRepository) {
                Remove-Item -LiteralPath $testRepository -Recurse -Force
            }
        }
    }

    It 'requires a PASS review file before advancing to Phase 02' {
        $testRepository = New-OrchestratorTestRepository
        $statePath = Join-Path $testRepository '.orchestration\state.json'
        $runtimeDirectory = Join-Path $testRepository '.orchestration\runs'
        try {
            & $invokePhasePath -Phase 1 -RepositoryRoot $testRepository -CmdcPath $fakeCmdcPath -StatePath $statePath -RuntimeDirectory $runtimeDirectory -ActiveWriterProbe { @() } | Out-Null
            $existingCommit = (git -C $testRepository rev-parse HEAD).Trim()
            (Test-Throws { & $setReviewPath -RepositoryRoot $testRepository -StatePath $statePath -Decision PASS -Reason 'reviewed' -NextBaselineCommit $existingCommit }) | Should Be $true

            Set-Content -LiteralPath (Join-Path $testRepository 'docs\phases\phase-01\review.md') -Value "# Phase 01 Review`n`n- Reviewer: Codex`n- Verdict: PASS`n" -Encoding utf8NoBOM
            git -C $testRepository add docs/phases/phase-01/review.md
            git -C $testRepository commit -m 'docs: pass phase 01' | Out-Null
            $nextBaseline = (git -C $testRepository rev-parse HEAD).Trim()

            $result = & $setReviewPath -RepositoryRoot $testRepository -StatePath $statePath -Decision PASS -Reason 'reviewed' -NextBaselineCommit $nextBaseline
            $state = Read-OrchestrationState -StatePath $statePath

            $result.status | Should Be 'READY'
            $state.phase | Should Be 2
            $state.branch | Should Be 'codex/phase-02-reminder-core-calendar'
            $state.baselineCommit | Should Be $nextBaseline
        }
        finally {
            if (Test-Path -LiteralPath $testRepository) {
                Remove-Item -LiteralPath $testRepository -Recurse -Force
            }
        }
    }

    It 'blocks PASS for every phase marked as requiring external approval' {
        $testRepository = New-OrchestratorTestRepository
        $statePath = Join-Path $testRepository '.orchestration\state.json'
        try {
            foreach ($phaseNumber in @(4, 5, 10)) {
                $definition = Get-PhaseDefinition -Phase $phaseNumber -ManifestPath $manifestPath
                $state = New-OrchestrationState -PhaseDefinition $definition -BaselineCommit '0123456789abcdef0123456789abcdef01234567'
                $state = Move-OrchestrationState -State $state -ToStatus IMPLEMENTING -Reason 'run' -ManifestPath $manifestPath
                $state = Move-OrchestrationState -State $state -ToStatus REVIEWING -Reason 'implemented' -ManifestPath $manifestPath
                Write-OrchestrationState -State $state -StatePath $statePath
                $number = '{0:D2}' -f $phaseNumber
                [System.IO.Directory]::CreateDirectory((Join-Path $testRepository "docs\phases\phase-$number")) | Out-Null
                Set-Content -LiteralPath (Join-Path $testRepository "docs\phases\phase-$number\review.md") -Value "# Phase $number Review`n`n- Reviewer: Codex`n- Verdict: PASS`n" -Encoding utf8NoBOM
                $existingCommit = (git -C $testRepository rev-parse HEAD).Trim()

                (Test-Throws { & $setReviewPath -RepositoryRoot $testRepository -StatePath $statePath -Decision PASS -Reason 'reviewed' -NextBaselineCommit $existingCommit }) | Should Be $true
            }
        }
        finally {
            if (Test-Path -LiteralPath $testRepository) {
                Remove-Item -LiteralPath $testRepository -Recurse -Force
            }
        }
    }

    It 'advances after a reviewed Phase 09 PASS' {
        $testRepository = New-OrchestratorTestRepository
        $statePath = Join-Path $testRepository '.orchestration\state.json'
        try {
            $phaseNine = Get-PhaseDefinition -Phase 9 -ManifestPath $manifestPath
            $state = New-OrchestrationState -PhaseDefinition $phaseNine -BaselineCommit '0123456789abcdef0123456789abcdef01234567'
            $state = Move-OrchestrationState -State $state -ToStatus IMPLEMENTING -Reason 'run' -ManifestPath $manifestPath
            $state = Move-OrchestrationState -State $state -ToStatus REVIEWING -Reason 'implemented' -ManifestPath $manifestPath
            Write-OrchestrationState -State $state -StatePath $statePath
            [System.IO.Directory]::CreateDirectory((Join-Path $testRepository 'docs\phases\phase-09')) | Out-Null
            Set-Content -LiteralPath (Join-Path $testRepository 'docs\phases\phase-09\review.md') -Value "# Phase 09 Review`n`n- Reviewer: Codex`n- Verdict: PASS`n" -Encoding utf8NoBOM

            $nextBaseline = (git -C $testRepository rev-parse HEAD).Trim()
            $result = & $setReviewPath -RepositoryRoot $testRepository -StatePath $statePath -Decision PASS -Reason 'reviewed' -NextBaselineCommit $nextBaseline
            $result.status | Should Be 'READY'
            $result.phase | Should Be 10
            $result.branch | Should Be 'codex/phase-10-observability-security'
        }
        finally {
            if (Test-Path -LiteralPath $testRepository) {
                Remove-Item -LiteralPath $testRepository -Recurse -Force
            }
        }
    }

    It 'does not consume an invocation when an active writer blocks startup' {
        $testRepository = New-OrchestratorTestRepository
        $statePath = Join-Path $testRepository '.orchestration\state.json'
        $runtimeDirectory = Join-Path $testRepository '.orchestration\runs'
        try {
            $throws = Test-Throws {
                & $invokePhasePath -Phase 1 -RepositoryRoot $testRepository -CmdcPath $fakeCmdcPath -StatePath $statePath -RuntimeDirectory $runtimeDirectory -ActiveWriterProbe {
                    @([pscustomobject]@{ processId = 19708; name = 'node.exe'; commandLine = 'command-code --model gpt-5.6-luna' })
                }
            }
            $state = Read-OrchestrationState -StatePath $statePath

            $throws | Should Be $true
            $state.status | Should Be 'READY'
            $state.attempt | Should Be 0
        }
        finally {
            if (Test-Path -LiteralPath $testRepository) {
                Remove-Item -LiteralPath $testRepository -Recurse -Force
            }
        }
    }

    It 'does not attribute an unchanged pre-existing REVISE review to cmdc' {
        $testRepository = New-OrchestratorTestRepository
        $statePath = Join-Path $testRepository '.orchestration\state.json'
        $runtimeDirectory = Join-Path $testRepository '.orchestration\runs'
        $reviewPath = Join-Path $testRepository 'docs\phases\phase-01\review.md'
        try {
            & $invokePhasePath -Phase 1 -RepositoryRoot $testRepository -CmdcPath $fakeCmdcPath -StatePath $statePath -RuntimeDirectory $runtimeDirectory -ActiveWriterProbe { @() } | Out-Null
            Set-Content -LiteralPath $reviewPath -Value "# Phase 01 Review`n`n- Reviewer: Codex`n- Verdict: REVISE`n" -Encoding utf8NoBOM
            & $setReviewPath -RepositoryRoot $testRepository -StatePath $statePath -Decision REVISE -Reason 'fix findings' | Out-Null

            $result = & $invokePhasePath -Phase 1 -RepositoryRoot $testRepository -CmdcPath $fakeCmdcPath -StatePath $statePath -RuntimeDirectory $runtimeDirectory -ActiveWriterProbe { @() }

            $result.status | Should Be 'REVIEWING'
            $result.attempt | Should Be 2
        }
        finally {
            if (Test-Path -LiteralPath $testRepository) {
                Remove-Item -LiteralPath $testRepository -Recurse -Force
            }
        }
    }

    It 'blocks cmdc when it modifies the pre-existing Codex review' {
        $testRepository = New-OrchestratorTestRepository
        $statePath = Join-Path $testRepository '.orchestration\state.json'
        $runtimeDirectory = Join-Path $testRepository '.orchestration\runs'
        $reviewPath = Join-Path $testRepository 'docs\phases\phase-01\review.md'
        try {
            & $invokePhasePath -Phase 1 -RepositoryRoot $testRepository -CmdcPath $fakeCmdcPath -StatePath $statePath -RuntimeDirectory $runtimeDirectory -ActiveWriterProbe { @() } | Out-Null
            Set-Content -LiteralPath $reviewPath -Value "# Phase 01 Review`n`n- Reviewer: Codex`n- Verdict: REVISE`n" -Encoding utf8NoBOM
            & $setReviewPath -RepositoryRoot $testRepository -StatePath $statePath -Decision REVISE -Reason 'fix findings' | Out-Null
            $env:MIDDLEPROJECT_FAKE_CMDC_WRITE_PATH = $reviewPath

            $result = & $invokePhasePath -Phase 1 -RepositoryRoot $testRepository -CmdcPath $fakeCmdcPath -StatePath $statePath -RuntimeDirectory $runtimeDirectory -ActiveWriterProbe { @() }

            $result.status | Should Be 'BLOCKED'
            $result.reason | Should Match ([regex]::Escape('docs/phases/phase-01/review.md'))
        }
        finally {
            Remove-Item Env:\MIDDLEPROJECT_FAKE_CMDC_WRITE_PATH -ErrorAction SilentlyContinue
            if (Test-Path -LiteralPath $testRepository) {
                Remove-Item -LiteralPath $testRepository -Recurse -Force
            }
        }
    }

    It 'blocks cmdc when it creates a Git commit' {
        $testRepository = New-OrchestratorTestRepository
        $statePath = Join-Path $testRepository '.orchestration\state.json'
        $runtimeDirectory = Join-Path $testRepository '.orchestration\runs'
        $env:MIDDLEPROJECT_FAKE_CMDC_COMMIT_PATH = 'frontend/committed-by-agent.txt'
        try {
            $result = & $invokePhasePath -Phase 1 -RepositoryRoot $testRepository -CmdcPath $fakeCmdcPath -StatePath $statePath -RuntimeDirectory $runtimeDirectory -ActiveWriterProbe { @() }

            $result.status | Should Be 'BLOCKED'
            $result.reason | Should Match 'HEAD|commit'
        }
        finally {
            Remove-Item Env:\MIDDLEPROJECT_FAKE_CMDC_COMMIT_PATH -ErrorAction SilentlyContinue
            if (Test-Path -LiteralPath $testRepository) {
                Remove-Item -LiteralPath $testRepository -Recurse -Force
            }
        }
    }
}
