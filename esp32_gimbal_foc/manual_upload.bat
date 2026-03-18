@echo off
echo ========================================
echo ESP32-S3 Manual Upload Script
echo ========================================
echo.
echo 请按照以下步骤操作：
echo 1. 按住 BOOT 按钮
echo 2. 按一下 RST 按钮
echo 3. 松开 BOOT 按钮
echo 4. 按任意键继续烧录...
pause

echo.
echo 正在烧录到 COM9...
python C:\Users\54257\.platformio\packages\tool-esptoolpy\esptool.py --chip esp32s3 --port COM9 --baud 460800 write_flash -z --flash_mode dio --flash_freq 80m --flash_size 8MB 0x0 .pio\build\esp32-s3\bootloader.bin 0x8000 .pio\build\esp32-s3\partitions.bin 0xe000 C:\Users\54257\.platformio\packages\framework-arduinoespressif32\tools\partitions\boot_app0.bin 0x10000 .pio\build\esp32-s3\firmware.bin

echo.
echo ========================================
echo 烧录完成！
echo ========================================
pause
