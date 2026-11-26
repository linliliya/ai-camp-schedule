@echo off
powershell.exe -NoProfile -File "%~dp0start.ps1" -Open
if errorlevel 1 pause
