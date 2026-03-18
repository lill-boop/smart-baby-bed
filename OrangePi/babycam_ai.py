#!/usr/bin/env python3
"""
BabyCam AI Vision Analysis Service
Baby pose and state detection using motion analysis
"""

import os
import cv2
import time
import json
import logging
import numpy as np
from datetime import datetime
from threading import Thread, Event
import paho.mqtt.client as mqtt

# Logging config
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger('BabyCamAI')

# MQTT config
MQTT_BROKER = "127.0.0.1"
MQTT_PORT = 1883
MQTT_USER = "admin"
MQTT_PASS = "public"
MQTT_TOPIC_NOTIFY = "babycam/ai/notify"
MQTT_TOPIC_STATUS = "babycam/ai/status"

# AI config
ANALYSIS_INTERVAL = 10  # seconds
MOTION_THRESHOLD = 5000
NO_MOTION_ALERT_TIME = 300  # 5 minutes

# RTSP config
RTSP_URL = "rtsp://127.0.0.1:8554/babycam"

class BabyCamAI:
    def __init__(self):
        self.mqtt_client = None
        self.running = False
        self.stop_event = Event()
        self.last_motion_time = time.time()
        self.prev_frame = None
        self.motion_history = []
        
        self.stats = {
            "total_frames": 0,
            "motion_events": 0,
            "alerts": 0,
            "last_analysis": None,
            "baby_detected": False,
            "motion_level": 0
        }
        
    def connect_mqtt(self):
        self.mqtt_client = mqtt.Client()
        self.mqtt_client.username_pw_set(MQTT_USER, MQTT_PASS)
        
        try:
            self.mqtt_client.connect(MQTT_BROKER, MQTT_PORT, 60)
            self.mqtt_client.loop_start()
            logger.info("MQTT connected")
            return True
        except Exception as e:
            logger.error(f"MQTT connect failed: {e}")
            return False
    
    def publish_status(self, status_data):
        if self.mqtt_client:
            payload = json.dumps(status_data, ensure_ascii=False)
            self.mqtt_client.publish(MQTT_TOPIC_STATUS, payload)
    
    def publish_alert(self, alert_type, message, level="warning"):
        alert = {
            "type": alert_type,
            "message": message,
            "level": level,
            "timestamp": datetime.now().isoformat()
        }
        if self.mqtt_client:
            self.mqtt_client.publish(MQTT_TOPIC_NOTIFY, json.dumps(alert, ensure_ascii=False))
        logger.warning(f"Alert: {message}")
        self.stats["alerts"] += 1
    
    def detect_motion(self, frame):
        gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
        gray = cv2.GaussianBlur(gray, (21, 21), 0)
        
        if self.prev_frame is None:
            self.prev_frame = gray
            return 0
        
        frame_delta = cv2.absdiff(self.prev_frame, gray)
        thresh = cv2.threshold(frame_delta, 25, 255, cv2.THRESH_BINARY)[1]
        thresh = cv2.dilate(thresh, None, iterations=2)
        
        motion_level = np.sum(thresh) / 255
        
        self.prev_frame = gray
        return motion_level
    
    def analyze_baby_state(self, frame, motion_level):
        current_time = time.time()
        
        self.motion_history.append({
            "time": current_time,
            "level": motion_level
        })
        
        cutoff = current_time - 300
        self.motion_history = [m for m in self.motion_history if m["time"] > cutoff]
        
        if motion_level > MOTION_THRESHOLD:
            self.last_motion_time = current_time
            self.stats["motion_events"] += 1
        
        no_motion_duration = current_time - self.last_motion_time
        if no_motion_duration > NO_MOTION_ALERT_TIME:
            self.publish_alert(
                "no_motion",
                f"Baby no activity for {int(no_motion_duration/60)} minutes",
                "warning"
            )
        
        if self.motion_history:
            avg_motion = sum(m["level"] for m in self.motion_history) / len(self.motion_history)
        else:
            avg_motion = 0
        
        if motion_level < 1000:
            state = "sleeping"
            state_text = "Sleeping"
        elif motion_level < 5000:
            state = "light_activity"
            state_text = "Light activity"
        elif motion_level < 20000:
            state = "active"
            state_text = "Active"
        else:
            state = "very_active"
            state_text = "Very active"
        
        return {
            "state": state,
            "state_text": state_text,
            "motion_level": int(motion_level),
            "avg_motion_5min": int(avg_motion),
            "last_motion_ago": int(no_motion_duration),
            "timestamp": datetime.now().isoformat()
        }
    
    def capture_frame(self):
        cap = cv2.VideoCapture(RTSP_URL)
        cap.set(cv2.CAP_PROP_BUFFERSIZE, 1)
        
        if not cap.isOpened():
            logger.error("Cannot open RTSP stream")
            return None
        
        for _ in range(5):
            cap.grab()
        
        ret, frame = cap.read()
        cap.release()
        
        if ret:
            return frame
        return None
    
    def analysis_loop(self):
        logger.info("AI analysis service started")
        
        while not self.stop_event.is_set():
            try:
                frame = self.capture_frame()
                
                if frame is not None:
                    self.stats["total_frames"] += 1
                    
                    motion_level = self.detect_motion(frame)
                    self.stats["motion_level"] = int(motion_level)
                    
                    analysis = self.analyze_baby_state(frame, motion_level)
                    self.stats["last_analysis"] = analysis
                    
                    status = {
                        **self.stats,
                        "analysis": analysis
                    }
                    self.publish_status(status)
                    
                    logger.info(f"Analysis: {analysis['state_text']} (motion: {motion_level:.0f})")
                else:
                    logger.warning("Cannot get video frame")
                
            except Exception as e:
                logger.error(f"Analysis error: {e}")
            
            self.stop_event.wait(ANALYSIS_INTERVAL)
        
        logger.info("AI analysis service stopped")
    
    def start(self):
        if not self.connect_mqtt():
            return False
        
        self.running = True
        self.stop_event.clear()
        
        self.analysis_thread = Thread(target=self.analysis_loop, daemon=True)
        self.analysis_thread.start()
        
        return True
    
    def stop(self):
        self.running = False
        self.stop_event.set()
        
        if hasattr(self, 'analysis_thread'):
            self.analysis_thread.join(timeout=5)
        
        if self.mqtt_client:
            self.mqtt_client.loop_stop()
            self.mqtt_client.disconnect()


def main():
    ai_service = BabyCamAI()
    
    try:
        if ai_service.start():
            logger.info("BabyCam AI service running... Press Ctrl+C to stop")
            while True:
                time.sleep(1)
    except KeyboardInterrupt:
        logger.info("Stop signal received")
    finally:
        ai_service.stop()


if __name__ == "__main__":
    main()
