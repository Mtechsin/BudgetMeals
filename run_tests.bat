@echo off
call gradlew.bat :app:testDebugUnitTest --console=plain
exit /b %ERRORLEVEL%
