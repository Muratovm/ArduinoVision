package com.michaelmuratov.arduinovision;

import android.app.Activity;
import android.graphics.Bitmap;
import android.widget.Toast;

import com.michaelmuratov.arduinovision.UART.UARTListener;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class Inferencer {

    ArrayList<int[]> int_bitmaps = new ArrayList<>();
    ArrayList<Long> timestamps = new ArrayList<>();

    Activity activity;

    public Inferencer(Activity activity){
        this.activity = activity;
    }

    public ArrayList<String> multiImageInference(Model model, Bitmap bitmap, int num_saved) {

        int_bitmaps.add(BitmapHelper.BitmapToArray(bitmap));
        timestamps.add(System.currentTimeMillis());

        if(int_bitmaps.size() < num_saved){
            return null;
        }
        else if(int_bitmaps.size() > num_saved){
            int_bitmaps.remove(0);
            timestamps.remove(0);
        }

        int_bitmaps.add(BitmapHelper.BitmapToArray(bitmap));
        timestamps.add(System.currentTimeMillis());

        float[][][][] float_pixels = new float[1][50][50][num_saved];
        float[][] outputVal = new float[1][9];

        for(int i = 0; i < 50; i++){
            for(int j = 0; j < 50; j++){
                for(int b = 0; b < num_saved; b++){
                    float_pixels[0][i][j][b] = (float) int_bitmaps.get(b)[i*50+j]/255;
                }
            }
        }

        try{
            model.get_Interpreter().run(float_pixels,outputVal);
        }catch (Exception e){
            e.printStackTrace();
            //Toast.makeText(activity,"Can't run interpreter", Toast.LENGTH_SHORT).show();
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


        ArrayList<String> output = new ArrayList<String>();
        for (String string : Data.getSector(max_index)){
            output.add(string);
        }
        output.add(bins.toString());
        String delay = ""+(System.currentTimeMillis() - timestamps.get(timestamps.size()-1));
        output.add(delay);
        return output;
    }
/*
    public ArrayList<String> singleImageInference(Model model, Bitmap bitmap) {
        int[] int_bitmap = BitmapHelper.BitmapToArray(bitmap) ;
        if(timestamps.size() > 0){
            timestamps.remove(0);
        }
        timestamps.add(System.currentTimeMillis());

        float[][][][] float_pixels = new float[1][50][50][1];
        float[][] outputVal = new float[1][9];

        for(int i = 0; i < 50; i++){
            for(int j = 0; j < 50; j++){
                float_pixels[0][i][j][0] = (float) int_bitmap[i*50+j]/255;
            }
        }

        try{
            model.get_Interpreter().run(float_pixels,outputVal);
        }catch (Exception e){
            e.printStackTrace();
            //Toast.makeText(activity,"Can't run interpreter", Toast.LENGTH_SHORT).show();
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


        ArrayList<String> output = new ArrayList<String>();
        for (String string : Data.getSector(max_index)){
            output.add(string);
        }
        output.add(bins.toString());
        String delay = ""+(System.currentTimeMillis() - timestamps.get(timestamps.size()-1));
        output.add(delay);
        return output;
    }
*/

}
