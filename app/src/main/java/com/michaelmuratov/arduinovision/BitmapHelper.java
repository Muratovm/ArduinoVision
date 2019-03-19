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
}
