#!/usr/bin/env python3
"""
BabyCam AI - YOLOv8 Baby Detection with Bounding Boxes
Uses OpenCV DNN for inference
"""

import cv2
import numpy as np
import time
import json
import logging
from datetime import datetime
from threading import Thread, Event
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
RTSP_OUTPUT = "rtsp://127.0.0.1:8554/babycam_ai"

MODEL_PATH = "/var/log.hdd/babycam/ai/rknn_model_zoo/examples/yolov8/model/yolov8n.onnx"
CONF_THRESHOLD = 0.5
NMS_THRESHOLD = 0.4
INPUT_SIZE = (640, 640)

# COCO classes - person is class 0
CLASSES = ["person", "bicycle", "car", "motorcycle", "airplane", "bus", "train", "truck", "boat", 
           "traffic light", "fire hydrant", "stop sign", "parking meter", "bench", "bird", "cat", 
           "dog", "horse", "sheep", "cow", "elephant", "bear", "zebra", "giraffe", "backpack", 
           "umbrella", "handbag", "tie", "suitcase", "frisbee", "skis", "snowboard", "sports ball",
           "kite", "baseball bat", "baseball glove", "skateboard", "surfboard", "tennis racket",
           "bottle", "wine glass", "cup", "fork", "knife", "spoon", "bowl", "banana", "apple",
           "sandwich", "orange", "broccoli", "carrot", "hot dog", "pizza", "donut", "cake", "chair",
           "couch", "potted plant", "bed", "dining table", "toilet", "tv", "laptop", "mouse",
           "remote", "keyboard", "cell phone", "microwave", "oven", "toaster", "sink", "refrigerator",
           "book", "clock", "vase", "scissors", "teddy bear", "hair drier", "toothbrush"]

