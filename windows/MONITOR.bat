@echo off
cd /d "%~dp0"
title Lumen OS - monitor
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0tv-agent.ps1"
pause
