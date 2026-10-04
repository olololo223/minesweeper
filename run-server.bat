@echo off
chcp 65001 >nul
cd /d "%~dp0"

if exist "server-cert.pem" if exist "server-key.pem" (
    echo Starting server with TLS...
    java "-Dfile.encoding=UTF-8" "-Dstdout.encoding=UTF-8" ^
         "-Dtls.cert=%~dp0server-cert.pem" ^
         "-Dtls.key=%~dp0server-key.pem" ^
         -jar server\target\minesweeper-server-1.0-SNAPSHOT.jar
) else (
    echo Starting server without TLS...
    java "-Dfile.encoding=UTF-8" "-Dstdout.encoding=UTF-8" ^
         -jar server\target\minesweeper-server-1.0-SNAPSHOT.jar
)