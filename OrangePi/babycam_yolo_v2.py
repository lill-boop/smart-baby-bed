#!/usr/bin/env python3
"""
BabyCam AI - YOLOv8 Detection with Ultralytics
"""

import cv2
import time
import json
import logging
from datetime import datetime
from threading import Thread, Event
from ultralytics import YOLO
import paho.mqtt.client as mqtt

logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger('BabyCamYOLO')

# Config
MQTT_BROKER = "127.0.0.1"
MQTT_PORT = 1883
MQTT_USER = "admin"
MQTT_PASS = "public"
MQTT_TOPIC_STATUS = "babycam/ai/status"
MQTT_TOPIC_NOTIFY = "babycam/ai/notify"

RTSP_INPUT = "rtsp://127.0.0.1:8554/babycam"
OUTPUT_IMAGE = "/var/log.hdd/babycam/ai_frame.jpg"

CONF_THRESHOLD = 0.3

class BabyCamYOLO:
    def __init__(self):
        self.mqtt_client = None
        self.model = None
        self.stop_event = Event()
        self.last_person_time = time.time()
        self.no_person_alert_sent = False
        
    def connect_mqtt(self):
        try:
            self.mqtt_client = mqtt.Client()
            self.mqtt_client.username_pw_set(MQTT_USER, MQTT_PASS)
            self.mqtt_client.connect(MQTT_BROKER, MQTT_PORT, 60)
            self.mqtt_client.loop_start()
            logger.info("MQTT connected")
            return True
        except Exception as e:
            logger.error(f"MQTT failed: {e}")
            return False
    
    def load_model(self):
        logger.info("Loading YOLOv8n model...")
        try:
            self.model = YOLO('yolov8n.pt')
            logger.info("Model loaded successfully")
            return True
        except Exception as e:
            logger.error(f"Failed to load model: {e}")
            return False
    
    def publish_alert(self, alert_type, message):
        alert = {
            "type": alert_type,
            "message": message,
            "timestamp": datetime.now().isoformat()
        }
        if self.mqtt_client:
            self.mqtt_client.publish(MQTT_TOPIC_NOTIFY, json.dumps(alert))
        logger.warning(f"ALERT: {message}")
    
    def run(self):
        if not self.load_model():
            return
        self.connect_mqtt()
        
        logger.info(f"Opening RTSP: {RTSP_INPUT}")
        cap = cv2.VideoCapture(RTSP_INPUT)
        cap.set(cv2.CAP_PROP_BUFFERSIZE, 1)
        
        if not cap.isOpened():
            logger.error("Cannot open RTSP stream")
            return
        
        frame_count = 0
        fps_start = time.time()
        
        logger.info("Starting detection loop...")
        
        while not self.stop_event.is_set():
            ret, frame = cap.read()
            if not ret:
                logger.warning("Failed to read frame, reconnecting...")
                cap.release()
                time.sleep(1)
                cap = cv2.VideoCapture(RTSP_INPUT)
                continue
            
            # Run detection every 3 frames
            if frame_count % 3 == 0:
                results = self.model(frame, conf=CONF_THRESHOLD, verbose=False)
                
                # Draw results
                annotated_frame = results[0].plot()
                
                # Check for person detection
                person_detected = False
                detections = []
                
                for box in results[0].boxes:
                    cls_id = int(box.cls[0])
                    conf = float(box.conf[0])
                    cls_name = self.model.names[cls_id]
                    
                    detections.append({
                        "class": cls_name,
                        "confidence": round(conf, 2)
                    })
                    
                    if cls_name == "person":
                        person_detected = True
                        self.last_person_time = time.time()
                        self.no_person_alert_sent = False
                
                # Check for no person alert
                no_person_duration = time.time() - self.last_person_time
                if no_person_duration > 300 and not self.no_person_alert_sent:
                    self.publish_alert("no_person", f"No person detected for {int(no_person_duration/60)} minutes")
                    self.no_person_alert_sent = True
                
                # Add status overlay
                status = "Person Detected" if person_detected else f"No person ({int(no_person_duration)}s)"
                color = (0, 255, 0) if person_detected else (0, 165, 255)
                cv2.putText(annotated_frame, status, (10, 30), cv2.FONT_HERSHEY_SIMPLEX, 0.8, color, 2)
                cv2.putText(annotated_frame, datetime.now().strftime("%H:%M:%S"), (10, 60), 
                            cv2.FONT_HERSHEY_SIMPLEX, 0.6, (255,255,255), 2)
                
                # Save frame
                cv2.imwrite(OUTPUT_IMAGE, annotated_frame)
                
                # Publish status
                if self.mqtt_client:
                    status_data = {
                        "person_detected": person_detected,
                        "detections": detections,
                        "no_person_seconds": int(no_person_duration),
                        "timestamp": datetime.now().isoformat()
                    }
                    self.mqtt_client.publish(MQTT_TOPIC_STATUS, json.dumps(status_data))
            
            frame_count += 1
            
            # FPS calculation
            if frame_count % 30 == 0:
                fps = 30 / (time.time() - fps_start)
                fps_start = time.time()
                logger.info(f"FPS: {fps:.1f}, Detections: {len(detections)}")
        
        cap.release()
        logger.info("Detection stopped")
    
    def stop(self):
        self.stop_event.set()
        if self.mqtt_client:
            self.mqtt_client.loop_stop()

def main():
    detector = BabyCamYOLO()
    try:
        detector.run()
    except KeyboardInterrupt:
        logger.info("Stopping...")
    finally:
        detector.stop()

if __name__ == "__main__":
    main()
