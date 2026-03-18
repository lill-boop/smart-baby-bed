import socket
import threading
import time
import logging

# Configuration
BIND_IP = "0.0.0.0"
APP_PORT_RX = 12348     # Listen for App audio (App -> Relay)
ESP_PORT_RX = 12347     # Listen for ESP audio (ESP -> Relay)

# Targets
ESP_TARGET_IP = "192.168.0.161"
ESP_TARGET_PORT = 12346 # ESP listening port

# App return port (where App listens for audio)
APP_TARGET_PORT = 12347 

logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(message)s')

# Global state
app_address = None
address_lock = threading.Lock()

def handle_app_to_esp():
    """Receives audio from App and forwards to ESP32"""
    global app_address
    
    rx_sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    rx_sock.bind((BIND_IP, APP_PORT_RX))
    logging.info(f"Listening for App audio (Upstream) on {APP_PORT_RX}")
    
    tx_sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)

    while True:
        try:
            data, addr = rx_sock.recvfrom(2048)
            
            # Learn App address dynamically
            with address_lock:
                if app_address is None or app_address[0] != addr[0]:
                    logging.info(f"APP CONNECTED: {addr}")
                    app_address = addr
            
            # Forward to ESP32
            tx_sock.sendto(data, (ESP_TARGET_IP, ESP_TARGET_PORT))
                
        except Exception as e:
            logging.error(f"App->ESP Error: {e}")

def handle_esp_to_app():
    """Receives mic audio from ESP32 and forwards to App"""
    global app_address
    
    rx_sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    rx_sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    rx_sock.bind((BIND_IP, ESP_PORT_RX))
    logging.info(f"Listening for ESP audio (Downstream) on {ESP_PORT_RX}")
    
    tx_sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)

    while True:
        try:
            data, addr = rx_sock.recvfrom(2048)
            # DEBUG: Confirm we are receiving audio from ESP32
            # only log every ~50 packets to avoid flood
            # if int(time.time() * 10) % 50 == 0: 
            #    logging.info(f"ESP RX: {len(data)} bytes from {addr}")
            
            # Forward to App if we know its address
            target = None
            with address_lock:
                target = app_address
            
            if target:
                # Forward to App IP AND Port (Dynamic return path)
                tx_sock.sendto(data, target)
            else:
                # logging.debug("Dropping ESP audio - No App connected")
                pass
                
        except Exception as e:
            logging.error(f"ESP->App Error: {e}")

if __name__ == "__main__":
    logging.info(f"=== BI-DIRECTIONAL INTERCOM RELAY ===")
    
    # Start threads
    t1 = threading.Thread(target=handle_app_to_esp)
    t2 = threading.Thread(target=handle_esp_to_app)
    
    t1.start()
    t2.start()
    
    t1.join()
    t2.join()
