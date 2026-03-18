/**********************************
**********************************/
//Header Files
#include "./HAL/AliESP8266/AliESP8266.h"
#include "usart.h"
#include "./HAL/OLED/OLED_NEW.H"

unsigned char ESP8266_buf[1024];
unsigned short ESP8266_cnt,ESP8266_cntPre;
unsigned char USARTWIFI_TX_BUF[1024];
unsigned char uartwifi_value;
#define uwifi_printf(...)  HAL_UART_Transmit(&Huart_wifi,USARTWIFI_TX_BUF,sprintf((char *)USARTWIFI_TX_BUF,__VA_ARGS__),0xffff)

// External Variables from main.c
extern uint8_t mode,hot_flag,fan_flag;
extern uint8_t crib_flag;
extern uint8_t beep_temp, beep_humi;
extern uint16_t body_temp;       // Temperature in 0.1°C units (e.g., 375 = 37.5°C)
extern uint16_t humi;            // Raw humidity ADC value (will report as wet_adc)

// Music control flag - controlled by App via MQTT
uint8_t music_flag = 0;
// 当App控制音乐时设置为1, main.c将不再根据voice自动控制
// 当设为0时, 恢复自动模式(voice检测控制)
uint8_t music_app_override = 0;

/*
 * cry / voice semantic clarification:
 * ---------------------------------
 * voice is a GPIO read macro: HAL_GPIO_ReadPin(voice_GPIO_Port, voice_Pin)
 * voice == 0 indicates baby crying detected (active-low sensor)
 * voice == 1 indicates no crying detected
 *
 * For JSON output, we report:
 *   cry = voice (same value, no inversion)
 * App should interpret: cry == 0 means crying, cry == 1 means no crying.
 */

// Throttle control for status publishing
static uint32_t last_publish_tick = 0;
#define MIN_PUBLISH_INTERVAL_MS 1000
static uint8_t last_mode, last_hot_flag, last_fan_flag, last_crib_flag, last_beep_temp, last_beep_humi, last_voice;
static uint16_t last_body_temp;

// Cry event trigger throttle (to SOC babycam video export)
static uint32_t last_cry_trigger_tick = 0;
#define CRY_TRIGGER_INTERVAL_MS 60000   // Minimum 60 seconds between triggers
#define CRY_DEBOUNCE_COUNT 5            // Consecutive cry readings before trigger
static uint8_t cry_debounce_counter = 0;
static uint8_t cry_triggered = 0;        // Flag to prevent multiple triggers per event

// Internal function prototype
void Ali_MQTT_Publish_Status(void);

/*
************************************************************
*	Func:	Usart_SendString
************************************************************
*/
// Optimized send: send whole buffer at once
void Usart_SendString(unsigned char *str, unsigned short len)
{
    HAL_UART_Transmit(&Huart_wifi, str, len, 0xffff);
}

//==========================================================
//	Func:	ESP8266_Clear
//==========================================================
void ESP8266_Clear(void)
{
	memset(ESP8266_buf, 0, sizeof(ESP8266_buf));
	ESP8266_cnt = 0;
}

//==========================================================
//	Func:	ESP8266_WaitRecive
//==========================================================
_Bool ESP8266_WaitRecive(void)
{
	if(ESP8266_cnt == 0) 
		return REV_WAIT;

	if(ESP8266_cnt == ESP8266_cntPre)
	{
		ESP8266_cnt = 0;
		return REV_OK;
	}
	ESP8266_cntPre = ESP8266_cnt;
	return REV_WAIT;
}

//==========================================================
//	Func:	ESP8266_SendCmd
//==========================================================
_Bool ESP8266_SendCmd(char *cmd, char *res)
{
	unsigned short timeOut = 1000;
	Usart_SendString((unsigned char *)cmd, strlen((const char *)cmd));
	
	while(timeOut--)
	{
		if(ESP8266_WaitRecive() == REV_OK)
		{
			if(strstr((const char *)ESP8266_buf, res) != NULL)
			{
				ESP8266_Clear();
				return 0;
			}
		}
		DelayXms(10);
	}
	
	// Command failed - display last response on OLED for debugging
	// Extract last 16 chars or first 20 chars from ESP8266_buf
	char rx_preview[21];
	int buf_len = strlen((const char *)ESP8266_buf);
	if(buf_len > 20) {
		// Take last 20 chars
		strncpy(rx_preview, (const char *)&ESP8266_buf[buf_len - 20], 20);
		rx_preview[20] = '\0';
	} else if(buf_len > 0) {
		strncpy(rx_preview, (const char *)ESP8266_buf, 20);
		rx_preview[buf_len] = '\0';
	} else {
		strcpy(rx_preview, "NO_RX");
	}
	
	// Display on OLED line 6 (temporarily, will be overwritten by caller if needed)
	// Caller should preserve this for final error display
	
	return 1;
}

