#!/usr/bin/env python3
"""
BabyCam AI - RKNN NPU Accelerated Pose Detection
Uses RK3588 NPU for 30+ FPS inference
"""

import cv2
import time
import json
import logging
import numpy as np
from datetime import datetime
from collections import deque
from rknnlite.api import RKNNLite
import paho.mqtt.client as mqtt

logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger('BabyCamRKNN')

# Config
MQTT_BROKER = "127.0.0.1"
MQTT_PORT = 1883
MQTT_USER = "admin"
MQTT_PASS = "public"
MQTT_TOPIC_STATUS = "babycam/ai/status"
MQTT_TOPIC_NOTIFY = "babycam/ai/notify"

RTSP_INPUT = "rtsp://127.0.0.1:8554/babycam"
OUTPUT_IMAGE = "/var/log.hdd/babycam/ai_frame.jpg"
RKNN_MODEL = "/var/log.hdd/babycam/ai/yolov8n-pose.rknn"

INPUT_SIZE = 640
CONF_THRESHOLD = 0.4
IOU_THRESHOLD = 0.45

# YOLOv8 Pose has 17 keypoints
NUM_KEYPOINTS = 17
SKELETON = [[16, 14], [14, 12], [17, 15], [15, 13], [12, 13], [6, 12], [7, 13], 
            [6, 7], [6, 8], [7, 9], [8, 10], [9, 11], [2, 3], [1, 2], [1, 3], [2, 4], [3, 5], [4, 6], [5, 7]]

