@echo off
chcp 65001 >nul
cd /d "%~dp0"

echo === Building client JAR ===
call mvn clean package -pl client -am
if errorlevel 1 exit /b 1

echo === Preparing input ===
if exist packaging\input rmdir /s /q packaging\input
mkdir packaging\input
copy client\target\minesweeper-client-1.0-SNAPSHOT.jar packaging\input\

echo === Running jpackage ===
if exist packaging\output rmdir /s /q packaging\output
mkdir packaging\output

jpackage ^
  --type exe ^
  --name "Minesweeper" ^
  --app-version "1.0.0" ^
  --input packaging\input ^
  --main-jar minesweeper-client-1.0-SNAPSHOT.jar ^
  --main-class com.example.minesweeper.ui.Minesweeper ^
  --dest packaging\output ^
  --icon packaging\icon.ico ^
  --win-menu ^
  --win-shortcut ^
  --win-dir-chooser ^
  --win-per-user-install ^
  --runtime-image my-own-runtime

echo === Done: packaging\output ===
pause