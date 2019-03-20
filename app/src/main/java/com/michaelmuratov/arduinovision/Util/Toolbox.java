package com.michaelmuratov.arduinovision.Util;

import android.app.Activity;
import android.content.res.Configuration;
import android.os.Build;
import android.view.Display;
import android.view.View;

public class Toolbox {

    public static void activiateFullscreen(Activity activity){
        View decorView = activity.getWindow().getDecorView();
        int uiOptions = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
        if (Build.VERSION.SDK_INT >= 18) {
            uiOptions ^= View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    |View.SYSTEM_UI_FLAG_LOW_PROFILE;
        }
        if (Build.VERSION.SDK_INT >= 19) {
            uiOptions ^= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        }
        decorView.setSystemUiVisibility(uiOptions);
    }

    public static int getScreenOrientation(Activity activity)
    {
        Display screenOrientation = activity.getWindowManager().getDefaultDisplay();
        int orientation = Configuration.ORIENTATION_UNDEFINED;
        if(screenOrientation.getWidth()==screenOrientation.getHeight()){
            orientation = Configuration.ORIENTATION_SQUARE;
            //Do something

        } else{
            if(screenOrientation.getWidth() < screenOrientation.getHeight()){
                orientation = Configuration.ORIENTATION_PORTRAIT;
                //Do something

            }else {
                orientation = Configuration.ORIENTATION_LANDSCAPE;
                //Do something

            }
        }
        return orientation;
    }
}
