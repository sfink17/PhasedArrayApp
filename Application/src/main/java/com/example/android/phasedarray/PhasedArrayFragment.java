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

import android.app.ActionBar;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.AssetFileDescriptor;
import android.media.AudioManager;
import android.net.wifi.WifiManager;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.StrictMode;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.example.android.common.logger.Log;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;


/**
 * This fragment controls Bluetooth to communicate with other devices.
 */
public class PhasedArrayFragment extends Fragment implements WifiP2pManager.ConnectionInfoListener {

    private static final String TAG = "PhasedArrayFragment";

    // Intent request codes
    private static final int REQUEST_CONNECT_DEVICE = 1;
    private static final int REQUEST_ENABLE_BT = 3;

    // Layout Views
    private Button mPlayButton;
    private Button mSynchButton;
    private EditText mSteeringAngle;
    private Button mConfirmAngle;
    private Button mStopTone;
    private Button mToggleListen;

    /**
     * Name of the connected device
     */

    // Synchronization component for each device.


    private static Long mClientSentTime = null;
    private static Long mClientReceiveTime = null;
    private static Long mServerReceiveTime = null;
    private static Long mServerSentTime = null;
    private static Long mClockOffset = null;
    private static Long mLag = null;
    private static Double mAngle = 0.0;
    private static Long mPhaseDelay = null;


    /**
     * Member object for the chat services
     */
    private WiFiDirectService mWiFiDirectService = null;

    public WifiP2pManager mManager;
    private WifiP2pManager.Channel mChannel;
    private BroadcastReceiver mReceiver = null;
    private WifiManager mWiFi;
    private boolean isConnected = false;
    private final IntentFilter mIntentFilter = new IntentFilter();
    private int mDevices = 0;

    private long[] mLags = new long[10];
    private long[] mOffsets = new long[10];
    private int index = 0;
    private int iterations;

    int pingIndex = 0;


