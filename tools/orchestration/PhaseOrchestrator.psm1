Set-StrictMode -Version Latest

function Get-PhaseDefinition {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)]
        [int]$Phase,

        [Parameter(Mandatory)]
        [string]$ManifestPath
    )

    if (-not (Test-Path -LiteralPath $ManifestPath)) {
        throw "Phase manifest does not exist: $ManifestPath"
    }

    $manifest = Get-Content -LiteralPath $ManifestPath -Raw | ConvertFrom-Json
    $definition = @($manifest.phases | Where-Object { [int]$_.phase -eq $Phase })
    if ($definition.Count -ne 1) {
        throw "Phase $Phase is outside the configured application implementation range."
    }

    $phaseDefinition = $definition[0]
    foreach ($propertyName in @('model', 'effort', 'maxTurns', 'maxInvocationsPerPhase')) {
        if ($propertyName -notin $phaseDefinition.PSObject.Properties.Name) {
            $phaseDefinition | Add-Member -NotePropertyName $propertyName -NotePropertyValue $manifest.$propertyName
        }
    }
    if ('repairMaxTurns' -notin $phaseDefinition.PSObject.Properties.Name) {
        $repairMaxTurns = if ('repairMaxTurns' -in $manifest.PSObject.Properties.Name) {
            $manifest.repairMaxTurns
        }
        else {
            $phaseDefinition.maxTurns
        }
        $phaseDefinition | Add-Member -NotePropertyName repairMaxTurns -NotePropertyValue $repairMaxTurns
    }
    return $phaseDefinition
}

function New-PhaseRuntimePrompt {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)]
        [psobject]$PhaseDefinition,

        [Parameter(Mandatory)]
        [string]$RepositoryRoot,

        [Parameter(Mandatory)]
        [ValidatePattern('^[0-9a-fA-F]{40}$')]
        [string]$BaselineCommit
    )

    $invariantsPath = Join-Path $RepositoryRoot 'docs\architecture\project-invariants.md'
    $briefPath = Join-Path $RepositoryRoot $PhaseDefinition.briefPath
    $implementationPromptPath = Join-Path $RepositoryRoot $PhaseDefinition.implementationPromptPath

    foreach ($requiredPath in @($invariantsPath, $briefPath, $implementationPromptPath)) {
        if (-not (Test-Path -LiteralPath $requiredPath)) {
            throw "Required phase document does not exist: $requiredPath"
        }
    }

    $invariants = Get-Content -LiteralPath $invariantsPath -Raw
    $brief = Get-Content -LiteralPath $briefPath -Raw
    $implementationPrompt = (Get-Content -LiteralPath $implementationPromptPath -Raw).Replace('<BASE_COMMIT>', $BaselineCommit)
    $allowedPathList = @($PhaseDefinition.allowedPaths | ForEach-Object { "- $_" }) -join [Environment]::NewLine

    $continuation = ''
    if ([int]$PhaseDefinition.phase -eq 1) {
        $continuation = @"
Preserve the existing valid backend commits and the current frontend working tree.
Do not recreate completed work. Inspect the repository and continue from the first incomplete acceptance criterion.
"@
    }

    return @"
You are the implementation agent for Phase $($PhaseDefinition.phase). Work only in the current repository.

Baseline commit: $BaselineCommit
Model: $($PhaseDefinition.model)
Reasoning effort: $($PhaseDefinition.effort)
Result file: $($PhaseDefinition.resultPath)

$continuation
Allowed implementation paths:
$allowedPathList

Required safety boundaries:
- Do not push, merge, rebase, reset, or rewrite Git history.
- Do not create Git commits. Codex Desktop commits only after independent verification.
- Do not modify Architecture, ADRs, project invariants, phase briefs, or phase implementation prompts.
- Do not run terraform apply or mutate live AWS resources.
- Do not create, rotate, reveal, or persist credentials or secrets.
- If $($PhaseDefinition.reviewPath) exists with a non-PASS verdict, read it and address only its unresolved findings.
- Do not edit $($PhaseDefinition.reviewPath). Codex Desktop owns the review decision.
- Record every verification command, real exit code, and blocker truthfully in $($PhaseDefinition.resultPath).
- Stop and report if any required change falls outside the allowlist.

