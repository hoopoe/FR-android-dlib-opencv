package opencv.android.fdt;

/**
 * Created by alorusso on 27/07/18.
 */

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.util.Scanner;
import java.io.BufferedReader;
import java.io.InputStreamReader;

import org.junit.Test;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size; // needed for Java HAAR call
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;

import android.os.Environment;
import android.support.test.InstrumentationRegistry;
import android.util.Log;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import static xdroid.core.Global.getResources;
import com.google.android.gms.samples.vision.face.facetracker.R;



public class DetectionBasedTrackerTest {

    private static final String     TAG = "FDbT-Test";
    private static final Scalar     DETECT_BOX_COLOR   = new Scalar(255, 255, 255, 255);
    private String                  filename = "FDbT-SolvayRes.png";
    private File                    mCascadeFile;
    private String                  AppResPath = "/myAppRes/";
    private DetectionBasedTracker   nativeDetect;
    private CascadeClassifier       mJavaDetector;

    static {
        System.loadLibrary("opencv_java3");
        System.loadLibrary("OCV-DetectionBasedTracker");
    }

    public DetectionBasedTrackerTest() {
    }


    @Test
    public void OCVDetectTrackTest() throws IOException {

        Context testContext = InstrumentationRegistry.getInstrumentation().getContext();
        InputStream is = getResources().openRawResource(R.raw.haarcascade_frontalface_default);


        Context appContext =  InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();
        File cascadeDir = appContext.getDir("cascade", Context.MODE_PRIVATE);
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

        nativeDetect = new DetectionBasedTracker(mCascadeFile.getAbsolutePath(), 0);
        mJavaDetector = new CascadeClassifier(mCascadeFile.getAbsolutePath());

        // read input from Assets
        InputStream testInput = testContext.getAssets().open("11.png");
        // InputStream testInput = testContext.getAssets().open("collage.jpg");
        // InputStream testInput = testContext.getAssets().open("SolvayConf.jpg");
        Bitmap bitmap = BitmapFactory.decodeStream(testInput);

        Mat matImg = new Mat();
        Utils.bitmapToMat(bitmap, matImg);
        Imgproc.cvtColor(matImg, matImg, Imgproc.COLOR_RGB2GRAY);

        MatOfRect rectList = new MatOfRect();
        nativeDetect.detect(matImg, rectList);
        //mJavaDetector.detectMultiScale(matImg, rectList, 1.1, 3, 0, // TODO: objdetect.CV_HAAR_SCALE_IMAGE
        //       new Size(15,15), new Size(1000,1000));
        Rect[] faces = rectList.toArray();
        Utils.bitmapToMat(bitmap, matImg);
        Log.i(TAG, "Number of faces = " + faces.length);

        for (int j=0; j<faces.length; j++)
             Imgproc.rectangle(matImg, faces[j].tl(),faces[j].br(), DETECT_BOX_COLOR,3);

        testInput.close();

        try {
            // get SD Card dir
            String SDCardPath = Environment.getExternalStorageDirectory().toString();
            new File(SDCardPath + AppResPath).mkdirs();
            File imageFile = new File(SDCardPath + AppResPath + filename);

            Log.i(TAG,"file= " + imageFile);
            Utils.matToBitmap(matImg,bitmap);
            FileOutputStream outStream = new FileOutputStream(imageFile);
            // compress BitMap to write image to the OutputStream
            bitmap.compress(Bitmap.CompressFormat.PNG, 80, outStream); // 100 = full quality

            outStream.flush();
            outStream.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