class BabyCamYOLO:
    def __init__(self):
        self.mqtt_client = None
        self.net = None
        self.stop_event = Event()
        self.last_detection_time = time.time()
        self.baby_detected = False
        self.detection_count = 0
        
    def connect_mqtt(self):
        self.mqtt_client = mqtt.Client()
        self.mqtt_client.username_pw_set(MQTT_USER, MQTT_PASS)
        try:
            self.mqtt_client.connect(MQTT_BROKER, MQTT_PORT, 60)
            self.mqtt_client.loop_start()
            logger.info("MQTT connected")
            return True
        except Exception as e:
            logger.error(f"MQTT failed: {e}")
            return False
    
    def load_model(self):
        logger.info(f"Loading model: {MODEL_PATH}")
        try:
            self.net = cv2.dnn.readNetFromONNX(MODEL_PATH)
            self.net.setPreferableBackend(cv2.dnn.DNN_BACKEND_OPENCV)
            self.net.setPreferableTarget(cv2.dnn.DNN_TARGET_CPU)
            logger.info("Model loaded successfully")
            return True
        except Exception as e:
            logger.error(f"Failed to load model: {e}")
            return False
    
    def preprocess(self, frame):
        blob = cv2.dnn.blobFromImage(frame, 1/255.0, INPUT_SIZE, swapRB=True, crop=False)
        return blob
    
    def postprocess(self, outputs, frame_shape):
        h, w = frame_shape[:2]
        boxes = []
        confidences = []
        class_ids = []
        
        # YOLOv8 output shape: [1, 84, 8400] -> transpose to [8400, 84]
        output = outputs[0]
        if len(output.shape) == 3:
            output = output[0].T
        
        for detection in output:
            scores = detection[4:]
            class_id = np.argmax(scores)
            confidence = scores[class_id]
            
            if confidence > CONF_THRESHOLD:
                cx, cy, bw, bh = detection[:4]
                
                # Scale to original image
                x = int((cx - bw/2) * w / INPUT_SIZE[0])
                y = int((cy - bh/2) * h / INPUT_SIZE[1])
                width = int(bw * w / INPUT_SIZE[0])
                height = int(bh * h / INPUT_SIZE[1])
                
                boxes.append([x, y, width, height])
                confidences.append(float(confidence))
                class_ids.append(class_id)
        
        # NMS
        indices = cv2.dnn.NMSBoxes(boxes, confidences, CONF_THRESHOLD, NMS_THRESHOLD)
        
        results = []
        if len(indices) > 0:
            for i in indices.flatten():
                results.append({
                    "box": boxes[i],
                    "confidence": confidences[i],
                    "class_id": class_ids[i],
                    "class_name": CLASSES[class_ids[i]] if class_ids[i] < len(CLASSES) else "unknown"
                })
        
        return results
    
    def draw_detections(self, frame, detections):
        baby_found = False
        
        for det in detections:
            x, y, w, h = det["box"]
            class_name = det["class_name"]
            conf = det["confidence"]
            
            # Person detection (baby is detected as person)
            if class_name == "person":
                color = (0, 255, 0)  # Green for person
                baby_found = True
                label = f"Baby {conf:.2f}"
            elif class_name in ["teddy bear", "bed", "couch"]:
                color = (255, 165, 0)  # Orange for objects
                label = f"{class_name} {conf:.2f}"
            else:
                continue  # Skip other classes
            
            # Draw box
            cv2.rectangle(frame, (x, y), (x+w, y+h), color, 2)
            
            # Draw label
            label_size = cv2.getTextSize(label, cv2.FONT_HERSHEY_SIMPLEX, 0.6, 2)[0]
            cv2.rectangle(frame, (x, y-25), (x+label_size[0], y), color, -1)
            cv2.putText(frame, label, (x, y-5), cv2.FONT_HERSHEY_SIMPLEX, 0.6, (255,255,255), 2)
        
        # Status overlay
        status_text = "Baby Detected" if baby_found else "Monitoring..."
        status_color = (0, 255, 0) if baby_found else (128, 128, 128)
        cv2.putText(frame, status_text, (10, 30), cv2.FONT_HERSHEY_SIMPLEX, 1, status_color, 2)
        cv2.putText(frame, datetime.now().strftime("%H:%M:%S"), (10, 60), 
                    cv2.FONT_HERSHEY_SIMPLEX, 0.7, (255,255,255), 2)
        
        return frame, baby_found
    
    def publish_status(self, baby_detected, detections):
        status = {
            "baby_detected": baby_detected,
            "detection_count": len(detections),
            "detections": [{"class": d["class_name"], "conf": d["confidence"]} for d in detections],
            "timestamp": datetime.now().isoformat()
        }
        if self.mqtt_client:
            self.mqtt_client.publish(MQTT_TOPIC_STATUS, json.dumps(status))
    
    def run(self):
        if not self.load_model():
            return
        if not self.connect_mqtt():
            logger.warning("Running without MQTT")
        
        logger.info(f"Opening RTSP: {RTSP_INPUT}")
        cap = cv2.VideoCapture(RTSP_INPUT)
        cap.set(cv2.CAP_PROP_BUFFERSIZE, 1)
        
        if not cap.isOpened():
            logger.error("Cannot open RTSP stream")
            return
        
        # Output writer
        fourcc = cv2.VideoWriter_fourcc(*'XVID')
        out = None
        
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
            
            # Inference every 3 frames to reduce CPU load
            if frame_count % 3 == 0:
                blob = self.preprocess(frame)
                self.net.setInput(blob)
                outputs = self.net.forward()
                detections = self.postprocess(outputs, frame.shape)
                
                # Draw and publish
                frame, baby_found = self.draw_detections(frame, detections)
                self.publish_status(baby_found, detections)
            
            frame_count += 1
            
            # FPS calculation
            if frame_count % 30 == 0:
                fps = 30 / (time.time() - fps_start)
                fps_start = time.time()
                logger.info(f"FPS: {fps:.1f}")
            
            # Save frame for viewing
            cv2.imwrite("/var/log.hdd/babycam/ai/latest_frame.jpg", frame)
        
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
