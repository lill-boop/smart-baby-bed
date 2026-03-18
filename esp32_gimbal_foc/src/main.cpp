/**
 * ESP32-S3 Intercom - FINAL RESTORE
 * Based on main_full_backup.cpp.bak
 * ACTION: REMOVED MOTOR CODE ONLY. KEPT ALL AUDIO SETTINGS.
 */

#include "G711.h"
#include <Arduino.h>
// #include <SimpleFOC.h> // REMOVED
#include <U8g2lib.h>
#include <WiFi.h>
#include <WiFiUdp.h>
#include <driver/i2s.h>
#include <vector>

// ⚠️ DEBUG MODE
#define DEBUG_MODE 1

#if DEBUG_MODE
#define DEBUG_PRINT(x) Serial.print(x)
#define DEBUG_PRINTLN(x) Serial.println(x)
#define DEBUG_PRINTF(fmt, ...) Serial.printf(fmt, ##__VA_ARGS__)
#else
#define DEBUG_PRINT(x)
#define DEBUG_PRINTLN(x)
#define DEBUG_PRINTF(fmt, ...)
#endif

// WiFi credentials
const char *ssid = "ZTE_3B493B";
const char *password = "787970lzh!";

// Network
const char *relay_ip = "192.168.0.222";
const int relay_port_tx = 12347;
const int local_port_rx = 12346;
const int gimbal_port = 12349;

// I2S Pins - MAX98357A (Speaker)
#define I2S_SPK_BCLK 5
#define I2S_SPK_LRC 4
#define I2S_SPK_DOUT 6

// I2S Pins - INMP441 (Microphone)
#define I2S_MIC_SCK 42
#define I2S_MIC_WS 41
#define I2S_MIC_SD 2

// OLED Pins
#define OLED_SDA 8
#define OLED_SCL 9

// I2S Config
#define SAMPLE_RATE 16000
#define DMA_BUF_COUNT 16
#define DMA_BUF_LEN 256

// OLED Display - SH1106 for 1.3" screen
U8G2_SH1106_128X64_NONAME_F_HW_I2C u8g2(U8G2_R0, OLED_SCL, OLED_SDA);

// UDP for gimbal commands (Kept for compatibility, but logic removed)
WiFiUDP gimbalUdp;

// State
WiFiUDP udp;
std::vector<int16_t> pcm_buffer(640);
std::vector<uint8_t> g711_buffer(640);

// Stats
unsigned long total_packets = 0;
unsigned long last_packet_time = 0;

// OLED Display Helper (Optimized for 1.3" 128x64)
void updateOLED(const String &status) {
  u8g2.clearBuffer();
  u8g2.setFont(u8g2_font_5x7_tr); // Tiny font for 1.3"

  u8g2.drawStr(0, 10, "Intercom System");
  u8g2.drawHLine(0, 12, 128);

  String wifi = WiFi.localIP().toString();
  u8g2.drawStr(0, 24, "IP:");
  u8g2.drawStr(20, 24, wifi.c_str());

  u8g2.drawStr(0, 38, "Status:");
  u8g2.drawStr(0, 50, status.c_str());

  String packets = "Pkts: " + String(total_packets);
  u8g2.drawStr(0, 62, packets.c_str());

  u8g2.sendBuffer();
}

void setup() {
#if DEBUG_MODE
  Serial.begin(115200);
  delay(1000);
  Serial.println("\n╔═══════════════════════════════════════╗");
  Serial.println("║  ESP32-S3 Intercom PRODUCTION v2.0   ║");
  Serial.println("╚═══════════════════════════════════════╝");
#endif

  // Init OLED
  u8g2.begin();
  u8g2.enableUTF8Print();
  updateOLED("Booting...");

  // Connect WiFi
  WiFi.begin(ssid, password);

  int timeout = 0;
  while (WiFi.status() != WL_CONNECTED && timeout < 20) {
    delay(500);
    timeout++;
  }

  if (WiFi.status() == WL_CONNECTED) {
    DEBUG_PRINTF("IP: %s\n", WiFi.localIP().toString().c_str());
    updateOLED("WiFi OK");
  } else {
    updateOLED("WiFi Failed");
    while (1)
      delay(1000);
  }

  // Init I2S for speaker - XIAOZHI-INSPIRED CONFIG (EXACT RESTORE)
  i2s_config_t i2s_config = {.mode =
                                 (i2s_mode_t)(I2S_MODE_MASTER | I2S_MODE_TX),
                             .sample_rate = SAMPLE_RATE,
                             .bits_per_sample = I2S_BITS_PER_SAMPLE_32BIT,
                             .channel_format = I2S_CHANNEL_FMT_ONLY_LEFT,
                             .communication_format = I2S_COMM_FORMAT_STAND_MSB,
                             .intr_alloc_flags = ESP_INTR_FLAG_LEVEL1,
                             .dma_buf_count = DMA_BUF_COUNT, // 16
                             .dma_buf_len = DMA_BUF_LEN,     // 256
                             .use_apll = false,
                             .tx_desc_auto_clear = true};

  i2s_pin_config_t pin_config = {.mck_io_num = I2S_PIN_NO_CHANGE,
                                 .bck_io_num = I2S_SPK_BCLK,
                                 .ws_io_num = I2S_SPK_LRC,
                                 .data_out_num = I2S_SPK_DOUT,
                                 .data_in_num = I2S_PIN_NO_CHANGE};

  if (i2s_driver_install(I2S_NUM_0, &i2s_config, 0, NULL) != ESP_OK) {
    DEBUG_PRINTLN("I2S install failed!");
    while (1)
      delay(1000);
  }

  if (i2s_set_pin(I2S_NUM_0, &pin_config) != ESP_OK) {
    DEBUG_PRINTLN("I2S pin config failed!");
    while (1)
      delay(1000);
  }
  DEBUG_PRINTLN("I2S init OK");

  // Init I2S for MIC - INMP441 (Channel 1)
  i2s_config_t i2s_mic_config = {
      .mode = (i2s_mode_t)(I2S_MODE_MASTER | I2S_MODE_RX),
      .sample_rate = SAMPLE_RATE,
      .bits_per_sample = I2S_BITS_PER_SAMPLE_32BIT,
      .channel_format = I2S_CHANNEL_FMT_ONLY_LEFT,
      .communication_format = I2S_COMM_FORMAT_I2S, // INMP441 uses standard I2S
      .intr_alloc_flags = ESP_INTR_FLAG_LEVEL1,
      .dma_buf_count = 8, // Reduced from 16 to reduce latency
      .dma_buf_len = 256, // Reduced from 512 for smaller chunks
      .use_apll = false,
      .tx_desc_auto_clear = false,
      .fixed_mclk = 0};

  i2s_pin_config_t mic_pin_config = {.bck_io_num = I2S_MIC_SCK,
                                     .ws_io_num = I2S_MIC_WS,
                                     .data_out_num = I2S_PIN_NO_CHANGE,
                                     .data_in_num = I2S_MIC_SD};

  if (i2s_driver_install(I2S_NUM_1, &i2s_mic_config, 0, NULL) != ESP_OK) {
    DEBUG_PRINTLN("Mic I2S install failed!");
  }
  if (i2s_set_pin(I2S_NUM_1, &mic_pin_config) != ESP_OK) {
    DEBUG_PRINTLN("Mic pin config failed!");
  }

  // Start UDP
  if (!udp.begin(local_port_rx)) {
    DEBUG_PRINTLN("UDP bind failed!");
    while (1)
      delay(1000);
  }
  DEBUG_PRINTLN("UDP listening on port " + String(local_port_rx));

  updateOLED("Ready!");
}

