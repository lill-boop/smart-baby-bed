package com.example.babybedapp;

import android.content.Context;
import android.util.Log;

import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class MqttManager {
    private static final String TAG = "MqttManager";
    private static final String BROKER_URL = "tcp://100.115.202.71:1883";
    private static final String CLIENT_ID_PREFIX = "app_android_01_";
    private static final String USERNAME = "esp8266_01";
    private static final String PASSWORD = "787970";

    private static final String TOPIC_SUBSCRIBE = "device/esp8266_01/status";
    private static final String TOPIC_AI_STATUS = "babycam/ai/status"; // AI Camera alerts

    private MqttAsyncClient mqttClient;
    private final Context context;
    private final Callback callback;
    private final String clientId;

    // Atomic flag to prevent multiple connection attempts
    private final AtomicBoolean isConnecting = new AtomicBoolean(false);

    public interface Callback {
        void onConnected();

        void onDisconnected();

        void onMessageReceived(String topic, String message);

        void onLog(String message);
    }

    public MqttManager(Context context, Callback callback) {
        this.context = context;
        this.callback = callback;
        // Generate a random Client ID to avoid conflicts
        this.clientId = CLIENT_ID_PREFIX + UUID.randomUUID().toString().substring(0, 8);
        initMqtt();
    }

    private void initMqtt() {
        try {
            mqttClient = new MqttAsyncClient(BROKER_URL, clientId, new MemoryPersistence());
            mqttClient.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable cause) {
                    log("Connection lost: " + (cause != null ? cause.getMessage() : "Unknown"));
                    if (callback != null)
                        callback.onDisconnected();
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) throws Exception {
                    String payload = new String(message.getPayload());
                    // log("RX [" + topic + "]: " + payload); // Optional: verbose logging
                    if (callback != null)
                        callback.onMessageReceived(topic, payload);
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                    // Log handled in publish/subscribe callbacks
                }
            });
        } catch (MqttException e) {
            log("Init Error: " + e.getMessage());
        }
    }

    public void connect() {
        if (mqttClient == null)
            return;

        if (mqttClient.isConnected()) {
            log("Already connected.");
            if (callback != null)
                callback.onConnected(); // Notify UI anyway
            return;
        }

        // Prevent re-entrant connect calls
        if (isConnecting.get()) {
            log("Connection already in progress...");
            return;
        }

        isConnecting.set(true);
        MqttConnectOptions options = new MqttConnectOptions();
        options.setUserName(USERNAME);
        options.setPassword(PASSWORD.toCharArray());
        options.setAutomaticReconnect(false); // We handle reconnect manually
        options.setCleanSession(true);
        options.setConnectionTimeout(30); // Increased to 30s for slow VPN
        options.setKeepAliveInterval(20);

        try {
            log("Connecting to " + BROKER_URL + "...");
            mqttClient.connect(options, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    isConnecting.set(false);
                    log("Connected!");
                    subscribeToTopics();
                    if (callback != null)
                        callback.onConnected();
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    isConnecting.set(false);
                    log("Connect Failed: " + exception.getMessage());
                    if (callback != null)
                        callback.onDisconnected();
                }
            });
        } catch (MqttException e) {
            isConnecting.set(false);
            log("Connect Exception: " + e.getMessage());
        }
    }

    private void subscribeToTopics() {
        try {
            // Subscribe to ESP8266 status
            mqttClient.subscribe(TOPIC_SUBSCRIBE, 1, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    log("Subscribed to " + TOPIC_SUBSCRIBE + " (QoS 1)");
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    log("Subscribe Failed: " + exception.getMessage());
                }
            });

            // Subscribe to AI Camera status
            mqttClient.subscribe(TOPIC_AI_STATUS, 1, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    log("Subscribed to " + TOPIC_AI_STATUS + " (QoS 1)");
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    log("AI Subscribe Failed: " + exception.getMessage());
                }
            });
        } catch (MqttException e) {
            log("Subscribe Error: " + e.getMessage());
        }
    }

    public void publish(String topic, String payload) {
        try {
            if (mqttClient != null && mqttClient.isConnected()) {
                MqttMessage message = new MqttMessage(payload.getBytes());
                message.setQos(1); // QoS 1: At least once
                message.setRetained(false);
                mqttClient.publish(topic, message, null, new IMqttActionListener() {
                    @Override
                    public void onSuccess(IMqttToken asyncActionToken) {
                        log("TX Success: " + payload);
                    }

                    @Override
                    public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                        log("TX Failed: " + exception.getMessage());
                    }
                });

            } else {
                log("Cannot publish: Disconnected");
            }
        } catch (MqttException e) {
            log("Publish Error: " + e.getMessage());
        }
    }

    public void disconnect() {
        try {
            if (mqttClient != null && mqttClient.isConnected()) {
                mqttClient.disconnect();
                log("Disconnected.");
                if (callback != null)
                    callback.onDisconnected();
            }
        } catch (MqttException e) {
            log("Disconnect Error: " + e.getMessage());
        }
    }

    private void log(String message) {
        Log.d(TAG, message);
        if (callback != null)
            callback.onLog(message);
    }
}
