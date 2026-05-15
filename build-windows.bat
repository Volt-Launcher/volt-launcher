@echo off
setlocal enabledelayedexpansion

set VERSION=0.1.0
set APP_NAME=VoltLauncher
set BINARY_NAME=volt-launcher

REM Use GraalVM JDK if GRAALVM_HOME is set
if defined GRAALVM_HOME (
    set "JAVA_HOME=%GRAALVM_HOME%"
    set "PATH=%GRAALVM_HOME%\bin;%PATH%"
    echo Using GraalVM at: %GRAALVM_HOME%
    java -version 2>&1 | findstr /n "." | findstr "^1:"
)

echo === Building Volt Launcher for Windows ===

REM Step 1: Build Electron UI
echo [1/3] Building Electron UI...
cd ui
call pnpm install
call pnpm exec vite build
call pnpm exec electron-builder --win --dir
cd ..

REM Step 2: Package electron binary as tar.gz
echo [2/3] Packaging Electron binary...
del /q src\main\resources\electron-bin\*.tar.gz 2>nul

if exist "src\main\resources\electron-bin\win-unpacked" (
    cd src\main\resources\electron-bin
    tar -czf win-unpacked.tar.gz win-unpacked
    rmdir /s /q win-unpacked
    cd ..\..\..\..
) else (
    echo ERROR: win-unpacked directory not found!
    exit /b 1
)

REM Remove other platform artifacts
if exist "src\main\resources\electron-bin\mac" rmdir /s /q "src\main\resources\electron-bin\mac"
if exist "src\main\resources\electron-bin\mac-arm64" rmdir /s /q "src\main\resources\electron-bin\mac-arm64"
if exist "src\main\resources\electron-bin\linux-unpacked" rmdir /s /q "src\main\resources\electron-bin\linux-unpacked"
del /q "src\main\resources\electron-bin\builder-debug.yml" 2>nul
del /q "src\main\resources\electron-bin\builder-effective-config.yaml" 2>nul

REM Step 3: Build GraalVM native image
echo [3/3] Building GraalVM native image...
call mvn clean package -Pnative -DskipTests

if not exist "target\%BINARY_NAME%.exe" (
    echo ERROR: Native image build failed - target\%BINARY_NAME%.exe not found!
    exit /b 1
)

echo.
echo === Build complete ===
echo   Native binary: target\%BINARY_NAME%.exe

endlocal
