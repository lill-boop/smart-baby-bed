#!/usr/bin/env python3
import cv2
import numpy as np

# Load model
net = cv2.dnn.readNetFromONNX('/var/log.hdd/babycam/ai/rknn_model_zoo/examples/yolov8/model/yolov8n.onnx')

# Read a frame
cap = cv2.VideoCapture('rtsp://127.0.0.1:8554/babycam')
for _ in range(5): cap.grab()
ret, frame = cap.read()
cap.release()

if ret:
    h, w = frame.shape[:2]
    print(f'Frame: {w}x{h}')
    
    # Preprocess
    blob = cv2.dnn.blobFromImage(frame, 1/255.0, (640, 640), swapRB=True, crop=False)
    net.setInput(blob)
    outputs = net.forward()
    
    print(f'Output shape: {outputs.shape}')
    
    # YOLOv8 output is [1, 84, 8400]
    out = outputs[0]  # [84, 8400]
    out = out.T  # [8400, 84]
    
    print(f'Transposed shape: {out.shape}')
    
    # First 4 values are cx, cy, w, h
    # Remaining 80 values are class scores
    scores = out[:, 4:]
    print(f'Scores shape: {scores.shape}')
    print(f'Max score: {scores.max():.4f}')
    
    # Find detections > 0.25
    max_scores = scores.max(axis=1)
    valid = max_scores > 0.25
    print(f'Detections > 0.25: {valid.sum()}')
    
    if valid.sum() > 0:
        indices = np.where(valid)[0]
        for i in indices[:5]:
            class_id = scores[i].argmax()
            conf = scores[i, class_id]
            cx, cy, bw, bh = out[i, :4]
            print(f'  Detection: class={class_id}, conf={conf:.3f}, box=({cx:.0f},{cy:.0f},{bw:.0f},{bh:.0f})')
else:
    print('Failed to read frame')