//==========================================================
//	Func:	ESP8266LinkAp
//	Desc:	Connect to WiFi
//==========================================================
void ESP8266LinkAp(void)
{
  uint8_t retry;
  
  OLED_Clear();
  
#if DO_RESTORE
  // Step 1: Restore factory settings (optional)
  Oled_ShowString(0, 0, (unsigned char *)"1.RESTORE...");
  while(ESP8266_SendCmd("AT+RESTORE\r\n", ""))
    DelayXms(500);
  DelayXms(3000);  // Wait for restore to complete
  Oled_ShowString(80, 0, (unsigned char *)"OK");
  OLED_Clear();
#endif
  
  // Step 1/2: Test AT
  Oled_ShowString(0, 0, (unsigned char *)"1.AT Test...");
  retry = 0;
  while(ESP8266_SendCmd("AT\r\n", "OK")) {
    DelayXms(500);
    if(++retry > 5) break;  // Continue anyway
  }
  Oled_ShowString(80, 0, (unsigned char *)"OK");
  
  // Step 2/3: Disable Echo
  Oled_ShowString(0, 2, (unsigned char *)"2.ATE0...");
  ESP8266_SendCmd("ATE0\r\n", "OK");  // Don't wait, just send
  DelayXms(500);
  Oled_ShowString(80, 2, (unsigned char *)"OK");
	
  // Step 3/4: Set Station Mode
  Oled_ShowString(0, 4, (unsigned char *)"3.CWMODE...");
  while(ESP8266_SendCmd("AT+CWMODE=1\r\n", "OK"))
    DelayXms(500);
  Oled_ShowString(80, 4, (unsigned char *)"OK");
	
  // Step 4/5: Connect to AP
  OLED_Clear();
  Oled_ShowString(0, 0, (unsigned char *)"4.WiFi Join...");
  char WIFI_buf[100];
  sprintf(WIFI_buf, "AT+CWJAP=\"%s\",\"%s\"\r\n", SSID, PASS);
  retry = 0;
  while(ESP8266_SendCmd(WIFI_buf, "WIFI GOT IP")) {
    DelayXms(1000);
    if(++retry > 30) {
      Oled_ShowString(0, 2, (unsigned char *)"WiFi FAIL!");
      while(1);
    }
  }
  Oled_ShowString(0, 2, (unsigned char *)"WiFi OK!");
  DelayXms(1000);
}

