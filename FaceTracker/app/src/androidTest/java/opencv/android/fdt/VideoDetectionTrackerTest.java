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
import org.opencv.videoio.VideoCapture;

import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Environment;
import android.support.test.InstrumentationRegistry;
import android.util.Log;

import android.content.Context;
import android.graphics.Bitmap;

import java.io.FileNotFoundException;
import java.io.OutputStreamWriter;

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
    //private String                  videoname  = "Girl.mp4";
    private String                  videoname  = "v1465.mp4";
    private String                  outImgName = "/FDbT-frame";
    private String                  AppResSDcardDir = "/myAppDataRes/";
    private DetectionBasedTracker   nativeDetect;
    private CascadeClassifier       mJavaDetector;
    private String                  AppResTxtFile = "FDbT-TestRes.txt";
    private OutputStreamWriter      fos = null;
    private int                     imgCount = 0;


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

        Log.i(TAG, "filename =" + videoFile.toString());

        int i = 0, j = 0;
        // for(long i=0, j=0;i<millis*1000; j++, i+=33334) {
        while (i<millis*1000) {

            Mat matImg = new Mat();
            Mat grayImg = new Mat();

            Bitmap bitmap = retriever.getFrameAtTime(i, MediaMetadataRetriever.OPTION_CLOSEST);
            if (bitmap != null) {

                Utils.bitmapToMat(bitmap, matImg);
                Imgproc.cvtColor(matImg, grayImg, Imgproc.COLOR_RGB2GRAY);

                MatOfRect rectList = new MatOfRect();
                long start = System.currentTimeMillis();

                nativeDetect.detect(grayImg, rectList);
                //mJavaDetector.detectMultiScale(matImg, rectList, 1.1, 3, 0, // TODO: objdetect.CV_HAAR_SCALE_IMAGE
                //       new Size(15,15), new Size(1000,1000));

                long end = System.currentTimeMillis();
                long duration = end -start;
                Log.i(TAG,"FDbT-Activity Time: " + duration/1000.0 + " sec");


                Rect[] faces = rectList.toArray();
                // Utils.bitmapToMat(bitmap, matImg); // copy again RGB img in Mat
                Log.i(TAG, "Num of faces = " + faces.length);

                try {
                    if (faces.length == 0){
                        fos.write(Integer.toString(0) + ","+ Integer.toString(0) + "," +
                                Integer.toString(0) + "," + Integer.toString(0) + ";"+ "\n"); // write to file
                        fos.flush();
                    }
                    else {
                        for (int k = 0; k < faces.length; k++) {
                            Imgproc.rectangle(matImg, faces[k].tl(), faces[k].br(), DETECT_BOX_COLOR, 3);

                            Log.i(TAG, "x, y, w, h = " + Integer.toString((int)faces[k].tl().x) + " " + Integer.toString((int)faces[k].tl().y) + " " +
                                    Integer.toString((int)(faces[k].br().x - faces[k].tl().x)) + " " + Integer.toString((int)(faces[k].br().y - faces[k].tl().y)) + " ");
                            fos.write( Integer.toString((int)faces[k].tl().x) + "," + Integer.toString((int)faces[k].tl().y) + "," +
                                    Integer.toString((int)(faces[k].br().x - faces[k].tl().x)) + "," + Integer.toString((int)(faces[k].br().y - faces[k].tl().y)) + ";");

                            fos.flush();
                        }
                        fos.write("\n");
                    }
                } catch (FileNotFoundException e) {
                    System.out.println("Exception e: " + e);
                }

                bitmap = Bitmap.createBitmap(matImg.cols(),matImg.rows(),Bitmap.Config. ARGB_8888); // Each pixel is stored on 4 bytes. Each channel (RGB and alpha for translucency) is stored with 8 bits of precision (256 possible values.) This configuration is very flexible and offers the best quality.
                Utils.matToBitmap(matImg,bitmap); // copy img with detection boxes in bitmap to be saved
                saveImage(bitmap);
                Log.i(TAG, "added frame = " +i+"  "+ j);
            }
            else {
                Log.i(TAG, "END of VIDEO or BITMAP is NULL!!");
                break;
            }
            j++;
            i += 1000000;
            // i+=33334;
        }
        fos.close();
        mp.release();
        if(fOut != null) fOut.close();
    }


    public void saveImage(Bitmap saveBitmap) throws IOException{

        String SDCardPath = Environment.getExternalStorageDirectory().toString();
        File saveFolder = new File(SDCardPath + AppResPath);
        if(!saveFolder.exists()){
            saveFolder.mkdirs();
        }

        if(saveBitmap != null) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            saveBitmap.compress(Bitmap.CompressFormat.JPEG, 40, bytes);

            imgCount++;
            File f = new File(saveFolder + outImgName + imgCount + ".jpg");
            Log.i(TAG, "writing image file = " + imgCount +"   "+ f);

            f.createNewFile();

            FileOutputStream fo = new FileOutputStream(f);
            fo.write(bytes.toByteArray());

            fo.flush();
            fo.close();
        }
    }
}

// http://answers.opencv.org/question/183186/how-to-open-video-file-mp4avi-using-opencv-in-android-and-detect-faces-in-the-video/
// http://answers.opencv.org/question/126732/loading-video-files-using-videocapture-in-android/
// limited to mjpg/avi

// wrt blueish images with OpenCV
// https://stackoverflow.com/questions/36402088/opencv-android-imwrite-give-me-a-blue-image
