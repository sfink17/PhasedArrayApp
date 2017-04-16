/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.phasedarray;

import android.content.Context;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.SparseArray;

import com.example.android.common.logger.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.UUID;

public class WiFiDirectService {
    // Debugging
    private static final String TAG = "WiFiDirectService";

    // Name for the SDP record when creating server socket
    private static final String NAME_SECURE = "BluetoothChatSecure";
    private static final String NAME_INSECURE = "BluetoothChatInsecure";

    // Unique UUID for this application
    private static final UUID MY_UUID_SECURE =
            UUID.fromString("fa87c0d0-afac-11de-8a39-0800200c9a66");
    private static final UUID MY_UUID_INSECURE =
            UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66");

    // Member fields
    private final WifiP2pManager mManager;
    private final WifiP2pManager.Channel mChannel;
    private final WiFiDirectBroadcastReceiver mReceiver;
    private final Handler mHandler;
    private ConnectThread mConnectThread;
    private ConnectedThread mConnectedThread;
    private ConnectedServerThread[] mConnectedServerThread = new ConnectedServerThread[5];
    private InputStream[] mInStream = new InputStream[5];
    private OutputStream[] mOutStream = new OutputStream[5];
    private int mStreams;
    private int mState;

    private InputStream mInputStream;
    private OutputStream mOutputStream;


    private String mConnectedDeviceName = null;
    private SparseArray<String> mConnectedDevices = new SparseArray<>();
    private static Integer index = 0;
    private static final double mTimeDelay = 1000000;
    private static double mPhaseDelay;

    // Constants that indicate the current connection state
    public static final int STATE_NONE = 0;       // we're doing nothing
    public static final int STATE_LISTEN = 1;     // now listening for incoming connections
    public static final int STATE_CONNECTING = 2; // now initiating an outgoing connection
    public static final int STATE_CONNECTED = 3;  // now connected to a remote device
    public static final int STATE_CONNECTED_SERVER = 4;

    /**
     * Constructor. Prepares a new WiFiDirectService session.
     *
     * @param context The UI Activity Context
     * @param handler A Handler to send messages back to the UI Activity
     */
    public WiFiDirectService(Context context, Handler handler, WifiP2pManager manager,
                             WifiP2pManager.Channel channel, WiFiDirectBroadcastReceiver receiver) {
        mManager = manager;
        mChannel = channel;
        mState = STATE_NONE;
        mHandler = handler;
        mReceiver = receiver;





    }

    /**
     * Set the current state of the chat connection
     *
     * @param state An integer defining the current connection state
     */
    private synchronized void setState(int state) {
        Log.d(TAG, "setState() " + mState + " -> " + state);
        mState = state;

        // Give the new state to the Handler so the UI Activity can update
        mHandler.obtainMessage(Constants.MESSAGE_STATE_CHANGE, state, -1).sendToTarget();
    }

    /**
     * Return the current connection state.
     */
    public synchronized int getState() {
        return mState;
    }

