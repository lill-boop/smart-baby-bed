#!/usr/bin/env python3
"""
BabyCam MJPEG Streamer
Reads the AI processed frame and serves it as an MJPEG stream
"""
import time
import os
from flask import Flask, Response

app = Flask(__name__)
FRAME_PATH = "/var/log.hdd/babycam/ai_frame.jpg"

def generate_frames():
    last_modified = 0
    while True:
        try:
            # Wait for file update
            if os.path.exists(FRAME_PATH):
                mod_time = os.path.getmtime(FRAME_PATH)
                if mod_time > last_modified:
                    last_modified = mod_time
                    with open(FRAME_PATH, "rb") as f:
                        frame_data = f.read()
                    
                    yield (b'--frame\r\n'
                           b'Content-Type: image/jpeg\r\n\r\n' + frame_data + b'\r\n')
                else:
                    time.sleep(0.01) # 100fps poll rate
            else:
                time.sleep(0.1)
        except Exception as e:
            time.sleep(0.1)

@app.route('/stream')
def video_feed():
    return Response(generate_frames(),
                    mimetype='multipart/x-mixed-replace; boundary=frame')

@app.route('/')
def index():
    return "BabyCam AI Stream <br><img src='/stream' style='width:100%'>"

if __name__ == '__main__':
    # Run on port 8088
    app.run(host='0.0.0.0', port=8088, threaded=True)