Read first: docs/architecture/project-invariants.md

--- PROJECT INVARIANTS ---
$invariants

--- PHASE BRIEF: $($PhaseDefinition.briefPath) ---
$brief

--- COMMITTED IMPLEMENTATION PROMPT: $($PhaseDefinition.implementationPromptPath) ---
$implementationPrompt
"@
}

function New-OrchestrationState {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)]
        [psobject]$PhaseDefinition,

        [Parameter(Mandatory)]
        [ValidatePattern('^[0-9a-fA-F]{40}$')]
        [string]$BaselineCommit
    )

    return [pscustomobject]@{
        schemaVersion            = 1
        phase                    = [int]$PhaseDefinition.phase
        status                   = 'READY'
        attempt                  = 0
        branch                   = [string]$PhaseDefinition.branch
        baselineCommit           = $BaselineCommit
        maxInvocationsPerPhase   = [int]$PhaseDefinition.maxInvocationsPerPhase
        externalApprovalGranted = $false
        reason                   = 'initialized'
        updatedAt                = [DateTimeOffset]::UtcNow.ToString('o')
        history                  = @()
    }
}

function Move-OrchestrationState {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)]
        [psobject]$State,

        [Parameter(Mandatory)]
        [ValidateSet('READY', 'IMPLEMENTING', 'REVIEWING', 'PASS', 'REVISE', 'AWAITING_APPROVAL', 'BLOCKED')]
        [string]$ToStatus,

        [Parameter(Mandatory)]
        [ValidateNotNullOrEmpty()]
        [string]$Reason,

        [Parameter(Mandatory)]
        [string]$ManifestPath,

        [switch]$ExternalApproval,

        [ValidatePattern('^[0-9a-fA-F]{40}$')]
        [string]$NextBaselineCommit
    )

    $fromStatus = [string]$State.status
    $phaseAtTransition = [int]$State.phase
    $manifest = Get-Content -LiteralPath $ManifestPath -Raw | ConvertFrom-Json
    $terminalPhase = [int](@($manifest.phases.phase | Measure-Object -Maximum).Maximum)
    $transition = "$fromStatus->$ToStatus"
    $allowedTransitions = @(
        'READY->IMPLEMENTING',
        'READY->BLOCKED',
        'IMPLEMENTING->READY',
        'IMPLEMENTING->REVIEWING',
        'IMPLEMENTING->BLOCKED',
        'REVIEWING->PASS',
        'REVIEWING->REVISE',
        'REVIEWING->AWAITING_APPROVAL',
        'REVIEWING->BLOCKED',
        'AWAITING_APPROVAL->REVIEWING',
        'AWAITING_APPROVAL->BLOCKED'
    )

    if ($transition -notin $allowedTransitions) {
        throw "Invalid orchestration transition: $transition"
    }

    if ($transition -eq 'AWAITING_APPROVAL->REVIEWING' -and -not $ExternalApproval) {
        throw 'Explicit external approval is required before review can resume.'
    }

    if ($transition -eq 'REVIEWING->PASS' -and $phaseAtTransition -lt $terminalPhase -and [string]::IsNullOrWhiteSpace($NextBaselineCommit)) {
        throw 'NextBaselineCommit is required when a passing phase advances to the next phase.'
    }

    if ($transition -eq 'READY->IMPLEMENTING') {
        if ([int]$State.attempt -ge [int]$State.maxInvocationsPerPhase) {
            throw "Phase $($State.phase) already consumed all $($State.maxInvocationsPerPhase) Command Code invocations."
        }
        $State.attempt = [int]$State.attempt + 1
    }

    $history = @($State.history)
    $history += [pscustomobject]@{
        phase     = $phaseAtTransition
        from      = $fromStatus
        decision  = $ToStatus
        reason    = $Reason
        timestamp = [DateTimeOffset]::UtcNow.ToString('o')
    }
    $State.history = $history
    $State.reason = $Reason
    $State.updatedAt = [DateTimeOffset]::UtcNow.ToString('o')

    switch ($transition) {
        'REVIEWING->PASS' {
            if ($phaseAtTransition -eq $terminalPhase) {
                $State.status = 'COMPLETE'
                break
            }

            $nextDefinition = Get-PhaseDefinition -Phase ($phaseAtTransition + 1) -ManifestPath $ManifestPath
            $State.phase = [int]$nextDefinition.phase
            $State.status = 'READY'
            $State.attempt = 0
            $State.branch = [string]$nextDefinition.branch
            $State.baselineCommit = $NextBaselineCommit
            $State.maxInvocationsPerPhase = [int]$nextDefinition.maxInvocationsPerPhase
            $State.externalApprovalGranted = $false
        }
        'REVIEWING->REVISE' {
            $State.status = 'READY'
        }
        'AWAITING_APPROVAL->REVIEWING' {
            $State.status = 'REVIEWING'
            $State.externalApprovalGranted = $true
        }
        default {
            $State.status = $ToStatus
        }
    }

    return $State
}

