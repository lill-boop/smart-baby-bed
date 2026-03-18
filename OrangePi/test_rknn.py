#!/usr/bin/env python3
from rknnlite.api import RKNNLite
import cv2
import numpy as np

rknn = RKNNLite()
rknn.load_rknn('/var/log.hdd/babycam/ai/yolov8n-pose.rknn')
rknn.init_runtime()

# Get a frame
cap = cv2.VideoCapture('rtsp://127.0.0.1:8554/babycam')
for _ in range(5): cap.grab()
ret, frame = cap.read()
cap.release()

print(f'Frame shape: {frame.shape}')

# Preprocess - NCHW format, BUT KEEP UINT8 (0-255)
# RKNN will handle /255 normalization internally
img = cv2.resize(frame, (640, 640))
img = cv2.cvtColor(img, cv2.COLOR_BGR2RGB)
img = np.transpose(img, (2, 0, 1))  # HWC -> CHW
img = np.expand_dims(img, axis=0)   # CHW -> NCHW

print(f'Input shape: {img.shape}, dtype: {img.dtype}')
print(f'Input min/max: {img.min()} / {img.max()}')

# Inference
outputs = rknn.inference(inputs=[img], data_format='nchw')

output = outputs[0]
if len(output.shape) == 3: output = output[0]
output = output.T

# Check confidence scores
scores = output[:, 4]
print(f'Scores min/max: {scores.min():.4f} / {scores.max():.4f}')
print(f'Num > 0.5: {(scores > 0.5).sum()}')

# Top 5
top_indices = np.argsort(scores)[-5:][::-1]
print(f'Top 5 scores: {scores[top_indices]}')

rknn.release()
