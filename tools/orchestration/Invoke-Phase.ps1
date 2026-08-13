#Requires -Version 7.0

[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [ValidateRange(1, 9)]
    [int]$Phase,

    [Parameter(Mandatory)]
    [string]$RepositoryRoot,

    [string]$CmdcPath = 'cmdc',

    [string]$StatePath,

    [string]$RuntimeDirectory,

    [switch]$DryRun,

    [scriptblock]$ActiveWriterProbe
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$modulePath = Join-Path $PSScriptRoot 'PhaseOrchestrator.psm1'
$manifestPath = Join-Path $PSScriptRoot 'phases.json'
Import-Module $modulePath -Force

$resolvedRepositoryRoot = (Resolve-Path -LiteralPath $RepositoryRoot).Path
if ([string]::IsNullOrWhiteSpace($StatePath)) {
    $StatePath = Join-Path $resolvedRepositoryRoot '.orchestration\state.json'
}
if ([string]::IsNullOrWhiteSpace($RuntimeDirectory)) {
    $RuntimeDirectory = Join-Path $resolvedRepositoryRoot '.orchestration\runs'
}

$definition = Get-PhaseDefinition -Phase $Phase -ManifestPath $manifestPath
$currentBranch = (git -C $resolvedRepositoryRoot branch --show-current).Trim()
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($currentBranch)) {
    throw "Unable to determine the current Git branch in $resolvedRepositoryRoot"
}
if ($currentBranch -ne [string]$definition.branch) {
    throw "Phase $Phase requires branch $($definition.branch), but the current branch is $currentBranch."
}

if (Test-Path -LiteralPath $StatePath) {
    $state = Read-OrchestrationState -StatePath $StatePath
    if ([int]$state.phase -ne $Phase) {
        throw "State expects Phase $($state.phase), not Phase $Phase."
    }
    if ([string]$state.status -ne 'READY') {
        throw "Phase $Phase cannot start while state is $($state.status)."
    }
    $baselineCommit = [string]$state.baselineCommit
}
else {
    $baselineCommit = (git -C $resolvedRepositoryRoot rev-parse HEAD).Trim()
    if ($LASTEXITCODE -ne 0 -or $baselineCommit -notmatch '^[0-9a-fA-F]{40}$') {
        throw 'Unable to determine a valid baseline commit.'
    }
    $state = New-OrchestrationState -PhaseDefinition $definition -BaselineCommit $baselineCommit
}

git -C $resolvedRepositoryRoot cat-file -e "$baselineCommit`^{commit}"
if ($LASTEXITCODE -ne 0) {
    throw "Baseline commit does not exist in the repository: $baselineCommit"
}

$runtimePrompt = New-PhaseRuntimePrompt -PhaseDefinition $definition -RepositoryRoot $resolvedRepositoryRoot -BaselineCommit $baselineCommit
$plan = New-CommandCodeRunPlan -PhaseDefinition $definition -RuntimePrompt $runtimePrompt -CmdcPath $CmdcPath -RepositoryRoot $resolvedRepositoryRoot -BaselineCommit $baselineCommit

if ($DryRun) {
    $dryRunResult = Invoke-CommandCodeAttempt -Plan $plan -LogDirectory $RuntimeDirectory -DryRun
    return [pscustomobject]@{
        status         = $dryRunResult.status
        phase          = $Phase
        branch         = $currentBranch
        baselineCommit = $baselineCommit
        model          = [string]$definition.model
        effort         = [string]$definition.effort
        autoAccept     = $true
        command        = $dryRunResult.command
        arguments      = @($dryRunResult.arguments)
        workingDirectory = $dryRunResult.workingDirectory
    }
}