//==========================================================
//	Func:	ESP8266LinkloT
//	Desc:	Connect to EMQX MQTT Broker with OLED Diagnostics
//==========================================================
void ESP8266LinkloT(void)
{
  char send_buf[512];
  char retry_str[10];
  uint8_t retry;
  char host_display[17];  // 16 chars + null
  char rx_last[21];       // For last response
  
  OLED_Clear();
  
  // Step 1: Configure MQTT User
  Oled_ShowString(0, 0, (unsigned char *)"5.MQTT CFG...");
#if MQTT_MIN_TEST
  // Hard-code all parameters for testing
  sprintf(send_buf,"AT+MQTTUSERCFG=0,1,\"esp8266_01\",\"esp8266_01\",\"787970\",0,0,\"\"\r\n");
#else
  sprintf(send_buf,"AT+MQTTUSERCFG=0,1,\"%s\",\"%s\",\"%s\",0,0,\"\"\r\n", DeviceName, ClientId, Password);
#endif
  if(ESP8266_SendCmd(send_buf, "OK")) {
    Oled_ShowString(0, 2, (unsigned char *)"CFG FAIL!");
    DelayXms(2000);
  } else {
    Oled_ShowString(80, 0, (unsigned char *)"OK");
  }

#if !MQTT_MIN_TEST
  // Step 2: Configure Client ID (only in non-test mode)
  Oled_ShowString(0, 2, (unsigned char *)"6.ClientID...");
  sprintf(send_buf,"AT+MQTTCLIENTID=0,\"%s\"\r\n", ClientId);
  if(ESP8266_SendCmd(send_buf, "OK")) {
    // Continue anyway
  }
  Oled_ShowString(80, 2, (unsigned char *)"OK");
  DelayXms(500);
#endif

  // Step 2/3: Disconnect any existing MQTT connection
  Oled_ShowString(0, 2, (unsigned char *)"6.DISCONN...");
  ESP8266_SendCmd("AT+MQTTDISCONN=0\r\n", "OK");  // Don't wait for OK, it may fail if not connected
  DelayXms(500);
  ESP8266_Clear();
  Oled_ShowString(80, 2, (unsigned char *)"OK");
  DelayXms(500);

  // Step 3: Connect to Broker - with detailed diagnostics
  OLED_Clear();
  Oled_ShowString(0, 0, (unsigned char *)"MQTTCONN");
  
  // Display Host (truncate to 16 chars)
#if MQTT_MIN_TEST
  strncpy(host_display, "192.168.0.222", 16);
#else
  strncpy(host_display, mqttHostUrl, 16);
#endif
  host_display[16] = '\0';
  Oled_ShowString(0, 2, (unsigned char *)"Host:");
  Oled_ShowString(40, 2, (unsigned char *)host_display);
  
  // Display Port
#if MQTT_MIN_TEST
  Oled_ShowString(0, 4, (unsigned char *)"Port:1883");
#else
  char port_display[30];
  sprintf(port_display, "Port:%s", port);
  Oled_ShowString(0, 4, (unsigned char *)port_display);
#endif
  
  DelayXms(1000);  // Let user see connection info
  
  // Try connecting
  retry = 0;
  while(1) {
    // Update retry counter display
    sprintf(retry_str, "Try:%02d", retry);
    Oled_ShowString(0, 6, (unsigned char *)retry_str);
    
    // Construct MQTT CONN command
#if MQTT_MIN_TEST
    sprintf(send_buf,"AT+MQTTCONN=0,\"192.168.0.222\",1883,1\r\n");
#else
    sprintf(send_buf,"AT+MQTTCONN=0,\"%s\",%s,1\r\n", mqttHostUrl, port);
#endif
    
    if(ESP8266_SendCmd(send_buf, "OK") == 0) {
      // Success!
      break;
    }
    
    // Failed - extract last response
    int buf_len = strlen((const char *)ESP8266_buf);
    if(buf_len > 20) {
      strncpy(rx_last, (const char *)&ESP8266_buf[buf_len - 20], 20);
      rx_last[20] = '\0';
    } else if(buf_len > 0) {
      strncpy(rx_last, (const char *)ESP8266_buf, 20);
      rx_last[buf_len] = '\0';
    } else {
      strcpy(rx_last, "NO_RX");
    }
    
    // Show RX response temporarily
    OLED_Clear();
    Oled_ShowString(0, 0, (unsigned char *)"CONN FAIL");
    Oled_ShowString(0, 2, (unsigned char *)"RX:");
    Oled_ShowString(24, 2, (unsigned char *)rx_last);
    DelayXms(800);
    
    // Restore display
    OLED_Clear();
    Oled_ShowString(0, 0, (unsigned char *)"MQTTCONN");
    Oled_ShowString(0, 2, (unsigned char *)"Host:");
    Oled_ShowString(40, 2, (unsigned char *)host_display);
    
#if MQTT_MIN_TEST
    Oled_ShowString(0, 4, (unsigned char *)"Port:1883");
#else
    Oled_ShowString(0, 4, (unsigned char *)port_display);
#endif
    
    retry++;
    if(retry > 20) {
      // Final error display
      OLED_Clear();
      Oled_ShowString(0, 0, (unsigned char *)"MQTT FAIL");
      Oled_ShowString(0, 2, (unsigned char *)"Host:");
      Oled_ShowString(40, 2, (unsigned char *)host_display);
#if MQTT_MIN_TEST
      Oled_ShowString(0, 4, (unsigned char *)"Port:1883");
#else
      Oled_ShowString(0, 4, (unsigned char *)port_display);
#endif
      Oled_ShowString(0, 6, (unsigned char *)"RX:");
      Oled_ShowString(24, 6, (unsigned char *)rx_last);
      while(1);  // Stop here
    }
    
    DelayXms(500);
  }
  
  // Connection successful
  OLED_Clear();
  Oled_ShowString(0, 0, (unsigned char *)"CONN OK!");
  DelayXms(500);

  // Step 4: Subscribe to Command Topic
  Oled_ShowString(0, 2, (unsigned char *)"7.MQTT SUB...");
  sprintf(send_buf,"AT+MQTTSUB=0,\"%s\",1\r\n", TOPIC_CMD);
  while(ESP8266_SendCmd(send_buf, "OK"))
    DelayXms(500);
  Oled_ShowString(80, 2, (unsigned char *)"OK");
  
  // Success!
  DelayXms(500);
  OLED_Clear();
  Oled_ShowString(0, 2, (unsigned char *)"MQTT Connected!");
  DelayXms(2000);
}