class BabyCamRKNN:
    def __init__(self):
        self.rknn = None
        self.mqtt_client = None
        self.prev_keypoints = None
        self.motion_history = deque(maxlen=30)
        self.last_motion_time = time.time()
        self.last_alert_time = {}
        
    def load_model(self):
        logger.info(f"Loading RKNN model: {RKNN_MODEL}")
        self.rknn = RKNNLite()
        ret = self.rknn.load_rknn(RKNN_MODEL)
        if ret != 0:
            logger.error(f"Load RKNN failed: {ret}")
            return False
        
        ret = self.rknn.init_runtime(core_mask=RKNNLite.NPU_CORE_0_1_2)
        if ret != 0:
            logger.error(f"Init runtime failed: {ret}")
            return False
        
        logger.info("RKNN model loaded, using all 3 NPU cores")
        return True
    
    def connect_mqtt(self):
        try:
            self.mqtt_client = mqtt.Client()
            self.mqtt_client.username_pw_set(MQTT_USER, MQTT_PASS)
            self.mqtt_client.connect(MQTT_BROKER, MQTT_PORT, 60)
            self.mqtt_client.loop_start()
            logger.info("MQTT connected")
        except Exception as e:
            logger.error(f"MQTT failed: {e}")
    
    def preprocess(self, frame):
        """Preprocess frame for RKNN input - NCHW float32"""
        h, w = frame.shape[:2]
        scale = min(INPUT_SIZE / w, INPUT_SIZE / h)
        new_w, new_h = int(w * scale), int(h * scale)
        
        resized = cv2.resize(frame, (new_w, new_h))
        
        # Pad to 640x640
        padded = np.full((INPUT_SIZE, INPUT_SIZE, 3), 114, dtype=np.uint8)
        pad_x, pad_y = (INPUT_SIZE - new_w) // 2, (INPUT_SIZE - new_h) // 2
        padded[pad_y:pad_y+new_h, pad_x:pad_x+new_w] = resized
        
        # Convert to RGB, transpose to NCHW, keep as uint8
        img = cv2.cvtColor(padded, cv2.COLOR_BGR2RGB)
        img = np.transpose(img, (2, 0, 1))  # HWC -> CHW
        img = np.expand_dims(img, axis=0)   # CHW -> NCHW
        
        return img, scale, pad_x, pad_y
    
    def postprocess(self, outputs, scale, pad_x, pad_y, orig_h, orig_w):
        """Process RKNN outputs to get detections"""
        # YOLOv8 pose output: [1, 56, 8400] - 4 box + 1 conf + 51 keypoints (17*3)
        output = outputs[0]
        
        if len(output.shape) == 3:
            output = output[0]
        output = output.T  # [8400, 56]
        
        # Get boxes and scores
        boxes = output[:, :4]  # cx, cy, w, h
        scores = output[:, 4]  # confidence
        keypoints = output[:, 5:].reshape(-1, NUM_KEYPOINTS, 3)  # x, y, conf for each keypoint
        
        # Filter by confidence
        mask = scores > CONF_THRESHOLD
        boxes = boxes[mask]
        scores = scores[mask]
        keypoints = keypoints[mask]
        
        if len(boxes) == 0:
            return [], [], []
        
        # Convert to xyxy
        cx, cy, w, h = boxes[:, 0], boxes[:, 1], boxes[:, 2], boxes[:, 3]
        x1 = cx - w / 2
        y1 = cy - h / 2
        x2 = cx + w / 2
        y2 = cy + h / 2
        boxes_xyxy = np.stack([x1, y1, x2, y2], axis=1)
        
        # NMS
        indices = cv2.dnn.NMSBoxes(boxes_xyxy.tolist(), scores.tolist(), CONF_THRESHOLD, IOU_THRESHOLD)
        if len(indices) == 0:
            return [], [], []
        
        indices = indices.flatten()
        
        # Scale back to original image
        result_boxes = []
        result_scores = []
        result_keypoints = []
        
        for i in indices:
            box = boxes_xyxy[i]
            # Remove padding and scale
            box[0] = (box[0] - pad_x) / scale
            box[1] = (box[1] - pad_y) / scale
            box[2] = (box[2] - pad_x) / scale
            box[3] = (box[3] - pad_y) / scale
            
            # Clip to image bounds
            box[0] = max(0, min(box[0], orig_w))
            box[1] = max(0, min(box[1], orig_h))
            box[2] = max(0, min(box[2], orig_w))
            box[3] = max(0, min(box[3], orig_h))
            
            result_boxes.append(box)
            result_scores.append(scores[i])
            
            # Scale keypoints
            kpts = keypoints[i].copy()
            kpts[:, 0] = (kpts[:, 0] - pad_x) / scale
            kpts[:, 1] = (kpts[:, 1] - pad_y) / scale
            result_keypoints.append(kpts)
        
        return result_boxes, result_scores, result_keypoints
    
    def analyze_pose(self, kpts):
        """Analyze pose for danger"""
        if kpts is None or len(kpts) < 7:
            return False, "none", 0
        
        # Check face visibility (nose, eyes)
        nose_conf = kpts[0, 2]
        leye_conf = kpts[1, 2]
        reye_conf = kpts[2, 2]
        
        face_visible = sum([1 if c > 0.4 else 0 for c in [nose_conf, leye_conf, reye_conf]])
        
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
            if kpts[i, 2] > 0.3 and self.prev_keypoints[i, 2] > 0.3:
                dx = kpts[i, 0] - self.prev_keypoints[i, 0]
                dy = kpts[i, 1] - self.prev_keypoints[i, 1]
                total += np.sqrt(dx*dx + dy*dy)
                count += 1
        
        self.prev_keypoints = kpts.copy()
        return total / count if count > 0 else 0
    
    def draw_results(self, frame, boxes, scores, keypoints, danger, pose, motion, fps):
        h, w = frame.shape[:2]
        
        # Draw detections
        for i, (box, score, kpts) in enumerate(zip(boxes, scores, keypoints)):
            x1, y1, x2, y2 = map(int, box)
            
            # Draw box
            color = (0, 0, 255) if danger else (0, 255, 0)
            cv2.rectangle(frame, (x1, y1), (x2, y2), color, 2)
            cv2.putText(frame, f"person {score:.2f}", (x1, y1-5), 
                       cv2.FONT_HERSHEY_SIMPLEX, 0.5, color, 2)
            
            # Draw keypoints
            for j, (x, y, c) in enumerate(kpts):
                if c > 0.3:
                    cv2.circle(frame, (int(x), int(y)), 4, (0, 255, 0), -1)
            
            # Draw skeleton
            for s1, s2 in SKELETON:
                if kpts[s1-1, 2] > 0.3 and kpts[s2-1, 2] > 0.3:
                    pt1 = (int(kpts[s1-1, 0]), int(kpts[s1-1, 1]))
                    pt2 = (int(kpts[s2-1, 0]), int(kpts[s2-1, 1]))
                    cv2.line(frame, pt1, pt2, (0, 255, 255), 2)
        
        # Status bar
        bg_color = (0, 0, 180) if danger else (50, 50, 50)
        cv2.rectangle(frame, (0, 0), (w, 70), bg_color, -1)
        
        status = f"DANGER: {pose}" if danger else f"OK: {pose}"
        text_color = (0, 0, 255) if danger else (0, 255, 0)
        cv2.putText(frame, status, (10, 30), cv2.FONT_HERSHEY_SIMPLEX, 0.8, text_color, 2)
        
        info = f"{datetime.now().strftime('%H:%M:%S')} | Motion:{motion:.0f} | FPS:{fps:.0f}"
        cv2.putText(frame, info, (10, 55), cv2.FONT_HERSHEY_SIMPLEX, 0.5, (200, 200, 200), 1)
        cv2.putText(frame, "RKNN NPU", (w-120, 30), cv2.FONT_HERSHEY_SIMPLEX, 0.6, (255, 200, 0), 2)
        
        return frame
    
    def send_alert(self, alert_type, message, cooldown=60):
        now = time.time()
        if alert_type in self.last_alert_time and now - self.last_alert_time[alert_type] < cooldown:
            return
        self.last_alert_time[alert_type] = now
        
        if self.mqtt_client:
            alert = {"type": alert_type, "message": message, "time": datetime.now().isoformat()}
            self.mqtt_client.publish(MQTT_TOPIC_NOTIFY, json.dumps(alert, ensure_ascii=False))
        logger.warning(f"ALERT: {message}")
    
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
        fps = 0
        
        logger.info("Starting RKNN detection...")
        
        while True:
            ret, frame = cap.read()
            if not ret:
                logger.warning("Frame failed")
                cap.release()
                time.sleep(0.5)
                cap = cv2.VideoCapture(RTSP_INPUT)
                continue
            
            orig_h, orig_w = frame.shape[:2]
            
            # Preprocess
            input_data, scale, pad_x, pad_y = self.preprocess(frame)
            
            # Inference (NCHW format)
            outputs = self.rknn.inference(inputs=[input_data], data_format='nchw')
            
            # Postprocess
            boxes, scores, keypoints = self.postprocess(outputs, scale, pad_x, pad_y, orig_h, orig_w)
            
            danger, pose, face_pts = False, "none", 0
            motion = 0
            
            if len(keypoints) > 0:
                danger, pose, face_pts = self.analyze_pose(keypoints[0])
                motion = self.calculate_motion(keypoints[0])
                self.motion_history.append(motion)
                
                if motion > 20:
                    self.last_motion_time = time.time()
                
                if danger:
                    self.send_alert("pose_danger", f"Danger: {pose}", 30)
            
            # Draw and save
            result = self.draw_results(frame, boxes, scores, keypoints, danger, pose, motion, fps)
            cv2.imwrite(OUTPUT_IMAGE, result)
            
            # MQTT status
            if self.mqtt_client and frame_count % 5 == 0:
                status = {
                    "person": len(boxes) > 0,
                    "pose": str(pose),  # Ensure string
                    "danger": bool(danger), # Ensure bool
                    "motion": float(motion), # Ensure float
                    "fps": float(round(fps, 1)), # Ensure float
                    "time": datetime.now().isoformat()
                }
                self.mqtt_client.publish(MQTT_TOPIC_STATUS, json.dumps(status))
            
            frame_count += 1
            if frame_count % 30 == 0:
                fps = 30 / (time.time() - fps_start)
                fps_start = time.time()
                logger.info(f"FPS: {fps:.1f}")
        
        cap.release()
        self.rknn.release()

def main():
    detector = BabyCamRKNN()
    try:
        detector.run()
    except KeyboardInterrupt:
        logger.info("Stopping...")

if __name__ == "__main__":
    main()
