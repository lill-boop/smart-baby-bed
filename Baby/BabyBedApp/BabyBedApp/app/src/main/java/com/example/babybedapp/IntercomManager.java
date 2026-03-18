package com.example.babybedapp;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.concurrent.atomic.AtomicBoolean;

public class IntercomManager {
    private static final String TAG = "IntercomManager";

    // Singleton instance
    private static IntercomManager INSTANCE;
    private static final Object LOCK = new Object();

    // Config
    private static final String RELAY_IP = "192.168.0.222"; // LAN IP for low latency
    private static final int PORT_SEND = 12348;
    private static final int PORT_LISTEN = 12347;

    // Audio Config (G.711 PCMA Standard) - UPGRADED TO 16kHz
    private static final int SAMPLE_RATE = 16000;
    private static final int CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO;
    private static final int CHANNEL_OUT = AudioFormat.CHANNEL_OUT_MONO;
    private static final int ENCODING = AudioFormat.ENCODING_PCM_16BIT;
    private static final int FRAME_SIZE = 640;

    private AudioRecord audioRecord;
    private AudioTrack audioTrack;
    private DatagramSocket socket;

    private final AtomicBoolean isTalking = new AtomicBoolean(false);
    private final AtomicBoolean isListening = new AtomicBoolean(false);

    private Thread txThread;
    private Thread rxThread;

    // Private constructor for singleton
    private IntercomManager() {
    }

    // Get singleton instance
    public static IntercomManager getInstance() {
        if (INSTANCE == null) {
            synchronized (LOCK) {
                if (INSTANCE == null) {
                    INSTANCE = new IntercomManager();
                }
            }
        }
        return INSTANCE;
    }

