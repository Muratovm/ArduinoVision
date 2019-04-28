package com.michaelmuratov.arduinovision;

public class Data {

    public static String[] getSector(int max_index){
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
        String[] output = {direction, X, Y};
        return output;
    }
}
