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
//import android.support.test.InstrumentationRegistry;
import android.util.Log;

//import com.google.android.gms.samples.vision.face.facetracker.R;

import org.junit.Test;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import org.opencv.videoio.VideoCapture;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;


import static xdroid.core.Global.getContext;
//import static xdroid.core.Global.getResources;



public class VideoHaarDetectorTest {

    private static final String     TAG                = "OCV-FDT-Test";
    private static final Scalar     DETECT_BOX_COLOR   = new Scalar(0, 255, 0, 255);
    private String                  AppPath    = "/myAppFolder/";
    private String                  AppResPath = "/myAppRes/";
    //private String                  videoname  = "v14651.mp4";
    private String                  videoname  = "v1465.mp4";
    private String                  outImgName = "/FDT-frame";
    private String                  AppResSDcardDir = "/myAppDataRes/";
    private CascadeClassifier       mJavaDetector;
    private String                  AppResTxtFile = "FDT-TestRes.txt";
    private OutputStreamWriter      fos           = null;
    private int                     imgCount      = 0;
    private int                     frameNum      = 0;


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

        long i=0, j=0;
        // for(long i=0, j=0;i<millis*1000; j++, i+=33334) {
        while (i<millis*1000) {
            //while (i<20000000) {
            Mat rgbaImg = new Mat();
            Bitmap bitmap = retriever.getFrameAtTime(i, MediaMetadataRetriever.OPTION_CLOSEST);
            frameNum ++;
            if (bitmap != null) {
                MatOfRect faces = new MatOfRect();
                /* supported Bitmap = 'ARGB_8888' or 'RGB_565'
                 * Output size as the input Bitmap size, type is CV_8UC4,
                 * it keeps the image in RGBA format.
                */
                Utils.bitmapToMat(bitmap, rgbaImg);
                Log.i(TAG,"Matrix:  " + rgbaImg.type() +"  "+ rgbaImg.cols()+"  " + rgbaImg.rows());

                long start = System.currentTimeMillis();
                hd.testOCvDetect(rgbaImg, faces); // the detector will then convert the images from RGBA into GRAY
                long end = System.currentTimeMillis();
                long duration = end -start;
                Log.i(TAG,"FDT-Activity Time " + duration/1000.0 + " sec");

                Utils.bitmapToMat(bitmap, rgbaImg); // copy again RGB img in Mat
                Log.i(TAG, "Number of faces = " + faces.toArray().length);

                try {
                    if (faces.toArray().length == 0) {
                        Log.i(TAG, "Faces# inside IF = " + faces.toArray().length);
                        fos.write(Integer.toString(0) + "," + Integer.toString(0) + "," +
                                Integer.toString(0) + "," + Integer.toString(0) + ";"+"\n"); // write to file
                        fos.flush();
                    }
                    else {
                        Log.i(TAG, "Faces# inside else = " + faces.toArray().length);
                        for (int k = 0; k < faces.toArray().length; k++) {
                            Imgproc.rectangle(rgbaImg, faces.toArray()[k].tl(), faces.toArray()[k].br(), DETECT_BOX_COLOR, 3);

                            fos.write(Integer.toString((int) faces.toArray()[k].tl().x) + "," + Integer.toString((int) faces.toArray()[k].tl().y) + "," +
                                    Integer.toString((int) (faces.toArray()[k].br().x - faces.toArray()[k].tl().x)) + "," +
                                    Integer.toString((int) (faces.toArray()[k].br().y - faces.toArray()[k].tl().y)) + ";");
                            fos.flush();
                        }
                        fos.write("\n");
                    }
                } catch (FileNotFoundException e) {
                        System.out.println("Exception e: " + e);
                }

                Imgproc.putText(rgbaImg, String.valueOf(frameNum), new Point(30,90), 3, 3,
                        new Scalar(0, 0, 0, 0), 5);

                bitmap = Bitmap.createBitmap(rgbaImg.cols(),rgbaImg.rows(),Bitmap.Config. ARGB_8888); // Each pixel is stored on 4 bytes. Each channel (RGB and alpha for translucency) is stored with 8 bits of precision (256 possible values.) This configuration is very flexible and offers the best quality.
                Utils.matToBitmap(rgbaImg,bitmap); // copy img with detection boxes in bitmap to be saved
                saveImage(bitmap);
                Log.i(TAG, "added frame = " +i+"  "+ j);
                //Log.i(TAG, "added frame = " +(++i));
                faces.release();
            } else {
                Log.i(TAG, "END of SEQUENCE or NULL BITMAP!!");
                break;
            }
            rgbaImg.release();
            j++;
            //i+=33334;
            i+=1000000;
        }
        fos.close();
        mp.release();
        if(fOut != null) fOut.close();
    }


    public void saveImage(Bitmap saveBitmap) throws IOException{

        String SDCardPath = Environment.getExternalStorageDirectory().toString();
        File saveFolder = new File(SDCardPath + AppResPath);
        if(!saveFolder.exists()) {
            saveFolder.mkdirs();
        }

        if(saveBitmap != null) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            saveBitmap.compress(Bitmap.CompressFormat.JPEG, 40, bytes);

            imgCount++;
            File f = new File(saveFolder + outImgName + imgCount + ".jpg");
            Log.i(TAG, "writing file = " + imgCount +"   "+ f);
            f.createNewFile();

            FileOutputStream fo = new FileOutputStream(f);
            fo.write(bytes.toByteArray());

            fo.flush();
            fo.close();
        }
    }


}
