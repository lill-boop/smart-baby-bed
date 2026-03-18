@echo off
chcp 65001 >nul
title ESP32-S3 一键烧录工具

echo.
echo ========================================
echo   ESP32-S3 一键烧录工具 (COM9)
echo ========================================
echo.
echo 操作步骤：
echo   1. 按住 BOOT 按钮
echo   2. 按一下 RST 按钮
echo   3. 松开 BOOT 按钮
echo   4. 等待烧录完成
echo.
echo ----------------------------------------
echo.

pause

echo 正在烧录...
echo.

C:\Users\54257\.platformio\penv\Scripts\platformio.exe run -t upload

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ========================================
    echo   烧录成功！✅
    echo ========================================
    echo.
    echo 按任意键打开串口监视器...
    pause >nul
    C:\Users\54257\.platformio\penv\Scripts\platformio.exe device monitor -p COM9 -b 115200
) else (
    echo.
    echo ========================================
    echo   烧录失败 ❌
    echo ========================================
    echo.
    echo 请检查：
    echo   - 是否正确按了 BOOT+RST
    echo   - 是否有其他程序占用 COM9
    echo   - USB 线是否插好
    echo.
    pause
)
