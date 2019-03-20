package com.michaelmuratov.arduinovision;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

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
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
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
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Toolbar;

import com.michaelmuratov.arduinovision.UART.UARTListener;
import com.michaelmuratov.arduinovision.Util.Toolbox;

public class VisionActivity extends AppCompatActivity implements OnTouchListener, CvCameraViewListener2 {
    private static final String  TAG              = "VisionActivity";

    private boolean              mIsColorSelected = false;
    private Mat                  mRgba;
    private Scalar               mBlobColorRgba;
    private Scalar               mBlobColorHsv;
    //private ColorBlobDetector    mDetector;
    private Mat                  mSpectrum;
    private Size                 SPECTRUM_SIZE;
    private Scalar               CONTOUR_COLOR;
    ImageView image;
    TextView output;
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
        output = findViewById(R.id.output);
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
/*
        Mat mRgbaT = mRgba.t();
        Core.flip(mRgba.t(), mRgbaT, 1);
        Imgproc.resize(mRgbaT, mRgbaT, mRgba.size());

        mRgba = mRgbaT;
*/

        counter++;

        if (mIsColorSelected) {
            //mDetector.process(mRgba);
            //List<MatOfPoint> contours = mDetector.getContours();
            //Log.e(TAG, "Contours count: " + contours.size());
            //Imgproc.drawContours(mRgba, contours, -1, CONTOUR_COLOR);

            Mat colorLabel = mRgba.submat(4, 68, 4, 68);
            colorLabel.setTo(mBlobColorRgba);

            Mat spectrumLabel = mRgba.submat(4, 4 + mSpectrum.rows(), 70, 70 + mSpectrum.cols());
            mSpectrum.copyTo(spectrumLabel);
        }
        Mat grayImage = new Mat();
        Imgproc.cvtColor(mRgba, grayImage, Imgproc.COLOR_BGR2GRAY);

        Bitmap bitmap =
                Bitmap.createBitmap(grayImage.cols(), grayImage.rows(), Bitmap.Config.RGB_565);
        Utils.matToBitmap(grayImage, bitmap);

        if(portrait){
            Matrix matrix = new Matrix();
            matrix.preRotate(90);

            bitmap = Bitmap.createBitmap(bitmap, 0,0,bitmap.getWidth(),bitmap.getHeight(),matrix,false);

        }

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

        final String prediction = doInference(float_pixels);

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                BitmapDrawable drawable = new BitmapDrawable(getResources(), demo_bitmap);
                drawable.setAntiAlias(false);
                drawable.getPaint().setFilterBitmap(false);
                image.setImageDrawable(drawable);

                output.setText("Direction: "+prediction);

            }
        });

        return grayImage;
    }

    private String doInference(float[] pixels) {

        float[][] outputVal = new float[1][9];
        float[][] float_pixels = new float[1][pixels.length];

        for(int i = 0; i < pixels.length; i++){
            float_pixels[0][i] = pixels[i];
        }

        tflite.run(float_pixels,outputVal);

        float max = 0;
        int max_index = -1;

        for(int i = 0; i < outputVal[0].length; i++){
            if(outputVal[0][i] > max){
                max_index = i;
                max = outputVal[0][i];
            }
        }

        String output = "{";

        for(int i =0; i < outputVal[0].length; i++){
            if(i == max_index){
                output += "1,";
            }
            else{
                output += "0,";
            }
        }
        output += "}";


        String Y = "\0";
        String X = "\0";

        switch (max_index){
            case 0:
                Y="-100\0";
                X="-254\0";
                break;
            case 1:
                Y="-100\0";
                X="0\0";
                break;
            case 2:
                Y="-100\0";
                X="254\0";
                break;
            case 3:
                Y="40\0";
                X="0\0";
                break;
            case 4:
                Y="0\0";
                X="0\0";
                break;
            case 5:
                Y="-40\0";
                X="0\0";
                break;
            case 6:
                Y="100\0";
                X="-254\0";
                break;
            case 7:
                Y="100\0";
                X="0\0";
                break;
            case 8:
                Y="100\0";
                X="254\0";
                break;
        }
        if(UARTListener.mState == 20){
            uartListener.sendCommand("F" + Y);
            uartListener.sendCommand("S" + X);
        }
        return output;
    }

    private Scalar converScalarHsv2Rgba(Scalar hsvColor) {
        Mat pointMatRgba = new Mat();
        Mat pointMatHsv = new Mat(1, 1, CvType.CV_8UC3, hsvColor);
        Imgproc.cvtColor(pointMatHsv, pointMatRgba, Imgproc.COLOR_HSV2RGB_FULL, 4);

        return new Scalar(pointMatRgba.get(0, 0));
    }

    private ByteBuffer loadModelFile() throws IOException{
        AssetFileDescriptor fileDescriptor = this.getAssets().openFd("model.tflite");
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }
}