@echo off
setlocal
set "MODULE=%~1"
if "%MODULE%"=="" set "MODULE=wms-core-service"
echo Starting %MODULE% ...
mvn spring-boot:run -pl %MODULE% %*