function Write-OrchestrationState {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)]
        [psobject]$State,

        [Parameter(Mandatory)]
        [string]$StatePath
    )

    $parent = Split-Path -Parent $StatePath
    if ([string]::IsNullOrWhiteSpace($parent)) {
        $parent = (Get-Location).Path
        $StatePath = Join-Path $parent $StatePath
    }
    [System.IO.Directory]::CreateDirectory($parent) | Out-Null

    $temporaryPath = Join-Path $parent ('.' + [System.IO.Path]::GetFileName($StatePath) + '.' + [guid]::NewGuid().ToString('N') + '.tmp')
    try {
        $json = $State | ConvertTo-Json -Depth 20
        [System.IO.File]::WriteAllText($temporaryPath, $json, [System.Text.UTF8Encoding]::new($false))
        [System.IO.File]::Move($temporaryPath, $StatePath, $true)
    }
    finally {
        if (Test-Path -LiteralPath $temporaryPath) {
            Remove-Item -LiteralPath $temporaryPath -Force
        }
    }
}

function Read-OrchestrationState {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)]
        [string]$StatePath
    )

    if (-not (Test-Path -LiteralPath $StatePath)) {
        throw "Orchestration state does not exist: $StatePath"
    }

    return Get-Content -LiteralPath $StatePath -Raw | ConvertFrom-Json
}

function Test-PhaseChangeScope {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)]
        [psobject]$PhaseDefinition,

        [Parameter(Mandatory)]
        [AllowEmptyCollection()]
        [string[]]$ChangedPaths
    )

    $allowed = [System.Collections.Generic.List[string]]::new()
    $rejected = [System.Collections.Generic.List[string]]::new()

    foreach ($changedPath in $ChangedPaths) {
        $isAllowed = $false
        $normalized = $changedPath.Replace('\', '/').Trim()
        while ($normalized.StartsWith('./', [System.StringComparison]::Ordinal)) {
            $normalized = $normalized.Substring(2)
        }

        $segments = @($normalized.Split('/', [System.StringSplitOptions]::RemoveEmptyEntries))
        $isUnsafe = [System.IO.Path]::IsPathRooted($changedPath) -or $segments -contains '..'

        if (-not $isUnsafe) {
            foreach ($patternValue in @($PhaseDefinition.allowedPaths)) {
                $pattern = ([string]$patternValue).Replace('\', '/').Trim()
                if ($pattern.EndsWith('/**', [System.StringComparison]::Ordinal)) {
                    $root = $pattern.Substring(0, $pattern.Length - 3)
                    if ($normalized.Equals($root, [System.StringComparison]::OrdinalIgnoreCase) -or
                        $normalized.StartsWith($root + '/', [System.StringComparison]::OrdinalIgnoreCase)) {
                        $isAllowed = $true
                        break
                    }
                }
                elseif ($normalized.Equals($pattern, [System.StringComparison]::OrdinalIgnoreCase)) {
                    $isAllowed = $true
                    break
                }
            }
        }

        if ($isAllowed) {
            $allowed.Add($changedPath)
        }
        else {
            $rejected.Add($changedPath)
        }
    }

    return [pscustomobject]@{
        isAllowed     = $rejected.Count -eq 0
        allowedPaths  = @($allowed)
        rejectedPaths = @($rejected)
    }
}

function Get-WorkingTreeSnapshot {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)]
        [string]$RepositoryRoot,

        [Parameter(Mandatory)]
        [ValidatePattern('^[0-9a-fA-F]{40}$')]
        [string]$BaselineCommit
    )

    $resolvedRepositoryRoot = (Resolve-Path -LiteralPath $RepositoryRoot -ErrorAction Stop).Path
    $trackedChanges = @(git -C $resolvedRepositoryRoot -c core.quotepath=false diff --name-only $BaselineCommit --)
    if ($LASTEXITCODE -ne 0) {
        throw 'Unable to inspect tracked phase changes.'
    }

    $untrackedChanges = @(git -C $resolvedRepositoryRoot -c core.quotepath=false ls-files --others --exclude-standard)
    if ($LASTEXITCODE -ne 0) {
        throw 'Unable to inspect untracked phase changes.'
    }

    $entries = [System.Collections.Generic.List[object]]::new()
    $paths = @(
        $trackedChanges + $untrackedChanges |
            Where-Object { -not [string]::IsNullOrWhiteSpace($_) } |
            Sort-Object -Unique
    )

    foreach ($pathValue in $paths) {
        $relativePath = ([string]$pathValue).Replace('\', '/')
        $absolutePath = Join-Path $resolvedRepositoryRoot $relativePath
        $fingerprint = '<missing>'
        if (Test-Path -LiteralPath $absolutePath -PathType Leaf) {
            $fingerprint = (Get-FileHash -LiteralPath $absolutePath -Algorithm SHA256 -ErrorAction Stop).Hash
        }

        $entries.Add([pscustomobject]@{
            path        = $relativePath
            fingerprint = $fingerprint
        })
    }

    return @($entries)
}

