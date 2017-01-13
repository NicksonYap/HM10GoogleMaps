/*
 * Copyright (C) 2013 The Android Open Source Project
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

package me.nickson.HM10.googlemaps;

import android.app.Activity;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

//import android.support.v7.app.AppCompatActivity;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
//import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;


/**
 * For a given BLE device, this Activity provides the user interface to connect, display data,
 * and display GATT services and characteristics supported by the device.  The Activity
 * communicates with {@code BluetoothLeService}, which in turn interacts with the
 * Bluetooth LE API.
 */
//public class GoogleMapsActivity extends Activity {
//public class GoogleMapsActivity extends AppCompatActivity
public class GoogleMapsActivity extends Activity
        implements OnMapReadyCallback {
    private final static String TAG = GoogleMapsActivity.class.getSimpleName();

    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";
    public static final String EXTRAS_DEVICE_STATE = "DEVICE_STATE";
    public static final String EXTRAS_DEVICE_SERIAL = "DEVICE_SERIAL";
    private int[] RGBFrame = {0, 0, 0};
    private TextView isSerial;
    private TextView mConnectionState;
    //private CheckBox mAutoscrollCheckBox;
    private ScrollView mDataScroll;
    private TextView mDataField;

    private SeekBar mRed, mGreen, mBlue;
    private String mDeviceName;
    private String mDeviceAddress;
    //  private ExpandableListView mGattServicesList;
    private BluetoothLeService mBluetoothLeService;
    private boolean mConnected = false;

    private boolean mSerialSupported = false;

    private BluetoothGattCharacteristic characteristicTX;
    private BluetoothGattCharacteristic characteristicRX;

    private GoogleMap map;

    private String mReceivedString;

    public final static UUID HM_RX_TX =
            UUID.fromString(SampleGattAttributes.HM_RX_TX);

    private final String LIST_NAME = "NAME";
    private final String LIST_UUID = "UUID";

    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            // Automatically connects to the device upon successful start-up initialization.
            mBluetoothLeService.connect(mDeviceAddress);

        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };

    // Handles various events fired by the Service.
    // ACTION_GATT_CONNECTED: connected to a GATT server.
    // ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
    // ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
    // ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read
    //                        or notification operations.
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                mConnected = true;
                updateConnectionState(R.string.connected);
                invalidateOptionsMenu();
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;
                updateConnectionState(R.string.disconnected);
                invalidateOptionsMenu();
                Log.d(TAG, "RETRY CONNECTION");
                mBluetoothLeService.connect(mDeviceAddress); //retry conenction
                //clearUI();
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                // Show all the supported services and characteristics on the user interface.
                displayGattServices(mBluetoothLeService.getSupportedGattServices());
                if (mSerialSupported) {
                    Log.d(TAG, "SET RX MODE");
                    mBluetoothLeService.setCharacteristicNotification(characteristicRX, true); //automatic set to RX mode
                }

            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {

                displayData(intent.getStringExtra(mBluetoothLeService.EXTRA_DATA));
            }
        }
    };

    private void clearUI() {
        // mDataField.setText(R.string.no_data);
        mDataField.setText("");
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.google_maps);

        final Intent intent = getIntent();
        mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);

        // Sets up UI references.
        ((TextView) findViewById(R.id.device_address)).setText(mDeviceAddress);
        mConnectionState = (TextView) findViewById(R.id.connection_state);
        // is serial present?
        isSerial = (TextView) findViewById(R.id.isSerial);

        //mAutoscrollCheckBox = (CheckBox) findViewById(R.id.autoscroll_checkBox);
        mDataScroll = (ScrollView) findViewById(R.id.data_scroll);


        mDataField = (TextView) findViewById(R.id.data_value);


        //ref: http://codetheory.in/android-ontouchevent-ontouchlistener-motionevent-to-detect-common-gestures/
        // "Capturing Touch Events on a View"

        /*mDataField.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {

                // have same code as onTouchEvent() (for the Activity) above

                int action = event.getActionMasked();

                Log.d(TAG, "DF Touch" +String.valueOf(action));

                return true;
            }
        });*/


        mRed = (SeekBar) findViewById(R.id.seekRed);
        mGreen = (SeekBar) findViewById(R.id.seekGreen);
        mBlue = (SeekBar) findViewById(R.id.seekBlue);

        readSeek(mRed, 0);
        readSeek(mGreen, 1);
        readSeek(mBlue, 2);

        if (getActionBar() != null) { //if actionbar is there
            getActionBar().setTitle(mDeviceName);
            getActionBar().setDisplayHomeAsUpEnabled(true);
        }
        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (mBluetoothLeService != null) {
            final boolean result = mBluetoothLeService.connect(mDeviceAddress);
            Log.d(TAG, "Connect request result=" + result);
        }

        if (savedInstanceState != null) { //restore destroyed data
            mDataField.setText(savedInstanceState.getString("mDataField"));
            mDataScroll.post(new Runnable() {
                @Override
                public void run() {
                    mDataScroll.fullScroll(ScrollView.FOCUS_DOWN);
                }
            });
        }


        // Get the SupportMapFragment and request notification
        // when the map is ready to be used.
       /* SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);*/
        MapFragment mapFragment = (MapFragment) getFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (mBluetoothLeService != null) {
            final boolean result = mBluetoothLeService.connect(mDeviceAddress);
            Log.d(TAG, "Connect request result=" + result);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mBluetoothLeService != null) {
            unbindService(mServiceConnection);
            mBluetoothLeService = null;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.gatt_services, menu);


        menu.findItem(R.id.menu_json).setVisible(false); //hide own menu

        if (mConnected) {
            menu.findItem(R.id.menu_connect).setVisible(false);
            menu.findItem(R.id.menu_disconnect).setVisible(true);
        } else {
            menu.findItem(R.id.menu_connect).setVisible(true);
            menu.findItem(R.id.menu_disconnect).setVisible(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_text:
                if (mBluetoothLeService != null) {
                    unbindService(mServiceConnection);
                    mBluetoothLeService = null;
                }
                final Intent intent = new Intent(this, DeviceControlActivity.class);
                intent.putExtra(DeviceControlActivity.EXTRAS_DEVICE_NAME, mDeviceName);
                intent.putExtra(DeviceControlActivity.EXTRAS_DEVICE_ADDRESS, mDeviceAddress);
                startActivity(intent);
                finish();
                return true;
            case R.id.menu_connect:
                mBluetoothLeService.connect(mDeviceAddress);
                return true;
            case R.id.menu_disconnect:
                mBluetoothLeService.disconnect();
                return true;
            case android.R.id.home:
                onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void updateConnectionState(final int resourceId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mConnectionState.setText(resourceId);
            }
        });
    }

    private void displayData(String data) {

        if (data != null) {
            // mDataField.setText(data);
            //mDataField.append(data);
            mReceivedString += data;

            if (mReceivedString.contains("\n")) {

                boolean jsonDetected = false;
                JSONArray receivedJSON = new JSONArray();
                //ref: http://stackoverflow.com/a/10174938
                try {
                    JSONObject receivedJSONobj = new JSONObject(mReceivedString);
                    jsonDetected = true;
                    Log.d(TAG, "obj:" + receivedJSONobj.toString());

                    //ref: http://stackoverflow.com/a/16496238
                    receivedJSON.put(receivedJSONobj); //convert obj to array


                } catch (JSONException ex) {
                    // in case JSONArray is valid as well...
                    try {
                        receivedJSON = new JSONArray(mReceivedString);
                        jsonDetected = true;
                        Log.d(TAG, "arr:" + receivedJSON.toString());

                    } catch (JSONException ex1) {
                        Log.d(TAG, "Non-JSON string received");
                    }
                }
                if (jsonDetected) {
                    try {
                        //ref: http://stackoverflow.com/a/26442839
                        mDataField.setText(receivedJSON.toString(2));

                        JSONObject coordinateJSON = receivedJSON.getJSONObject(0);

                        if (coordinateJSON.has("lat") && coordinateJSON.has("lng")) {
                            Log.d(TAG, "lat:" + coordinateJSON.getDouble("lat"));
                            Log.d(TAG, "long:" + coordinateJSON.getDouble("lng"));

                            if (map != null) {
                                LatLng currentLoc = new LatLng(coordinateJSON.getDouble("lat"), coordinateJSON.getDouble("lng"));
                                map.addMarker(new MarkerOptions().position(currentLoc));
                                map.moveCamera(CameraUpdateFactory.newLatLng(currentLoc));
                            }

                        } else {
                            Log.d(TAG, "No coordinate JSON found");
                        }

                    } catch (JSONException ex) {
                        Log.d(TAG, "JSON error");
                    }
                }
                mReceivedString = "";
            }
            //mDataScroll.fullScroll(ScrollView.FOCUS_DOWN);

            /*if (mAutoscrollCheckBox.isChecked()) {
                mDataScroll.post(new Runnable() {
                    @Override
                    public void run() {
                        mDataScroll.fullScroll(ScrollView.FOCUS_DOWN);
                    }
                });
            }*/
        }
    }


    // Demonstrates how to iterate through the supported GATT Services/Characteristics.
    // In this sample, we populate the data structure that is bound to the ExpandableListView
    // on the UI.
    private void displayGattServices(List<BluetoothGattService> gattServices) {
        if (gattServices == null) return;
        String uuid = null;
        String unknownServiceString = getResources().getString(R.string.unknown_service);
        ArrayList<HashMap<String, String>> gattServiceData = new ArrayList<HashMap<String, String>>();


        // Loops through available GATT Services.
        for (BluetoothGattService gattService : gattServices) {
            HashMap<String, String> currentServiceData = new HashMap<String, String>();
            uuid = gattService.getUuid().toString();
            currentServiceData.put(
                    LIST_NAME, SampleGattAttributes.lookup(uuid, unknownServiceString));

            // If the service exists for HM 10 Serial, say so.
            if (SampleGattAttributes.lookup(uuid, unknownServiceString) == "HM 10 Serial") {
                isSerial.setText("Yes");
                mSerialSupported = true;
            } else {
                isSerial.setText("No");
                mSerialSupported = false;
            }
            currentServiceData.put(LIST_UUID, uuid);
            gattServiceData.add(currentServiceData);

            // get characteristic when UUID matches RX/TX UUID
            characteristicTX = gattService.getCharacteristic(BluetoothLeService.UUID_HM_RX_TX);
            characteristicRX = gattService.getCharacteristic(BluetoothLeService.UUID_HM_RX_TX);
        }

    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }

    private void readSeek(SeekBar seekBar, final int pos) {
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress,
                                          boolean fromUser) {
                RGBFrame[pos] = progress;
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // TODO Auto-generated method stub
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // TODO Auto-generated method stub
                makeChange();
            }
        });
    }

    // on change of bars write char
    private void makeChange() {
        String str = RGBFrame[0] + "," + RGBFrame[1] + "," + RGBFrame[2] + "\n";
        Log.d(TAG, "Sending result=" + str);
        final byte[] tx = str.getBytes();
        if (mConnected) {
            characteristicTX.setValue(tx);
            mBluetoothLeService.writeCharacteristic(characteristicTX);
            mBluetoothLeService.setCharacteristicNotification(characteristicRX, true);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("mDataField", mDataField.getText().toString());
    }


    /**
     * Manipulates the map when it's available.
     * The API invokes this callback when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user receives a prompt to install
     * Play services inside the SupportMapFragment. The API invokes this method after the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {

        map = googleMap;
        map.setMapType(GoogleMap.MAP_TYPE_HYBRID);
        CameraUpdateFactory.zoomTo(20.0f);
        /*
        // Add a marker in Sydney, Australia,
        // and move the map's camera to the same location.
        LatLng sydney = new LatLng(-33.852, 151.211);
        googleMap.addMarker(new MarkerOptions().position(sydney)
                .title("Marker in Sydney"));
        googleMap.moveCamera(CameraUpdateFactory.newLatLng(sydney));
        */
    }


}