    public void init() {
        try {
            // Bind to ANY available port (let OS decide)
            // This avoids EADDRINUSE errors
            if (socket == null || socket.isClosed()) {
                socket = new DatagramSocket(0);
                Log.i(TAG, "Initialized UDP on port: " + socket.getLocalPort());
                startListen();
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to init socket", e);
        }
    }

    public void release() {
        stopTalk();
        stopListen();
        if (socket != null && !socket.isClosed()) {
            socket.close();
            socket = null;
        }
    }

    // ================== TX (Talk) ==================

    public void startTalk() {
        if (isTalking.get())
            return;
        isTalking.set(true);

        txThread = new Thread(this::txLoop, "Intercom-TX");
        txThread.start();
    }

    public void stopTalk() {
        isTalking.set(false);
        try {
            if (txThread != null)
                txThread.join(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        txThread = null;
    }

    private void txLoop() {
        Log.i(TAG, "Starting TX Loop");
        int minBufSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_IN, ENCODING);
        // VOICE_COMMUNICATION has noise cancellation (better for intercom)
        audioRecord = new AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE, CHANNEL_IN, ENCODING, minBufSize * 2);

        if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord init failed");
            return;
        }

        byte[] pcmBuffer = new byte[FRAME_SIZE * 2]; // 16-bit PCM = 2 bytes per sample
        byte[] g711Buffer = new byte[FRAME_SIZE]; // 8-bit G.711 = 1 byte per sample

        try {
            audioRecord.startRecording();
            InetAddress targetAddr = InetAddress.getByName(RELAY_IP);

            while (isTalking.get()) {
                int read = audioRecord.read(pcmBuffer, 0, pcmBuffer.length);
                if (read > 0) {
                    // Send RAW PCM directly (High Audio Quality for LAN)
                    // No G.711 compression artifacts
                    DatagramPacket packet = new DatagramPacket(pcmBuffer, read, targetAddr, PORT_SEND);
                    if (socket != null) {
                        socket.send(packet);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "TX Error", e);
        } finally {
            if (audioRecord != null) {
                audioRecord.stop();
                audioRecord.release();
                audioRecord = null;
            }
        }
        Log.i(TAG, "TX Loop Stopped");
    }

    // ================== RX (Listen) ==================

    public void startListen() {
        if (isListening.get())
            return;
        isListening.set(true);

        rxThread = new Thread(this::rxLoop, "Intercom-RX");
        rxThread.start();

        // Start Heartbeat Thread (to keep UDP hole open and register IP with Relay)
        new Thread(() -> {
            try {
                InetAddress targetAddr = InetAddress.getByName(RELAY_IP);
                byte[] hello = "HELLO".getBytes();
                DatagramPacket packet = new DatagramPacket(hello, hello.length, targetAddr, PORT_SEND);

                Log.i(TAG, "Starting Heartbeat: " + RELAY_IP + ":" + PORT_SEND);

                while (isListening.get()) {
                    if (socket != null && !socket.isClosed()) {
                        socket.send(packet);
                    }
                    Thread.sleep(2000); // Check-in every 2s
                }
            } catch (Exception e) {
                Log.e(TAG, "Heartbeat Error", e);
            }
        }, "Intercom-Heartbeat").start();
    }

    public void stopListen() {
        isListening.set(false);
        try {
            if (rxThread != null)
                rxThread.join(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        rxThread = null;
    }

    private void rxLoop() {
        Log.i(TAG, "Starting RX Loop");
        int minBufSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_OUT, ENCODING);
        // Larger buffer (4x) reduces dropouts from network jitter
        audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
                SAMPLE_RATE, CHANNEL_OUT, ENCODING,
                minBufSize * 4, AudioTrack.MODE_STREAM);

        if (audioTrack.getState() != AudioTrack.STATE_INITIALIZED) {
            Log.e(TAG, "AudioTrack init failed");
            return;
        }

        byte[] rxBuffer = new byte[FRAME_SIZE + 100];
        byte[] pcmBuffer = new byte[FRAME_SIZE * 2];

        try {
            socket.setSoTimeout(100); // 100ms timeout

            audioTrack.play();

            while (isListening.get()) {
                if (socket == null || socket.isClosed())
                    break;

                DatagramPacket packet = new DatagramPacket(pcmBuffer, pcmBuffer.length);
                try {
                    socket.receive(packet); // Will timeout after 100ms

                    int len = packet.getLength();
                    if (len > 0) {
                        // Write RAW PCM directly
                        audioTrack.write(packet.getData(), 0, len);
                    }
                } catch (java.net.SocketTimeoutException e) {
                    // Timeout is normal, just continue loop
                }
            }
        } catch (Exception e) {
            if (isListening.get()) { // Only log if not intentional stop
                Log.e(TAG, "RX Error", e);
            }
        } finally {
            if (audioTrack != null) {
                audioTrack.stop();
                audioTrack.release();
                audioTrack = null;
            }
        }
        Log.i(TAG, "RX Loop Stopped");
    }

    // Simple G.711 PCMA (A-law) Codec Helper
    public static class G711 {
        private static final int SIGN_BIT = 0x80;
        private static final int QUANT_MASK = 0xf;
        private static final int SEG_SHIFT = 4;
        private static final int SEG_MASK = 0x70;

        private static final short[] seg_end = {
                0xFF, 0x1FF, 0x3FF, 0x7FF, 0xFFF, 0x1FFF, 0x3FFF, 0x7FFF
        };

        public static void linear2alaw(byte[] pcm, int offset, byte[] alaw, int count) {
            for (int i = 0; i < count; i++) {
                short val = (short) ((pcm[offset + 2 * i] & 0xff) | (pcm[offset + 2 * i + 1] << 8));
                alaw[i] = linear2alaw(val);
            }
        }

        public static byte linear2alaw(short pcm_val) {
            int mask;
            int seg;
            int aval;

            if (pcm_val >= 0) {
                mask = 0xD5;
            } else {
                mask = 0x55;
                pcm_val = (short) (-pcm_val - 8);
            }

            aval = pcm_val;
            for (seg = 0; seg < 8; seg++) {
                if (aval <= seg_end[seg])
                    break;
            }

            if (seg >= 8)
                return (byte) (0x7F ^ mask);
            else {
                aval >>= (seg == 0) ? 4 : (seg + 3);
                return (byte) (((aval & QUANT_MASK) | (seg << SEG_SHIFT) | SIGN_BIT) ^ mask);
            }
        }

        public static void alaw2linear(byte[] alaw, int count, byte[] pcm) {
            for (int i = 0; i < count; i++) {
                short val = alaw2linear(alaw[i]);
                pcm[2 * i] = (byte) (val & 0xff);
                pcm[2 * i + 1] = (byte) ((val >> 8) & 0xff);
            }
        }

        public static short alaw2linear(byte alaw_val) {
            int t;
            int seg;

            alaw_val ^= 0x55;

            t = (alaw_val & QUANT_MASK) << 4;
            seg = ((int) alaw_val & SEG_MASK) >> SEG_SHIFT;
            switch (seg) {
                case 0:
                    t += 8;
                    break;
                case 1:
                    t += 0x108;
                    break;
                default:
                    t += 0x108;
                    t <<= (seg - 1);
            }
            return (short) ((alaw_val & SIGN_BIT) != 0 ? t : -t);
        }
    }
}
