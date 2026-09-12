@echo off
cd /d E:\java_demo\Ai-Backend
set SPRING_APPLICATION_JSON=%~1
D:\Maven\apache-maven-3.9.11\bin\mvn.cmd -Dmaven.test.skip=true spring-boot:run -Dspring-boot.run.arguments=--spring.devtools.restart.enabled=false