void loop() {
  // ==========================================
  // 1. RECEIVE AUDIO (App -> ESP32)
  // ==========================================
  int packetSize = udp.parsePacket();
  if (packetSize > 0) {
    // Ignore small packets (e.g. 5-byte HELLO heartbeat)
    // Just flush and skip playback, but don't return! (Continue to mic capture)
    if (packetSize < 10) {
      udp.read((char *)pcm_buffer.data(), packetSize); // Flush
      // Don't return! Let the loop continue to mic capture section
    } else {

      // Resize buffer if needed
      int samples = packetSize / 2;
      if (samples > (int)pcm_buffer.size()) {
        pcm_buffer.resize(samples);
      }

      // Read UDP data
      udp.read((char *)pcm_buffer.data(), packetSize);

      // ===== XIAOZHI'S VOLUME ALGORITHM =====
      std::vector<int32_t> pcm_32bit(pcm_buffer.size());
      const int output_volume = 80; // 80% volume
      float volume_factor = pow(double(output_volume) / 100.0, 2.0) * 65536.0;

      for (size_t i = 0; i < pcm_buffer.size(); i++) {
        int64_t temp = int64_t(pcm_buffer[i]) * int64_t(volume_factor);
        if (temp > INT32_MAX)
          temp = INT32_MAX;
        else if (temp < INT32_MIN)
          temp = INT32_MIN;
        pcm_32bit[i] = (int32_t)temp;
      }

      // Play to Speaker (I2S0)
      size_t bytes_written = 0;
      i2s_write(I2S_NUM_0, pcm_32bit.data(), pcm_32bit.size() * sizeof(int32_t),
                &bytes_written, portMAX_DELAY);

      // Update State
      last_packet_time = millis();
      updateOLED("Playing Audio");
      total_packets++;
    }
  } // End of if (packetSize > 0)

  // OLED Status Update (always check, not just when packet received)
  if (millis() - last_packet_time > 5000 && last_packet_time > 0) {
    static unsigned long last_idle = 0;
    if (millis() - last_idle > 3000) {
      updateOLED("Waiting...");
      last_idle = millis();
    }
  }

  // ==========================================
  // 2. CAPTURE MIC (ESP32 -> App)
  // ==========================================
  // HALF-DUPLEX: Only capture when NOT playing audio
  // This PREVENTS feedback howling (speaker -> mic -> loop)

  unsigned long timeSinceLastRx = millis() - last_packet_time;
  bool shouldCaptureMic =
      (timeSinceLastRx > 500); // 500ms silence before capturing

  if (shouldCaptureMic) {
    static int32_t i2s_read_buff[320];
    size_t bytes_read = 0;

    esp_err_t result =
        i2s_read(I2S_NUM_1, (void *)i2s_read_buff, sizeof(i2s_read_buff),
                 &bytes_read, pdMS_TO_TICKS(20));

    if (result == ESP_OK && bytes_read > 0) {
      int samples = bytes_read / 4;
      static int16_t tx_pcm[320];

      // LOW GAIN to prevent feedback
      const int MIC_GAIN = 4;     // Reduced to 4x
      const int NOISE_GATE = 200; // Higher threshold

      for (int i = 0; i < samples; i++) {
        int32_t val = (i2s_read_buff[i] >> 14);

        if (abs(val) < NOISE_GATE) {
          val = 0;
        } else {
          val *= MIC_GAIN;
        }

        if (val > 30000)
          val = 30000;
        if (val < -30000)
          val = -30000;

        tx_pcm[i] = (int16_t)val;
      }

      if (udp.beginPacket(relay_ip, relay_port_tx)) {
        udp.write((uint8_t *)tx_pcm, samples * 2);
        udp.endPacket();
      }
    }
  }

  delay(1);
}
