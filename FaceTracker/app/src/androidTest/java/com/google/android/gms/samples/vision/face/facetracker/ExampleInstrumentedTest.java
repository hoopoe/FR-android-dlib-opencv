package com.google.android.gms.samples.vision.face.facetracker;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Environment;
import android.support.test.InstrumentationRegistry;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.util.Log;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import android.util.SparseArray;
import com.google.android.gms.vision.Detector;
import com.google.android.gms.vision.face.FaceDetector;
import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.face.Face;

import static junit.framework.Assert.assertEquals;

@RunWith(AndroidJUnit4.class)
public class ExampleInstrumentedTest {

    private static final        String TAG = "GMS-Test";
    private Detector<Face>      mDetector;
    private String              filename = "GMS-SolvayRes.png";
    private String              AppResPath = "/myAppRes/";

    /*
    @Test
    public void useAppContext() throws Exception {
        Log.i(TAG, "In useAppContext ");
        Context appContext = InstrumentationRegistry.getTargetContext();
        assertEquals("com.google.android.gms.samples.vision.face.facetracker", appContext.getPackageName());
    }

    @Rule
    public ActivityTestRule<FaceTrackerActivity> activityRule = new ActivityTestRule(FaceTrackerActivity.class);

    @Test
    public void callAddContext()  {
        Log.i(TAG, "In callAddContext ");
        FaceTrackerActivity activity  = activityRule.getActivity();
//      int res = activity.nativeAdd(1,2);
//      assertEquals(3, res);
    }
    */

    @Test
    public void gmsDetectionTest() throws IOException {

        Context testContext = InstrumentationRegistry.getInstrumentation().getContext();
        Context appContext =  InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();

        //InputStream testInput = testContext.getAssets().open("11.png");
        InputStream testInput = testContext.getAssets().open("SolvayConf.jpg");
        Bitmap bitmap = BitmapFactory.decodeStream(testInput);
        Log.d(TAG, bitmap.getWidth() + " " + bitmap.getHeight());

        FaceDetector mDetector = new FaceDetector.Builder(appContext) // using testContext didn't work
                .setTrackingEnabled(true)
                .setClassificationType(FaceDetector.NO_CLASSIFICATIONS)
                .setProminentFaceOnly(false)
                .setMode(FaceDetector.ACCURATE_MODE)
                .setMinFaceSize(0.015f)
                .build();

        SparseArray<Face> faces = null;

        if (!mDetector.isOperational()) {
            Log.i(TAG, "Detector is NOT operational ");
        } else {
            Frame frame = new Frame.Builder().setBitmap(bitmap).build();
            faces = mDetector.detect(frame);
            mDetector.release();
            Log.i(TAG, "GMS-TEST Number of faces = " + faces.size());
        }

        Bitmap mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(mutableBitmap); // Create a canvas instance pointing to the bitmap

        Paint paint = new Paint();
        paint.setColor(Color.RED);
        paint.setStrokeWidth(5.0f);
        paint.setStyle(Paint.Style.STROKE);

        // all that is drawn on canvas it is also on the bitmap
        for(int i=0; i < faces.size(); i++){
            int key = faces.keyAt(i);
            Face face = faces.get(key); // get object by the key
            // Face face = mFaces.get(i);

            canvas.drawRect(face.getPosition().x, face.getPosition().y,
                            face.getPosition().x + face.getWidth(),
                            face.getPosition().y + face.getHeight(), paint);
        }

        try {
            // get SD Card dir
            String SDCardPath = Environment.getExternalStorageDirectory().toString();
            new File(SDCardPath + AppResPath).mkdirs();
            File imageFile = new File(SDCardPath + AppResPath + filename);
            Log.i(TAG,"file= " + imageFile);

            FileOutputStream outStream = new FileOutputStream(imageFile);
            // compress BitMap and write image to the OutputStream
            mutableBitmap.compress(Bitmap.CompressFormat.PNG, 80, outStream); // 100 = full quality

            outStream.flush();
            outStream.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
