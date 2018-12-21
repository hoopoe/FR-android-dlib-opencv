package org.opencv.android.facetracker;

/**
 * Created by alorusso on 10/12/18.
 */
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;

import android.graphics.BitmapFactory;
import android.graphics.YuvImage;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.SurfaceHolder;
import android.widget.ImageView;

import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;

import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfRect;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import static org.opencv.core.Core.FONT_HERSHEY_PLAIN;
import static org.opencv.core.CvType.CV_8UC1;

public class CameraPreview implements SurfaceHolder.Callback, Camera.PreviewCallback
{
    private Camera mCamera = null;
    private ImageView MyCameraPreview = null;
    private Bitmap bitmap = null;
    private int[] pixels = null;
    private byte[] FrameData = null;
    private int imageFormat;
    private int PreviewSizeWidth;
    private int PreviewSizeHeight;
    private boolean bProcessing = false;

    private static final String TAG = "OCV-CamPrev";
    private HaarDetector hd = new HaarDetector();
    private static final Scalar DETECT_BOX_COLOR   = new Scalar(0, 0, 0, 0);
    private int frameNum = 0;


    Handler mHandler = new Handler(Looper.getMainLooper());

    public CameraPreview(int PreviewlayoutWidth, int PreviewlayoutHeight,
                         ImageView CameraPreview)
    {
        Log.i(TAG, "CameraPrev: constructor");
        PreviewSizeWidth = PreviewlayoutWidth;
        PreviewSizeHeight = PreviewlayoutHeight;
        MyCameraPreview = CameraPreview;
        bitmap = Bitmap.createBitmap(PreviewSizeWidth, PreviewSizeHeight, Bitmap.Config.ARGB_8888);
        pixels = new int[PreviewSizeWidth * PreviewSizeHeight];
        hd.loadNative();
        Log.i(TAG, "CameraPrev: after  loadNative");
    }

    @Override
    public void onPreviewFrame(byte[] arg0, Camera arg1)
    {
        Log.i(TAG, "onPreviewFrame");
        // At preview mode, the frame data will push to here.
        if (imageFormat == ImageFormat.NV21)
        {
            //We only accept the NV21(YUV420) format.
            if ( !bProcessing )
            {
                FrameData = arg0;
                mHandler.post(DoImageProcessing);
            }
        }
    }

    public void onPause()
    {
        Log.i(TAG, "CamPrev:  onPause");
        mCamera.stopPreview();
    }

    @Override
    public void surfaceChanged(SurfaceHolder arg0, int arg1, int arg2, int arg3)
    {
        Log.i(TAG, "CamPrev: surfaceChanged ");
        Parameters parameters;

        parameters = mCamera.getParameters();
        // Set the camera preview size
        parameters.setPreviewSize(PreviewSizeWidth, PreviewSizeHeight);

        imageFormat = parameters.getPreviewFormat();
        mCamera.setParameters(parameters);
        mCamera.startPreview();
    }

    @Override
    public void surfaceCreated(SurfaceHolder arg0)
    {
        Log.i(TAG, "CamPrev: surfaceCreated ");
        mCamera = Camera.open();
        try
        {
            // If did not set the SurfaceHolder, the preview area will be black.
            mCamera.setPreviewDisplay(arg0);
            mCamera.setPreviewCallback(this);
        }
        catch (IOException e)
        {
            mCamera.release();
            mCamera = null;
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder arg0)
    {
        Log.i(TAG, "CamPrev: surfaceDestroyed ");
        mCamera.setPreviewCallback(null);
        mCamera.stopPreview();
        mCamera.release();
        mCamera = null;
    }



    private Runnable DoImageProcessing = new Runnable()
    {
        public void run()
        {
            Log.i(TAG, "DoImageProcessing():");

            bProcessing = true;
            MatOfRect faces = new MatOfRect();
            Mat mRgba = new Mat();

            // From: docs.scandit.com/5.2/android/access-the-camera-frames.html
            YuvImage image = new YuvImage(FrameData, ImageFormat.NV21, PreviewSizeWidth, PreviewSizeHeight, null);
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            image.compressToJpeg(new android.graphics.Rect(0, 0, PreviewSizeWidth, PreviewSizeHeight), 100, stream);
            byte[] jpegData = stream.toByteArray();
            Bitmap theBitmap = BitmapFactory.decodeByteArray(jpegData, 0, jpegData.length);

            Log.i(TAG, "DoImageProcessing 1: frameData length = " + FrameData.length);
            Log.i(TAG, "DoImageProcessing 2: pixels length = " + pixels.length);


            long start = System.currentTimeMillis();
            // hd.ImageProcessing(PreviewSizeWidth, PreviewSizeHeight, FrameData, pixels);
            hd.OCvDetect(PreviewSizeWidth, PreviewSizeHeight, FrameData, faces);
            long end = System.currentTimeMillis();
            long duration = end -start;
            Log.i(TAG,"OCV  detect  Time  =  " + duration/1000.0 + "  sec");


            Utils.bitmapToMat(theBitmap, mRgba);

            org.opencv.core.Rect[] facesArray = faces.toArray();
            frameNum ++;
            for (int i = 0; i < facesArray.length; i++) {
                Log.i(TAG,"BB values  =" + facesArray[i].tl().x +"  "+ facesArray[i].tl().y + "  " +
                        (facesArray[i].br().x - facesArray[i].tl().x) +"  "+ (facesArray[i].br().y - facesArray[i].tl().y));
                Imgproc.rectangle(mRgba, facesArray[i].tl(), facesArray[i].br(), DETECT_BOX_COLOR, 3);
            }
            Imgproc.putText(mRgba, String.valueOf(frameNum), new Point(10,30), 3, 1,
                    new Scalar(0, 0, 0, 0), 3);

            Utils.matToBitmap(mRgba, bitmap);
            Log.i(TAG,"after matToBitmap ");

            MyCameraPreview.setImageBitmap(bitmap);
            if (faces != null)  faces.release();

            bProcessing = false;
        }
    };
}