@echo off
REM cmd.exe wrapper for redeploy.ps1 — in cmd, running a .ps1 directly just
REM opens it in Notepad, so hand it to PowerShell explicitly.
REM
REM   scripts\redeploy.bat queue-service
REM   scripts\redeploy.bat queue-service,identity-service -Frontend
REM   scripts\redeploy.bat -All
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0redeploy.ps1" %*
