package com.michaelmuratov.arduinovision;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
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
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;
import org.tensorflow.lite.Interpreter;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Objects;

public class VisionActivity extends AppCompatActivity implements CvCameraViewListener2 {
    private static final String  TAG              = "VisionActivity";
    private static final int ACTIVITY_CHOOSE_FILE = 52;

    Activity activity;
    private Mat                  mGray;
    private Mat                  mRgbaF;
    private Mat                  mRgbaT;
    ImageView image;
    ImageView original;
    TextView tvBins;
    TextView tvDirection;
    EditText setresolution;
    Interpreter tflite;
    LinkedList<Bitmap> bitmap_list;


    int resolution = 50;

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
        bitmap_list = new LinkedList<>();
        Intent intent = getIntent();
        String deviceAddress = intent.getStringExtra("device address");

        Log.d("ADDRESS",deviceAddress);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_DENIED){
            ActivityCompat.requestPermissions(this, new String[] {Manifest.permission.CAMERA}, 0);
        }


        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.color_blob_detection_surface_view);

        mOpenCvCameraView = findViewById(R.id.color_blob_detection_activity_surface_view);
        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
        mOpenCvCameraView.setCvCameraViewListener(this);


        image = findViewById(R.id.thumbImage);
        original = findViewById(R.id.original);
        tvBins = findViewById(R.id.tvBuckets);
        tvDirection = findViewById(R.id.tvDirection);
        setresolution = findViewById(R.id.etDisplayResolution);
        try{
            tflite = new Interpreter(loadModelFile());
        }catch (Exception e){
            e.printStackTrace();
        }

        if(!deviceAddress.equals("")){
            Log.d("SERVICE","STARTING LISTENER");
            uartListener = new UARTListener(this,this);
            uartListener.service_init(deviceAddress);
        }
        myArray = new JSONArray();

        setresolution.addTextChangedListener(new TextWatcher() {

            public void afterTextChanged(Editable s) {
            }

            public void beforeTextChanged(CharSequence s, int start,
                                          int count, int after) {
            }

            public void onTextChanged(final CharSequence s, final int start,
                                      final int before, final int count) {
                try{
                    resolution = Integer.valueOf(s.toString());
                }
                catch (Exception e){
                    e.printStackTrace();
                }
            }
        });

        Button changeModel = findViewById(R.id.btnChangeModel);
        changeModel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBrowse(v);
            }
        });
    }

    public void onBrowse(View view) {
        Intent chooseFile;
        Intent intent;
        chooseFile = new Intent(Intent.ACTION_GET_CONTENT);
        chooseFile.addCategory(Intent.CATEGORY_OPENABLE);
        chooseFile.setType("*/*");
        intent = Intent.createChooser(chooseFile, "Choose a file");
        startActivityForResult(intent, ACTIVITY_CHOOSE_FILE);
    }

    public void isStoragePermissionGranted() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED) {
                Log.v(TAG,"Permission is granted");
            } else {

                Log.v(TAG,"Permission is revoked");
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
            }
        }
        else { //permission is automatically granted on sdk<23 upon installation
            Log.v(TAG,"Permission is granted");
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode,
                                 Intent resultData) {

        // The ACTION_OPEN_DOCUMENT intent was sent with the request code
        // READ_REQUEST_CODE. If the request code seen here doesn't match, it's the
        // response to some other intent, and the code below shouldn't run at all.

        if (requestCode == ACTIVITY_CHOOSE_FILE && resultCode == Activity.RESULT_OK) {
            // The document selected by the user won't be returned in the intent.
            // Instead, a URI to that document will be contained in the return intent
            // provided to this method as a parameter.
            // Pull that URI using resultData.getData().
            Uri uri = null;
            if (resultData != null) {
                uri = resultData.getData();
                Log.i(TAG, "Uri: " + uri.toString());
                try {
                    getModel(uri);
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
        }
    }

    private void getModel(Uri uri) throws IOException {
        ParcelFileDescriptor parcelFileDescriptor =
                getContentResolver().openFileDescriptor(uri, "r");
        FileDescriptor fileDescriptor = parcelFileDescriptor.getFileDescriptor();
        FileInputStream is = new FileInputStream(fileDescriptor);
        final FileChannel in = is.getChannel();
        final MappedByteBuffer buf = in.map(FileChannel.MapMode.READ_ONLY, 0, in.size());
        tflite = new Interpreter(buf);
    }

    @Override
    public void onPause()
    {
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

        if(portrait){
            // Rotate mRgba 90 degrees
            Core.transpose(mGray, mRgbaT);
            Imgproc.resize(mRgbaT, mRgbaF, mRgbaF.size(), 0,0, 0);
            Core.flip(mRgbaF, mGray, 1 );

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
        Bitmap original_bitmap =
                Bitmap.createBitmap(mGray.cols(), mGray.rows(), Bitmap.Config.RGB_565);
        Utils.matToBitmap(mGray, original_bitmap);

        Bitmap bitmap =
                Bitmap.createBitmap(mEqualized.cols(), mEqualized.rows(), Bitmap.Config.RGB_565);
        Utils.matToBitmap(mEqualized, bitmap);


        //final Bitmap demo_bitmap = Bitmap.createScaledBitmap(bitmap, resolution, resolution, false);
        final Bitmap scaled_bitmap = Bitmap.createScaledBitmap(bitmap, 50, 50, false);
        /*
        String path = Environment.getExternalStorageDirectory().toString();
        OutputStream fOut = null;
        File file = new File(path, "/CapturePictures/"+counter+".jpg"); // the File to save , append increasing numeric counter to prevent files from getting overwritten.
        try {
            fOut = new FileOutputStream(file);
            new_bitmap.compress(Bitmap.CompressFormat.JPEG, 85, fOut); // saving the Bitmap to a file compressed as a JPEG with 85% compression rate
            fOut.flush(); // Not really required
            fOut.close(); // do not forget to close the stream
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        */
        doInference(scaled_bitmap);
        final BitmapDrawable drawable = new BitmapDrawable(getResources(), bitmap);
        drawable.setAntiAlias(false);
        drawable.getPaint().setFilterBitmap(false);

        final BitmapDrawable drawable2 = new BitmapDrawable(getResources(), original_bitmap);
        drawable2.setAntiAlias(false);
        drawable2.getPaint().setFilterBitmap(false);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                image.setImageDrawable(drawable);
                original.setImageDrawable(drawable2);
            }
        });
        return mGray;
    }

    private void doInference(Bitmap bitmap) {

        if(bitmap_list.size() < 10){
            bitmap_list.add(bitmap);
        }
        else{
            bitmap_list.pop();
            bitmap_list.add(bitmap);

            float[][][][] float_pixels = new float[1][50][50][10];
            float[][] outputVal = new float[1][9];

            ArrayList<float[]> multiple_bitmaps = new ArrayList<>();

            for(int b = 0; b < 10; b++){
                Bitmap cur_bitmap = bitmap_list.get(b);
                int[] pixels = BitmapHelper.getBitmapPixels(bitmap,0,0, cur_bitmap.getWidth(), cur_bitmap.getHeight());
                float[] rgb_float_pixels = new float[pixels.length];
                for(int i =0; i < pixels.length; i++){
                    int[] rgb = BitmapHelper.unPackPixel(pixels[i]);
                    rgb_float_pixels[i] = (float) rgb[1]/255;
                    //Log.d("PIXEL", String.format("r:%d, g:%d, b:%d", rgb[0],rgb[1],rgb[2]));
                    //Log.d("PIXEL",""+float_pixels[i]);
                }
                multiple_bitmaps.add(rgb_float_pixels);
            }
            for(int i = 0; i < 50; i++){
                for(int j = 0; j < 50; j++){
                    for(int b = 0; b < 10; b++){
                        float_pixels[0][i][j][b] = multiple_bitmaps.get(b)[i*50+j];
                    }
                }
            }

            try{
                tflite.run(float_pixels,outputVal);
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


            String Y = "\0";
            String X = "\0";

            String direction = "";

            switch (max_index){
                case 0:
                    Y="-100\0";
                    X="-254\0";
                    direction = "Backwards Right";
                    break;
                case 1:
                    Y="-100\0";
                    X="0\0";
                    direction = "Backwards";
                    break;
                case 2:
                    Y="-100\0";
                    X="254\0";
                    direction = "Backwards Left";
                    break;
                case 3:
                    Y="40\0";
                    X="0\0";
                    direction = "Forward Slightly";
                    break;
                case 4:
                    Y="0\0";
                    X="0\0";
                    direction = "Stop";
                    break;
                case 5:
                    Y="-40\0";
                    X="0\0";
                    direction = "Backwards Slightly";
                    break;
                case 6:
                    Y="100\0";
                    X="-254\0";
                    direction = "Forward Right";
                    break;
                case 7:
                    Y="100\0";
                    X="0\0";
                    direction = "Forward";
                    break;
                case 8:
                    Y="100\0";
                    X="254\0";
                    direction = "Forward Left";
                    break;
            }
            if(UARTListener.mState == 20){
                uartListener.sendCommand("F" + Y);
                uartListener.sendCommand("S" + X);
            }

            final String final_bins = bins.toString();
            final String final_direction = direction;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {

                    tvDirection.setText(MessageFormat.format("Direction: {0}", final_direction));
                    tvBins.setText(MessageFormat.format("Bins: {0}", final_bins));

                }
            });
        }
    }

    private ByteBuffer loadModelFile() throws IOException{
        AssetFileDescriptor fileDescriptor = this.getAssets().openFd("multi_image.tflite");
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }
}