    public PhasedArrayFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mWiFi = (WifiManager) getActivity().getSystemService(Context.WIFI_SERVICE);

        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);


        setHasOptionsMenu(true);

        String samplerateString = null, buffersizeString = null;

        if (Build.VERSION.SDK_INT >= 17) {
            AudioManager audioManager = (AudioManager) getActivity().getSystemService(Context.AUDIO_SERVICE);
            samplerateString = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE);
            buffersizeString = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER);
        }
        if (samplerateString == null) samplerateString = "44100";
        if (buffersizeString == null) buffersizeString = "512";

        Toast.makeText(getActivity(), samplerateString, Toast.LENGTH_SHORT).show();
        Toast.makeText(getActivity(), buffersizeString, Toast.LENGTH_SHORT).show();

        AssetFileDescriptor afd = getResources().openRawResourceFd(R.raw.pure_tone3);
        int fileOffset = (int)afd.getStartOffset(), fileLength = (int)afd.getLength();
        try {
            afd.getParcelFileDescriptor().close();
        } catch (IOException e) {
            android.util.Log.d("", "Close error.");
        }
        // Arguments: path to the APK file, offset and length of the two resource files, sample rate, audio buffer size.
        SuperpoweredPlayer(Integer.parseInt(samplerateString), Integer.parseInt(buffersizeString), getActivity().getPackageResourcePath(), fileOffset, fileLength);


        mManager = (WifiP2pManager) getActivity().getSystemService(Context.WIFI_P2P_SERVICE);
        mChannel = mManager.initialize(getActivity(), getActivity().getMainLooper(), null);
        mReceiver = new WiFiDirectBroadcastReceiver(mManager, mChannel, this);

        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        mIntentFilter
                .addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        mIntentFilter
                .addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);
    }


    @Override
    public void onStart() {
        super.onStart();
        // If BT is not on, request that it be enabled.
        // setupChat() will then be called during onActivityResult

        if (mWiFiDirectService == null) {
            setupChat();
        }

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        disconnect();
        if (mWiFiDirectService != null) {
            mWiFiDirectService.stop();
        }



    }

    @Override
    public void onResume() {
        super.onResume();

        // Performing this check in onResume() covers the case in which BT was
        // not enabled during onStart(), so we were paused to enable it...
        // onResume() will be called when ACTION_REQUEST_ENABLE activity returns.
        if (mWiFiDirectService != null) {
            // Only if the state is STATE_NONE, do we know that we haven't started already
            if (mWiFiDirectService.getState() == WiFiDirectService.STATE_NONE) {
                // Start the Bluetooth chat services
                mWiFiDirectService.start();
            }
        }
        getActivity().registerReceiver(mReceiver, mIntentFilter);
    }

    @Override
    public void onPause() {
        super.onPause();
        getActivity().unregisterReceiver(mReceiver);
        if (mManager != null && mChannel != null)
            mManager.requestGroupInfo(mChannel, new WifiP2pManager.GroupInfoListener() {
                @Override
                public void onGroupInfoAvailable(WifiP2pGroup group) {
                    if (group != null && mManager != null && mChannel != null) {
                        mManager.removeGroup(mChannel, null);
                    }
                }
            });
    }

    @Override
    public void onConnectionInfoAvailable(final WifiP2pInfo p2pInfo) {
        /*
         * The group owner accepts connections using a server socket and then spawns a
         * client socket for every client. This is handled by {@code
         * GroupOwnerSocketHandler}
         */
        if (p2pInfo.isGroupOwner && p2pInfo.groupFormed) {
            Log.d(TAG, "Connected as group owner");
            mWiFiDirectService.connectedServer(p2pInfo.groupOwnerAddress.getHostAddress());
            isConnected = true;
        } else if (p2pInfo.groupFormed) {
            Log.d(TAG, "Connected as peer");
            mWiFiDirectService.connected(p2pInfo.groupOwnerAddress);
            isConnected = true;
        }
        else if (isConnected){
            mWiFiDirectService.connectionLost();
        }
    }

    public  void disconnect() {
        mWiFi.disconnect();
        mWiFi.reconnect();
    }

    public void setIsWifiP2pEnabled(Boolean isEnabled){
        if (!isEnabled){
            Toast.makeText(getActivity(), "Please enable WiFi Direct through your options menu",
                    Toast.LENGTH_SHORT).show();
        }
    }




    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_bluetooth_chat, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        mSteeringAngle = (EditText) view.findViewById(R.id.steering_angle);
        mConfirmAngle = (Button) view.findViewById(R.id.set_angle);
        mPlayButton = (Button) view.findViewById(R.id.button_send);
        mSynchButton = (Button) view.findViewById(R.id.button_synch);
        mStopTone = (Button) view.findViewById(R.id.button_stop);
        mToggleListen = (Button) view.findViewById(R.id.done_listening);
    }

    /**
     * Set up the UI and background operations for chat.
     */
    private void setupChat() {
        Log.d(TAG, "setupChat()");


        // Initialize the send button with a listener that for click events
        mPlayButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // Send a message using content of the edit text widget
                View view = getView();
                if (null != view) {
                    //sendPlayCommand();
                }
            }
        });

        mConfirmAngle.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // Send a message using content of the edit text widget
                View view = getView();
                if (null != view) {
                    TextView textView = (TextView) view.findViewById(R.id.steering_angle);
                    try {
                        double angle = Double.parseDouble(textView.getText().toString());
                        if (0 <= angle && angle <= 180) {
                            mAngle = angle;
                            mWiFiDirectService.saveAngle(mAngle, true);
                        } else {
                            Toast.makeText(getActivity(), "Angle cannot point behind array",
                                    Toast.LENGTH_SHORT).show();
                        }
                    } catch (NumberFormatException e) {
                        Toast.makeText(getActivity(), "Please input integers",
                                Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });


        mSynchButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // Send a message using content of the edit text widget
                View view = getView();
                if (null != view) {
                    sendSynchCommand();
                }
            }
        });

        mToggleListen.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                View view = getView();
                if (null != view) {
                    mManager.stopPeerDiscovery(mChannel, null);
                }
            }
        });

        mStopTone.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // Send a message using content of the edit text widget
                View view = getView();
                if (null != view) {
                    byte[] send = "stop".getBytes();
                    mWiFiDirectService.write(send);
                }
            }
        });



        // Initialize the WiFiDirectService to perform bluetooth connections
        mWiFiDirectService = new WiFiDirectService(getActivity(), mHandler, mManager,
                mChannel, (WiFiDirectBroadcastReceiver) mReceiver);

    }



    private void sendSynchCommand() {
        // Check that we're actually connected before trying anything
        if (mWiFiDirectService.getState() != WiFiDirectService.STATE_CONNECTED_SERVER) {
            Toast.makeText(getActivity(), R.string.not_connected, Toast.LENGTH_SHORT).show();
        }
            
        if (mWiFiDirectService != null) {
            mWiFiDirectService.writeSynch();
        }

    }

    private void sendPlayCommand() {
        //Check that we're actually connected before trying anything
        if (mWiFiDirectService.getState() != WiFiDirectService.STATE_CONNECTED_SERVER) {
            Toast.makeText(getActivity(), R.string.not_connected, Toast.LENGTH_SHORT).show();
            return;
        }



        mWiFiDirectService.writeLaglessServer(("play" + ((Long) System.nanoTime()).toString()).getBytes());
    }


    /**
     * Updates the status on the action bar.
     *
     * @param resId a string resource ID
     */
    private void setStatus(int resId) {
        FragmentActivity activity = getActivity();
        if (null == activity) {
            return;
        }
        final ActionBar actionBar = activity.getActionBar();
        if (null == actionBar) {
            return;
        }
        actionBar.setSubtitle(resId);
    }

    /**
     * Updates the status on the action bar.
     *
     * @param subTitle status
     */
    private void setStatus(CharSequence subTitle) {
        FragmentActivity activity = getActivity();
        if (null == activity) {
            return;
        }
        final ActionBar actionBar = activity.getActionBar();
        if (null == actionBar) {
            return;
        }
        actionBar.setSubtitle(subTitle);
    }

    private void lagTimer(){
        long startTime = System.nanoTime();
        while(true){
                if (System.nanoTime() - startTime >= 10000000){
                    if (mLags[index] != 0) {
                        index++;
                        if (index == 10) {
                            index = 0;
                            iterations += pingIndex;
                            Log.d(TAG, "Timer triggered " + ((Integer) iterations).toString() + " times");
                            break;
                        }
                        else {
                            iterations += pingIndex;
                            pingIndex = 0;
                            String ping = "pingpingy" + ((Integer) index).toString() + "X"
                                    + ((Integer) pingIndex).toString();
                            mWiFiDirectService.write(ping.getBytes());
                            lagTimer();
                            break;
                        }
                    }
                    else if (mLags[index] == 0){
                        pingIndex++;
                        String ping = "pingpingy" + ((Integer) index).toString() + "X"
                                + ((Integer) pingIndex).toString();
                        mWiFiDirectService.write(ping.getBytes());
                        lagTimer();
                        break;
                    }

                }
        }
    }

    /**
     * The Handler that gets information back from the WiFiDirectService
     */
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            FragmentActivity activity = getActivity();
            switch (msg.what) {
                case Constants.MESSAGE_STATE_CHANGE:
                    switch (msg.arg1) {
                        case WiFiDirectService.STATE_CONNECTED_SERVER:
                            break;
                        case WiFiDirectService.STATE_CONNECTED:
                            setStatus(getString(R.string.title_connected_to));
                            break;
                        case WiFiDirectService.STATE_CONNECTING:
                            setStatus(R.string.title_connecting);
                            break;
                        case WiFiDirectService.STATE_LISTEN:
                        case WiFiDirectService.STATE_NONE:
                            setStatus(R.string.title_not_connected);
                            break;
                    }
                    break;
                case Constants.MESSAGE_WRITE:
                    break;
                case Constants.MESSAGE_READ:
                    byte[] readBuf = (byte[]) msg.obj;
                    ByteBuffer mBuffer = ByteBuffer.wrap(readBuf, 0, msg.arg1);
                    CharBuffer cb = Charset.defaultCharset().decode(mBuffer);
                    // construct a string from the valid bytes in the buffer
                    String request = cb.toString();
                    if (request.equals("synch")){
                        // 'Ping' message should be 18 bytes, to match incoming message
                        String ping = "pingpingy" + ((Integer) index).toString() + "X"
                                + ((Integer) pingIndex).toString();
                        pingIndex++;
                        mWiFiDirectService.write(ping.getBytes());
                        lagTimer();
                    }
                    if (request.contains("X" + ((Integer) index).toString() + "X"
                            + ((Integer) pingIndex).toString())){
                        mServerReceiveTime = mBuffer.getLong(0);
                        mServerSentTime = mBuffer.getLong(8);
                        mLag = (mClientReceiveTime - mClientSentTime -
                                (mServerSentTime - mServerReceiveTime)) / 2;
                        mClockOffset = mServerReceiveTime - mClientSentTime - mLag;
                        mLags[index] = mLag;
                        mOffsets[index] = mClockOffset;
                        if (index == 9) {
                            Log.d(TAG, "Lag Times:");
                            for (int i = 0; i < 10; i++){
                                Log.d(TAG,((Long) mLags[i]).toString());
                            }
                            Log.d(TAG, "Offsets:");
                            for (int i = 0; i < 10; i++){
                                Log.d(TAG,((Long) mOffsets[i]).toString());
                            }
                        }
                        //Toast.makeText(activity, mClockOffset.toString(),
                        //        Toast.LENGTH_LONG).show();
                        /** Long[] playLag = new Long[100];
                         Toast.makeText(activity, "Testing",
                         Toast.LENGTH_SHORT).show();
                         mMediaPlayer.start();
                         for (int i = 0; i < playLag.length; i++)
                         {
                         long time1 = System.nanoTime();
                         mMediaPlayer.seekTo(0);
                         Long seekTime = System.nanoTime() - time1;
                         playLag[i] = seekTime;
                         SystemClock.sleep(80);
                         }
                         mMediaPlayer.pause();
                         mPlayerLag = null;
                         mPlayerLagResolution = null;
                         Toast.makeText(activity, "Done",
                         Toast.LENGTH_SHORT).show();
                         int maxClusterIndex = 0;
                         for (long i = 10000; i <= 100000; i += 10000){
                         for (Long lag : playLag){
                         int clusterIndex = 0;
                         for (Long compareLag : playLag){
                         long lagDif = lag - compareLag;
                         if (Math.abs(lagDif) <= i){
                         clusterIndex++;
                         }
                         }
                         if (clusterIndex > maxClusterIndex){
                         maxClusterIndex = clusterIndex;
                         mPlayerLag = lag;
                         }
                         }
                         if (maxClusterIndex > 25){
                         mPlayerLagResolution = i;
                         break;
                         }
                         }
                         if (null != mPlayerLagResolution){
                         byte[] buffer = mPlayerLagResolution.toString().getBytes();
                         mWiFiDirectService.writeLagless(buffer);
                         Toast.makeText(activity, mPlayerLagResolution.toString(),
                         Toast.LENGTH_SHORT).show();
                         }
                         else{
                         mWiFiDirectService.writeLagless("fail".getBytes());
                         }
                         */

                    }


                    if (request.contains("play")){
                        long pStamp = Long.parseLong(request.replace("play",""));
                        long playAt;
                        if (null!= mPhaseDelay) {
                            playAt = pStamp + 1000000000 - mClockOffset - mPhaseDelay;
                        }
                        else{
                            playAt = pStamp + 1000000000 - mClockOffset;
                        }
                        while (true) {
                            if (System.nanoTime() - playAt >= 0) {
                                onPlayPause(true);
                                break;
                            }

                        }
                    }

                    if (request.contains("phase")){
                        long delay = Long.parseLong(request.replace("phase", ""));
                        if (true) {
                            onPlayPause(false);
                            long stamp = System.nanoTime() + delay;
                            while (true){
                                if (System.nanoTime() >= stamp) {
                                    onPlayPause(true);
                                    break;
                                }

                            }
                        }
                        else {
                            mPhaseDelay = delay;
                        }

                    }

                    if (request.equals("stop")){
                        onPlayPause(false);
                    }

                    break;
                case Constants.MESSAGE_DEVICE_NAME:
                    // save the connected device's name
                    if (null != activity) {
                        Toast.makeText(activity, "Connected", Toast.LENGTH_SHORT).show();
                        setStatus("Connected as server");
                        mDevices++;
                    }
                    break;
                case Constants.MESSAGE_TOAST:
                    if (null != activity) {
                        Toast.makeText(activity, msg.getData().getString(Constants.TOAST),
                                Toast.LENGTH_SHORT).show();
                    }
                    break;
                case Constants.MESSAGE_DISCONNECTED:
                    if (null != activity) {
                        Toast.makeText(activity, "Lost connection from device",
                                Toast.LENGTH_SHORT).show();
                        mDevices--;
                    }
                case Constants.TIMESTAMP:
                    if (null != activity) {
                        if (msg.arg1 == 0) {
                            mClientSentTime = (Long) msg.obj;
                        }
                        else mClientReceiveTime = (Long) msg.obj;

                    }
                    break;
            }
        }
    };

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_CONNECT_DEVICE:
                // When DeviceListActivity returns with a device to connect
                if (resultCode == Activity.RESULT_OK) {
                    connectDevice(data);
                }
                break;
            case REQUEST_ENABLE_BT:
                // When the request to enable Bluetooth returns
                if (resultCode == Activity.RESULT_OK) {
                    // Bluetooth is now enabled, so set up a chat session
                    setupChat();
                } else {
                    // User did not enable Bluetooth or an error occurred
                    Log.d(TAG, "BT not enabled");
                    Toast.makeText(getActivity(), R.string.bt_not_enabled_leaving,
                            Toast.LENGTH_SHORT).show();
                    getActivity().finish();
                }
        }
    }

    /**
     * Establish connection with other divice
     *
     * @param data   An {@link Intent} with {@link DeviceListActivity#EXTRA_DEVICE_ADDRESS} extra.
     */
    private void connectDevice(Intent data) {
        // Get the device MAC address
        String address = data.getExtras()
                .getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
        // Get the BluetoothDevice object
        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = address;
        config.wps.setup = WpsInfo.PBC;
        // Attempt to connect to the device
        mWiFiDirectService.connect(mChannel, config, config.deviceAddress);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.bluetooth_chat, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.secure_connect_scan: {
                // Launch the DeviceListActivity to see devices and do scan
                Intent serverIntent = new Intent(getActivity(), DeviceListActivity.class);
                startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
                return true;
            }

        }
        return false;
    }
    private native void SuperpoweredPlayer(int samplerate, int buffersize, String apkPath, int fileOffset, int fileLength);
    private native void onPlayPause(boolean play);

    static {
        System.loadLibrary("jniNativeAudio");
    }



}