$beforeSnapshot = @(Get-WorkingTreeSnapshot -RepositoryRoot $resolvedRepositoryRoot -BaselineCommit $baselineCommit)
$beforeHead = (git -C $resolvedRepositoryRoot rev-parse HEAD).Trim()
if ($LASTEXITCODE -ne 0 -or $beforeHead -notmatch '^[0-9a-fA-F]{40}$') {
    throw 'Unable to determine HEAD before the Command Code attempt.'
}
$beforeBranch = (git -C $resolvedRepositoryRoot branch --show-current).Trim()
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($beforeBranch)) {
    throw 'Unable to determine the branch before the Command Code attempt.'
}
$state = Move-OrchestrationState -State $state -ToStatus IMPLEMENTING -Reason "Command Code attempt $([int]$state.attempt + 1) started" -ManifestPath $manifestPath
Write-OrchestrationState -State $state -StatePath $StatePath

try {
    if ($null -ne $ActiveWriterProbe) {
        $attemptResult = Invoke-CommandCodeAttempt -Plan $plan -LogDirectory $RuntimeDirectory -ActiveWriterProbe $ActiveWriterProbe
    }
    else {
        $attemptResult = Invoke-CommandCodeAttempt -Plan $plan -LogDirectory $RuntimeDirectory
    }
}
catch {
    $state = Move-OrchestrationState -State $state -ToStatus READY -Reason "Command Code could not start: $($_.Exception.Message)" -ManifestPath $manifestPath
    $state.attempt = [Math]::Max(0, [int]$state.attempt - 1)
    Write-OrchestrationState -State $state -StatePath $StatePath
    throw
}

$afterHead = (git -C $resolvedRepositoryRoot rev-parse HEAD).Trim()
if ($LASTEXITCODE -ne 0 -or $afterHead -notmatch '^[0-9a-fA-F]{40}$') {
    throw 'Unable to determine HEAD after the Command Code attempt.'
}
$afterBranch = (git -C $resolvedRepositoryRoot branch --show-current).Trim()
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($afterBranch)) {
    throw 'Unable to determine the branch after the Command Code attempt.'
}
$afterSnapshot = @(Get-WorkingTreeSnapshot -RepositoryRoot $resolvedRepositoryRoot -BaselineCommit $baselineCommit)
$changedPaths = @(Compare-WorkingTreeSnapshot -Before $beforeSnapshot -After $afterSnapshot)
$scope = Test-PhaseChangeScope -PhaseDefinition $definition -ChangedPaths $changedPaths

if ($afterHead -ne $beforeHead -or $afterBranch -ne $beforeBranch) {
    $reason = "Command Code changed Git HEAD or branch ($beforeBranch@$beforeHead -> $afterBranch@$afterHead)."
    $state = Move-OrchestrationState -State $state -ToStatus BLOCKED -Reason $reason -ManifestPath $manifestPath
}
elseif (-not $scope.isAllowed) {
    $reason = 'Out-of-scope changes: ' + (@($scope.rejectedPaths) -join ', ')
    $state = Move-OrchestrationState -State $state -ToStatus BLOCKED -Reason $reason -ManifestPath $manifestPath
}
elseif ([int]$attemptResult.exitCode -ne 0) {
    $state = Move-OrchestrationState -State $state -ToStatus READY -Reason "Command Code exited with code $($attemptResult.exitCode)" -ManifestPath $manifestPath
}
else {
    $state = Move-OrchestrationState -State $state -ToStatus REVIEWING -Reason 'Command Code exited zero and phase paths stayed within scope.' -ManifestPath $manifestPath
}

Write-OrchestrationState -State $state -StatePath $StatePath

return [pscustomobject]@{
    status         = [string]$state.status
    phase          = [int]$state.phase
    attempt        = [int]$state.attempt
    branch         = [string]$state.branch
    baselineCommit = [string]$state.baselineCommit
    exitCode       = [int]$attemptResult.exitCode
    stdoutPath     = [string]$attemptResult.stdoutPath
    stderrPath     = [string]$attemptResult.stderrPath
    reason         = [string]$state.reason
}
