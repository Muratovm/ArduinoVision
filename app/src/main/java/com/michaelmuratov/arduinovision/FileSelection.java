package com.michaelmuratov.arduinovision;

import android.app.Activity;
import android.content.Intent;
import android.view.View;

public class FileSelection {

    public static final int ACTIVITY_CHOOSE_FILE = 1;

    public static void onBrowse(View view, Activity activity) {
        Intent chooseFile;
        Intent intent;
        chooseFile = new Intent(Intent.ACTION_GET_CONTENT);
        chooseFile.addCategory(Intent.CATEGORY_OPENABLE);
        chooseFile.setType("*/*");
        intent = Intent.createChooser(chooseFile, "Choose a file");
        activity.startActivityForResult(intent, ACTIVITY_CHOOSE_FILE);
    }
}
