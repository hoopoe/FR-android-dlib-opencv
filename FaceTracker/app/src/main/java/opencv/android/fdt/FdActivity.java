package opencv.android.fdt;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;

import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

// To switch activity
import com.google.android.gms.samples.vision.face.facetracker.FaceTrackerActivity;
import com.google.android.gms.samples.vision.face.facetracker.R;

import tensorflow.detector.spc.CameraActivityMain;

/**
 * Created by alorusso on 12/07/18.
 */


public class FdActivity extends AppCompatActivity implements CameraBridgeViewBase.CvCameraViewListener2 {

    private static final String    TAG                 = "Fd-Activity";
    private static final Scalar    FACE_RECT_COLOR     = new Scalar(255, 255, 255, 255);

    private Button                 mBtnBack;
    private Button                 mBtnSwitch;

    private Mat                    mRgba;
    private Mat                    mGray;
    private File                   mCascadeFile;
    private CascadeClassifier      mJavaDetector;
    private DetectionBasedTracker  mNativeDetector;
    private int                    mAbsoluteFaceSize   = 1;
    private CameraBridgeViewBase   mOpenCvCameraView;
    private long                   prev = 0;
    private int                    framenum=0;

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {

        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    Log.i(TAG, "OpenCV loaded successfully");

                    // Load native library after(!) OpenCV initialization
                    System.loadLibrary("OCV-DetectionBasedTracker");
                    Log.i(TAG, "DetectionBasedTracker class library loaded");

                    try {
                        // load cascade file from application resources
                        InputStream is = getResources().openRawResource(R.raw.haarcascade_frontalface_default);
                        File cascadeDir = getDir("cascade", Context.MODE_PRIVATE);
                        Log.i(TAG, "after openRawResource");
                        mCascadeFile = new File(cascadeDir, "haarcascade_frontalface_default.xml");

                        Log.i(TAG, "cascadeDir " + cascadeDir);
                        Log.i(TAG, "cascadePath " + mCascadeFile.getAbsolutePath());

                        FileOutputStream os = new FileOutputStream(mCascadeFile);

                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        while ((bytesRead = is.read(buffer)) != -1) {
                            os.write(buffer, 0, bytesRead);
                        }

                        os.close();
                        is.close();
                        cascadeDir.delete();

                        mNativeDetector = new DetectionBasedTracker(mCascadeFile.getAbsolutePath(), 0);
                        mJavaDetector = new CascadeClassifier(mCascadeFile.getAbsolutePath());

                    } catch (IOException e) {
                        e.printStackTrace();
                        Log.e(TAG, "Failed to load cascade. Exception thrown: " + e);
                    }

                    mOpenCvCameraView.enableView();
                } break;
                default:
                {
                    super.onManagerConnected(status);
                } break;
            }
        }
    };


    public FdActivity() {
        Log.i(TAG, "Instantiated new " + this.getClass());
    }


    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "called onCreate");
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.face_detect_surface_view);

        mOpenCvCameraView = (CameraBridgeViewBase) findViewById(R.id.fd_activity_surface_view);
        mOpenCvCameraView.setVisibility(CameraBridgeViewBase.VISIBLE);
        mOpenCvCameraView.setCvCameraViewListener(this);

        onListenButton();
    }


    private void onListenButton() {
        Log.d(TAG, "called onListenButton");
        mBtnBack = (Button) findViewById(R.id.btnGMS);
        mBtnSwitch = (Button) findViewById(R.id.btnTF);

        mBtnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent myIntent = new Intent(FdActivity.this, FaceTrackerActivity.class);
                FdActivity.this.startActivity(myIntent);
            }
        });

        mBtnSwitch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent myIntent = new Intent(FdActivity.this, CameraActivityMain.class);
                FdActivity.this.startActivity(myIntent);
            }
        });
    }



    @Override
    public void onPause()
    {
        super.onPause();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    public void onResume()
    {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_3_0, this, mLoaderCallback);
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    public void onDestroy() {
        super.onDestroy();
        mOpenCvCameraView.disableView();
    }

    public void onCameraViewStarted(int width, int height) {
        mGray = new Mat();
        mRgba = new Mat();
    }

    public void onCameraViewStopped() {
        mGray.release();
        mRgba.release();
    }

    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {

        long currentime = 0;
        mRgba = inputFrame.rgba();
        mGray = inputFrame.gray();

        Log.i(TAG,"In onCameraFrame");

        currentime = SystemClock.elapsedRealtime(); // elapsed time is measured in milliseconds
        Log.i(TAG,"FdActivity: framerate = " + 1000.0/(currentime-prev) + " fps");
        prev = currentime;

        Log.i(TAG,"WIDTH = " + mRgba.width() + "   COLS = " + mRgba.cols());
        Log.i(TAG,"HEIGHT = " + mRgba.height() + "   ROWS = " + mRgba.rows());

        // mNativeDetector.setMinFaceSize(mAbsoluteFaceSize); // this is only needed if minFaceSize changes

        MatOfRect faces = new MatOfRect();

        long start = System.currentTimeMillis();

        //mJavaDetector.detectMultiScale(mGray, faces, 1.1, 2, 2, // TODO: objdetect.CV_HAAR_SCALE_IMAGE
        //        new Size(mAbsoluteFaceSize, mAbsoluteFaceSize), new Size());

        mNativeDetector.detect(mGray, faces);
        long end = System.currentTimeMillis();
        long duration = end -start;
        Log.i(TAG,"FdActivity: SAM Native Exectime = " + duration/1000.0 + " sec");
        Log.i(TAG,"FdActivity: NUM OF FACES = " + faces.size());

        Rect[] facesArray = faces.toArray();
        for (int i = 0; i < facesArray.length; i++)
            Imgproc.rectangle(mRgba, facesArray[i].tl(), facesArray[i].br(), FACE_RECT_COLOR, 2);

        framenum++;
        Imgproc.putText(mRgba, String.valueOf(framenum), new Point(10,30), 3, 1,
                new Scalar(255, 0, 0, 255), 3);

        return mRgba;
    }
}
