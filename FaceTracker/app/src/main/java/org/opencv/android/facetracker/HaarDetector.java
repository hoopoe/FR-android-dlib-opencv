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
    //boolean okThreadT = false;
    //private MatOfRect detectedFaces;
    private Rect[] detectedFacesArray = {};
    int dn = 0;
    int tn = 0;
    boolean newFaceFound = false;
    private static final Scalar DETECT_BOX_COLOR   = new Scalar(255, 255, 0, 255);//yellow
    public Long threadCpuTime;//new
    public Long threadTCpuTime;//new
    private Long mStartThreadCpuTime;//new
    private Long mStartThreadTCpuTime;//new
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
    }


    public void OCvDetect(Mat imageRgba, MatOfRect faces) {
        OpenCVdetector(imageRgba.getNativeObjAddr(), faces.getNativeObjAddr());
        Log.d(TAG, "AL faces in HaarDetector" + faces.size());
    }

    public void OCvTrack(Mat imageRgba, MatOfRect faces) {
        OpenCVtracker(imageRgba.getNativeObjAddr(), faces.getNativeObjAddr());
    }


    public MatOfRect testOnCameraFrame(final Mat mRgba) {

        //final MatOfRect detectedFaces = new MatOfRect();//commented here
        //final MatOfRect trackedFaces = new MatOfRect();

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
                    OpenCVdetector(mRgba.getNativeObjAddr(), detectedFaces.getNativeObjAddr());

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


      /*  if(!okThreadT) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        okThreadT = true;
                        mStartThreadTCpuTime = Debug.threadCpuTimeNanos();//new
                        Log.i(TAG, "Tracking thread_START @ #frame:" + counterF);
                        long start_time2 = SystemClock.currentThreadTimeMillis();

                        trackedFaces.release();
                        //trackedFacesArray = trackedFaces.toArray();

                        if (newFaceFound) {
                            for(int i=0;i<detectedFaces.toArray().length;i++) {
                                Log.i(TAG, "(threadTRACK)detectedFaces.toArray()["+i+"] (x,y,w,h): " + (int) detectedFaces.toArray()[i].x +", "+
                                        (int) detectedFaces.toArray()[i].y +", "+
                                        (int) detectedFaces.toArray()[i].width +", "+
                                        (int) detectedFaces.toArray()[i].height);
                            }
                            Log.i(TAG, "New faces found -> trackedFaces is populated using detectedFace @ #frame:" + counterF);
                            trackedFacesArray=detectedFaces.toArray();
                            Log.i(TAG, "#trackedfaces_ramo1 (threadTRACK): "+trackedFaces.toArray().length);
                            for (int i = 0; i < trackedFacesArray.length; i++) {
                                Log.i(TAG, "CONTROLLO_ramo1 (threadTRACK) -> trackedFacesArray["+i+"]  (x,y,w,h): " + trackedFacesArray[i] + " @ #frame:" + counterF);
                            }
                            newFaceFound = false;
                        } else {
                            if (!trackedFaces.empty()) {
                                for (int i = 0; i < trackedFaces.toArray().length; i++) {
                                    Log.i(TAG, "detectedFaces.toArray()[" + i + "] (x,y,w,h): " + (int) trackedFaces.toArray()[i].x + ", " +
                                            (int) trackedFaces.toArray()[i].y + ", " +
                                            (int) trackedFaces.toArray()[i].width + ", " +
                                            (int) trackedFaces.toArray()[i].height);
                                }
                                trackedFacesArray = trackedFaces.toArray();
                            } else {
                                Log.i(TAG, "trackedFaces is empty @ #frame:" + counterF);
                            }
                        }

                        if(trackedFacesArray.length>0) {//serve?
                            trackedFaces.fromArray(trackedFacesArray);//here trackedFaces is populated
                        }

                        if (!trackedFaces.empty()) {
                            hd.OCvTrack(mRgba, trackedFaces);
                            if(!trackedFaces.empty()) {
                                trackedFacesArray = trackedFaces.toArray();
                                for (int i = 0; i < trackedFacesArray.length; i++) {
                                    Log.i(TAG, "newTrackedFaces -> trackedFaces[" + i +"] (x,y,w,h): " + trackedFacesArray[i] + " @ #frame:" + counterF);
                                }
                            }
                        }

                        okThreadT = false;
                        Log.i(TAG, "Tracking thread_END @ #frame:" + counterF);
                        threadTCpuTime = Debug.threadCpuTimeNanos() - mStartThreadTCpuTime;//new
                        Log.i(TAG, "TRACKthreadCpuTime [sec] "+(float)threadTCpuTime/1000000000+" @ #frame:"+counterF);

                        //Returns milliseconds running in the current thread.
                        long elapsed_time2 = SystemClock.currentThreadTimeMillis() - start_time2;
                        Log.i(TAG, "TRACKdeltaCurrentThreadTimeMillis [sec] "+(float)elapsed_time2/1000+" @ #frame:"+counterF);
                    }
                }).start();
            }*/


        //DRAW stuff---------------------------------------------------------------------------------------
        ////   Imgproc.cvtColor(mRgba,mRgba, Imgproc.COLOR_RGB2RGBA);//new


        //if (detectedFacesArray.length > 0) {
        /*
        if (!detectedFaces.empty()) {//ok
            Log.i(TAG, "#detectedFacesArray (DRAW):"+detectedFacesArray.length+"@ #frame:"+counterF);
            for (Rect rect : detectedFacesArray) {
                Imgproc.rectangle(mRgba, rect.tl(), rect.br(), DETECT_BOX_COLOR, 3);
            }
        }

        ////if (trackedFacesArray.length > 0) {
        if (!trackedFaces.empty()) {//ok
            Log.i(TAG, "#trackedFacesArray (DRAW):"+trackedFacesArray.length+"@ #frame:"+counterF);
            for (Rect rect : trackedFacesArray) {
                Imgproc.rectangle(mRgba, rect.tl(), rect.br(), TRACKER_BOX_COLOR, 13);
            }
        }


        Imgproc.putText(mRgba, String.valueOf(counterF), new Point(50, 50), 3, 3,
                new Scalar(255, 0, 0, 255), 3);

        //------------------------------------------------------------------------------------------------
        */


        return detectedFaces;
    }



    private native void OpenCVdetector(long imageRgba, long faces);
    private native void OpenCVtracker(long imageRgba, long faces);
    private native void loadResources();
}
