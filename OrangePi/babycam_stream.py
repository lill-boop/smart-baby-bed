#!/usr/bin/env python3
"""
BabyCam AI - MJPEG Streaming Server + Pose Detection
Provides real-time video stream with AI detection overlay
"""

import cv2
import time
import json
import logging
import numpy as np
from datetime import datetime
from collections import deque
from flask import Flask, Response
from threading import Thread, Lock
from ultralytics import YOLO
import paho.mqtt.client as mqtt

logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger('BabyCamStream')

# Config
MQTT_BROKER = "127.0.0.1"
MQTT_PORT = 1883
MQTT_USER = "admin"
MQTT_PASS = "public"
MQTT_TOPIC_STATUS = "babycam/ai/status"
MQTT_TOPIC_NOTIFY = "babycam/ai/notify"

RTSP_INPUT = "rtsp://127.0.0.1:8554/babycam"
OUTPUT_IMAGE = "/var/log.hdd/babycam/ai_frame.jpg"
MJPEG_PORT = 8088

# Keypoint indices
NOSE, LEFT_EYE, RIGHT_EYE = 0, 1, 2
LEFT_EAR, RIGHT_EAR = 3, 4
LEFT_SHOULDER, RIGHT_SHOULDER = 5, 6

VISIBLE_CONF = 0.4

app = Flask(__name__)

class BabyCamStream:
    def __init__(self):
        self.model = None
        self.mqtt_client = None
        self.current_frame = None
        self.frame_lock = Lock()
        self.running = True
        self.last_motion_time = time.time()
        self.prev_keypoints = None
        self.motion_history = deque(maxlen=30)
        self.fps = 0
        
    def connect_mqtt(self):
        try:
            self.mqtt_client = mqtt.Client()
            self.mqtt_client.username_pw_set(MQTT_USER, MQTT_PASS)
            self.mqtt_client.connect(MQTT_BROKER, MQTT_PORT, 60)
            self.mqtt_client.loop_start()
            logger.info("MQTT connected")
        except Exception as e:
            logger.error(f"MQTT failed: {e}")
    
    def load_model(self):
        logger.info("Loading YOLOv8n-pose...")
        self.model = YOLO('yolov8n-pose.pt')
        logger.info("Model loaded")
    
    def get_keypoint(self, kpts, idx):
        if kpts is None or idx >= len(kpts):
            return 0, 0, 0
        pt = kpts[idx]
        return float(pt[0]), float(pt[1]), float(pt[2]) if len(pt) > 2 else 0
    
    def analyze_pose(self, kpts):
        if kpts is None:
            return False, "none", 0
        
        nose_c = self.get_keypoint(kpts, NOSE)[2]
        leye_c = self.get_keypoint(kpts, LEFT_EYE)[2]  
        reye_c = self.get_keypoint(kpts, RIGHT_EYE)[2]
        
        face_visible = sum([1 if c > VISIBLE_CONF else 0 for c in [nose_c, leye_c, reye_c]])
        
        if face_visible < 2:
            return True, "face_obscured", face_visible
        return False, "normal", face_visible
    
    def calculate_motion(self, kpts):
        if self.prev_keypoints is None or kpts is None:
            self.prev_keypoints = kpts
            return 0
        
        total = 0
        count = 0
        for i in range(min(len(kpts), len(self.prev_keypoints))):
            curr, prev = kpts[i], self.prev_keypoints[i]
            if len(curr) > 2 and len(prev) > 2 and curr[2] > 0.3 and prev[2] > 0.3:
                dx, dy = curr[0] - prev[0], curr[1] - prev[1]
                total += np.sqrt(dx*dx + dy*dy)
                count += 1
        
        self.prev_keypoints = kpts
        return total / count if count > 0 else 0
    
    def draw_overlay(self, frame, danger, pose, motion, face_pts):
        h, w = frame.shape[:2]
        
        # Status bar
        color = (0, 0, 180) if danger else (50, 50, 50)
        cv2.rectangle(frame, (0, 0), (w, 60), color, -1)
        
        # Status text
        status = f"DANGER: {pose}" if danger else f"OK: {pose}"
        text_color = (0, 0, 255) if danger else (0, 255, 0)
        cv2.putText(frame, status, (10, 25), cv2.FONT_HERSHEY_SIMPLEX, 0.7, text_color, 2)
        
        # Info
        info = f"{datetime.now().strftime('%H:%M:%S')} | Face:{face_pts}/3 | Motion:{motion:.0f} | FPS:{self.fps:.0f}"
        cv2.putText(frame, info, (10, 50), cv2.FONT_HERSHEY_SIMPLEX, 0.4, (200, 200, 200), 1)
        
        return frame
    
    def capture_loop(self):
        logger.info(f"Opening: {RTSP_INPUT}")
        cap = cv2.VideoCapture(RTSP_INPUT)
        cap.set(cv2.CAP_PROP_BUFFERSIZE, 1)
        
        frame_count = 0
        fps_start = time.time()
        
        while self.running:
            ret, frame = cap.read()
            if not ret:
                logger.warning("Frame read failed")
                cap.release()
                time.sleep(1)
                cap = cv2.VideoCapture(RTSP_INPUT)
                continue
            
            # Run detection
            results = self.model(frame, conf=0.3, verbose=False)
            annotated = results[0].plot()
            
            danger, pose, face_pts = False, "none", 0
            motion = 0
            
            if results[0].keypoints is not None and len(results[0].keypoints.data) > 0:
                kpts = results[0].keypoints.data[0].cpu().numpy()
                danger, pose, face_pts = self.analyze_pose(kpts)
                motion = self.calculate_motion(kpts)
                self.motion_history.append(motion)
                
                if motion > 20:
                    self.last_motion_time = time.time()
            
            # Draw overlay
            annotated = self.draw_overlay(annotated, danger, pose, motion, face_pts)
            
            # Update frame
            with self.frame_lock:
                _, jpeg = cv2.imencode('.jpg', annotated, [cv2.IMWRITE_JPEG_QUALITY, 80])
                self.current_frame = jpeg.tobytes()
            
            # Save static image too
            if frame_count % 10 == 0:
                cv2.imwrite(OUTPUT_IMAGE, annotated)
            
            # FPS
            frame_count += 1
            if frame_count % 30 == 0:
                self.fps = 30 / (time.time() - fps_start)
                fps_start = time.time()
                logger.info(f"FPS: {self.fps:.1f}")
        
        cap.release()
    
    def generate_stream(self):
        while True:
            with self.frame_lock:
                if self.current_frame is not None:
                    yield (b'--frame\r\n'
                           b'Content-Type: image/jpeg\r\n\r\n' + self.current_frame + b'\r\n')
            time.sleep(0.033)  # ~30 FPS
    
    def start(self):
        self.load_model()
        self.connect_mqtt()
        
        # Start capture thread
        capture_thread = Thread(target=self.capture_loop, daemon=True)
        capture_thread.start()
        
        logger.info(f"MJPEG stream available at http://0.0.0.0:{MJPEG_PORT}/stream")

streamer = BabyCamStream()

@app.route('/stream')
def video_stream():
    return Response(streamer.generate_stream(),
                    mimetype='multipart/x-mixed-replace; boundary=frame')

@app.route('/')
def index():
    return '''
    <html>
    <head><title>BabyCam AI Stream</title></head>
    <body style="background:#000;margin:0;padding:0;">
        <img src="/stream" style="width:100%;height:auto;">
    </body>
    </html>
    '''

if __name__ == '__main__':
    streamer.start()
    app.run(host='0.0.0.0', port=MJPEG_PORT, threaded=True)
