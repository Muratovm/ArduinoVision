package com.michaelmuratov.arduinovision.UART;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.os.Handler;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import com.michaelmuratov.arduinovision.MyAdapter;
import com.michaelmuratov.arduinovision.R;

import java.util.ArrayList;
import java.util.List;

public class DeviceScan {
    private BluetoothAdapter mBluetoothAdapter;
    private Handler mHandler;
    private Activity activity;
    private static final long SCAN_PERIOD = 10000; //scanning for 10 seconds
    public boolean mScanning;
    private Button btnConnectDisconnect;
    private List<BluetoothDevice> deviceList;


    private RecyclerView.Adapter mAdapter;

    public DeviceScan(Activity activity){
        mHandler = new Handler();
        this.activity = activity;
        mScanning = true;
        deviceList = new ArrayList<>();
        btnConnectDisconnect= activity.findViewById(R.id.btn_select);

        // Initializes a Bluetooth adapter.  For API level 18 and above, get a reference to
        // BluetoothAdapter through BluetoothManager.
        final BluetoothManager bluetoothManager =
                (BluetoothManager) activity.getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        // Checks if Bluetooth is supported on the device.
        if (mBluetoothAdapter == null) {
            Toast.makeText(activity, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            activity.finish();
            return;
        }

        RecyclerView recyclerView = activity.findViewById(R.id.recyclerview);
        recyclerView.setHasFixedSize(true);

        // use a linear layout manager
        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(activity);
        recyclerView.setLayoutManager(layoutManager);

        // specify an adapter (see also next example)

        mAdapter = new MyAdapter(activity,deviceList);
        recyclerView.setAdapter(mAdapter);
        recyclerView.setLayoutManager(new LinearLayoutManager(activity));

        scanLeDevice(true);
    }

    private BluetoothAdapter.LeScanCallback mLeScanCallback =
            new BluetoothAdapter.LeScanCallback() {
                @Override
                public void onLeScan(final BluetoothDevice device, final int rssi, byte[] scanRecord) {
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            addDevice(device);
                        }
                    });
                }
            };

    public void scanLeDevice(final boolean enable) {
        btnConnectDisconnect.setText(activity.getString(R.string.stop_scanning));
        if (enable) {
            // Stops scanning after a pre-defined scan period.
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mScanning = false;
                    btnConnectDisconnect.setText(activity.getString(R.string.start_scanning));
                    mBluetoothAdapter.stopLeScan(mLeScanCallback);

                }
            }, SCAN_PERIOD);

            mScanning = true;
            Log.d("SCAN","START");
            boolean result = mBluetoothAdapter.startLeScan(mLeScanCallback);
            Log.d("BEGAN SCAN",result+"");
            Log.d("SCAN","START");
        } else {
            mScanning = false;
            btnConnectDisconnect.setText(activity.getString(R.string.start_scanning));
            mBluetoothAdapter.stopLeScan(mLeScanCallback);
        }

    }

    private void addDevice(BluetoothDevice device) {
        boolean deviceFound = false;
        for (BluetoothDevice listDev : deviceList) {
            if (listDev.getAddress().equals(device.getAddress())) {
                deviceFound = true;
                break;
            }
        }
        if (!deviceFound && device.getName() != null) {
            deviceList.add(device);
            mAdapter.notifyDataSetChanged();
        }
    }
}
