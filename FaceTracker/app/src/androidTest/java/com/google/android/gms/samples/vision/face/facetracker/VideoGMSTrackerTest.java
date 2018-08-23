package com.google.android.gms.samples.vision.face.facetracker;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Environment;
import android.support.test.InstrumentationRegistry;
import android.util.Log;
import android.util.SparseArray;

import com.google.android.gms.vision.Detector;
import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.face.Face;
import com.google.android.gms.vision.face.FaceDetector;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;

import static xdroid.core.Global.getContext;

/**
 * Created by alorusso on 21/08/18.
 */

public class VideoGMSTrackerTest {

    private static final        String TAG = "GMS-Test";
    private Detector<Face>      mDetector;
    private String              filename   = "GMS-SolvayRes.png";
    private String              AppResPath = "/myAppRes/";
    private String              AppPath    = "/myAppFolder/";
    private String              videoname  = "v1465.mp4";
    private String              outImgName = "/GMS-frame";

    @Test
    public void gmsVideoTest() throws IOException {

        Context testContext = InstrumentationRegistry.getInstrumentation().getContext();
        Context appContext =  InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();

        // video reading
        File videoFile=new File(Environment.getExternalStorageDirectory().getAbsolutePath()+ AppPath + videoname);
        Log.i(TAG,"Video-path = " + videoFile.toString());
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        retriever.setDataSource(getContext(), Uri.parse(videoFile.getAbsolutePath()));

        ArrayList<Bitmap> rev=new ArrayList<Bitmap>();

        // Create a new Media Player to get video metadata
        Uri videoFileUri=Uri.parse(videoFile.toString());
        MediaPlayer mp = MediaPlayer.create(getContext(), videoFileUri);
        // mp.start(); // plays video
        int millis = mp.getDuration(); // time in millisecs
        Log.i(TAG,"video duration (msec)= " + millis);


        //for(int i=0, j=0;i<millis*1000; j++, i+=1000000) {
        for(int i=0, j=0;i<18000000; j++, i+=1000000) {
                Bitmap bitmap = retriever.getFrameAtTime(i, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                if (bitmap != null) {

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
                    for(int k=0; k<faces.size(); k++){
                        int key = faces.keyAt(k);
                        Face face = faces.get(key); // get object by the key
                        // Face face = mFaces.get(k);

                        Log.i(TAG, "x, y, w, h = " + face.getPosition().x + face.getPosition().y +
                                face.getWidth() + face.getHeight());
                        canvas.drawRect(face.getPosition().x, face.getPosition().y,
                                face.getPosition().x + face.getWidth(),
                                face.getPosition().y + face.getHeight(), paint);
                    }

                    rev.add(mutableBitmap); // add image to a list to be written to memory
                    Log.i(TAG, "added frame = " +i+"  "+ j);
                }
        }

        saveImage(rev); // save frames
        mp.release();
    }


    public void saveImage(ArrayList<Bitmap> saveBitmapList) throws IOException{

        String SDCardPath = Environment.getExternalStorageDirectory().toString();
        File saveFolder = new File(SDCardPath + AppResPath);
        if(!saveFolder.exists()){
            saveFolder.mkdirs();
        }


        int i=0;
        for (Bitmap b : saveBitmapList){
            if(b != null) {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                b.compress(Bitmap.CompressFormat.JPEG, 40, bytes);

                i++;
                File myFile = new File(saveFolder + outImgName + i + ".jpg");
                Log.i(TAG, "writing image file = " + i +"   "+ myFile);

                if (!myFile.exists())
                    myFile.createNewFile();

                FileOutputStream fo = new FileOutputStream(myFile);
                fo.write(bytes.toByteArray());

                fo.flush();
                fo.close();
            }
        }
    }
}
