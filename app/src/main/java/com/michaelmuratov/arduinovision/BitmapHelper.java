package com.michaelmuratov.arduinovision;

import android.graphics.Bitmap;

public class BitmapHelper {

    public static int[] getBitmapPixels(Bitmap bitmap, int x, int y, int width, int height) {
        int[] pixels = new int[bitmap.getWidth() * bitmap.getHeight()];
        bitmap.getPixels(pixels, 0, bitmap.getWidth(), x, y,
                width, height);
        final int[] subsetPixels = new int[width * height];
        for (int row = 0; row < height; row++) {
            System.arraycopy(pixels, (row * bitmap.getWidth()),
                    subsetPixels, row * width, width);
        }
        return subsetPixels;
    }

    public static int[] unPackPixel(int pixel){
        int[] rgb = new int[3];
        rgb[0] = (pixel >> 16) & 0xFF;
        rgb[1] = (pixel >> 8) & 0xFF;
        rgb[2] = (pixel >> 0) & 0xFF;
        return rgb;
    }



    public static int[] BitmapToArray(Bitmap bitmap){
        int[] pixels = BitmapHelper.getBitmapPixels(bitmap,0,0, bitmap.getWidth(), bitmap.getHeight());
        int[] rgb_float_pixels = new int[pixels.length];
        for(int i =0; i < pixels.length; i++){
            int[] rgb = BitmapHelper.unPackPixel(pixels[i]);
            rgb_float_pixels[i] = rgb[1];
            //Log.d("PIXEL", String.format("r:%d, g:%d, b:%d", rgb[0],rgb[1],rgb[2]));
            //Log.d("PIXEL",""+rgb_float_pixels[i]);
        }
        return rgb_float_pixels;
    }
}
