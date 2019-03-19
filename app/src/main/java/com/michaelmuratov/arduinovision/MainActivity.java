package com.michaelmuratov.arduinovision;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.support.constraint.ConstraintLayout;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.michaelmuratov.arduinovision.UART.DeviceScan;
import com.michaelmuratov.arduinovision.Util.Permissions;
import com.michaelmuratov.arduinovision.Util.Toolbox;

public class MainActivity extends Activity{
    public static final String TAG = "nRFUART";
    private static final int REQUEST_ENABLE_BT = 2;
    private BluetoothAdapter mBtAdapter = null;
    DeviceScan scan;
    Activity activity;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        this.activity = this;
        Permissions permissions = new Permissions(this);
        permissions.askForLocation();

        mBtAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBtAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        Button btnConnectDisconnect = findViewById(R.id.btn_select);
        ConstraintLayout blu_title = findViewById(R.id.bluetooth_titlebar);
        scan = new DeviceScan(this);

        // Handle Disconnect & Connect button
        btnConnectDisconnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if(scan.mScanning){
                    scan.scanLeDevice(false);
                }
                else {
                    //Disconnect button pressed
                    scan.scanLeDevice(true);
                }
            }

        });

        blu_title.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                Intent intent = new Intent(activity, VisionActivity.class);
                intent.putExtra("device address", "");
                startActivity(intent);
                finish();
            }
        });
        // Set initial UI state

    }

    @Override
    public void onDestroy() {
    	 super.onDestroy();
         scan.scanLeDevice(false);
    }

    @Override
    public void onResume() {
        super.onResume();
        Toolbox.activiateFullscreen(this);
        Log.d(TAG, "onResume");
        if (!mBtAdapter.isEnabled()) {
            Log.i(TAG, "onResume - BT not enabled yet");
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
            finish();
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
        case REQUEST_ENABLE_BT:
            // When the request to enable Bluetooth returns
            if (resultCode == Activity.RESULT_OK) {
                Toast.makeText(this, "Bluetooth has turned on ", Toast.LENGTH_SHORT).show();

            } else {
                // User did not enable Bluetooth or an error occurred
                Log.d(TAG, "BT not enabled");
                Toast.makeText(this, "Problem in BT Turning ON ", Toast.LENGTH_SHORT).show();
                finish();
            }
            break;
        default:
            Log.e(TAG, "wrong request code");
            break;
        }
    }
}
