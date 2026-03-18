#!/usr/bin/env python3
"""
BabyCam AI All-in-One: RKNN NPU Detection + MJPEG Streaming
Version 2.4: Optimized NHWC Input + Threaded Capture
"""
import cv2
import time
import json
import logging
import numpy as np
import threading
from datetime import datetime
from collections import deque
from rknnlite.api import RKNNLite
import paho.mqtt.client as mqtt
from flask import Flask, Response
from PIL import Image, ImageDraw, ImageFont

# Flask App
app = Flask(__name__)
output_frame = None
lock = threading.Lock()

# Config
MQTT_BROKER = "100.115.202.71"  # Same as App's broker
MQTT_PORT = 1883
MQTT_USER = "esp8266_01"  # Same as App's username
MQTT_PASS = "787970"       # Same as App's password
MQTT_TOPIC_STATUS = "babycam/ai/status"
MQTT_TOPIC_NOTIFY = "babycam/ai/notify"
RTSP_INPUT = "rtsp://127.0.0.1:8554/babycam"
RKNN_MODEL = "/var/log.hdd/babycam/ai/yolov8n-pose-320.rknn"
INPUT_SIZE = 320
CONF_THRESHOLD = 0.5
IOU_THRESHOLD = 0.45

# Logging
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger('BabyCamRKNN')

class ThreadedVideoCapture:
    def __init__(self, src):
        self.cap = cv2.VideoCapture(src)
        self.cap.set(cv2.CAP_PROP_BUFFERSIZE, 2)
        self.q = deque(maxlen=1)
        self.stopped = False
        self.lock = threading.Lock()
        
        # Start reading
        t = threading.Thread(target=self._reader)
        t.daemon = True
        t.start()

    def _reader(self):
        while not self.stopped:
            ret, frame = self.cap.read()
            if not ret:
                self.cap.release()
                time.sleep(1)
                self.cap = cv2.VideoCapture(RTSP_INPUT)
                continue
            with self.lock:
                if len(self.q) == 0:
                    self.q.append(frame)
                else:
                    self.q[0] = frame 

    def read(self):
        with self.lock:
            if len(self.q) > 0:
                return True, self.q[0]
            return False, None

    def release(self):
        self.stopped = True
        self.cap.release()