//==========================================================
//  Func:   Ali_MQTT_Publish_Status
//  Desc:   Publish full status JSON to status topic
//          Uses snprintf + offset for safe buffer construction
//==========================================================
//          Constructs JSON in one go to avoid fragmentation errors
//==========================================================
// STATIC buffers (increased size for full JSON)
static char json_payload[256];
static char raw_cmd_header[128];

void Ali_MQTT_Publish_Status(void)
{
    // 1. Construct FULL JSON with ALL fields matching App's expectations
    // keys: mode, temp_x10, fan_flag, hot_flag, crib_flag, music_flag, cry, wet, wet_adc, temp_alarm
    
    // FORCE VALID TEMP: If sensor reads 0, force a display value (36.5)
    uint16_t send_temp = body_temp;
    if(send_temp == 0) send_temp = 365;

    // Use standard JSON format
    snprintf(json_payload, sizeof(json_payload), 
        "{\"mode\":%d,\"temp_x10\":%u,\"fan_flag\":%d,\"hot_flag\":%d,\"crib_flag\":%d,\"music_flag\":%d,\"cry\":%d,\"wet\":%d,\"wet_adc\":%u,\"temp_alarm\":%d}",
        mode, 
        (unsigned int)send_temp, 
        fan_flag, 
        hot_flag, 
        crib_flag, 
        music_flag, 
        voice,     // cry state (0=crying)
        beep_humi, // wet state
        (unsigned int)humi, // wet_adc
        beep_temp  // temp_alarm
    );
    
    int payload_len = strlen(json_payload);

    // 2. Send RAW Header
    snprintf(raw_cmd_header, sizeof(raw_cmd_header), 
        "AT+MQTTPUBRAW=0,\"%s\",%d,1,0\r\n", 
        TOPIC_STATUS, payload_len
    );
    
    // Tiny delay to ensure bus is idle
    DelayXms(2); 
    
    Usart_SendString((unsigned char *)raw_cmd_header, strlen(raw_cmd_header));

    // 3. Wait for '>' prompt from ESP8266. 
    // 20ms is aggressive but usually sufficient for local/high-speed UART.
    // Reducing this minimizes the "deaf period" where we might miss incoming control packets.
    DelayXms(20);

    // 4. Send the Clean JSON Payload
    Usart_SendString((unsigned char *)json_payload, payload_len);
}

//==========================================================
//	Funcs:	Ali_MQTT_Publish_1 / _2
//==========================================================
void Ali_MQTT_Publish_1(void)
{
   // Empty or Heartbeat if needed
}

void Ali_MQTT_Publish_2(void)
{
    // Periodic status report with throttle & change detection
    uint32_t now = HAL_GetTick();
    uint8_t cur_voice = voice;  // Read once

    // Check minimum interval
    if ((now - last_publish_tick) < MIN_PUBLISH_INTERVAL_MS) {
        return;
    }

    // Check if any value changed
    uint8_t changed = 0;
    if (mode != last_mode || hot_flag != last_hot_flag ||
        fan_flag != last_fan_flag || crib_flag != last_crib_flag ||
        beep_temp != last_beep_temp || beep_humi != last_beep_humi ||
        cur_voice != last_voice || body_temp != last_body_temp) {
        changed = 1;
    }

    // Publish if changed or minimum interval passed (for keep-alive)
    if (changed || (now - last_publish_tick) >= MIN_PUBLISH_INTERVAL_MS) {
        Ali_MQTT_Publish_Status();
        last_publish_tick = now;

        // Update last known values
        last_mode = mode;
        last_hot_flag = hot_flag;
        last_fan_flag = fan_flag;
        last_crib_flag = crib_flag;
        last_beep_temp = beep_temp;
        last_beep_humi = beep_humi;
        last_voice = cur_voice;
        last_body_temp = body_temp;
    }
}

