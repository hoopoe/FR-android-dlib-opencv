package org.opencv.android.facetracker;

/**
 * Created by alorusso.
 */

import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Environment;
import android.support.test.InstrumentationRegistry;
import android.util.Log;

import com.google.android.gms.samples.vision.face.facetracker.R;

import org.junit.Test;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;


import static xdroid.core.Global.getContext;
import static xdroid.core.Global.getResources;



public class VideoHaarDetectorTest {

    private static final String     TAG = "FDT-Test";
    private static final Scalar     DETECT_BOX_COLOR   = new Scalar(0, 255, 0, 255);
    private String                  AppPath    = "/myAppFolder/";
    private String                  AppResPath = "/myAppRes/";
    //private String                  videoname  = "v1465.mp4";
    private String                  videoname  = "Trellis.mp4";
    private String                  outImgName = "/FDT-frame";
    private String                  AppResSDcardDir = "/myAppDataRes/";
    private CascadeClassifier       mJavaDetector;
    private String                  AppResTxtFile = "FDT-TestRes.txt";
    private OutputStreamWriter      fos = null;


    static {
        System.loadLibrary("opencv_java3");
    }

    public VideoHaarDetectorTest() {
    }


    @Test
    public void VideoOCVDetectorTest() throws IOException {

        HaarDetector hd = new HaarDetector();
        hd.loadNative();
        Log.i(TAG, "Loaded Native" );


        // video reading
        File videoFile=new File(Environment.getExternalStorageDirectory().getAbsolutePath()+ AppPath + videoname);
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        retriever.setDataSource(getContext(), Uri.parse(videoFile.getAbsolutePath()));

        // Create a dir in SD card to store txt file for test results
        File newFolder = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + AppResSDcardDir);
        try {
            if (!newFolder.exists())
                newFolder.mkdir();
        } catch (Exception e) {
            System.out.println("Exception e: " + e);
        }
        File file = new File(newFolder, AppResTxtFile);
        try {
            file.createNewFile();
        } catch (Exception ex) {
            System.out.println("Exception ex: " + ex);
        }
        FileOutputStream fOut = null;
        try {
            fOut = new FileOutputStream(file, false); // delete file if it exists
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        if(fOut != null) fos = new OutputStreamWriter(fOut);
        // end creation result file on SD card

        ArrayList<Bitmap> rev=new ArrayList<Bitmap>();

        // Create a new Media Player to get video metadata
        Uri videoFileUri=Uri.parse(videoFile.toString());
        MediaPlayer mp = MediaPlayer.create(getContext(), videoFileUri);
        // mp.start(); // plays video
        int millis = mp.getDuration(); // time in millisecs
        Log.i(TAG,"video duration (msec)= " + millis);


        for(int i=0, j=0;i<millis*1000; j++, i+=1000000) {

            Bitmap bitmap = retriever.getFrameAtTime(i, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
            if (bitmap != null) {

                Mat matImg = new Mat();
                Utils.bitmapToMat(bitmap, matImg);
                Imgproc.cvtColor(matImg, matImg, Imgproc.COLOR_RGB2GRAY);

                MatOfRect rectList = new MatOfRect();
                hd.OCvDetect(matImg, rectList);

                Rect[] faces = rectList.toArray();
                Utils.bitmapToMat(bitmap, matImg); // copy again RGB img in Mat
                Log.i(TAG, "Number of faces = " + faces.length);

                try {
                    if (faces.length == 0) {
                        fos.write(Integer.toString(0) + "," + Integer.toString(0) + "," +
                                Integer.toString(0) + "," + Integer.toString(0) + ";"+"\n"); // write to file
                        fos.flush();
                    }
                    else {
                        for (int k = 0; k < faces.length; k++) {
                            Imgproc.rectangle(matImg, faces[k].tl(), faces[k].br(), DETECT_BOX_COLOR, 3);

                            fos.write(Integer.toString((int) faces[k].tl().x) + "," + Integer.toString((int) faces[k].tl().y) + "," +
                                    Integer.toString((int) (faces[k].br().x - faces[k].tl().x)) + "," + Integer.toString((int) (faces[k].br().y - faces[k].tl().y)) + ";");
                            fos.flush();
                        }
                        fos.write("\n");
                    }
                }catch (FileNotFoundException e) {
                        System.out.println("Exception e: " + e);
                }

                Utils.matToBitmap(matImg,bitmap); // convert Mat to bitmap before saving to memory

                rev.add(bitmap); // add to a list of images that will be written all together on memory
                Log.i(TAG, " frame i = " + i + " added frame =" + j);
            }
        }

        saveImage(rev); // save frames
        mp.release();
    }


    public void saveImage(ArrayList<Bitmap> saveBitmapList) throws IOException{

        String SDCardPath = Environment.getExternalStorageDirectory().toString();
        File saveFolder = new File(SDCardPath + AppResPath);
        if(!saveFolder.exists()) {
            saveFolder.mkdirs();
        }

        int i=0;
        for (Bitmap b : saveBitmapList){
            if(b != null) {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                b.compress(Bitmap.CompressFormat.JPEG, 40, bytes);

                i++;
                File f = new File(saveFolder + outImgName + i + ".jpg");
                Log.i(TAG, "writing file = " + i +"   "+ f);
                f.createNewFile();

                FileOutputStream fo = new FileOutputStream(f);
                fo.write(bytes.toByteArray());

                fo.flush();
                fo.close();
            }
        }
    }
}

