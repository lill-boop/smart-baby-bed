#!/usr/bin/env python3
"""
BabyCam AI - Complete Baby Monitoring with Pose Detection
Features:
1. Face-down detection (prone sleeping danger)
2. Face obscure detection (blanket covering face)
3. Motion analysis (activity level)
4. No motion alert (5 min stillness warning)
"""

import cv2
import time
import json
import logging
import numpy as np
from datetime import datetime
from collections import deque
from ultralytics import YOLO
import paho.mqtt.client as mqtt

logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger('BabyCamPose')

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

# Keypoint indices
NOSE, LEFT_EYE, RIGHT_EYE = 0, 1, 2
LEFT_EAR, RIGHT_EAR = 3, 4
LEFT_SHOULDER, RIGHT_SHOULDER = 5, 6
LEFT_HIP, RIGHT_HIP = 11, 12

# Thresholds
VISIBLE_CONF = 0.4          # Keypoint visibility threshold
NO_MOTION_ALERT_SEC = 300   # 5 minutes
MOTION_THRESHOLD = 20       # Pixel movement threshold

class BabyCamPose:
    def __init__(self):
        self.mqtt_client = None
        self.model = None
        
        # Tracking
        self.prev_keypoints = None
        self.last_motion_time = time.time()
        self.motion_history = deque(maxlen=30)  # Last 30 frames
        
        # Alerts
        self.last_alert_time = {}
        self.consecutive_danger = 0
        
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
        logger.info("Loading YOLOv8n-pose model...")
        try:
            self.model = YOLO('yolov8n-pose.pt')
            logger.info("Model loaded")
            return True
        except Exception as e:
            logger.error(f"Model load failed: {e}")
            return False
    
    def send_alert(self, alert_type, message, level="warning", cooldown=60):
        """Send alert with cooldown to prevent spam"""
        now = time.time()
        if alert_type in self.last_alert_time:
            if now - self.last_alert_time[alert_type] < cooldown:
                return
        
        self.last_alert_time[alert_type] = now
        alert = {
            "type": alert_type,
            "message": message,
            "level": level,
            "timestamp": datetime.now().isoformat()
        }
        if self.mqtt_client:
            self.mqtt_client.publish(MQTT_TOPIC_NOTIFY, json.dumps(alert, ensure_ascii=False))
        logger.warning(f"[{level.upper()}] {message}")
    
    def get_keypoint(self, kpts, idx):
        """Get keypoint (x, y, conf) safely"""
        if kpts is None or idx >= len(kpts):
            return 0, 0, 0
        pt = kpts[idx]
        return float(pt[0]), float(pt[1]), float(pt[2]) if len(pt) > 2 else 0
    
    def is_visible(self, conf):
        return conf > VISIBLE_CONF
    
    def analyze_pose(self, kpts):
        """
        Analyze baby pose for dangerous positions
        """
        result = {
            "is_dangerous": False,
            "pose": "normal",
            "face_visible": True,
            "details": {}
        }
        
        if kpts is None:
            result["pose"] = "no_detection"
            return result
        
        # Get face keypoints
        nose_x, nose_y, nose_c = self.get_keypoint(kpts, NOSE)
        leye_x, leye_y, leye_c = self.get_keypoint(kpts, LEFT_EYE)
        reye_x, reye_y, reye_c = self.get_keypoint(kpts, RIGHT_EYE)
        lear_x, lear_y, lear_c = self.get_keypoint(kpts, LEFT_EAR)
        rear_x, rear_y, rear_c = self.get_keypoint(kpts, RIGHT_EAR)
        
        # Get body keypoints
        lsh_x, lsh_y, lsh_c = self.get_keypoint(kpts, LEFT_SHOULDER)
        rsh_x, rsh_y, rsh_c = self.get_keypoint(kpts, RIGHT_SHOULDER)
        
        # Count visible face points
        face_visible_count = sum([
            1 if self.is_visible(nose_c) else 0,
            1 if self.is_visible(leye_c) else 0,
            1 if self.is_visible(reye_c) else 0,
        ])
        
        result["details"] = {
            "nose_conf": round(nose_c, 2),
            "left_eye_conf": round(leye_c, 2),
            "right_eye_conf": round(reye_c, 2),
            "face_points": face_visible_count
        }
        
        # === CHECK 1: Face Obscured ===
        # If less than 2 of (nose, left eye, right eye) are visible
        if face_visible_count < 2:
            result["is_dangerous"] = True
            result["pose"] = "face_obscured"
            result["face_visible"] = False
            return result
        
        # === CHECK 2: Face Down (Prone) ===
        # In a top-down camera view, if baby is face down:
        # - Nose should NOT be visible (or very low confidence)
        # - Back of head / ears might be visible
        # - Shoulders visible but nose hidden
        shoulders_visible = self.is_visible(lsh_c) or self.is_visible(rsh_c)
        ears_visible = self.is_visible(lear_c) or self.is_visible(rear_c)
        nose_visible = self.is_visible(nose_c)
        
        # Face down: shoulders visible, ears visible, but nose not clearly visible
        if shoulders_visible and ears_visible and not nose_visible:
            result["is_dangerous"] = True
            result["pose"] = "face_down"
            return result
        
        # === Determine normal pose ===
        # Side lying: one ear visible, one not
        left_ear_vis = self.is_visible(lear_c)
        right_ear_vis = self.is_visible(rear_c)
        
        if left_ear_vis and not right_ear_vis:
            result["pose"] = "side_lying_left"
        elif right_ear_vis and not left_ear_vis:
            result["pose"] = "side_lying_right"
        elif left_ear_vis and right_ear_vis:
            result["pose"] = "face_up"  # Both ears visible = face up
        else:
            result["pose"] = "normal"
        
        return result
    
    def calculate_motion(self, kpts):
        """Calculate motion from keypoint movement"""
        if self.prev_keypoints is None or kpts is None:
            self.prev_keypoints = kpts
            return 0
        
        total_motion = 0
        count = 0
        
        for i in range(min(len(kpts), len(self.prev_keypoints))):
            curr = kpts[i]
            prev = self.prev_keypoints[i]
            
            # Only consider visible points
            if len(curr) > 2 and len(prev) > 2:
                if curr[2] > VISIBLE_CONF and prev[2] > VISIBLE_CONF:
                    dx = curr[0] - prev[0]
                    dy = curr[1] - prev[1]
                    motion = np.sqrt(dx*dx + dy*dy)
                    total_motion += motion
                    count += 1
        
        self.prev_keypoints = kpts
        
        if count > 0:
            return total_motion / count
        return 0
    
    def draw_status(self, frame, pose_result, motion, person_detected):
        """Draw status overlay"""
        h, w = frame.shape[:2]
        
        is_dangerous = pose_result["is_dangerous"]
        pose = pose_result["pose"]
        
        # Background color based on status
        if is_dangerous:
            bg_color = (0, 0, 180)  # Red
        elif not person_detected:
            bg_color = (0, 100, 150)  # Orange
        else:
            bg_color = (50, 50, 50)  # Dark gray
        
        cv2.rectangle(frame, (0, 0), (w, 80), bg_color, -1)
        
        # Status text
        if not person_detected:
            status = "No baby detected"
            color = (0, 165, 255)
        elif is_dangerous:
            status = f"DANGER: {pose.replace('_', ' ').upper()}"
            color = (0, 0, 255)
        else:
            status = f"OK: {pose.replace('_', ' ')}"
            color = (0, 255, 0)
        
        cv2.putText(frame, status, (10, 30), cv2.FONT_HERSHEY_SIMPLEX, 0.8, color, 2)
        
        # Time and motion
        time_str = datetime.now().strftime("%H:%M:%S")
        motion_str = f"Motion: {motion:.0f}"
        cv2.putText(frame, time_str, (10, 60), cv2.FONT_HERSHEY_SIMPLEX, 0.5, (200, 200, 200), 1)
        cv2.putText(frame, motion_str, (150, 60), cv2.FONT_HERSHEY_SIMPLEX, 0.5, (200, 200, 200), 1)
        
        # Face visibility
        face_pts = pose_result["details"].get("face_points", 0)
        cv2.putText(frame, f"Face: {face_pts}/3", (w - 100, 30), cv2.FONT_HERSHEY_SIMPLEX, 0.5, (200, 200, 200), 1)
        cv2.putText(frame, "AI POSE", (w - 100, 60), cv2.FONT_HERSHEY_SIMPLEX, 0.5, (255, 200, 0), 2)
        
        return frame
    
    def run(self):
        if not self.load_model():
            return
        self.connect_mqtt()
        
        logger.info(f"Opening: {RTSP_INPUT}")
        cap = cv2.VideoCapture(RTSP_INPUT)
        cap.set(cv2.CAP_PROP_BUFFERSIZE, 1)
        
        if not cap.isOpened():
            logger.error("Cannot open RTSP")
            return
        
        frame_count = 0
        fps_start = time.time()
        last_person_time = time.time()
        no_motion_alerted = False
        
        logger.info("Starting detection...")
        
        while True:
            ret, frame = cap.read()
            if not ret:
                logger.warning("Frame read failed, reconnecting...")
                cap.release()
                time.sleep(1)
                cap = cv2.VideoCapture(RTSP_INPUT)
                continue
            
            if frame_count % 3 == 0:
                results = self.model(frame, conf=CONF_THRESHOLD, verbose=False)
                annotated = results[0].plot()
                
                person_detected = False
                pose_result = {"is_dangerous": False, "pose": "none", "details": {}}
                motion = 0
                
                if results[0].keypoints is not None and len(results[0].keypoints.data) > 0:
                    person_detected = True
                    last_person_time = time.time()
                    
                    kpts = results[0].keypoints.data[0].cpu().numpy()
                    pose_result = self.analyze_pose(kpts)
                    motion = self.calculate_motion(kpts)
                    self.motion_history.append(motion)
                    
                    # Motion check
                    if motion > MOTION_THRESHOLD:
                        self.last_motion_time = time.time()
                        no_motion_alerted = False
                    
                    # Danger alert
                    if pose_result["is_dangerous"]:
                        self.consecutive_danger += 1
                        if self.consecutive_danger >= 5:
                            self.send_alert("pose_danger", f"Dangerous: {pose_result['pose']}", "critical", 30)
                    else:
                        self.consecutive_danger = 0
                
                # No motion alert
                no_motion_sec = time.time() - self.last_motion_time
                if no_motion_sec > NO_MOTION_ALERT_SEC and not no_motion_alerted:
                    self.send_alert("no_motion", f"No movement for {int(no_motion_sec/60)} min", "warning", 300)
                    no_motion_alerted = True
                
                # No person alert
                no_person_sec = time.time() - last_person_time
                if no_person_sec > 300:
                    self.send_alert("no_person", f"No baby for {int(no_person_sec/60)} min", "warning", 300)
                
                # Draw and save
                annotated = self.draw_status(annotated, pose_result, motion, person_detected)
                cv2.imwrite(OUTPUT_IMAGE, annotated)
                
                # MQTT status
                if self.mqtt_client:
                    avg_motion = np.mean(self.motion_history) if self.motion_history else 0
                    status = {
                        "person": person_detected,
                        "pose": pose_result["pose"],
                        "danger": pose_result["is_dangerous"],
                        "motion": round(motion, 1),
                        "avg_motion": round(avg_motion, 1),
                        "no_motion_sec": int(no_motion_sec) if person_detected else 0,
                        "time": datetime.now().isoformat()
                    }
                    self.mqtt_client.publish(MQTT_TOPIC_STATUS, json.dumps(status))
            
            frame_count += 1
            
            if frame_count % 30 == 0:
                fps = 30 / (time.time() - fps_start)
                fps_start = time.time()
                avg_m = np.mean(self.motion_history) if self.motion_history else 0
                logger.info(f"FPS:{fps:.1f} Pose:{pose_result['pose']} Motion:{avg_m:.0f}")
        
        cap.release()

def main():
    detector = BabyCamPose()
    try:
        detector.run()
    except KeyboardInterrupt:
        logger.info("Stopping...")

if __name__ == "__main__":
    main()