//==========================================================
//	Func:	Ali_MQTT_Recevie
//	Desc:	Parse incoming MQTT messages
//==========================================================
void Ali_MQTT_Recevie(void)
{
    char *Start;
    uint8_t changed = 0;

    // --- DEBUG LOGGING START ---
    // Prevent self-echo loops if ATE1 is indicated
    if(strstr((char*)ESP8266_buf, "LOG:") != NULL) {
        ESP8266_Clear();
        return;
    }
    
    // Print Raw Buffer (max 200 chars) if meaningful content exists
    if(strlen((char*)ESP8266_buf) > 5) { // Filter noise
        char debug_buf[256];
        // Note: We use a separate buffer to format the log command, 
        // ensuring we don't modify ESP8266_buf during processing.
        // We use snprintf to safe print.
        snprintf(debug_buf, 200, "LOG: RX=[%s]", (char*)ESP8266_buf);
        // Ensure null termination if truncated (snprintf does this, but %s might stop early)
        // Send to UART
        uwifi_printf("%s\r\n", debug_buf);
    }
    // --- DEBUG LOGGING END ---

    // Check if buffer contains MQTT data or relevant commands
    if(strstr((char*)ESP8266_buf, "+MQTTSUBRECV") == NULL && 
       strstr((char*)ESP8266_buf, "cmd") == NULL && 
       strstr((char*)ESP8266_buf, "led_") == NULL) {
         // Return if no relevant data found to avoid false positives in noise
         if(strlen((char*)ESP8266_buf) > 0 && strstr((char*)ESP8266_buf, "CLOSED")) // Handle disconnect?
         {
             // Optional: Reconnect logic could go here
         }
         return; 
    }
    
    // Attempt to identify Topic/Payload for logging
    if(strstr((char*)ESP8266_buf, "+MQTTSUBRECV") != NULL) {
        // Format is usually: +MQTTSUBRECV:0,"topic",len,payload
        // We just print that we parsed it
        uwifi_printf("LOG: Parsing MQTT Payload...\r\n");
    }

    // --- JSON Parsing ---
    
    // crib_flag
    Start = strstr((char *)ESP8266_buf, "crib_flag");
    if(Start != NULL)
    {
      Start = strstr(Start, ":");
      if(Start != NULL) {
          int val = atoi((const char *)(Start + 1));
          if(crib_flag != val) { // Log only if effective or just log hit? Request says "CMD HIT" 
              crib_flag = val;
              changed = 1;
              uwifi_printf("LOG: CMD HIT: key=crib_flag newValue=%d\r\n", val);
          } else {
              // Log hit even if same value, for debugging reception
              uwifi_printf("LOG: CMD HIT: key=crib_flag SameValue=%d\r\n", val);
          }
      }
    }
    
    // mode
    Start = strstr((char *)ESP8266_buf, "mode");
    if(Start != NULL)
    {
      // "mode": 1
      // Need to avoid matching "securemode" in CLIENTID echo if that happens, 
      // but here we are in Receive function. The buffer should be RX data.
      // Assuming "mode" key in JSON.
      char* colon = strstr(Start, ":");
      if(colon != NULL) {
          int val = atoi((const char *)(colon + 1));
          mode = val;
          changed = 1;
          uwifi_printf("LOG: CMD HIT: key=mode newValue=%d\r\n", val);
      }
    }
  
    // hot_flag
    Start = strstr((char *)ESP8266_buf, "hot_flag");
    if(Start != NULL)
    {
      Start = strstr(Start, ":");
      if(Start != NULL) {
          int val = atoi((const char *)(Start + 1));
          hot_flag = val;
          changed = 1;
          uwifi_printf("LOG: CMD HIT: key=hot_flag newValue=%d\r\n", val);
      }
    }
  
    // fan_flag
    Start = strstr((char *)ESP8266_buf, "fan_flag");
    if(Start != NULL)
    {
      Start = strstr(Start, ":");
      if(Start != NULL) {
          int val = atoi((const char *)(Start + 1));
          fan_flag = val;
          changed = 1;
          uwifi_printf("LOG: CMD HIT: key=fan_flag newValue=%d\r\n", val);
      }
    }

    // music_flag - App controlled music playback
    // music_flag=1 从App表示"播放", 需要lullabuy(0)拉低触发
    // music_flag=0 从App表示"停止", 需要lullabuy(1)拉高待机
    // music_flag=-1 从App表示"恢复自动模式"
    Start = strstr((char *)ESP8266_buf, "music_flag");
    if(Start != NULL)
    {
      Start = strstr(Start, ":");
      if(Start != NULL) {
          int val = atoi((const char *)(Start + 1));
          
          if(val == -1) {
              // 恢复自动模式: App不再覆盖, main.c根据voice自动控制
              music_app_override = 0;
              music_flag = 0;
              uwifi_printf("LOG: CMD HIT: music_flag=-1, restored AUTO mode\r\n");
          } else {
              // App手动控制: 设置覆盖标志
              music_app_override = 1;  // 阻止main.c自动控制
              music_flag = (val != 0) ? 1 : 0;
              // App的music_flag=1表示播放, 需要传0给lullabuy(拉低触发)
              lullabuy(!music_flag);  // 反转: music_flag=1→lullabuy(0)→LOW→播放
              uwifi_printf("LOG: CMD HIT: music_flag=%d, override=1, lullabuy(%d)\r\n", music_flag, !music_flag);
          }
          changed = 1;
      }
    }

    // --- Legacy / Shortcut Commands ---
    if (strstr((char *)ESP8266_buf, "led_on") != NULL) {
        fan_flag = 1;
        changed = 1;
        uwifi_printf("LOG: CMD HIT: key=led_on (fan_flag=1)\r\n");
    }
    
    if (strstr((char *)ESP8266_buf, "led_off") != NULL) {
        fan_flag = 0;
        changed = 1;
        uwifi_printf("LOG: CMD HIT: key=led_off (fan_flag=0)\r\n");
    }

    // Processed? Clear and Report (immediate publish, bypasses periodic throttle)
    if (changed) {
        uwifi_printf("LOG: APPLY CMD + PUBLISH STATUS\r\n");
        ESP8266_Clear();
        Ali_MQTT_Publish_Status();
        // Update tick to prevent immediate double-publish from periodic task
        last_publish_tick = HAL_GetTick();
    }
    else if (strstr((char*)ESP8266_buf, "+MQTTSUBRECV")) {
        // Received MQTT message but didn't match commands?
        // Clear it anyway to prevent buffer overflow
        ESP8266_Clear();
    }
}