function Compare-WorkingTreeSnapshot {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)]
        [AllowEmptyCollection()]
        [object[]]$Before,

        [Parameter(Mandatory)]
        [AllowEmptyCollection()]
        [object[]]$After
    )

    $beforeByPath = [System.Collections.Generic.Dictionary[string, string]]::new([System.StringComparer]::OrdinalIgnoreCase)
    $afterByPath = [System.Collections.Generic.Dictionary[string, string]]::new([System.StringComparer]::OrdinalIgnoreCase)
    foreach ($entry in $Before) {
        $beforeByPath[[string]$entry.path] = [string]$entry.fingerprint
    }
    foreach ($entry in $After) {
        $afterByPath[[string]$entry.path] = [string]$entry.fingerprint
    }

    $changedPaths = [System.Collections.Generic.List[string]]::new()
    foreach ($pathValue in $afterByPath.Keys) {
        if (-not $beforeByPath.ContainsKey($pathValue) -or $beforeByPath[$pathValue] -ne $afterByPath[$pathValue]) {
            $changedPaths.Add($pathValue)
        }
    }
    foreach ($pathValue in $beforeByPath.Keys) {
        if (-not $afterByPath.ContainsKey($pathValue)) {
            $changedPaths.Add($pathValue)
        }
    }

    return @($changedPaths | Sort-Object -Unique)
}

function New-CommandCodeRunPlan {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)]
        [psobject]$PhaseDefinition,

        [Parameter(Mandatory)]
        [string]$RuntimePrompt,

        [Parameter(Mandatory)]
        [string]$CmdcPath,

        [Parameter(Mandatory)]
        [string]$RepositoryRoot,

        [ValidatePattern('^$|^[0-9a-fA-F]{40}$')]
        [string]$BaselineCommit = '',

        [ValidateRange(1, [int]::MaxValue)]
        [int]$AttemptNumber = 1
    )

    $phaseNumber = '{0:D2}' -f [int]$PhaseDefinition.phase
    $resolvedRepositoryRoot = (Resolve-Path -LiteralPath $RepositoryRoot -ErrorAction Stop).Path
    $maxTurns = if ($AttemptNumber -gt 1) { [int]$PhaseDefinition.repairMaxTurns } else { [int]$PhaseDefinition.maxTurns }
    return [pscustomobject]@{
        phase          = [int]$PhaseDefinition.phase
        command        = $CmdcPath
        arguments      = @(
            '-p',
            $RuntimePrompt,
            '--model',
            [string]$PhaseDefinition.model,
            '--effort',
            [string]$PhaseDefinition.effort,
            '--auto-accept',
            '--yolo',
            '--max-turns',
            [string]$maxTurns,
            '--output-format',
            'json',
            '--name',
            "phase-$phaseNumber-implementation"
        )
        baselineCommit = $BaselineCommit
        model           = [string]$PhaseDefinition.model
        effort          = [string]$PhaseDefinition.effort
        maxTurns        = $maxTurns
        attemptNumber   = $AttemptNumber
        workingDirectory = $resolvedRepositoryRoot
    }
}

