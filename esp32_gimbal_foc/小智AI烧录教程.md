# 🤖 小智AI ESP32-S3 面包板版本 - 烧录教程
？
## 📋 硬件清单确认

| 组件 | 状态 | 备注 |
|------|------|------|
| ESP32-S3 N16R8 | ✅ | 16MB Flash + 8MB PSRAM |
| INMP441 麦克风 | ✅ | I2S数字麦克风 |
| MAX98357A 功放 | ✅ | I2S音频功放 |
| 1.3寸 OLED | ✅ | I2C接口 SSD1306 |
| 8Ω 喇叭 | ⚠️ | 2-3W，需确认 |

---

## 🔌 完整接线图

### MAX98357A 功放模块
| MAX98357A 引脚 | ESP32-S3 GPIO |
|----------------|---------------|
| VIN | 3.3V |
| GND | GND |
| LRC | GPIO 4 |
| BCLK | GPIO 5 |
| DIN | GPIO 6 |
| SD | 3.3V (或悬空) |
| GAIN | GND |

### INMP441 麦克风模块
| INMP441 引脚 | ESP32-S3 GPIO |
|--------------|---------------|
| VDD | 3.3V |
| GND | GND |
| WS | GPIO 10 |
| SCK | GPIO 11 |
| SD | GPIO 3 |
| L/R | GND |

### OLED 显示屏 (I2C)
| OLED 引脚 | ESP32-S3 GPIO |
|-----------|---------------|
| GND | GND |
| VCC/VDD | 3.3V |
| SCL/SCK | GPIO 9 |
| SDA | GPIO 8 |

---

## 🔥 固件烧录方法

### 方法一：在线一键烧录（推荐✅）

1. **打开在线烧录工具**
   - 访问：https://16302.com/xiaozhiinit
   - 使用 **Chrome** 或 **Edge** 浏览器

2. **连接开发板**
   - 用USB线连接ESP32-S3到电脑
   - 进入下载模式：**按住BOOT键**，然后**按一下RST键**，松开BOOT键

3. **选择固件**
   - 芯片型号：ESP32-S3
   - 固件版本：选择 **bread-compact-wifi** (面包板接线WiFi版)
   - 版本号：v2.1.0 或最新版

4. **开始烧录**
   - 点击"连接设备"
   - 选择对应的COM端口
   - 点击"开始烧录"
   - 等待烧录完成（约1-2分钟）

5. **重启设备**
   - 烧录完成后按RST键重启

---

### 方法二：使用ESP官方烧录工具

1. **下载烧录工具**
   - ESP Flash Download Tool：https://www.espressif.com/en/support/download/other-tools
   
2. **下载固件文件**
   - GitHub Releases：https://github.com/78/xiaozhi-esp32/releases
   - 找到 `bread-compact-wifi` 相关的 `.bin` 文件

3. **烧录设置**
   - 芯片类型：ESP32-S3
   - 工作模式：Develop
   - 加载模式：UART
   - 固件地址：0x0
   - SPI Speed：40MHz
   - SPI Mode：DIO

---

## 📱 首次配置

烧录完成后，小智会创建一个WiFi热点：

1. **连接热点**
   - 用手机搜索WiFi：`xiaozhi-xxxx`
   - 密码：`12345678`

2. **配置网络**
   - 连接后会自动弹出配置页面
   - 输入你家的WiFi名称和密码
   - 点击保存

3. **注册账号**
   - 访问：https://xiaozhi.me
   - 注册账号并绑定设备
   - 个人用户可免费使用通义千问模型

---

## 🎤 语音唤醒

配置完成后：
- 说 **"小智小智"** 唤醒设备
- 或者按住开发板上的 **BOOT键** 进行对话

---

## ❓ 常见问题

### Q: 烧录失败怎么办？
A: 确保进入下载模式：按住BOOT → 按RST → 松开BOOT

### Q: 没有声音输出？
A: 检查MAX98357的SD引脚是否接3.3V，喇叭是否正确连接

### Q: 麦克风不工作？
A: 检查INMP441的L/R引脚接地，VDD接3.3V不要接5V

### Q: OLED不显示？
A: 确认I2C地址，有些OLED是0x3C，有些是0x3D

---

## 📞 获取帮助

- 小智AI百科全书：https://ccnphfhqs21z.feishu.cn/wiki/F5krwD16viZoF0kKkvDcrZNYnhb
- 交流QQ群：632069471
- GitHub Issues：https://github.com/78/xiaozhi-esp32/issues