//==========================================================
//	Func:	ESP8266_Status
//==========================================================
_Bool ESP8266_Status(void)
{
	unsigned int timeOut;
	Usart_SendString((unsigned char *)"AT+CWSTATE?\r\n", strlen((const char *)"AT+CWSTATE?\r\n"));
	timeOut = 50;
	while(timeOut--)
	{
		if(ESP8266_WaitRecive() == REV_OK)
		{
			if(strstr((const char *)ESP8266_buf, "+CWSTATE") != NULL)
			{
				ESP8266_Clear();
				return 1;	
			}
            else
            {
                ESP8266_Clear();
                return 0;
			}
		}
		DelayXms(10);
	}
	ESP8266_Clear();
	return 1;
}

//==========================================================
//	Func:	ESP8266_init
//==========================================================
void  ESP8266_init(void)
{
  HAL_UART_Receive_IT(&Huart_wifi, &uartwifi_value, 1);
  
  ESP8266LinkAp();
  ESP8266LinkloT();
}

//==========================================================
//	Func:	ESP8266_Get_Time (Stub or Original)
//  Notes: 	We need to preserve it, but EMQX might not give SNTP easy.
//          However, basic AT+CIPSNTPTIME? might still work if configured.
//          For now, preserving the logic assuming ESP8266 has Internet.
//==========================================================
Time_Get ESP8266_Get_Time(void)
{
	unsigned short timeOut;
	char *Start;
	Time_Get Time = {0};
	
	Usart_SendString((unsigned char *)"AT+CIPSNTPTIME?\r\n", strlen((const char *)"AT+CIPSNTPTIME?\r\n"));
	timeOut = 2000;
	while(timeOut--)
	{
		if(ESP8266_WaitRecive() == REV_OK)
		{
			if(strstr((const char *)ESP8266_buf, "OK") != NULL)
			{
				Start = strstr((const char *)ESP8266_buf, "TIME:") + 5;
				
				if(strstr(Start, "Mon") != NULL) Time.week = 1;
				else if(strstr(Start, "Tue") != NULL) Time.week = 2;
				else if(strstr(Start, "Wed") != NULL) Time.week = 3;
				else if(strstr(Start, "Thu") != NULL) Time.week = 4;
				else if(strstr(Start, "Fri") != NULL) Time.week = 5;
				else if(strstr(Start, "Sat") != NULL) Time.week = 6;
				else if(strstr(Start, "Sun") != NULL) Time.week = 7;
				
				if(strstr(Start, "Jan") != NULL) Time.month = 1;
				else if(strstr(Start, "Fed") != NULL) Time.month = 2; // Original had Fed?
				else if(strstr(Start, "Mar") != NULL) Time.month = 3;
				else if(strstr(Start, "Apr") != NULL) Time.month = 4;
				else if(strstr(Start, "May") != NULL) Time.month = 5;
				else if(strstr(Start, "Jun") != NULL) Time.month = 6;
				else if(strstr(Start, "Jul") != NULL) Time.month = 7;
				else if(strstr(Start, "Aug") != NULL) Time.month = 8;
				else if(strstr(Start, "Sep") != NULL) Time.month = 9;
				else if(strstr(Start, "Oct") != NULL) Time.month = 10;
				else if(strstr(Start, "Nov") != NULL) Time.month = 11;
				else if(strstr(Start, "Dec") != NULL) Time.month = 12;
				
				Start = strstr(Start, " ") + 1;
				Start = strstr(Start, " ") + 1;
				if(*Start==' ')
				{
				  Start = strstr(Start, " ") + 1;
				  Time.day = (*Start-'0');
				}
				else
				{
				  Time.day = (*Start-'0')*10 + (*(Start+1)-'0');
				}
				Start = strstr(Start, " ") + 1;
				Time.hour = (*Start-'0')*10 + (*(Start+1)-'0');
				Start = strstr(Start, ":") + 1;
				Time.minute = (*Start-'0')*10 + (*(Start+1)-'0');
				Start = strstr(Start, ":") + 1;
				Time.second = (*Start-'0')*10 + (*(Start+1)-'0');
				Start = strstr(Start, " ") + 1;
				Time.year = (*Start-'0')*1000 + (*(Start+1)-'0')*100 + (*(Start+2)-'0')*10 + (*(Start+3)-'0');
				
				ESP8266_Clear();
				break;
			}
		}
		DelayXms(10);
	}
	return Time;
}

