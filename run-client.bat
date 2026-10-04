@echo off
chcp 65001 >nul
cd /d "%~dp0client"

if not exist "server-cert.pem" (
    if exist "..\server-cert.pem" (
        copy "..\server-cert.pem" . >nul
        echo Certificate copied to client folder.
    )
)

java "-Dfile.encoding=UTF-8" "-Dstdout.encoding=UTF-8" ^
     -jar target\minesweeper-client-1.0-SNAPSHOT.jar