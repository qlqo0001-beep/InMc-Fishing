@echo off
title InMc-Fishing Full Git Reset
color 0A

cd /d "%~dp0"

echo ============================================
echo       InMc-Fishing Full Git Reset
echo ============================================
echo.
echo WARNING!
echo.
echo This will:
echo.
echo  - Delete local .git
echo  - Delete ALL remote branches except main
echo  - Create a new Git history
echo  - Force push current project
echo.
pause

echo.
echo [1/8] Removing local Git...

if exist ".git" (
    rmdir /s /q .git
)

echo.
echo [2/8] Initializing...

git init
git branch -M main

git config user.name "qlqo0001-beep"
git config user.email "qlqo0001@gmail.com"

echo.
echo [3/8] Adding remote...

git remote add origin https://github.com/qlqo0001-beep/InMc-Fishing.git

echo.
echo [4/8] Fetching branches...

git fetch origin

echo.
echo [5/8] Deleting remote branches...

for /f "tokens=1" %%b in ('git branch -r ^| findstr /v "HEAD"') do (
    set BRANCH=%%b
)

for /f "tokens=2 delims=/" %%b in ('git branch -r ^| findstr "origin/"') do (
    if /I not "%%b"=="main" (
        echo Deleting %%b
        git push origin --delete %%b
    )
)

echo.
echo [6/8] Adding files...

git add .

echo.
echo [7/8] Commit...

set /p msg=Commit message :

if "%msg%"=="" (
    set msg=Initial Commit
)

git commit -m "%msg%"

echo.
echo [8/8] Force Push...

git push -u origin main --force

echo.
echo ============================================
echo Completed!
echo ============================================
pause