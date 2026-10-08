@echo off
call gradlew.bat :app:assembleDebug --console=plain
exit /b %ERRORLEVEL%
