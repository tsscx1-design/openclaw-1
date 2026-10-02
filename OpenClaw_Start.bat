@echo off
title OpenClaw Gateway Launcher
cd /d "%~dp0"

echo ====================================================
echo            OpenClaw Gateway Launcher
echo ====================================================
echo.

REM Check if Gateway is already running on port 18789
netstat -ano | findstr ":18789" | findstr "LISTENING" > nul 2>&1
if %errorlevel% equ 0 goto already_running

REM Gateway is not running, start it
echo [STATUS] OpenClaw Gateway is not running.
REM Prefer the managed scheduled task: a second, unmanaged Gateway collides on the state-dir lock.
schtasks /Query /TN "OpenClaw Gateway" > nul 2>&1
if %errorlevel% equ 0 (
  echo [LAUNCH] Starting the "OpenClaw Gateway" scheduled task...
  schtasks /Run /TN "OpenClaw Gateway" > nul
) else (
  echo [LAUNCH] Starting OpenClaw Gateway server in a new window...
  start "OpenClaw Gateway Server" cmd /k "node openclaw.mjs gateway run"
)

echo [WAIT] Waiting for Gateway to initialize on port 18789...
set /a count=0

:waitloop
ping 127.0.0.1 -n 2 > nul
set /a count+=1

netstat -ano | findstr ":18789" | findstr "LISTENING" > nul 2>&1
if %errorlevel% equ 0 goto gateway_ready

if %count% geq 30 goto gateway_timeout

if %count% equ 5 echo   ... still waiting (%count%/30s)
if %count% equ 10 echo   ... still waiting (%count%/30s)
if %count% equ 15 echo   ... still waiting (%count%/30s)
if %count% equ 20 echo   ... still waiting (%count%/30s)
if %count% equ 25 echo   ... still waiting (%count%/30s)
goto waitloop

:already_running
echo [STATUS] OpenClaw Gateway is ALREADY RUNNING on port 18789.
echo [BROWSER] Opening Control UI dashboard with authentication token...
node openclaw.mjs dashboard
echo.
echo ====================================================
echo  Dashboard opened in your default web browser!
echo  Gateway is active and ready.
echo ====================================================
echo.
echo Press any key to exit this launcher window...
pause > nul
exit /b 0

:gateway_ready
echo [STATUS] OpenClaw Gateway is successfully UP on port 18789! (%count%s)
echo [BROWSER] Opening Control UI dashboard with authentication token...
node openclaw.mjs dashboard
echo.
echo ====================================================
echo  OpenClaw Gateway started and dashboard opened!
echo ====================================================
echo.
echo Press any key to exit this launcher window...
pause > nul
exit /b 0

:gateway_timeout
echo.
echo ====================================================
echo [WARNING] Gateway startup is taking longer than 30s.
echo Please check the "OpenClaw Gateway Server" window
echo for any errors or prompts.
echo ====================================================
echo.
echo Opening browser anyway...
start "" "http://127.0.0.1:18789"
echo.
echo Press any key to exit this launcher window...
pause > nul
exit /b 1
