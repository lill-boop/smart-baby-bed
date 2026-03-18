#!/usr/bin/env python3
"""
BabyCam MQTT Trigger Service
Event trigger service - receives events, exports video clips, publishes URL to App

Author: System Engineer
Version: 1.0.0
"""

import os
import sys
import json
import time
import logging
import subprocess
from pathlib import Path
from datetime import datetime
from typing import Optional, Dict, Any

import paho.mqtt.client as mqtt

# ============ Configuration ============
MQTT_BROKER = "127.0.0.1"
MQTT_PORT = 1883
MQTT_CLIENT_ID = "babycam_trigger"
MQTT_KEEPALIVE = 60
MQTT_USERNAME = "esp8266_01"
MQTT_PASSWORD = "787970"

# Topics
TOPIC_TRIGGER = "babycam/trigger"
TOPIC_NOTIFY = "babycam/notify"

# Paths
BASE_DIR = Path("/var/log.hdd/babycam")
EXPORT_SCRIPT = Path("/home/orangepi/babycam_ai/export_clip.py")
CLIPS_DIR = BASE_DIR / "clips"
EVENTS_DIR = BASE_DIR / "events"
EVENTS_FILE = EVENTS_DIR / "events.jsonl"

# HTTP config
HTTP_PORT = 8099

# ============ Logging ============
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
    handlers=[
        logging.StreamHandler(sys.stdout),
        logging.FileHandler(BASE_DIR / "trigger" / "mqtt_trigger.log")
    ]
)
logger = logging.getLogger(__name__)


def get_local_ip() -> str:
    """Get local IP dynamically (not hardcoded)"""
    try:
        result = subprocess.run(
            ["ip", "route", "get", "1.1.1.1"],
            capture_output=True, text=True, timeout=5
        )
        for part in result.stdout.split():
            if part.count(".") == 3:
                try:
                    parts = part.split(".")
                    if all(0 <= int(p) <= 255 for p in parts):
                        if not part.startswith("1.1.1."):
                            return part
                except ValueError:
                    continue
    except Exception as e:
        logger.warning(f"Failed to get IP via route: {e}")
    
    try:
        import socket
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
        s.close()
        return ip
    except Exception as e:
        logger.error(f"Failed to get local IP: {e}")
        return "127.0.0.1"


def export_clip(seconds: int = 60, pre: int = 10) -> Optional[str]:
    """Call export_clip.py to export video clip, return filename"""
    try:
        logger.info(f"Exporting clip: {seconds}s (pre={pre}s)")
        
        result = subprocess.run(
            ["python3", str(EXPORT_SCRIPT), "now", str(seconds)],
            capture_output=True, text=True, timeout=120,
            cwd=str(EXPORT_SCRIPT.parent)
        )
        
        logger.debug(f"Export stdout: {result.stdout}")
        logger.debug(f"Export stderr: {result.stderr}")
        
        if result.returncode != 0:
            logger.error(f"Export failed with code {result.returncode}: {result.stderr}")
            return None
        
        for line in result.stdout.strip().split("\n"):
            if line.startswith("OK:"):
                file_path = line.replace("OK:", "").strip()
                return Path(file_path).name
        
        mp4_files = sorted(CLIPS_DIR.glob("*.mp4"), key=lambda f: f.stat().st_mtime, reverse=True)
        if mp4_files:
            latest = mp4_files[0]
            if time.time() - latest.stat().st_mtime < 120:
                return latest.name
        
        logger.error("Could not find exported clip")
        return None
        
    except subprocess.TimeoutExpired:
        logger.error("Export timed out")
        return None
    except Exception as e:
        logger.error(f"Export error: {e}")
        return None


def save_event(event_data: Dict[str, Any]) -> bool:
    """Save event to JSONL file (timeline capability)"""
    try:
        EVENTS_DIR.mkdir(parents=True, exist_ok=True)
        
        with EVENTS_FILE.open("a", encoding="utf-8") as f:
            f.write(json.dumps(event_data, ensure_ascii=False) + "\n")
        
        logger.info(f"Event saved: {event_data.get('type', 'unknown')}")
        return True
    except Exception as e:
        logger.error(f"Failed to save event: {e}")
        return False


class BabyCamTriggerClient:
    """MQTT client wrapper"""
    
    def __init__(self):
        self.client = mqtt.Client(
            client_id=MQTT_CLIENT_ID,
            protocol=mqtt.MQTTv311,
            callback_api_version=mqtt.CallbackAPIVersion.VERSION2
        )
        self.client.username_pw_set(MQTT_USERNAME, MQTT_PASSWORD)
        self.client.on_connect = self._on_connect
        self.client.on_message = self._on_message
        self.client.on_disconnect = self._on_disconnect
        self.local_ip = "100.115.202.71"
        logger.info(f"Local IP: {self.local_ip}")
    
    def _on_connect(self, client, userdata, flags, reason_code, properties):
        if reason_code == 0:
            logger.info(f"Connected to MQTT broker at {MQTT_BROKER}:{MQTT_PORT}")
            client.subscribe(TOPIC_TRIGGER, qos=1)
            logger.info(f"Subscribed to {TOPIC_TRIGGER}")
        else:
            logger.error(f"Connection failed: {reason_code}")
    
    def _on_disconnect(self, client, userdata, disconnect_flags, reason_code, properties):
        logger.warning(f"Disconnected from MQTT broker: {reason_code}")
    
    def _on_message(self, client, userdata, msg):
        """Handle trigger message"""
        try:
            logger.info(f"Received trigger: {msg.topic} -> {msg.payload.decode()}")
            
            payload = json.loads(msg.payload.decode())
            event_type = payload.get("event", "unknown")
            seconds = payload.get("seconds", 60)
            pre = payload.get("pre", 10)
            ts = payload.get("ts", int(time.time()))
            
            filename = export_clip(seconds=seconds, pre=pre)
            
            if filename:
                clip_url = f"http://{self.local_ip}:{HTTP_PORT}/clips/{filename}"
                
                event_record = {
                    "ts": ts,
                    "type": event_type,
                    "seconds": seconds,
                    "file": filename,
                    "url": clip_url,
                    "created_at": datetime.now().isoformat()
                }
                save_event(event_record)
                
                notify_payload = {
                    "event": event_type,
                    "ts": ts,
                    "url": clip_url,
                    "file": filename
                }
                client.publish(
                    TOPIC_NOTIFY,
                    json.dumps(notify_payload, ensure_ascii=False),
                    qos=1
                )
                logger.info(f"Published notify: {notify_payload}")
            else:
                logger.error("Export failed, no notification sent")
                
        except json.JSONDecodeError as e:
            logger.error(f"Invalid JSON payload: {e}")
        except Exception as e:
            logger.error(f"Message processing error: {e}")
    
    def run(self):
        """Start service"""
        logger.info("Starting BabyCam Trigger Service...")
        
        while True:
            try:
                self.client.connect(MQTT_BROKER, MQTT_PORT, MQTT_KEEPALIVE)
                self.client.loop_forever()
            except KeyboardInterrupt:
                logger.info("Shutting down...")
                break
            except Exception as e:
                logger.error(f"Connection error: {e}, retrying in 5s...")
                time.sleep(5)


def main():
    EVENTS_DIR.mkdir(parents=True, exist_ok=True)
    CLIPS_DIR.mkdir(parents=True, exist_ok=True)
    
    client = BabyCamTriggerClient()
    client.run()


if __name__ == "__main__":
    main()
