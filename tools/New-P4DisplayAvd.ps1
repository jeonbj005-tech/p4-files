$ErrorActionPreference = 'Stop'

$name = 'P4DisplayAAOS1920x1200_TEMP160'
$avdRoot = Join-Path $env:USERPROFILE '.android\avd'
$configPath = Join-Path $avdRoot "$name.avd\config.ini"
$image = 'system-images;android-33;android-automotive;x86_64'
$avdManager = Join-Path $env:ANDROID_HOME 'cmdline-tools\latest\bin\avdmanager.bat'

if (-not (Test-Path -LiteralPath $avdManager)) { throw "avdmanager not found: $avdManager" }
if (-not (Test-Path -LiteralPath $configPath)) {
    & $avdManager create avd --name $name --package $image --device automotive_1080p_landscape
    if ($LASTEXITCODE -ne 0) { throw "avdmanager failed with exit code $LASTEXITCODE" }
}

$existing = Get-Content -LiteralPath $configPath -Raw
if ($existing -notmatch '(?m)^target=android-33\r?$' -or
    $existing -notmatch '(?m)^tag\.id=android-automotive\r?$') { throw "Existing AVD is not an Android Automotive API 33 profile: $name" }

$settings = [ordered]@{
    'hw.lcd.width' = '1920'
    'hw.lcd.height' = '1200'
    'hw.lcd.density' = '160'
    'hw.initialOrientation' = 'landscape'
    'hw.gpu.enabled' = 'yes'
}
$lines = [System.Collections.Generic.List[string]]::new()
$lines.AddRange([string[]](Get-Content -LiteralPath $configPath))
foreach ($key in $settings.Keys) {
    $pattern = "^$([regex]::Escape($key))=.*\r?$"
    $index = -1
    for ($i = 0; $i -lt $lines.Count; $i++) {
        if ($lines[$i] -match $pattern) { $index = $i; break }
    }
    if ($index -ge 0) { $lines[$index] = "$key=$($settings[$key])" }
    else { $lines.Add("$key=$($settings[$key])") }
}
[System.IO.File]::WriteAllLines($configPath, $lines, [System.Text.UTF8Encoding]::new($false))

$configured = Get-Content -LiteralPath $configPath -Raw
foreach ($setting in @('hw.lcd.width=1920', 'hw.lcd.height=1200', 'hw.lcd.density=160', 'hw.initialOrientation=landscape', 'tag.id=android-automotive')) {
    if ($configured -notmatch "(?m)^$([regex]::Escape($setting))\r?$") { throw "AVD verification failed: $setting" }
}
Write-Output "Configured: $name (1920x1200, temporary 160 dpi, Android Automotive API 33)"
