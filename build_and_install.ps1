$ErrorActionPreference = 'Continue'
Set-Location $PSScriptRoot
& .\gradlew.bat :app:assembleDebug --console=plain *>&1 | Tee-Object -FilePath (Join-Path $PSScriptRoot "build_run.log")
$code = $LASTEXITCODE
Write-Output "GRADLE_EXIT=$code"
exit $code
