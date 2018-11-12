package org.opencv.android.facetracker;

import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Scalar;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import android.os.Environment;
import android.support.test.InstrumentationRegistry;
import android.util.Log;

import com.google.android.gms.samples.vision.face.facetracker.*;

import org.junit.Test;
import org.opencv.core.Rect;
import org.opencv.imgproc.Imgproc;



/**
 * Created by alorusso on 07/06/18.
 */

public class HaarDetectorTest {
    private static final String TAG = "OCV-Test";
    private static final Scalar DETECT_BOX_COLOR   = new Scalar(0, 255, 0, 255);
    String filename =   "FD-SolvayRes.png";
    String AppResPath = "/myAppRes/";


    static {
        System.loadLibrary("opencv_java3");
    }

    public HaarDetectorTest() {
    }


    @Test
    public void OCVTrackerTest() throws IOException {

        HaarDetector hd = new HaarDetector();
        hd.loadNative();

        Log.i(TAG, "Loaded Native" );

        Context testContext = InstrumentationRegistry.getInstrumentation().getContext();

        InputStream testInput = testContext.getAssets().open("SolvayConf.jpg");
        Bitmap bitmap = BitmapFactory.decodeStream(testInput);

        Mat matImg = new Mat();
        Utils.bitmapToMat(bitmap, matImg);
        Imgproc.cvtColor(matImg, matImg, Imgproc.COLOR_RGB2RGBA); // pass colour img

        MatOfRect rectList = new MatOfRect();
        hd.OCvDetect(matImg, rectList);

        Log.i(TAG, "Number of faces = " + rectList.size());
        Rect[] faces = rectList.toArray();
        Utils.bitmapToMat(bitmap, matImg);

        for (int i=0; i<faces.length; i++)
            Imgproc.rectangle(matImg, faces[i].tl(),faces[i].br(), DETECT_BOX_COLOR,3);


        try {
            // get SD Card dir
            String SDCardPath = Environment.getExternalStorageDirectory().toString();
            File saveFolder = new File(SDCardPath + AppResPath);
            if(!saveFolder.exists())
                saveFolder.mkdirs();

            // File imageFile = new File(SDCardPath + AppResPath + filename);
            File imageFile = new File(saveFolder, filename);
            if (!imageFile.exists())
                imageFile.createNewFile();

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
