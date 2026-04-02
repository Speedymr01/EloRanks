@echo off
set JAVA_HOME=C:\Program Files\Java\jdk-21
set PATH=%JAVA_HOME%\bin;%PATH%
cd /d C:\Users\Matthew\Desktop\mc-plugins\EloRanks
call mvn clean package
pause