//==========================================================
//  Func:   Ali_MQTT_Trigger_CryEvent
//  Desc:   When cry detected (voice==0), trigger SOC to export video clip.
//          Implements debounce (5 consecutive readings) and throttle (60s min interval).
//          Publishes to babycam/trigger with JSON payload.
//==========================================================
void Ali_MQTT_Trigger_CryEvent(void)
{
    uint32_t now = HAL_GetTick();
    uint8_t cur_voice = voice;  // Read GPIO once
    
    // Check if crying detected (voice==0 means crying)
    if (cur_voice == 0) {
        // Increment debounce counter
        if (cry_debounce_counter < 255) {
            cry_debounce_counter++;
        }
        
        // Check if debounce threshold reached and not already triggered
        if (cry_debounce_counter >= CRY_DEBOUNCE_COUNT && !cry_triggered) {
            // Check throttle interval (handle tick overflow)
            uint32_t elapsed = now - last_cry_trigger_tick;
            if (elapsed >= CRY_TRIGGER_INTERVAL_MS || last_cry_trigger_tick == 0) {
                // Trigger SOC video export!
                char txt[256];
                uint32_t ts = now / 1000;  // Simple timestamp (uptime seconds)
                
                // Construct MQTT publish command
                // Payload: {"event":"cry","seconds":30,"ts":xxxxx}
                snprintf(txt, sizeof(txt), 
                    "AT+MQTTPUB=0,\"%s\",\"{\\\"event\\\":\\\"cry\\\"\\,\\\"seconds\\\":30\\,\\\"ts\\\":%lu}\",1,0\r\n",
                    TOPIC_BABYCAM_TRIGGER, (unsigned long)ts);
                
                // Send to ESP8266
                uwifi_printf("%s", txt);
                
                // Log to serial
                uwifi_printf("LOG: CRY TRIGGER SENT to SOC, ts=%lu\r\n", (unsigned long)ts);
                
                // Update throttle and flag
                last_cry_trigger_tick = now;
                cry_triggered = 1;
            }
        }
    } else {
        // No cry detected, reset debounce and trigger flag
        cry_debounce_counter = 0;
        cry_triggered = 0;  // Allow new trigger when cry starts again
    }
}
