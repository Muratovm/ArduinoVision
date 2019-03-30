package com.michaelmuratov.arduinovision;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.text.MessageFormat;

import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.imgproc.Imgproc;

import org.tensorflow.lite.Interpreter;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.View.OnTouchListener;
import android.view.SurfaceView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.michaelmuratov.arduinovision.UART.UARTListener;
import com.michaelmuratov.arduinovision.Util.Toolbox;

public class VisionActivity extends AppCompatActivity implements OnTouchListener, CvCameraViewListener2 {
    private static final String  TAG              = "VisionActivity";
    private static final int ACTIVITY_CHOOSE_FILE = 52;

    Activity activity;

    private boolean              mIsColorSelected = false;
    private Mat                  mRgba;
    private Mat                  mRgbaF;
    private Mat                  mRgbaT;
    private Scalar               mBlobColorRgba;
    private Scalar               mBlobColorHsv;
    //private ColorBlobDetector    mDetector;
    private Mat                  mSpectrum;
    private Size                 SPECTRUM_SIZE;
    private Scalar               CONTOUR_COLOR;
    ImageView image;
    TextView tvBins;
    TextView tvDirection;
    EditText setresolution;
    Interpreter tflite;

    int resolution = 50;

    boolean portrait = true;

    UARTListener uartListener;
    int num = 0;
    JSONArray myArray;

    Integer counter = 0;

    private CameraBridgeViewBase mOpenCvCameraView;

    private BaseLoaderCallback  mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    Log.i(TAG, "OpenCV loaded successfully");
                    mOpenCvCameraView.enableView();
                    mOpenCvCameraView.setOnTouchListener(VisionActivity.this);
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

        setContentView(R.layout.color_blob_detection_surface_view);

        mOpenCvCameraView = (CameraBridgeViewBase) findViewById(R.id.color_blob_detection_activity_surface_view);
        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
        mOpenCvCameraView.setCvCameraViewListener(this);


        image = findViewById(R.id.thumbImage);
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

