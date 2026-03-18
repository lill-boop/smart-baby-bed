#!/usr/bin/env python3
import paho.mqtt.client as mqtt
import json
import time

client = mqtt.Client(client_id="verify_test", protocol=mqtt.MQTTv311, callback_api_version=mqtt.CallbackAPIVersion.VERSION2)
client.username_pw_set("esp8266_01", "787970")
client.connect("127.0.0.1", 1883)

payload = {"event": "cry", "seconds": 30, "ts": int(time.time())}
client.publish("babycam/trigger", json.dumps(payload), qos=1)
print(f"Triggered: {payload}")
client.disconnect()
