package org.opencv.android.facetracker;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;

import android.app.Activity;
import android.content.Context;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.os.SystemClock;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.content.Intent;
import android.util.SparseArray;
import android.view.Display;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.RelativeLayout;

import com.google.android.gms.samples.vision.face.facetracker.R;
import com.google.android.gms.samples.vision.face.facetracker.FaceTrackerActivity;
import com.google.android.gms.vision.face.Face;
import java.util.List;//mic
//import java.io.IOException;
//import com.digi.android.system.cpu.CPUManager;


public class OpenCvActivity extends AppCompatActivity implements CameraBridgeViewBase.CvCameraViewListener2 {

    private static final String TAG = "OCV-Activity";
    private static final Scalar DETECT_BOX_COLOR   = new Scalar(255, 255, 0, 255);//yellow
    private static final Scalar TRACKER_BOX_COLOR   = new Scalar(0, 0, 255, 255);//blue
    private static final Scalar cyan_BOX_COLOR   = new Scalar(0, 255, 255, 255);//blue
    private Mat mRgba;
    private Mat mGray;
    private CameraBridgeViewBase mOpenCvCameraView;
    private RelativeLayout       mRelativeLayout;
    private Camera               mCamera;
    private CascadeClassifier    mJavaDetector;
    HaarDetector hd = new HaarDetector();
    Button mBtnSwitch;
    int cameraId = -1;
    long prev = 0;
    int counterF=0;
    boolean okThread = false;
    boolean okThreadT = false;
    int dn = 0;
    int tn = 0;
    int numThread=0;
    boolean FirstTime = true;
    boolean newFaceFound = false;
    //volatile boolean newFaceFound = false;