    /**
     * Start the chat service. Specifically start AcceptThread to begin a
     * session in listening (server) mode. Called by the Activity onResume()
     */
    public synchronized void start() {
        Log.d(TAG, "start");

        // Cancel any thread attempting to make a connection
        if (mConnectThread != null) {
            mConnectThread = null;
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        setState(STATE_LISTEN);

    }

    /**
     * Start the ConnectThread to initiate a connection to a remote device.
     *
     */
    public synchronized void connect(WifiP2pManager.Channel channel, WifiP2pConfig config, String deviceAddress) {

        // Cancel any thread attempting to make a connection
        if (mState == STATE_CONNECTING) {
            if (mConnectThread != null) {
                mConnectThread = null;
            }
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        // Start the thread to connect with the given device
        mConnectThread = new ConnectThread(channel, config, deviceAddress);
        mConnectThread.start();
        setState(STATE_CONNECTING);
    }

    /**
     * Start the ConnectedThread to begin managing a Bluetooth connection
     *
     */
    public synchronized void connected(InetAddress address) {
        Log.d(TAG, "connected");

        // Cancel the thread that completed the connection
        if (mConnectThread != null) {
            mConnectThread = null;
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        mManager.stopPeerDiscovery(mChannel, null);

        // Start the thread to manage the connection and perform transmissions
        mConnectedThread = new ConnectedThread(address);
        mConnectedThread.start();

        setState(STATE_CONNECTED);
    }

    public synchronized void saveAngle(double angle, boolean isPlaying){
        mPhaseDelay = -(3/4)*mTimeDelay* Math.cos(Math.toRadians(angle));
        Long delay = (long) mPhaseDelay;
        writePhased(delay, isPlaying);
    }

    // Connects devices without closing accept thread, allows for more connections.

    public synchronized void connectedServer(String deviceAddress) {
        Log.d(TAG, "connected");

        // Cancel the thread that completed the connection
        if (mConnectThread != null) {
            mConnectThread = null;
        }


        // Send the name of the connected device back to the UI Activity
        mConnectedDeviceName = deviceAddress;
        if (mConnectedDevices.indexOfValue(mConnectedDeviceName) < 0) {
            mConnectedDevices.put(index, mConnectedDeviceName);
            index++;
        }


        // Start the thread to manage the connection and perform transmissions
        mHandler.obtainMessage(Constants.MESSAGE_DEVICE_NAME).sendToTarget();
        if (index < 2) {
            mConnectServerStreams();
            setState(STATE_CONNECTED_SERVER);
        }
        else if (index >= 2) {
            mConnectServerStreams();
        }
    }

    /**
     * Stop all threads
     */
    public synchronized void stop() {
        Log.d(TAG, "stop");

        if (mConnectThread != null) {
            mConnectThread = null;
        }

        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        setState(STATE_NONE);
    }

    /**
     * Write to the ConnectedThread in an unsynchronized manner
     *
     * @see ConnectedThread#write(byte[])
     */
    public void write(byte[] buffer) {
        try {
            long mSTime = System.nanoTime();
            mOutputStream.flush();
            mOutputStream.write(buffer);

            // Share the sent message back to the UI Activity
            mHandler.obtainMessage(Constants.TIMESTAMP, 0, -1, mSTime)
                    .sendToTarget();
        } catch (IOException e) {
            Log.e(TAG, "Exception during write", e);
        }
    }

    public void writePhased(long delay, boolean isPlaying) {
        for (int i = 0; i < mStreams; i++) {


            if (isPlaying) {
                if (delay > mPhaseDelay) {
                    Long pDelay = (delay - (long) mPhaseDelay);
                    byte[] out = ("phase" + pDelay.toString()).getBytes();
                    mConnectedServerThread[i].write(out);
                } else {
                    Long pDelay = ((long) mPhaseDelay - delay);
                    byte[] out = ("phase" + pDelay.toString()).getBytes();
                    mConnectedServerThread[i].write(out);
                }

            } else {
                Long pDelay = (long) mPhaseDelay;
                byte[] out = ("phase" + pDelay.toString()).getBytes();
                mConnectedServerThread[i].write(out);
            }
        }
    }


    public void writeLaglessServer(byte[] out) {
        for (int i = 0; i < mStreams; i++) {
            mConnectedServerThread[i].write(out);
        }
    }

    public void writeSynch() {
        // Create temporary object
        for (int i = 0; i < mStreams; i++) {
            mConnectedServerThread[i].write("synch".getBytes());
        }
    }

    /**
     * Indicate that the connection attempt failed and notify the UI Activity.
     */
    private void connectionFailed() {
        // Send a failure message back to the Activity
        Message msg = mHandler.obtainMessage(Constants.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(Constants.TOAST, "Unable to connect device");
        msg.setData(bundle);
        mHandler.sendMessage(msg);

        // Start the service over to restart listening mode
        WiFiDirectService.this.start();
    }

    /**
     * Indicate that the connection was lost and notify the UI Activity.
     */
    public void connectionLost() {
        // Send a failure message back to the Activity
        mHandler.obtainMessage(Constants.MESSAGE_DISCONNECTED).sendToTarget();

        // Start the service over to restart listening mode
        if (mState == STATE_CONNECTED_SERVER){
            index--;
            if (index == 0){
                WiFiDirectService.this.start();
            }
        }
        else {
            WiFiDirectService.this.start();
        }
    }


    /**
     * This thread runs while attempting to make an outgoing connection
     * with a device. It runs straight through; the connection either
     * succeeds or fails.
     */
    private class ConnectThread extends Thread {
        private WifiP2pManager.Channel mmChannel;
        private WifiP2pConfig mmConfig;
        private String mmAddress;

        public ConnectThread(WifiP2pManager.Channel channel, WifiP2pConfig config, String deviceAddress) {
            mmChannel = channel;
            mmConfig = config;
            mmAddress = deviceAddress;
        }

        public void run() {
            Log.i(TAG, "BEGIN mConnectThread");
            setName("ConnectThread");

            // Make a connection to the BluetoothSocket
            mManager.connect(mmChannel, mmConfig, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                }

                @Override
                public void onFailure(int reason) {
                    connectionFailed();
                }
            });

            // Reset the ConnectThread because we're done
            synchronized (WiFiDirectService.this) {
                mConnectThread = null;
            }
        }

        public void cancel() {

        }



    }


    /**
     * This thread runs during a connection with a remote device.
     * It handles all incoming and outgoing transmissions.
     */
    private class ConnectedThread extends Thread {
        private final Socket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;
        private InetAddress mAddress;

        public ConnectedThread(InetAddress serverAddress) {
            Log.d(TAG, "create ConnectedThread");
            mAddress = serverAddress;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;
            Socket socket = new Socket();

            // Get the BluetoothSocket input and output streams
            try {
                socket.bind(null);
                socket.connect(new InetSocketAddress(mAddress.getHostAddress(),
                        Constants.SERVER_PORT), 5000);
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();

                Log.d(MainActivity.TAG, "sockets created");
            } catch (IOException e) {
                Log.e(TAG, "temp sockets not created", e);
            }

            mmSocket = socket;
            mmInStream = tmpIn;
            mmOutStream = tmpOut;
            mInputStream = mmInStream;
            mOutputStream = mmOutStream;
        }

        public void run() {
            Log.i(TAG, "BEGIN mConnectedThread");

            byte[] buffer = new byte[32];
            int bytes;
            if (mState != STATE_CONNECTED) {
                setState(STATE_CONNECTED);
            }

            // Keep listening to the InputStream while connected
            while (mState == STATE_CONNECTED) {
                try {
                    // Read from the InputStream
                    bytes = mmInStream.read(buffer);
                    long mRTime = System.nanoTime();
                    // Send the obtained bytes to the UI Activity
                    synchronized (this) {
                        mHandler.obtainMessage(Constants.TIMESTAMP, 1, -1, mRTime)
                                .sendToTarget();
                        mHandler.obtainMessage(Constants.MESSAGE_READ, bytes, -1, buffer)
                                .sendToTarget();
                    }
                } catch (IOException e) {
                    Log.e(TAG, "disconnected", e);
                    connectionLost();
                    break;
                }
            }
        }




        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect socket failed", e);
            }
        }
    }