function Get-ActiveAgentWriter {
    [CmdletBinding()]
    param()

    $processes = Get-CimInstance Win32_Process -ErrorAction Stop | Where-Object {
        $_.ProcessId -ne $PID -and
        $_.Name -match '^(node|opencode|cmdc|cmd)(\.exe)?$' -and
        $_.CommandLine -match '(command-code|opencode|cmdc)'
    }

    return @(
        $processes | ForEach-Object {
            [pscustomobject]@{
                processId  = [int]$_.ProcessId
                name       = [string]$_.Name
                commandLine = [string]$_.CommandLine
            }
        }
    )
}

function Invoke-CommandCodeAttempt {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)]
        [psobject]$Plan,

        [Parameter(Mandatory)]
        [string]$LogDirectory,

        [switch]$DryRun,

        [scriptblock]$ActiveWriterProbe = { Get-ActiveAgentWriter }
    )

    if ($DryRun) {
        return [pscustomobject]@{
            invoked        = $false
            status         = 'DRY_RUN'
            exitCode       = $null
            phase          = [int]$Plan.phase
            baselineCommit = [string]$Plan.baselineCommit
            command        = [string]$Plan.command
            arguments      = @($Plan.arguments)
            workingDirectory = [string]$Plan.workingDirectory
            stdoutPath     = $null
            stderrPath     = $null
            startedAt      = $null
            finishedAt     = $null
        }
    }

    $activeWriters = @(& $ActiveWriterProbe)
    if ($activeWriters.Count -gt 0) {
        $writerSummary = @($activeWriters | ForEach-Object { "$($_.processId):$($_.name)" }) -join ', '
        throw "Another coding agent writer is active: $writerSummary"
    }

    [System.IO.Directory]::CreateDirectory($LogDirectory) | Out-Null
    $timestamp = [DateTimeOffset]::UtcNow.ToString('yyyyMMddTHHmmssfffZ')
    $phaseNumber = '{0:D2}' -f [int]$Plan.phase
    $stdoutPath = Join-Path $LogDirectory "phase-$phaseNumber-$timestamp.stdout.jsonl"
    $stderrPath = Join-Path $LogDirectory "phase-$phaseNumber-$timestamp.stderr.log"
    $startedAt = [DateTimeOffset]::UtcNow

    $arguments = @($Plan.arguments)
    Push-Location -LiteralPath $Plan.workingDirectory
    try {
        & $Plan.command @arguments 1> $stdoutPath 2> $stderrPath
        $exitCode = $LASTEXITCODE
    }
    finally {
        Pop-Location
    }
    if ($null -eq $exitCode) {
        $exitCode = 0
    }
    $finishedAt = [DateTimeOffset]::UtcNow

    return [pscustomobject]@{
        invoked        = $true
        status         = $(if ([int]$exitCode -eq 0) { 'SUCCEEDED' } else { 'FAILED' })
        exitCode       = [int]$exitCode
        phase          = [int]$Plan.phase
        baselineCommit = [string]$Plan.baselineCommit
        command        = [string]$Plan.command
        arguments      = @($Plan.arguments)
        workingDirectory = [string]$Plan.workingDirectory
        stdoutPath     = $stdoutPath
        stderrPath     = $stderrPath
        startedAt      = $startedAt.ToString('o')
        finishedAt     = $finishedAt.ToString('o')
    }
}

Export-ModuleMember -Function Get-PhaseDefinition, New-PhaseRuntimePrompt, New-OrchestrationState, Move-OrchestrationState, Write-OrchestrationState, Read-OrchestrationState, Test-PhaseChangeScope, Get-WorkingTreeSnapshot, Compare-WorkingTreeSnapshot, New-CommandCodeRunPlan, Get-ActiveAgentWriter, Invoke-CommandCodeAttempt