   // public static MatOfRect detectedFaces;//uncommented here

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {

        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS: {
                    Log.d(TAG, "OpenCV loaded successfully");
                    // Load native library after(!) OpenCV initialization
                    hd.loadNative();
                   //uncommented here================================
                   /* try{
                        detectedFaces = new MatOfRect();
                        Log.i(TAG, "detectedFaces Creation: ");
                    }catch (Exception e) {
                        e.printStackTrace();
                        Log.e(TAG, "Failed detectedFace creation: " + e);
                    }*/
                    //==============================
                    mOpenCvCameraView.enableView();
                }
                break;
                default: {
                    super.onManagerConnected(status);
                }
                break;
            }
        }
    };

    public OpenCvActivity() { Log.i(TAG, "Instantiated " + this.getClass()); }


    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "called onCreate");

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_open_cv);

        mOpenCvCameraView = (CameraBridgeViewBase) findViewById(R.id.main_surface);
        mOpenCvCameraView.setMaxFrameSize(1920, 1080);//1080p: 1920x1080
        mOpenCvCameraView.enableFpsMeter();


        // what are the following used for?
        mOpenCvCameraView.setVisibility(CameraBridgeViewBase.VISIBLE);
        mOpenCvCameraView.setCvCameraViewListener(this);
        Log.i(TAG,"#processor:"+Runtime.getRuntime().availableProcessors());
        onListenButton();
    }


    private void onListenButton() {
        Log.d(TAG, "called onListenButton");
        mBtnSwitch = (Button) findViewById(R.id.OCVbtnSwitch);
        mBtnSwitch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent myIntent = new Intent(OpenCvActivity.this, FaceTrackerActivity.class);
                OpenCvActivity.this.startActivity(myIntent);
            }
        });
    }

    public void onCameraViewStarted(int width, int height) {
        mRgba = new Mat();
        mGray = new Mat();
    }


    public void onCameraViewStopped() {
        mRgba.release();
        mGray.release();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "OpenCV library not found");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION, this, mLoaderCallback);
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mOpenCvCameraView.disableView();
    }

    private Rect[] detectedFacesArray = {};
    private Rect[] trackedFacesArray = {};

    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        Log.d(TAG, "called onCameraFrame");

        mRgba = inputFrame.rgba();
        mGray = inputFrame.gray();


        long currentime = SystemClock.elapsedRealtime(); // elapsed time is measured in milliseconds
        Log.i(TAG,"framerate = " + 1000.0/(currentime-prev) + " fps");
        Log.i(TAG,"Rgba.rows: " + mRgba.rows() + " Rgba.cols: " + mRgba.cols() + " Rgba.width" + mRgba.width() +" Rgba.height:"+mRgba.height());

        //compute how long time it's displayed the image----------
        Log.i(TAG,"1/framerate = " + (currentime-prev)/1000.0 + " [sec]");
        //--------------------------------------------------------
        prev = currentime;


        final MatOfRect detectedFaces = new MatOfRect();//commented here
        final MatOfRect trackedFaces = new MatOfRect();

        counterF++;
        Log.i(TAG,"#frame:"+ counterF);
        if(!okThread) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    okThread=true;

                    Log.i(TAG, "Detection thread_START @ #frame:"+counterF);
                   // detectedFaces.release();
                    hd.OCvDetect(mRgba, detectedFaces);
                    if(!detectedFaces.empty()) {
                      /*  newFaceFound = true;
                        //Log.i(TAG, "A new Face is found -> "+newFaceFound+" @ #frame:"+counterF);
                        detectedFacesArray = detectedFaces.toArray();*/


                       // detectedFacesArray=null;
                        detectedFacesArray = detectedFaces.toArray();
                        dn=detectedFacesArray.length;
                        Log.i(TAG,"#detectedFaces:"+dn);
                        if(dn>0){
                            newFaceFound = true;
                            Log.i(TAG, "A new Face is found!");}

                    }

                    okThread=false;
                    Log.i(TAG, "Detection thread_END @ #frame:"+counterF);
                }
            }).start();
        }

       // if(!detectedFaces.empty()) {
            if (detectedFacesArray.length > 0) {
                for (Rect rect : detectedFacesArray) {
                    Imgproc.rectangle(mRgba, rect.tl(), rect.br(), DETECT_BOX_COLOR, 3);
                }
            }
      //  }


        //=========================
        Log.i(TAG,"#detectedFaces_fuoriTT:"+detectedFacesArray.length+" @ #frame:"+counterF);

     /*   if(!okThreadT) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    okThreadT = true;
                    Log.i(TAG, "Tracking thread_START @ #frame:"+counterF);
                    trackedFaces.release();

                    if(newFaceFound) {
                        Log.i(TAG, "New faces found -> trackedFaces is populated using detectedFace @ #frame:"+counterF);
                        trackedFaces.fromArray(detectedFacesArray);//here trackedFaces is populated
                    }
                    else{
                        Log.i(TAG, "trackedFaces is populated using trackedFaces @ #frame:"+counterF);
                        trackedFaces.fromArray(trackedFacesArray);//here trackedFaces is populated
                    }
                    if (!trackedFaces.empty()){
                        hd.OCvTrack(mRgba, trackedFaces);
                        trackedFacesArray = trackedFaces.toArray();
                        tn = trackedFacesArray.length;
                        Log.i(TAG, "#trackeFacesArray_insideThread: " +tn);
                        //to cancel----------------------------------------------------
                        for (int i=0;i<tn; i++) {
                            Log.i(TAG, "CONTROLLO -> trackedFaces (x,y,w,h): " +trackedFacesArray[i]+" @ #frame:"+counterF);
                        }
                        //-------------------------------------------------------------
                        if(tn>0) {
                            //FirstTime = false;
                            newFaceFound = false;
                        }
                    }

                    okThreadT = false;
                    Log.i(TAG, "Tracking thread_END @ #frame:"+counterF);
                }
            }).start();
        }


        for (Rect rect : trackedFacesArray) {
            Imgproc.rectangle(mRgba, rect.tl(), rect.br(),  TRACKER_BOX_COLOR, 13);
        }*/

        Imgproc.putText(mRgba, String.valueOf(counterF), new Point(50, 50), 3, 3,
                    new Scalar(255, 0, 0, 255), 3);




        return mRgba;
    }

}