#Requires -Version 7.0

[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [string]$RepositoryRoot,

    [Parameter(Mandatory)]
    [string]$StatePath,

    [Parameter(Mandatory)]
    [ValidateSet('PASS', 'REVISE', 'BLOCKED', 'AWAITING_APPROVAL', 'RESUME_AFTER_APPROVAL')]
    [string]$Decision,

    [Parameter(Mandatory)]
    [ValidateNotNullOrEmpty()]
    [string]$Reason,

    [ValidatePattern('^$|^[0-9a-fA-F]{40}$')]
    [string]$NextBaselineCommit = '',

    [switch]$ExternalApproval
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$modulePath = Join-Path $PSScriptRoot 'PhaseOrchestrator.psm1'
$manifestPath = Join-Path $PSScriptRoot 'phases.json'
Import-Module $modulePath -Force

$resolvedRepositoryRoot = (Resolve-Path -LiteralPath $RepositoryRoot).Path
$state = Read-OrchestrationState -StatePath $StatePath
$definition = Get-PhaseDefinition -Phase ([int]$state.phase) -ManifestPath $manifestPath
$manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
$terminalPhase = [int](@($manifest.phases.phase | Measure-Object -Maximum).Maximum)

if ($Decision -eq 'RESUME_AFTER_APPROVAL') {
    if (-not $ExternalApproval) {
        throw 'RESUME_AFTER_APPROVAL requires the ExternalApproval switch.'
    }
    $state = Move-OrchestrationState -State $state -ToStatus REVIEWING -Reason $Reason -ManifestPath $manifestPath -ExternalApproval
    Write-OrchestrationState -State $state -StatePath $StatePath
    return $state
}

if ([string]$state.status -ne 'REVIEWING') {
    throw "Review decision $Decision requires REVIEWING state, but state is $($state.status)."
}

switch ($Decision) {
    'PASS' {
        $reviewPath = Join-Path $resolvedRepositoryRoot $definition.reviewPath
        if (-not (Test-Path -LiteralPath $reviewPath)) {
            throw "PASS requires a Codex review file: $($definition.reviewPath)"
        }
        $reviewContent = Get-Content -LiteralPath $reviewPath -Raw
        if ($reviewContent -notmatch '(?im)^\s*-\s*Verdict:\s*PASS\s*$') {
            throw "PASS requires '- Verdict: PASS' in $($definition.reviewPath)."
        }
        if ([bool]$definition.requiresExternalApproval -and -not [bool]$state.externalApprovalGranted) {
            throw "Phase $($state.phase) PASS requires recorded external approval for the live AWS verification gate."
        }
        if ([int]$state.phase -lt $terminalPhase) {
            if ([string]::IsNullOrWhiteSpace($NextBaselineCommit)) {
                throw 'NextBaselineCommit is required before advancing to the next phase.'
            }
            git -C $resolvedRepositoryRoot cat-file -e "$NextBaselineCommit`^{commit}"
            if ($LASTEXITCODE -ne 0) {
                throw "Next baseline commit does not exist: $NextBaselineCommit"
            }
            $state = Move-OrchestrationState -State $state -ToStatus PASS -Reason $Reason -ManifestPath $manifestPath -NextBaselineCommit $NextBaselineCommit
        }
        else {
            $state = Move-OrchestrationState -State $state -ToStatus PASS -Reason $Reason -ManifestPath $manifestPath
        }
    }
    'REVISE' {
        $state = Move-OrchestrationState -State $state -ToStatus REVISE -Reason $Reason -ManifestPath $manifestPath
    }
    'BLOCKED' {
        $state = Move-OrchestrationState -State $state -ToStatus BLOCKED -Reason $Reason -ManifestPath $manifestPath
    }
    'AWAITING_APPROVAL' {
        $state = Move-OrchestrationState -State $state -ToStatus AWAITING_APPROVAL -Reason $Reason -ManifestPath $manifestPath
    }
}

Write-OrchestrationState -State $state -StatePath $StatePath
return $state
