package opencv.android.fdt;

/**
 * Created by alorusso on 12/07/18.
 */

import android.util.Log;

import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;



public class DetectionBasedTracker
{
    private static final String    TAG  = "FDbT-Act-DBT";

    public DetectionBasedTracker(String cascadeName, int minFaceSize) {
        Log.i(TAG, "Inside class DetectionBasedTracker");
        mNativeObj = nativeCreateObject(cascadeName, minFaceSize);
        Log.i(TAG, "Leaving class DetectionBasedTracker");
    }


    public void detect(Mat imageGray, MatOfRect faces) {
        nativeDetect(mNativeObj, imageGray.getNativeObjAddr(), faces.getNativeObjAddr());
    }

    public void release() {
        nativeDestroyObject(mNativeObj);
        mNativeObj = 0;
    }

    private long mNativeObj = 0;

    private static native long nativeCreateObject(String cascadeName, int minFaceSize);
    private static native void nativeDestroyObject(long thiz);
    private static native void nativeDetect(long thiz, long inputImage, long faces);
}
