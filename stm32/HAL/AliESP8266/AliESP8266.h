#ifndef _ESP8266_H_
#define _ESP8266_H_

#include "main.h"
#include <stdarg.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>

// WiFi Credentials
#define SSID "ZTE_3B493B"
#define PASS "787970lzh!"

// Diagnostic Switches
#define DO_RESTORE 0    // 0=skip AT+RESTORE, 1=do factory reset
#define MQTT_MIN_TEST 1 // 1=hardcode IP/port, 0=use macros

// EMQX MQTT Broker Parameters
#define ProductKey "N/A"
#define DeviceName "esp8266_01"
#define ClientId "esp8266_01"
#define Password "787970"
#define mqttHostUrl "192.168.0.222"
#define port "1883"

// Topic Definitions
#define TOPIC_CMD "device/esp8266_01/cmd"
#define TOPIC_STATUS "device/esp8266_01/status"
#define TOPIC_BABYCAM_TRIGGER "babycam/trigger" // SOC video export trigger

#define Huart_wifi huart2

#define REV_OK 0
#define REV_WAIT 1

#define DelayXms(x) HAL_Delay(x)

extern unsigned char ESP8266_buf[1024];
extern unsigned short ESP8266_cnt;
extern uint8_t uartwifi_value;

typedef struct {
  uint16_t year;
  uint8_t month;
  uint8_t day;
  uint8_t week;
  uint8_t hour;
  uint8_t minute;
  uint8_t second;
} Time_Get;

void ESP8266_init(void);
void Ali_MQTT_Publish_1(void);
void Ali_MQTT_Publish_2(void);
void Ali_MQTT_Recevie(void);
_Bool ESP8266_Status(void);
Time_Get ESP8266_Get_Time(void);
_Bool ESP8266_SendCmd(char *cmd, char *res);
void ESP8266_Clear(void);
_Bool ESP8266_WaitRecive(void);

// Cry event trigger to SOC (babycam video export)
void Ali_MQTT_Trigger_CryEvent(void);

#endif
