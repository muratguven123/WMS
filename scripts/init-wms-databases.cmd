@echo off
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0init-wms-databases.ps1" %*
