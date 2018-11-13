package org.opencv.android.facetracker;


/**
 * Created by alorusso on 06/06/18.
 */

import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import android.util.Log;

public class HaarDetector {
    private static final String TAG = "OCV-HaarDetector";


    public HaarDetector() {
    }

    public void loadNative() {
        System.loadLibrary("OCV-native-lib");

        loadResources();
    }


    public void OCvDetect(Mat imageGray, MatOfRect faces,int counterF) {
        long start = System.currentTimeMillis();//new
        OpenCVdetector(imageGray.getNativeObjAddr(), faces.getNativeObjAddr(), counterF);
        long end = System.currentTimeMillis();
        long duration = end -start;
        Log.i(TAG,"FDT-Activity Time " + duration/1000.0 + " sec");
    }


    public void OCvTrack(Mat imageGray, MatOfRect faces,int counterF) {
        OpenCVtracker(imageGray.getNativeObjAddr(), faces.getNativeObjAddr(), counterF);
    }
    private native void OpenCVdetector(long imageGray, long faces, int counterF);
    private native void OpenCVtracker(long imageGray, long faces, int counterF);


    private native void loadResources();

}
