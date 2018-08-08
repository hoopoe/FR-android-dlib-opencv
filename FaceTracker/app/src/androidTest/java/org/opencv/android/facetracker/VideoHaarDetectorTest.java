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
import java.io.InputStream;
import java.util.ArrayList;

import opencv.android.fdt.DetectionBasedTracker;

import static xdroid.core.Global.getContext;
import static xdroid.core.Global.getResources;



public class VideoHaarDetectorTest {

    private static final String     TAG = "FD-Test";
    private static final Scalar     DETECT_BOX_COLOR   = new Scalar(0, 255, 0, 255);
    private String                  AppPath    = "/myAppFolder/";
    private String                  AppResPath = "/myAppRes/";
    private String                  videoname  = "v1465.mp4";
    private String                  outImgName = "/FD-frame";
    private DetectionBasedTracker   nativeDetect;


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

                for (int k=0; k<faces.length; k++)
                    Imgproc.rectangle(matImg, faces[k].tl(),faces[k].br(), DETECT_BOX_COLOR,3);

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

