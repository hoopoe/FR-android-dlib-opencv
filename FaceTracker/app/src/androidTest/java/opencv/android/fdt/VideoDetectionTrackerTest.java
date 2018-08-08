package opencv.android.fdt;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import org.junit.Test;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;

import org.opencv.core.Size;  // needed for Java based HAAR call
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;

import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Environment;
import android.support.test.InstrumentationRegistry;
import android.util.Log;

import android.content.Context;
import android.graphics.Bitmap;

import static xdroid.core.Global.getContext;
import static xdroid.core.Global.getResources;
import com.google.android.gms.samples.vision.face.facetracker.R;

/**
 * Created by alorusso on 27/07/18.
 */

public class VideoDetectionTrackerTest {

    private static final String     TAG = "FDbT-Test";
    private static final Scalar     DETECT_BOX_COLOR   = new Scalar(255, 255, 255, 255);
    private File                    mCascadeFile;
    private String                  AppPath    = "/myAppFolder/";
    private String                  AppResPath = "/myAppRes/";
    private String                  videoname  = "v1465.mp4";
    private String                  outImgName = "/FDbT-frame";
    private DetectionBasedTracker   nativeDetect;
    private CascadeClassifier       mJavaDetector;


    static {
        System.loadLibrary("opencv_java3");
        System.loadLibrary("OCV-DetectionBasedTracker");
    }


    public VideoDetectionTrackerTest() {
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

                nativeDetect.detect(matImg, rectList);
                //mJavaDetector.detectMultiScale(matImg, rectList, 1.1, 3, 0, // TODO: objdetect.CV_HAAR_SCALE_IMAGE
                //       new Size(0,0), new Size(1000,1000));

                Rect[] faces = rectList.toArray();
                Utils.bitmapToMat(bitmap, matImg); // copy again RGB img in Mat
                Log.i(TAG, "Num of faces = " + faces.length);

                for (int k=0; k<faces.length; k++)
                     Imgproc.rectangle(matImg, faces[k].tl(),faces[k].br(), DETECT_BOX_COLOR,3);

                Utils.matToBitmap(matImg,bitmap); // convert Mat to bitmap before saving to memory
                rev.add(bitmap); // add image to a list to be written to memory
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
                File f = new File(saveFolder + outImgName + i + ".jpg");
                Log.i(TAG, "writing image file = " + i +"   "+ f);

                f.createNewFile();

                FileOutputStream fo = new FileOutputStream(f);
                fo.write(bytes.toByteArray());

                fo.flush();
                fo.close();
            }
        }
    }
}
