#!/usr/bin/env python3
"""Convert YOLOv8n-pose ONNX to RKNN - correct normalization"""
import os
from rknn.api import RKNN

ONNX_MODEL = '/mnt/c/Users/54257/Desktop/Bab11/yolov8n-pose.onnx' # Already 320x320 from export
RKNN_MODEL = '/mnt/c/Users/54257/Desktop/Bab11/yolov8n-pose-320.rknn' # New name

print("Loading RKNN...")
rknn = RKNN(verbose=True)

# YOLOv8 expects input normalized to [0, 1] (divide by 255)
print("Configuring...")
rknn.config(
    mean_values=[[0, 0, 0]],
    std_values=[[255, 255, 255]], 
    target_platform='rk3588',
    optimization_level=3
)

print(f"Loading ONNX: {ONNX_MODEL}")
ret = rknn.load_onnx(model=ONNX_MODEL)
if ret != 0:
    print(f"Load failed: {ret}")
    exit(1)

print("Building RKNN (FP16)...")
ret = rknn.build(do_quantization=False)
if ret != 0:
    print(f"Build failed: {ret}")
    exit(1)

print(f"Exporting: {RKNN_MODEL}")
ret = rknn.export_rknn(RKNN_MODEL)
if ret != 0:
    print(f"Export failed: {ret}")
    exit(1)

print(f"SUCCESS! Size: {os.path.getsize(RKNN_MODEL)/1024/1024:.2f} MB")
rknn.release()
