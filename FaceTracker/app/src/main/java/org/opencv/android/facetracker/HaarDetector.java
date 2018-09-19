package org.opencv.android.facetracker;


/**
 * Created by alorusso on 06/06/18.
 */

import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;

public class HaarDetector {
    private static final String TAG = "OCV-HaarDetector";


    public HaarDetector() {
    }

    public void loadNative() {
        System.loadLibrary("OCV-native-lib");

        loadResources();
    }


    public void OCvDetect(Mat imageGray, MatOfRect faces) {
        OpenCVdetector(imageGray.getNativeObjAddr(), faces.getNativeObjAddr());
    }

    public void OCvTrack(Mat imageGray, MatOfRect faces) {
        OpenCVtracker(imageGray.getNativeObjAddr(), faces.getNativeObjAddr());
    }

    private native void OpenCVdetector(long imageGray, long faces);
    private native void OpenCVtracker(long imageGray, long faces);
    private native void loadResources();
}
