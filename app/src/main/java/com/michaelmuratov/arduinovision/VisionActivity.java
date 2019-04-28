package com.michaelmuratov.arduinovision;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.michaelmuratov.arduinovision.UART.UARTListener;
import com.michaelmuratov.arduinovision.Util.Permissions;
import com.michaelmuratov.arduinovision.Util.Toolbox;

import org.json.JSONArray;
import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.LinkedList;

import static com.michaelmuratov.arduinovision.FileSelection.onBrowse;

public class VisionActivity extends AppCompatActivity implements CvCameraViewListener2 {
    private static final String  TAG              = "VisionActivity";

    /** The loaded TensorFlow Lite model. */

    Activity activity;
    private Mat                  mGray;
    private Mat                  mRgbaF;
    private Mat                  mRgbaT;
    ImageView image;
    TextView tvBins;
    TextView tvDirection;
    TextView tvDelay;

    Model model;

    LinkedList<int[]> float_bitmaps = new LinkedList<>();
    LinkedList<Long> timestamps = new LinkedList<>();

    boolean portrait = true;

    UARTListener uartListener;
    JSONArray myArray;

    private CameraBridgeViewBase mOpenCvCameraView;

    private BaseLoaderCallback  mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    Log.i(TAG, "OpenCV loaded successfully");
                    mOpenCvCameraView.enableView();
                } break;
                default:
                {
                    super.onManagerConnected(status);
                } break;
            }
        }
    };

    public VisionActivity() {
        Log.i(TAG, "Instantiated new " + this.getClass());
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "called onCreate");
        super.onCreate(savedInstanceState);
        this.activity = this;
        Intent intent = getIntent();
        String deviceAddress = intent.getStringExtra("device address");

        Log.d("ADDRESS",deviceAddress);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_DENIED){
            ActivityCompat.requestPermissions(this, new String[] {Manifest.permission.CAMERA}, 0);
        }


        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.vision_activity);

        mOpenCvCameraView = findViewById(R.id.color_blob_detection_activity_surface_view);
        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
        mOpenCvCameraView.setCvCameraViewListener(this);


        image = findViewById(R.id.thumbImage);
        tvBins = findViewById(R.id.tvBuckets);
        tvDirection = findViewById(R.id.tvDirection);
        tvDelay = findViewById(R.id.delay);

        model = new Model(this);
        try {
            model.load_internal_model("multi_image.tflite");
        } catch (IOException e) {
            e.printStackTrace();
        }

        if(!deviceAddress.equals("")){
            Log.d("SERVICE","STARTING LISTENER");
            uartListener = new UARTListener(this,this);
            uartListener.service_init(deviceAddress);
        }
        myArray = new JSONArray();

        Button changeModel = findViewById(R.id.btnChangeModel);
        changeModel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBrowse(v,activity);
            }
        });

        Permissions.isStoragePermissionGranted(this);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode,
                                 Intent resultData) {
        if (requestCode == FileSelection.ACTIVITY_CHOOSE_FILE && resultCode == Activity.RESULT_OK) {
            Uri uri = null;
            if (resultData != null) {
                uri = resultData.getData();
                Log.i(TAG, "Uri: " + uri.toString());
                try {
                    model.pick_model(uri);
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    public void onResume()
    {
        super.onResume();
        portrait = Toolbox.getScreenOrientation(this) == Configuration.ORIENTATION_PORTRAIT;

        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, mLoaderCallback);
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    public void onDestroy() {
        super.onDestroy();
        if(uartListener != null){
            uartListener.service_terminate();
        }
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    public void onCameraViewStarted(int width, int height) {
        mGray = new Mat(height, width, CvType.CV_8UC4);
        mRgbaF = new Mat(height, width, CvType.CV_8UC4);
        mRgbaT = new Mat(width, width, CvType.CV_8UC4);
    }

    public void onCameraViewStopped() {
        mGray.release();
    }

    public Mat onCameraFrame(CvCameraViewFrame inputFrame) {

        mGray = inputFrame.gray();

        if (portrait) {
            // Rotate mRgba 90 degrees
            Core.transpose(mGray, mRgbaT);
            Imgproc.resize(mRgbaT, mRgbaF, mRgbaF.size(), 0, 0, 0);
            Core.flip(mRgbaF, mGray, 1);
        }

        Mat mEqualized = new Mat(mGray.rows(), mGray.cols(), mGray.type());
        Imgproc.equalizeHist(mGray, mEqualized);

        /*
        Mat mEqualized = mGray;
        Mat edge = new Mat();
        Imgproc.Canny(mEqualized, edge, 100, 200, 3, true);

        counter++;

        mEqualized = edge;
        */

        Bitmap bitmap = Bitmap.createBitmap(mEqualized.cols(), mEqualized.rows(), Bitmap.Config.RGB_565);
        Utils.matToBitmap(mEqualized, bitmap);


        //final Bitmap demo_bitmap = Bitmap.createScaledBitmap(bitmap, resolution, resolution, false);
        final Bitmap scaled_bitmap = Bitmap.createScaledBitmap(bitmap, 50, 50, false);
        doInference(scaled_bitmap);

        final BitmapDrawable drawable = new BitmapDrawable(getResources(), bitmap);
        drawable.setAntiAlias(false);
        drawable.getPaint().setFilterBitmap(false);


        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                image.setImageDrawable(drawable);
            }
        });
        return mGray;
    }

    private void doInference(Bitmap bitmap) {
        float_bitmaps.add(BitmapHelper.BitmapToArray(bitmap));
        timestamps.add(System.currentTimeMillis());
        if(float_bitmaps.size() < 10){
            return;
        }
        else if(float_bitmaps.size() > 10){
            float_bitmaps.pop();
            timestamps.pop();
        }

        float_bitmaps.add(BitmapHelper.BitmapToArray(bitmap));
        timestamps.add(System.currentTimeMillis());

        float[][][][] float_pixels = new float[1][50][50][10];
        float[][] outputVal = new float[1][9];

        for(int i = 0; i < 50; i++){
            for(int j = 0; j < 50; j++){
                for(int b = 0; b < 10; b++){
                    float_pixels[0][i][j][b] = (float) float_bitmaps.get(b)[i*50+j]/255;
                }
            }
        }

        try{
            model.get_Interpreter().run(float_pixels,outputVal);
        }catch (Exception e){
            e.printStackTrace();
            Toast.makeText(this,"Can't run interpreter", Toast.LENGTH_SHORT).show();
        }

        float max = 0;
        int max_index = -1;

        for(int i = 0; i < outputVal[0].length; i++){
            if(outputVal[0][i] > max){
                max_index = i;
                max = outputVal[0][i];
            }
        }

        if(max_index == 4){
            max_index = 1; //go back instead of stopping
        }

        StringBuilder bins = new StringBuilder("{");

        for(int i =0; i < outputVal[0].length; i++){
            if(i == max_index){
                bins.append("1");
            }
            else{
                bins.append("0");
            }
            if(i<outputVal[0].length-1){
                bins.append(",");
            }
        }
        bins.append("}");

        String[] output = Data.getSector(max_index);

        if(UARTListener.mState == 20){
            uartListener.sendCommand("F" + output[2]); // Forward for the sector
            uartListener.sendCommand("S" + output[1]); // Side for the sector
        }

        final String final_bins = bins.toString();
        final String final_direction = output[0];   // label for the sector
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                tvDelay.setText("Delay: "+(System.currentTimeMillis() - timestamps.getLast()));
                tvDirection.setText(MessageFormat.format("Direction: {0}", final_direction));
                tvBins.setText(MessageFormat.format("Bins: {0}", final_bins));

            }
        });
    }
}