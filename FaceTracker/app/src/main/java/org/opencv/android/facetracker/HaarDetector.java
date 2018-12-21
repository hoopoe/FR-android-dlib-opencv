package org.opencv.android.facetracker;


/**
 * Created by alorusso on 06/06/18.
 */

import android.os.Debug;
import                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                             android.os.SystemClock;
import android.util.Log;

import org.opencv.android.CameraBridgeViewBase;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

public class HaarDetector {
    private static final String TAG = "OCV-HaarDetector";

    int counterF=0;
    boolean okThread = false;
    private Rect[] detectedFacesArray = {};
    int dn = 0;
    boolean newFaceFound = false;
    public Long threadCpuTime;//new
    private Long mStartThreadCpuTime;//new
    private static MatOfRect detectedFaces;


    public HaarDetector() {
    }

    public void loadNative() {
        Log.d(TAG, "loadNative called");
        System.loadLibrary("OCV-native-lib");
        Log.d(TAG, "OCV-native-lib loaded successfully");
        loadResources();
        try {
            detectedFaces = new MatOfRect();
        }catch (Exception e){
            e.printStackTrace();
        }
        Log.d(TAG, "loadNative left");
    }


    public void ImageProcessing(int width, int height, byte[] NV21FrameData, int [] pixels){
        Log.d(TAG, "ImageProcessing -> mImageProcessing");
        mImageProcessing(width, height, NV21FrameData, pixels);
    }


    public void OCvDetect(int width, int height, byte[] NV21FrameData, MatOfRect faces){
        Log.d(TAG, "ImageProcessing -> OCvDetect");
        OpenCVdetector(width, height, NV21FrameData, faces.getNativeObjAddr());
    }

    /*
    public void OCvDetect(Mat imageRgba, MatOfRect faces) {
        OpenCVdetector(imageRgba.getNativeObjAddr(), faces.getNativeObjAddr());
        Log.d(TAG, "AL faces in HaarDetector" + faces.size());
    }
    */

    public void OCvTrack(Mat imageRgba, MatOfRect faces) {
        // OpenCVtracker(imageRgba.getNativeObjAddr(), faces.getNativeObjAddr());
    }

    public void testOCvDetect(Mat imageRgba, MatOfRect faces){
        Log.d(TAG, "ImageProcessing -> testOCvDetect");
        //testOpenCVdetector(imageRgba.getNativeObjAddr(), faces.getNativeObjAddr());
        testOpenCVdetectorWeights(imageRgba.getNativeObjAddr(), faces.getNativeObjAddr());
    }


    ///////////////////////////////////////////////////
    // ATT: uncomment call to OCVdetector !!! /////////
    ///////////////////////////////////////////////////
    public MatOfRect testOnCameraFrame(final Mat mRgba) {

        counterF++;

        Log.i(TAG,"#frame:"+ counterF);
        //if(!okThread) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    okThread=true;
                    mStartThreadCpuTime = Debug.threadCpuTimeNanos();//new
                    long start_time1 = SystemClock.currentThreadTimeMillis();

                    Log.i(TAG, "Detection thread_START @ #frame:"+counterF);
                    // detectedFaces.release();

                    ////////// take out this comment ////////////////////////
                    ///OpenCVdetector(mRgba.getNativeObjAddr(), detectedFaces.getNativeObjAddr());
                    ////////// end of uncomment this instruction ////////////

                    Log.i(TAG, "AL detectedFaces OpenCvActivity:"+ detectedFaces.empty() +
                            "size = " + detectedFaces.size());

                    if(!detectedFaces.empty()) {

                        detectedFacesArray = detectedFaces.toArray();
                        for(int i=0;i<detectedFacesArray.length;i++) {
                            Log.i(TAG, "threadDET_detectedFaces.toArray()["+i+"] (x,y,w,h): " + (int) detectedFaces.toArray()[i].x +", "+
                                    (int) detectedFaces.toArray()[i].y +", "+
                                    (int) detectedFaces.toArray()[i].width +", "+
                                    (int) detectedFaces.toArray()[i].height+" @ #frame:"+counterF);
                        }
                        dn=detectedFacesArray.length;
                        Log.i(TAG,"#detectedFaces_threadDET_:"+dn);
                        if(dn>0){
                            newFaceFound = true;
                            Log.i(TAG, "A new Face is found (threadDET)!");}

                    }
                    okThread=false;
                    Log.i(TAG, "Detection thread_END @ #frame:"+counterF);

                    //Returns the amount of time that the current thread has spent executing code or waiting for certain types of I/O.
                    threadCpuTime = Debug.threadCpuTimeNanos() - mStartThreadCpuTime;//new
                    Log.i(TAG, "DETthreadCpuTime [sec] "+(float)threadCpuTime/1000000000+" @ #frame:"+counterF);

                    //Returns milliseconds running in the current thread.
                    long elapsed_time1 = SystemClock.currentThreadTimeMillis() - start_time1;
                    Log.i(TAG, "DETdeltaCurrentThreadTimeMillis [sec] "+(float)elapsed_time1/1000+" @ #frame:"+counterF);

                }
            }).start();
        //}
        return detectedFaces;
    }


    // private native void OpenCVdetector(long imageRgba, long faces);
    // private native void OpenCVtracker(long imageRgba, long faces);

    private native void loadResources();
    private native boolean mImageProcessing(int width, int height,byte[] NV21FrameData, int [] outPixels);
    private native boolean OpenCVdetector(int width, int height,byte[] NV21FrameData, long faces);
    private native boolean testOpenCVdetectorWeights(long imageRgba, long faces);
    // private native boolean testOpenCVdetector(long imageRgba, long faces);
}
