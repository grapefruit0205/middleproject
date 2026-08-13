$exitCode = 0
if (-not [string]::IsNullOrWhiteSpace($env:MIDDLEPROJECT_FAKE_CMDC_EXIT_CODE)) {
    $exitCode = [int]$env:MIDDLEPROJECT_FAKE_CMDC_EXIT_CODE
}
foreach ($argument in $args) {
    if ($argument -match '__FAKE_EXIT_(\d+)__') {
        $exitCode = [int]$Matches[1]
    }
}

if (-not [string]::IsNullOrWhiteSpace($env:MIDDLEPROJECT_FAKE_CMDC_MARKER)) {
    [System.IO.File]::WriteAllText($env:MIDDLEPROJECT_FAKE_CMDC_MARKER, 'invoked')
}
if (-not [string]::IsNullOrWhiteSpace($env:MIDDLEPROJECT_FAKE_CMDC_WRITE_PATH)) {
    Set-Content -LiteralPath $env:MIDDLEPROJECT_FAKE_CMDC_WRITE_PATH -Value 'modified by fake cmdc' -Encoding utf8NoBOM
}
if (-not [string]::IsNullOrWhiteSpace($env:MIDDLEPROJECT_FAKE_CMDC_COMMIT_PATH)) {
    $commitPath = Join-Path (Get-Location).Path $env:MIDDLEPROJECT_FAKE_CMDC_COMMIT_PATH
    $commitParent = Split-Path -Parent $commitPath
    [System.IO.Directory]::CreateDirectory($commitParent) | Out-Null
    Set-Content -LiteralPath $commitPath -Value 'committed by fake cmdc' -Encoding utf8NoBOM
    git add -- $env:MIDDLEPROJECT_FAKE_CMDC_COMMIT_PATH
    git commit -m 'fake cmdc commit'
}

Write-Output ([pscustomobject]@{
    arguments = @($args)
    workingDirectory = (Get-Location).Path
} | ConvertTo-Json -Compress)
Write-Error 'fake cmdc stderr' -ErrorAction Continue
exit $exitCode
