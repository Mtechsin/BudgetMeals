@echo off
echo ====================================================
echo        Android Wireless Debugging Pair ^& Connect
echo ====================================================
echo.
echo Step 1: On your phone, go to:
echo   Settings -^> Developer options -^> Wireless debugging
echo   Tap "Pair device with pairing code"
echo.
set /p PAIR_ADDR="Enter Pairing IP:PORT (e.g. 192.168.1.50:37123): "
set /p PAIR_CODE="Enter 6-digit Pairing Code: "
echo.
echo [1/2] Pairing with %PAIR_ADDR%...
adb pair %PAIR_ADDR% %PAIR_CODE%
echo.
echo Step 2: Now look at the main "Wireless debugging" screen for the connection IP:PORT.
set /p CONN_ADDR="Enter Connection IP:PORT (e.g. 192.168.1.50:41235): "
echo.
echo [2/2] Connecting to %CONN_ADDR%...
adb connect %CONN_ADDR%
echo.
echo Connected devices:
adb devices
echo.
pause
