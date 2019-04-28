package com.michaelmuratov.arduinovision;

import android.app.Activity;
import android.content.res.AssetFileDescriptor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import org.tensorflow.lite.Interpreter;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public class Model {

    private ByteBuffer tfliteModel;
    Interpreter tflite;
    Activity activity;

    public Model(Activity activity){
        this.activity = activity;
    }

    public void load_internal_model(String model_name) throws IOException {
        tfliteModel = loadModelFile(model_name);
        tflite = new Interpreter(tfliteModel);
    }

    private ByteBuffer loadModelFile(String model_name) throws IOException {
        AssetFileDescriptor fileDescriptor = activity.getAssets().openFd(model_name);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    public void pick_model(Uri uri) throws IOException{
        ParcelFileDescriptor parcelFileDescriptor =
                activity.getContentResolver().openFileDescriptor(uri, "r");
        FileDescriptor fileDescriptor = parcelFileDescriptor.getFileDescriptor();
        FileInputStream is = new FileInputStream(fileDescriptor);
        final FileChannel in = is.getChannel();
        tfliteModel = in.map(FileChannel.MapMode.READ_ONLY, 0, in.size());
        tflite = new Interpreter(tfliteModel);
    }

    public Interpreter get_Interpreter(){
        return tflite;
    }
}