    private void mConnectServerStreams() {
        Log.d(TAG, "create ConnectedServerThread");
        InputStream tmpIn = null;
        OutputStream tmpOut = null;
        ServerSocket socket;
        Socket client;

        // Get the BluetoothSocket input and output streams
        try {
            socket = new ServerSocket(Constants.SERVER_PORT);
            client = socket.accept();
            tmpIn = client.getInputStream();
            tmpOut = client.getOutputStream();
        } catch (IOException e) {
            Log.e(TAG, "temp sockets not created", e);
        }

        mInStream[mStreams] = tmpIn;
        mOutStream[mStreams] = tmpOut;
        mConnectedServerThread[mStreams] = new ConnectedServerThread(mInStream[mStreams],
                mOutStream[mStreams]);
        mStreams++;

    }

    private class ConnectedServerThread extends Thread {
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedServerThread(InputStream input, OutputStream output) {
            Log.d(TAG, "create ConnectedServerThread");
            mmInStream = input;
            mmOutStream = output;

        }

        public void run() {
            Log.i(TAG, "BEGIN mConnectedThread");

            byte[] buffer = new byte[32];
            int bytes;

            // Keep listening to the InputStream while connected
            while (mState == STATE_CONNECTED_SERVER || mState == STATE_LISTEN) {
                try {
                    // Read from the InputStream
                    bytes = mmInStream.read(buffer);
                    long mRTime = System.nanoTime();
                    writeSynchStream(mmOutStream, mRTime, buffer, bytes);

                } catch (IOException e) {
                    Log.e(TAG, "disconnected", e);
                    connectionLost();
                    break;
                }
            }

        }

        /**
         * Write to the connected OutStream.
         *
         * @param buffer The bytes to write
         */
        public void write(byte[] buffer) {

                try {
                    mmOutStream.write(buffer);

                    // Share the sent message back to the UI Activity
                    mHandler.obtainMessage(Constants.MESSAGE_WRITE, -1, -1, buffer)
                            .sendToTarget();
                } catch (IOException e) {
                    Log.e(TAG, "Exception during write", e);
                }



        }


    }
    public void writeSynchStream(OutputStream output, long timestamp, byte[] buffer, int bytes) {

        ByteBuffer longBuffer = ByteBuffer.allocate(Long.SIZE / 4 + 2);
        longBuffer.putLong(timestamp);
        String ping = new String(buffer, 0, bytes);
        if (ping.contains("pingpingy")) {
            try {
                String[] split = ping.replace("pingpingy", "").split("X");
                String pString = "X" + split[0] + "X" + split[1];
                longBuffer.putLong(System.nanoTime());
                longBuffer.put(pString.getBytes());

                output.write(longBuffer.array());

                // Share the sent message back to the UI Activity
                mHandler.obtainMessage(Constants.MESSAGE_WRITE, -1, -1, buffer)
                        .sendToTarget();
            } catch (IOException e) {
                Log.e(TAG, "Exception during write", e);
            }

        }


    }





}



