package org.opencv.android.facetracker;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;

import android.hardware.Camera;
import android.os.SystemClock;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.content.Intent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.RelativeLayout;

// to switch between activities
import com.google.android.gms.samples.vision.face.facetracker.R;
import com.google.android.gms.samples.vision.face.facetracker.FaceTrackerActivity;


public class OpenCvActivity extends AppCompatActivity implements CameraBridgeViewBase.CvCameraViewListener2 {

    private static final String TAG = "OCV-Activity";
    private static final Scalar DETECT_BOX_COLOR   = new Scalar(255, 255, 255, 255);
    private Mat mRgba;
    private Mat mGray;
    private CameraBridgeViewBase mOpenCvCameraView;
    private CascadeClassifier    mJavaDetector;
    HaarDetector hd = new HaarDetector();
    Button mBtnSwitch;
    private long prev = 0;


    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {

        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS: {
                    Log.d(TAG, "OpenCV loaded successfully");
                    // Load native library after(!) OpenCV initialization
                    hd.loadNative();

                    mJavaDetector = new CascadeClassifier("/sdcard/Download/haarcascade_frontalface_default.xml");
                    if (mJavaDetector.empty()) {
                        Log.e(TAG, "Failed to load Java cascade classifier ");
                        mJavaDetector = null;
                    } else
                        Log.i(TAG, "Loaded Java cascade classifier!!! " );

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

    public OpenCvActivity() {
        Log.i(TAG, "Instantiated " + this.getClass());
    }


    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "called onCreate");

        // getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN); //???
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        // getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN); //???

        setContentView(R.layout.activity_open_cv);

        /* Permissions for Android 6+
        ActivityCompat.requestPermissions(OpenCvActivity.this,
                new String[]{Manifest.permission.CAMERA},
                1);
        */

        mOpenCvCameraView = (CameraBridgeViewBase) findViewById(R.id.main_surface);
        mOpenCvCameraView.setVisibility(CameraBridgeViewBase.VISIBLE);
        //mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
        mOpenCvCameraView.setCvCameraViewListener(this);


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
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_3_0, this, mLoaderCallback);
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


    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        Log.d(TAG, "called onCameraFrame");

        mRgba = inputFrame.rgba();
        mGray = inputFrame.gray();

        long currentime = 0;

        Log.i(TAG,"In onCameraFrame");

        currentime = SystemClock.elapsedRealtime(); // elapsed time is measured in milliseconds
        Log.i(TAG,"framerate = " + 1000.0/(currentime-prev) + " fps");
        prev = currentime;

        Log.i(TAG,"WIDTH = " + mRgba.width() + "   COLS = " + mRgba.cols());
        Log.i(TAG,"HEIGHT = " + mRgba.height() + "   ROWS = " + mRgba.rows());



        MatOfRect faces = new MatOfRect();

        long start = System.currentTimeMillis();
        hd.OCvDetect(mGray, faces);
        //mJavaDetector.detectMultiScale(mGray, faces, 1.1, 3, 0,new Size(), new Size());
        long end = System.currentTimeMillis();
        long duration = end -start;
        Log.i(TAG,"FD OCV Exectime = " + duration/1000.0 + " sec");


        Rect[] facesArray = faces.toArray();
        for (int i = 0; i < facesArray.length; i++) {
            Imgproc.rectangle(mRgba, facesArray[i].tl(), facesArray[i].br(), DETECT_BOX_COLOR, 3);
            Log.i(TAG,"face "+i + "= "+ facesArray[i].tl()+"  "+ facesArray[i].br());
        }
        return mRgba;
    }

}