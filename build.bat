@echo off
set TARGET=%1
if "%TARGET%"=="" (
    set TARGET=--win
)

cd ui
call pnpm install
call pnpm exec vite build
call pnpm exec electron-builder %TARGET%
cd ..

call mvn clean package

