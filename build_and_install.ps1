$ErrorActionPreference = 'Continue'
Set-Location 'E:\sonify'
& .\gradlew.bat :app:assembleDebug --console=plain *>&1 | Tee-Object -FilePath E:\sonify\build_run.log
$code = $LASTEXITCODE
Write-Output "GRADLE_EXIT=$code"
exit $code
