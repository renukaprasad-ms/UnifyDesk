# Windows Terminal / PowerShell launcher for the existing WSL Kubernetes setup.
# Example: .\k8s.ps1 -Distro Ubuntu start
[CmdletBinding()]
param(
    [string]$Distro,
    [Parameter(Position = 0, ValueFromRemainingArguments = $true)]
    [string[]]$CommandArguments
)

$ErrorActionPreference = 'Stop'
if (-not (Get-Command wsl.exe -ErrorAction SilentlyContinue)) {
    throw 'WSL is required. Run this launcher in Windows PowerShell with WSL installed.'
}

$wslArguments = @()
# A repository opened through a WSL share must run in that share's distribution.
if ($PSScriptRoot -match '^\\\\(?:wsl\$|wsl\.localhost)\\([^\\]+)(\\.*)?$') {
    $shareDistro = $Matches[1]
    $linuxRoot = $Matches[2] -replace '\\', '/'
    if (-not $linuxRoot) { $linuxRoot = '/' }
    if ($Distro -and $Distro -ne $shareDistro) {
        throw "This repository belongs to WSL distribution '$shareDistro'. Use that distribution."
    }
    $Distro = $shareDistro
} else {
    if ($Distro) { $wslArguments = @('--distribution', $Distro) }
    $linuxRoot = & wsl.exe @wslArguments --exec wslpath -u $PSScriptRoot
    if ($LASTEXITCODE -ne 0 -or -not $linuxRoot) {
        throw 'Could not resolve the repository path in WSL. Check the selected distribution.'
    }
    $linuxRoot = ($linuxRoot -join "`n").Trim()
}

if ($Distro) { $wslArguments = @('--distribution', $Distro) }
# Pass arguments directly; do not interpolate them into a shell command.
# Keep Bash interactive input/output attached for logs, top, sudo and clear-db.
& wsl.exe @wslArguments --cd $linuxRoot --exec bash ./k8s.sh @CommandArguments
exit $LASTEXITCODE