    public  boolean isStoragePermissionGranted() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED) {
                Log.v(TAG,"Permission is granted");
                return true;
            } else {

                Log.v(TAG,"Permission is revoked");
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
                return false;
            }
        }
        else { //permission is automatically granted on sdk<23 upon installation
            Log.v(TAG,"Permission is granted");
            return true;
        }
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode != RESULT_OK) return;
        String path     = "";
        if(requestCode == ACTIVITY_CHOOSE_FILE)
        {
            isStoragePermissionGranted();
            final Uri uri = data.getData();
            assert uri != null;
            File source = new File(Environment.getExternalStorageDirectory().getPath()+uri.getPath().substring(29));
            //File source = new File("/sdcard/Models/model.tflite");
            Log.w("FILE",""+source.getName());
            Log.w("FILE",""+source.getPath());
            Log.w("FILE",""+Integer.parseInt(String.valueOf(source.length()/1024)));
            try {
                final FileInputStream is = new FileInputStream(source);
                final FileChannel in = is.getChannel();
                final MappedByteBuffer buf = in.map(FileChannel.MapMode.READ_ONLY, 0, in.size());
                tflite = new Interpreter(buf);
            }catch (IOException e) {
                e.printStackTrace();
                Log.w("FILE","something went wrong");
            }

            /*
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(activity,uri.getPath(), Toast.LENGTH_SHORT).show();;
                }
            });
            */
        }
    }
    public String changeModelLocation(String string){
        return Environment.getExternalStorageDirectory()+"/Models/" + string;
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
        mRgba = new Mat(height, width, CvType.CV_8UC4);
        mRgbaF = new Mat(height, width, CvType.CV_8UC4);
        mRgbaT = new Mat(width, width, CvType.CV_8UC4);
        //mDetector = new ColorBlobDetector();
        mSpectrum = new Mat();
        mBlobColorRgba = new Scalar(255);
        mBlobColorHsv = new Scalar(255);
        SPECTRUM_SIZE = new Size(200, 64);
        CONTOUR_COLOR = new Scalar(255,0,0,255);
    }

    public void onCameraViewStopped() {
        mRgba.release();
    }

    public boolean onTouch(View v, MotionEvent event) {
        int cols = mRgba.cols();
        int rows = mRgba.rows();

        int xOffset = (mOpenCvCameraView.getWidth() - cols) / 2;
        int yOffset = (mOpenCvCameraView.getHeight() - rows) / 2;

        int x = (int)event.getX() - xOffset;
        int y = (int)event.getY() - yOffset;

        Log.i(TAG, "Touch image coordinates: (" + x + ", " + y + ")");

        if ((x < 0) || (y < 0) || (x > cols) || (y > rows)) return false;

        Rect touchedRect = new Rect();

        touchedRect.x = (x>4) ? x-4 : 0;
        touchedRect.y = (y>4) ? y-4 : 0;

        touchedRect.width = (x+4 < cols) ? x + 4 - touchedRect.x : cols - touchedRect.x;
        touchedRect.height = (y+4 < rows) ? y + 4 - touchedRect.y : rows - touchedRect.y;

        Mat touchedRegionRgba = mRgba.submat(touchedRect);

        Mat touchedRegionHsv = new Mat();
        Imgproc.cvtColor(touchedRegionRgba, touchedRegionHsv, Imgproc.COLOR_RGB2HSV_FULL);

        // Calculate average color of touched region
        mBlobColorHsv = Core.sumElems(touchedRegionHsv);
        int pointCount = touchedRect.width*touchedRect.height;
        for (int i = 0; i < mBlobColorHsv.val.length; i++)
            mBlobColorHsv.val[i] /= pointCount;

        mBlobColorRgba = converScalarHsv2Rgba(mBlobColorHsv);

        Log.i(TAG, "Touched rgba color: (" + mBlobColorRgba.val[0] + ", " + mBlobColorRgba.val[1] +
                ", " + mBlobColorRgba.val[2] + ", " + mBlobColorRgba.val[3] + ")");

        //mDetector.setHsvColor(mBlobColorHsv);

        //Imgproc.resize(mDetector.getSpectrum(), mSpectrum, SPECTRUM_SIZE, 0, 0, Imgproc.INTER_LINEAR_EXACT);

        mIsColorSelected = true;

        touchedRegionRgba.release();
        touchedRegionHsv.release();

        return false; // don't need subsequent touch events
    }

    public Mat onCameraFrame(CvCameraViewFrame inputFrame) {

        mRgba = inputFrame.rgba();
        counter++;
/*
        if(portrait){
            // Rotate mRgba 90 degrees
            Core.transpose(mRgba, mRgbaT);
            Imgproc.resize(mRgbaT, mRgbaF, mRgbaF.size(), 0,0, 0);
            Core.flip(mRgbaF, mRgba, 1 );
        }
*/
        Bitmap bitmap =
                Bitmap.createBitmap(mRgba.cols(), mRgba.rows(), Bitmap.Config.RGB_565);
        Utils.matToBitmap(mRgba, bitmap);


        final Bitmap demo_bitmap = Bitmap.createScaledBitmap(bitmap, resolution, resolution, false);
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
        int[] pixels = BitmapHelper.getBitmapPixels(scaled_bitmap,0,0,scaled_bitmap.getWidth(),scaled_bitmap.getHeight());

        float[] float_pixels = new float[pixels.length];

        for(int i =0; i < pixels.length; i++){
            int[] rgb = BitmapHelper.unPackPixel(pixels[i]);
            float_pixels[i] = (float) (rgb[0] * 299/1000 + rgb[1]*587/1000 + rgb[2] * 114/1000) / 255;
            //Log.d("PIXEL", String.format("r:%d, g:%d, b:%d", rgb[0],rgb[1],rgb[2]));
            //Log.d("PIXEL",""+float_pixels[i]);
        }

        Log.w("FIRST",""+float_pixels[0]);

        doInference(float_pixels);

        final BitmapDrawable drawable = new BitmapDrawable(getResources(), demo_bitmap);
        drawable.setAntiAlias(false);
        drawable.getPaint().setFilterBitmap(false);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                image.setImageDrawable(drawable);
            }
        });

        return mRgba;
    }

    private void doInference(float[] pixels) {

        float[][] outputVal = new float[1][9];
        float[][][][] float_pixels = new float[1][50][50][1];

        for(int i = 0; i < 50; i++){
            for(int j = 0; j < 50; j++){
                float_pixels[0][i][j][0] = pixels[i*50+j];
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
        final String final_direction = direction.toString();
        runOnUiThread(new Runnable() {
            @Override
            public void run() {

                tvDirection.setText("Direction: "+final_direction);
                tvBins.setText("Bins: "+final_bins);

            }
        });
    }

    private Scalar converScalarHsv2Rgba(Scalar hsvColor) {
        Mat pointMatRgba = new Mat();
        Mat pointMatHsv = new Mat(1, 1, CvType.CV_8UC3, hsvColor);
        Imgproc.cvtColor(pointMatHsv, pointMatRgba, Imgproc.COLOR_HSV2RGB_FULL, 4);

        return new Scalar(pointMatRgba.get(0, 0));
    }

    private ByteBuffer loadModelFile() throws IOException{
        AssetFileDescriptor fileDescriptor = getResources().openRawResourceFd(R.raw.model);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }
}