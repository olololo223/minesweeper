@echo off
chcp 65001 >nul
cd /d "%~dp0"
call mvn clean package
if errorlevel 1 (
    echo BUILD FAILED
    pause
    exit /b 1
)
echo BUILD SUCCESS
dir /b server\target\minesweeper-server-*.jar
dir /b client\target\minesweeper-client-*.jar
dir /b admin\target\minesweeper-admin-*.jar
pause