class BabyCamRKNN:
    def __init__(self):
        self.rknn = None
        self.mqtt_client = None
        self.prev_keypoints = None
        self.last_motion_time = time.time()
        
        # Font
        self.font_path = "/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc"
        try:
            self.font_large = ImageFont.truetype(self.font_path, 32)
            self.font_med = ImageFont.truetype(self.font_path, 24)
            self.font_small = ImageFont.truetype(self.font_path, 18)
        except:
            logger.error("Chinese font not found, using default")
            self.font_med = ImageFont.load_default()
            
        self.connect_mqtt()
        if not self.load_model():
            logger.error("Failed to load model")

    def put_chinese_text(self, img_pil, text, pos, color, font):
        draw = ImageDraw.Draw(img_pil)
        # Outline for visibility
        x, y = pos
        outline_color = (0,0,0)
        for adj in [(-1,-1), (-1,1), (1,-1), (1,1)]:
             draw.text((x+adj[0], y+adj[1]), text, font=font, fill=outline_color)
        draw.text(pos, text, font=font, fill=color)
            
    def load_model(self):
        logger.info(f"Loading RKNN model: {RKNN_MODEL}")
        self.rknn = RKNNLite()
        ret = self.rknn.load_rknn(RKNN_MODEL)
        if ret != 0: return False
        ret = self.rknn.init_runtime(core_mask=RKNNLite.NPU_CORE_0_1_2)
        if ret != 0: return False
        logger.info("RKNN loaded")
        return True

    def connect_mqtt(self):
        try:
            self.mqtt_client = mqtt.Client()
            self.mqtt_client.username_pw_set(MQTT_USER, MQTT_PASS)
            logger.info(f"MQTT: Connecting to {MQTT_BROKER}:{MQTT_PORT}...")
            self.mqtt_client.connect(MQTT_BROKER, MQTT_PORT, 60)
            self.mqtt_client.loop_start()
            logger.info("MQTT: Connected successfully!")
        except Exception as e:
            logger.error(f"MQTT: Connection failed: {e}")
            self.mqtt_client = None

    def preprocess(self, frame):
        h, w = frame.shape[:2]
        scale = min(INPUT_SIZE/w, INPUT_SIZE/h)
        nw, nh = int(w*scale), int(h*scale)
        resized = cv2.resize(frame, (nw, nh), interpolation=cv2.INTER_LINEAR)
        
        padded = np.full((INPUT_SIZE, INPUT_SIZE, 3), 114, dtype=np.uint8)
        dx, dy = (INPUT_SIZE-nw)//2, (INPUT_SIZE-nh)//2
        padded[dy:dy+nh, dx:dx+nw] = resized
        
        # NHWC Output directly
        img = cv2.cvtColor(padded, cv2.COLOR_BGR2RGB)
        img = np.expand_dims(img, axis=0) # (1, 640, 640, 3)
        return img, scale, dx, dy

    def postprocess(self, outputs, scale, dx, dy):
        # outputs[0] shape might vary depending on driver, checks needed
        output = outputs[0][0].T if len(outputs[0].shape)==3 else outputs[0].T
        boxes = output[:, :4]
        scores = output[:, 4]
        kpts = output[:, 5:].reshape(-1, 17, 3)
        
        mask = scores > CONF_THRESHOLD
        boxes, scores, kpts = boxes[mask], scores[mask], kpts[mask]
        
        if len(boxes) == 0: return [], [], []
        
        # NMS
        xyxy = np.copy(boxes)
        xyxy[:, 0] = boxes[:, 0] - boxes[:, 2]/2
        xyxy[:, 1] = boxes[:, 1] - boxes[:, 3]/2
        xyxy[:, 2] = boxes[:, 0] + boxes[:, 2]/2
        xyxy[:, 3] = boxes[:, 1] + boxes[:, 3]/2
        
        indices = cv2.dnn.NMSBoxes(xyxy.tolist(), scores.tolist(), CONF_THRESHOLD, IOU_THRESHOLD)
        if len(indices) == 0: return [], [], []
        indices = indices.flatten()
        
        res_boxes, res_scores, res_kpts = [], [], []
        for i in indices:
            box = xyxy[i]
            box[[0,2]] = (box[[0,2]] - dx) / scale
            box[[1,3]] = (box[[1,3]] - dy) / scale
            res_boxes.append(box)
            res_scores.append(scores[i])
            
            kp = kpts[i].copy()
            kp[:, 0] = (kp[:, 0] - dx) / scale
            kp[:, 1] = (kp[:, 1] - dy) / scale
            res_kpts.append(kp)
            
        return res_boxes, res_scores, res_kpts

    def analyze_pose(self, kpts):
        """
        Analyze pose for danger and classify posture.
        Returns: (is_danger, status_text_for_ui, detailed_pose_class)
        """
        if kpts is None or len(kpts) < 11: 
            return False, "No Person", "Unknown"
        
        # Keypoints (COCO format)
        # 0:Nose, 1:LEye, 2:REye, 3:LEar, 4:REar, 5:LSh, 6:RSh, 7:LElbow, 8:RElbow, 9:LWrist, 10:RWrist
        nose = kpts[0]; nose_conf = nose[2]
        leye = kpts[1]; leye_conf = leye[2]
        reye = kpts[2]; reye_conf = reye[2]
        lear = kpts[3]; lear_conf = lear[2]
        rear = kpts[4]; rear_conf = rear[2]
        
        lwrist = kpts[9]; lwrist_conf = lwrist[2]
        rwrist = kpts[10]; rwrist_conf = rwrist[2]
        
        danger = False
        pose_text = "正常"
        pose_type = "仰卧 (Face Up)" # Default

        # --- 1. Posture Classification (simplified for Baby) ---
        # Logic: Compare visibility of Face parts vs Ears
        
        # Face Down (Prone) ?
        # Robust check: Ears detection > Face detection
        avg_face_conf = (nose_conf + leye_conf + reye_conf) / 3.0
        avg_ear_conf = (lear_conf + rear_conf) / 2.0
        
        if avg_ear_conf > 0.5 and avg_face_conf < 0.5: # Relaxed from 0.6/0.4
            pose_type = "趴睡 (Face Down)"
            if avg_face_conf < 0.35: # Relaxed from 0.2
                danger = True
                pose_text = "危险: 趴睡"
        
        # Side Sleeping?
        elif abs(lear_conf - rear_conf) > 0.4:
            pose_type = "侧卧 (Side)"
            
        # Sitting/Upright? (Check verticality of shoulders vs hips if available, but for now assume typical top-down)
        
        # --- 2. Occlusion / Covering Detection ---
        
        # A. Hand Covering (Dynamic Radius)
        # Calculate Eye Distance as reference scale
        eye_dist = 0
        if leye_conf > 0.5 and reye_conf > 0.5:
             eye_dist = np.sqrt((leye[0]-reye[0])**2 + (leye[1]-reye[1])**2)
        
        # Fallback if eyes not clear: use ear dist or fixed
        if eye_dist < 10: 
             eye_dist = 100 # Default fallback
             
        # Threshold: Elliptical Zone (Wide X, Strict Y)
        # Eye dist is our scale unit
        RAD_X = eye_dist * 5.0  # Wide (catch book holding)
        RAD_Y = eye_dist * 3.2  # Deeper (catch hands near neck/chin)
        
        hand_near_face = False
        
        # Check Function for Ellipse
        def is_in_danger_zone(pt, center, rx, ry):
             dx = abs(pt[0] - center[0])
             dy = abs(pt[1] - center[1])
             return (dx/rx)**2 + (dy/ry)**2 <= 1.0

        if lwrist_conf > 0.4: 
            if is_in_danger_zone(lwrist, nose, RAD_X, RAD_Y): hand_near_face = True
        if rwrist_conf > 0.4:
            if is_in_danger_zone(rwrist, nose, RAD_X, RAD_Y): hand_near_face = True
            
        if hand_near_face:
            danger = True
            pose_text = "危险: 手部遮挡/异物"
            
        return danger, pose_text, pose_type, RAD_X, RAD_Y

    def calculate_motion(self, kpts):
        if self.prev_keypoints is None or kpts is None:
            self.prev_keypoints = kpts
            return 100 
        valid_mask = (kpts[:, 2] > 0.4) & (self.prev_keypoints[:, 2] > 0.4)
        if not np.any(valid_mask):
            self.prev_keypoints = kpts
            return 5
        diff = kpts[valid_mask, :2] - self.prev_keypoints[valid_mask, :2]
        dist = np.sqrt(np.sum(diff**2, axis=1))
        motion = np.mean(dist)
        self.prev_keypoints = kpts.copy()
        return motion

    def draw(self, frame, boxes, kpts, fps, pose_text, pose_type, danger, motion, rad_x, rad_y):
        h, w = frame.shape[:2]
        
        # 1. Background bar (OpenCV) - Ensure it's big enough for text
        header_h = 100
        bg = (0, 0, 180) if danger else (40, 40, 40)
        cv2.rectangle(frame, (0,0), (w, header_h), bg, -1)
        
        # 2. Draw Shapes (OpenCV)
        for box, kp in zip(boxes, kpts):
            x1, y1, x2, y2 = map(int, box)
            # Box Color: Green if OK, Red if Danger
            box_col = (0, 0, 255) if danger else (0, 255, 0)
            cv2.rectangle(frame, (x1,y1), (x2,y2), box_col, 2)
            
            for s1, s2 in [(5,7),(7,9),(6,8),(8,10)]: # Arms
                if kp[s1,2]>0.4 and kp[s2,2]>0.4:
                    cv2.line(frame, (int(kp[s1,0]),int(kp[s1,1])), (int(kp[s2,0]),int(kp[s2,1])), (0,255,255), 2)
            
            # Ellipse zone (Dynamic Hand Radius Visualization)
            if kp[0,2] > 0.2:
                # Gray ellipse for detection zone
                center = (int(kp[0,0]), int(kp[0,1]))
                axes = (int(rad_x), int(rad_y))
                cv2.ellipse(frame, center, axes, 0, 0, 360, (100, 100, 100), 1)
                
            for i, (x, y, c) in enumerate(kp):
                if c > 0.4: 
                    pc = (0,0,255) if i==0 and danger else (0,255,0)
                    cv2.circle(frame, (int(x), int(y)), 4, pc, -1)
                    if i==0: # Nose confidence
                         cv2.putText(frame, f"{c:.2f}", (int(x)+5, int(y)-5), cv2.FONT_HERSHEY_SIMPLEX, 0.5, (0,255,255), 1)

        # 3. Text Rendering (PIL) - OPTIMIZED: Only convert the header area
        # Crop header
        header_roi = frame[0:header_h, 0:w]
        
        # Convert header to PIL
        img_pil = Image.fromarray(cv2.cvtColor(header_roi, cv2.COLOR_BGR2RGB))
        
        # Determine Color
        text_col = (255, 0, 0) if danger else (0, 255, 0) # RGB Red or Green
        
        # Line 1: Status (Large)
        self.put_chinese_text(img_pil, pose_text, (10, 5), text_col, self.font_large)
        
        # Line 2: Pose Type (Medium, Yellow)
        self.put_chinese_text(img_pil, f"姿态: {pose_type}", (10, 45), (255, 255, 0), self.font_med)
        
        # Line 3: Info (Small, Gray)
        info_str = f"V3.0 CN | FPS:{fps:.1f} | 动:{motion:.1f}"
        self.put_chinese_text(img_pil, info_str, (10, 75), (200, 200, 200), self.font_small)
        
        # Convert back to OpenCV and paste
        header_bgr = cv2.cvtColor(np.array(img_pil), cv2.COLOR_RGB2BGR)
        frame[0:header_h, 0:w] = header_bgr
        
        return frame

    def run_detection_loop(self):
        global output_frame
        logger.info("Opening RTSP stream (Threaded V2.4 NHWC)...")
        cap = ThreadedVideoCapture(RTSP_INPUT)
        
        prev_time = time.time()
        fps_start = time.time()
        frame_count = 0
        fps = 0
        
        # Hysteresis Config
        self.danger_counter = 0
        DANGER_TRIGGER_FRAMES = 2   # Fast trigger
        DANGER_RELEASE_FRAMES = 30  # ~1.0 sec hold (Reduced from 45)
        
        current_pose_text = "Normal"
        current_pose_type = "Unknown"
        is_danger_active = False
        
        # 15s Alert Logic
        self.alert_start_time = 0
        self.last_danger_time = 0
        self.alert_msg = ""

        while True:
            ret, frame = cap.read()
            if not ret or frame is None:
                time.sleep(0.01)
                continue
                
            # Frame Skipping Logic
            INFERENCE_INTERVAL = 3  # Run inference every 3rd frame (Boost FPS)
            perform_inference = (frame_count % INFERENCE_INTERVAL == 0)

            if perform_inference:
                # Inference
                img, scale, dx, dy = self.preprocess(frame)
                outputs = self.rknn.inference(inputs=[img], data_format='nhwc')
                boxes, scores, kpts_list = self.postprocess(outputs, scale, dx, dy)
                
                # Analyze Frame
                frame_danger = False
                frame_pose_text = "Normal"
                frame_pose_type = "Unknown"
                motion = 0
                rad_x = 100
                rad_y = 100
                
                if len(boxes) > 0:
                    frame_danger, frame_pose_text, frame_pose_type, rad_x, rad_y = self.analyze_pose(kpts_list[0])
                    motion = self.calculate_motion(kpts_list[0])
                    
                    if motion > 20: 
                        self.last_motion_time = time.time()
                    elif (time.time() - self.last_motion_time) > 300:
                        frame_danger = True
                        frame_pose_text = "危险: 长时间静止"
                else:
                     self.last_motion_time = time.time()
                
                # Cache results for skipped frames
                self.cached_results = (boxes, kpts_list, frame_danger, frame_pose_text, frame_pose_type, motion, rad_x, rad_y)
            
            else:
                # Use cached results
                if hasattr(self, 'cached_results'):
                    boxes, kpts_list, frame_danger, frame_pose_text, frame_pose_type, motion, rad_x, rad_y = self.cached_results
                else:
                    boxes, kpts_list = [], []
                    motion, rad_x, rad_y = 0, 100, 100
            
            # --- Hysteresis Logic ---
            if frame_danger:
                self.danger_counter += 1
            else:
                self.danger_counter -= 1
            
            # Clamp counter
            self.danger_counter = max(0, min(self.danger_counter, DANGER_RELEASE_FRAMES + 10))
            
            # Trigger State
            if self.danger_counter >= DANGER_TRIGGER_FRAMES:
                is_danger_active = True
                if frame_danger: 
                    current_pose_text = frame_pose_text
            elif self.danger_counter == 0:
                is_danger_active = False
                current_pose_text = frame_pose_text
                current_pose_type = frame_pose_type
            
            if is_danger_active:
                final_text = current_pose_text if "危险" in current_pose_text else "危险: 警报消除中..."
            else:
                final_text = frame_pose_text
                current_pose_type = frame_pose_type

            # FPS
            curr_time = time.time()
            fps = 1 / (curr_time - prev_time) if curr_time > prev_time else 0
            prev_time = curr_time
            
            # --- 15s Alert Logic ---
            # Trigger on ANY danger state (not just Face Down/Covering)
            current_danger_reason = ""
            if is_danger_active:
                # Get the current danger text directly
                if "趴" in final_text or "Face Down" in current_pose_type:
                    current_danger_reason = "宝宝趴睡 (Face Down)"
                elif "手" in final_text or "遮" in final_text or "异物" in final_text:
                    current_danger_reason = "口鼻遮挡 (Covering)"
                elif "静止" in final_text:
                    current_danger_reason = "长时间静止"
                else:
                    current_danger_reason = final_text  # Use whatever danger text is shown
            
            if current_danger_reason != "":
                self.last_danger_time = time.time() # Update last seen time
                
                if self.alert_start_time == 0:
                    self.alert_start_time = time.time()
                    logger.info(f"ALERT TIMER STARTED: {current_danger_reason}")
                elif (time.time() - self.alert_start_time) > 2:  # 2秒触发
                    self.alert_msg = current_danger_reason
                    logger.info(f"ALERT TRIGGERED after 5s: {self.alert_msg}")
                    logger.info(f"ALERT TRIGGERED! {self.alert_msg}")
                else:
                    elapsed = time.time() - self.alert_start_time
                    if frame_count % 30 == 0: # Log every ~1s (30fps)
                        logger.info(f"Alert Timer: {elapsed:.1f}s / 15s ({current_danger_reason})")
            
            # Debug Logic: Print status every 3s
            if frame_count % 90 == 0:
                logger.info(f"STATUS: Danger={is_danger_active}, Pose={current_pose_type}, Text={final_text}, Timer={self.alert_start_time > 0}, AlertMsg='{self.alert_msg}'")
            

            # Clear alert only if safe for > 10 seconds AND not a forced test
            if current_danger_reason == "" and self.alert_msg != "" and self.last_danger_time > 0:
                if not self.alert_msg.startswith("[TEST]") and (time.time() - self.last_danger_time) > 10.0:
                    logger.info("Alert cleared after 10s of no danger")
                    self.alert_msg = ""
                    self.alert_start_time = 0

            # Draw
            frame = self.draw(frame, boxes, kpts_list, fps, final_text, current_pose_type, is_danger_active, motion, rad_x, rad_y)
            with lock: output_frame = frame.copy()
            
            if self.mqtt_client and frame_count % 5 == 0:  # 每5帧发一次，平衡速度和稳定性
                try:
                    s = {
                        "person": len(boxes)>0, 
                        "pose_text": final_text, 
                        "pose_type": current_pose_type, 
                        "danger": is_danger_active, 
                        "alert_15s": self.alert_msg, 
                        "motion": motion, 
                        "fps": round(fps,1)
                    }
                    result = self.mqtt_client.publish(MQTT_TOPIC_STATUS, json.dumps(s), qos=0)  # QoS 0更快
                    # Log every publish that has an alert
                    if self.alert_msg != "":
                        logger.info(f"MQTT ALERT PUBLISH: alert_15s='{self.alert_msg}'")
                    if frame_count % 100 == 0:  # Log every ~3 sec
                        logger.info(f"MQTT Published: rc={result.rc}, mid={result.mid}, payload={json.dumps(s)[:200]}")
                    
                    if self.alert_msg != "" and (frame_count % 30 == 0):
                         self.mqtt_client.publish(MQTT_TOPIC_NOTIFY, json.dumps({"alert": f"严重警报: {self.alert_msg} 已持续15秒!"}))
                except: pass
            
            frame_count += 1

def generate():
    global output_frame
    while True:
        with lock:
            if output_frame is None: continue
            (flag, encodedImage) = cv2.imencode(".jpg", output_frame)
            if not flag: continue
        yield(b'--frame\r\n' b'Content-Type: image/jpeg\r\n\r\n' + bytearray(encodedImage) + b'\r\n')
        time.sleep(0.02)

@app.route("/stream")
def output():
    return Response(generate(), mimetype = "multipart/x-mixed-replace; boundary=frame")

if __name__ == "__main__":
    t = threading.Thread(target=BabyCamRKNN().run_detection_loop)
    t.daemon = True
    t.start()
    app.run(host="0.0.0.0", port=8088, debug=False, threaded=True)
