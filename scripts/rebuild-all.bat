@echo off
REM cmd.exe wrapper for rebuild-all.ps1 (running a .ps1 directly in cmd just
REM opens Notepad, so hand it to PowerShell explicitly).
REM
REM   scripts\rebuild-all.bat            rebuild everything, keep the database
REM   scripts\rebuild-all.bat -Fresh     ALSO wipe the DB volume (fresh schema + reseed)
REM
REM Use -Fresh after adding/changing migrations or when a service won't start
REM because of database schema drift.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0rebuild-all.ps1" %*
