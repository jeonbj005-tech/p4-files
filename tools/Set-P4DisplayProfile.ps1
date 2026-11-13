param(
    [Parameter(Mandatory)][ValidateSet('Configure', 'Restore')][string]$Action,
    [Parameter(Mandatory)][string]$Serial
)

$ErrorActionPreference = 'Stop'
$adb = Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe'
if (-not (Test-Path -LiteralPath $adb)) { throw "adb not found: $adb" }

$deviceList = & $adb devices
if ($LASTEXITCODE -ne 0 -or -not ($deviceList | Where-Object { $_ -match "^$([regex]::Escape($Serial))\s+device$" })) {
    throw "ADB device is not connected: $Serial"
}
$qemu = (& $adb -s $Serial shell getprop ro.kernel.qemu).Trim()
if ($LASTEXITCODE -ne 0 -or $qemu -ne '1') { throw 'This script only changes Android emulators; real devices are refused.' }

$stateRoot = Join-Path $env:LOCALAPPDATA 'P4FilesDisplayProfile'
$safeSerial = $Serial -replace '[^A-Za-z0-9._-]', '_'
$statePath = Join-Path $stateRoot "$safeSerial.json"

function Invoke-AdbShell([string[]]$Arguments) {
    $result = & $adb -s $Serial shell @Arguments
    if ($LASTEXITCODE -ne 0) { throw "adb shell $($Arguments -join ' ') failed with exit code $LASTEXITCODE" }
    return ($result -join "`n")
}

function Get-MatchValue([string]$Text, [string]$Pattern) {
    $match = [regex]::Match($Text, $Pattern, [System.Text.RegularExpressions.RegexOptions]::Multiline)
    if ($match.Success) { return $match.Groups[1].Value }
    return $null
}

if ($Action -eq 'Configure') {
    if (Test-Path -LiteralPath $statePath) {
        $state = Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json
        if ($state.serial -ne $Serial) { throw "Saved state belongs to a different emulator: $($state.serial)" }
    } else {
        $size = Invoke-AdbShell @('wm', 'size')
        $density = Invoke-AdbShell @('wm', 'density')
        $physicalSize = Get-MatchValue $size '^Physical size:\s*(\d+x\d+)$'
        $physicalDensity = Get-MatchValue $density '^Physical density:\s*(\d+)$'
        if (-not $physicalSize -or -not $physicalDensity) { throw 'Could not read the emulator display defaults; no settings were changed.' }
        New-Item -ItemType Directory -Path $stateRoot -Force | Out-Null
        $state = [ordered]@{
            serial = $Serial
            capturedAt = (Get-Date).ToString('o')
            physicalSize = $physicalSize
            sizeOverride = Get-MatchValue $size '^Override size:\s*(\d+x\d+)$'
            physicalDensity = [int]$physicalDensity
            densityOverride = Get-MatchValue $density '^Override density:\s*(\d+)$'
        }
        $json = $state | ConvertTo-Json
        [System.IO.File]::WriteAllText($statePath, $json, [System.Text.UTF8Encoding]::new($false))
    }

    Invoke-AdbShell @('wm', 'size', '1920x1200') | Out-Null
    Invoke-AdbShell @('wm', 'density', '160') | Out-Null
    Write-Output "Configured ${Serial}: 1920x1200 at temporary 160 dpi. Original overrides saved to $statePath"
    Write-Output 'The AVD orientation is unchanged; use a landscape AAOS profile.'
    return
}

if (-not (Test-Path -LiteralPath $statePath)) { throw "No saved display state for ${Serial}: $statePath" }
$saved = Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json
if ($saved.serial -ne $Serial) { throw "Saved state belongs to a different emulator: $($saved.serial)" }
if ($saved.sizeOverride) { Invoke-AdbShell @('wm', 'size', [string]$saved.sizeOverride) | Out-Null }
else { Invoke-AdbShell @('wm', 'size', 'reset') | Out-Null }
if ($saved.densityOverride) { Invoke-AdbShell @('wm', 'density', [string]$saved.densityOverride) | Out-Null }
else { Invoke-AdbShell @('wm', 'density', 'reset') | Out-Null }
Remove-Item -LiteralPath $statePath -Force
Write-Output "Restored $Serial to $($saved.physicalSize) at $($saved.physicalDensity) dpi; previous overrides were restored."
