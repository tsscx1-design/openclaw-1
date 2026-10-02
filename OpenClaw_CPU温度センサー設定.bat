@echo off
rem OpenClaw: CPU temperature sensor (LibreHardwareMonitor). Right-click > Run as administrator, once.
net session >nul 2>&1 || (echo Please right-click this file and choose "Run as administrator". & pause & exit /b 1)
schtasks /Create /TN "OpenClaw LHM" /TR "\"C:\Users\sd190901\.openclaw\tools\lhm\LibreHardwareMonitor.exe\"" /SC ONLOGON /RL HIGHEST /F
schtasks /Run /TN "OpenClaw LHM"
echo Done. CPU temperature will appear in the OpenClaw app within a minute.
pause
