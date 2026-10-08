Set-Location 'E:\sonify'
& .\gradlew.bat :app:testDebugUnitTest --console=plain *>&1 | Select-Object -Last 6
$code = $LASTEXITCODE
Write-Output "TESTS_EXIT=$code"
exit $code
