package org.opencv.android.facetracker;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.hardware.Camera;
import android.hardware.Camera.Face;
import android.hardware.Camera.FaceDetectionListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.util.Log;
import android.view.OrientationEventListener;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewGroup.LayoutParams;
import java.util.List;

/////////////////////
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.hardware.Camera.Face;
import android.view.View;

/////////////////////
import android.app.Activity;
import android.graphics.Matrix;
import android.graphics.Point;
import android.hardware.Camera;
import android.hardware.Camera.Size;
import android.os.Build;
import android.util.Log;
import android.view.Display;
import android.view.OrientationEventListener;
import android.view.Surface;
import java.util.List;

/////////////////////
import android.hardware.Camera;
import android.util.Log;



public class OpenCvActivity extends Activity implements SurfaceHolder.Callback {

    private static final String TAG = "OCV-Simple";  // private or public ?

    private Camera mCamera;
    private long prev =0;
    private Canvas mCanvas;
    private Bitmap mBitmap;

    // We need the phone orientation to correctly draw the overlay:
    private int mOrientation;
    private int mOrientationCompensation;
    private OrientationEventListener mOrientationEventListener;

    // Let's keep track of the display rotation and orientation also:
    private int mDisplayRotation;
    private int mDisplayOrientation;

    // Holds the Face Detection result:
    private Camera.Face[] mFaces;

    // The surface view for the camera data
    private SurfaceView mView;

    // Draw rectangles and other fancy stuff:
    private FaceOverlayView mFaceView;

    // Log all errors:
    private final CameraErrorCallback mErrorCallback = new CameraErrorCallback();

    /**
     * Sets the faces for the overlay view, so it can be updated
     * and the face overlays will be drawn again.
     */
    private FaceDetectionListener faceDetectionListener = new FaceDetectionListener() {
        @Override
        public void onFaceDetection(Face[] faces, Camera camera) {
            Log.i(TAG,"onFaceDetection" + " - Number of Faces:" + faces.length);
            // Update the view now!
            mFaceView.setFaces(faces);
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.i(TAG,"onCreate");
        super.onCreate(savedInstanceState);
        mView = new SurfaceView(this);

        setContentView(mView);

        // Now create the OverlayView:
        mFaceView = new FaceOverlayView(this);
        addContentView(mFaceView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        // Create and Start the OrientationListener:
        mOrientationEventListener = new SimpleOrientationEventListener(this);
        mOrientationEventListener.enable();
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        Log.i(TAG,"onPostCreate");
        super.onPostCreate(savedInstanceState);
        SurfaceHolder holder = mView.getHolder();
        holder.addCallback(this);
    }

    @Override
    protected void onPause() {
        Log.i(TAG,"onPause");
        mOrientationEventListener.disable();
        super.onPause();
    }

    @Override
    protected void onResume() {
        Log.i(TAG,"onResume");
        mOrientationEventListener.enable();
        super.onResume();
    }

    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
        Log.i(TAG,"surfaceCreated");
        mCamera = Camera.open();
        mCamera.setFaceDetectionListener(faceDetectionListener);
        // mCamera.startFaceDetection(); // doesn't start detector on surfaceCreated but only on surfaceChanged
        try {
            mCamera.setPreviewDisplay(surfaceHolder);
        } catch (Exception e) {
            Log.e(TAG, "Could not preview the image.", e);
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int format, int width, int height) {
        Log.i(TAG,"surfaceChanged");
        // We have no surface, return immediately:
        if (surfaceHolder.getSurface() == null) {
            return;
        }
        // Try to stop the current preview:
        try {
            mCamera.stopPreview();
        } catch (Exception e) { // ignore
        }
        configureCamera(width, height);
        setDisplayOrientation();
        setErrorCallback();

        // Everything is configured! Finally start the camera preview again:
        mCamera.startPreview();
        mCamera.startFaceDetection();
    }

    private void setErrorCallback() {
        Log.i(TAG,"setErrorCallback");
        mCamera.setErrorCallback(mErrorCallback);
    }

    private void setDisplayOrientation() {
        Log.i(TAG,"setDisplayOrientation");
        Util mUtil = new Util();
        // Now set the display orientation:
        mDisplayRotation = mUtil.getDisplayRotation(OpenCvActivity.this);
        mDisplayOrientation = mUtil.getDisplayOrientation(mDisplayRotation, 0);

        mCamera.setDisplayOrientation(mDisplayOrientation);

        if (mFaceView != null) {
            mFaceView.setDisplayOrientation(mDisplayOrientation);
        }
    }

    private void configureCamera(int width, int height) {
        Log.i(TAG,"configureCamera");
        Camera.Parameters parameters = mCamera.getParameters();
        Log.i(TAG,"configureCamera W, H = " + width + ", " + height);
        // Set the PreviewSize and AutoFocus:
        setOptimalPreviewSize(parameters, width, height);

        Log.i(TAG,"BFORE Bitmap W H = " + parameters.getPreviewSize().width + ", " + parameters.getPreviewSize().height);
        mBitmap = Bitmap.createBitmap(parameters.getPreviewSize().width, parameters.getPreviewSize().height, Bitmap.Config.ARGB_8888);
        mCanvas = new Canvas(mBitmap);
        Log.i(TAG,"AFTER Bitmap W H = " + mCanvas.getWidth() + ",  " + mCanvas.getHeight());

        setAutoFocus(parameters);
        // And set the parameters:
        mCamera.setParameters(parameters);
    }

    private void setOptimalPreviewSize(Camera.Parameters cameraParameters, int width, int height) {
        Log.i(TAG,"setOptimalPreviewSize");
        List<Camera.Size> previewSizes = cameraParameters.getSupportedPreviewSizes();
        float targetRatio = (float) width / height;
        Util mUtil = new Util();
        Camera.Size previewSize = mUtil.getOptimalPreviewSize(this, previewSizes, targetRatio);
        cameraParameters.setPreviewSize(previewSize.width, previewSize.height);
        Log.i(TAG,"setPrevSize W H = " + previewSize.width + ", " + previewSize.height);
    }

    private void setAutoFocus(Camera.Parameters cameraParameters) {
        cameraParameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
        Log.i(TAG,"surfaceDestroyed");
        mCamera.setPreviewCallback(null);
        mCamera.setFaceDetectionListener(null);
        mCamera.stopFaceDetection();
        mCamera.setErrorCallback(null);
        mCamera.release();
        mCamera = null;
    }

    /**
     * We need to react on OrientationEvents to rotate the screen and
     * update the views.
     */
    private class SimpleOrientationEventListener extends OrientationEventListener {

        public SimpleOrientationEventListener(Context context) {
            super(context, SensorManager.SENSOR_DELAY_NORMAL);
            Log.i(TAG,"SimpleOrientationEventListener");
        }

        @Override
        public void onOrientationChanged(int orientation) {
            // Log.i(TAG,"onOrientationChanged"); coomented because it was printed too many times
            // We keep the last known orientation. So if the user first orient
            // the camera then point the camera to floor or sky, we still have
            // the correct orientation.
            Util mUtil = new Util();
            if (orientation == ORIENTATION_UNKNOWN) return;
            mOrientation = mUtil.roundOrientation(orientation, mOrientation);
            // When the screen is unlocked, display rotation may change. Always
            // calculate the up-to-date orientationCompensation.
            int orientationCompensation = mOrientation
                    + mUtil.getDisplayRotation(OpenCvActivity.this);
            if (mOrientationCompensation != orientationCompensation) {
                mOrientationCompensation = orientationCompensation;
                mFaceView.setOrientation(mOrientationCompensation);
            }

        }
    }

    public class FaceOverlayView extends View {

        private Paint mPaint;
        private Paint mTextPaint;
        private int mDisplayOrientation;
        private int mOrientation;
        private Face[] mFaces;

        public FaceOverlayView(Context context) {
            super(context);
            Log.i(TAG,"FaceOverlayView");
            initialize();
        }

        private void initialize() {

            Log.i(TAG,"initialize");
            // We want a green box around the face:
            mPaint = new Paint();
            mPaint.setAntiAlias(true);
            mPaint.setDither(true);
            mPaint.setColor(Color.GREEN);
            mPaint.setAlpha(128);
            mPaint.setStyle(Paint.Style.FILL_AND_STROKE);

            mTextPaint = new Paint();
            mTextPaint.setAntiAlias(true);
            mTextPaint.setDither(true);
            mTextPaint.setTextSize(20);
            mTextPaint.setColor(Color.GREEN);
            mTextPaint.setStyle(Paint.Style.FILL);
        }

        public void setFaces(Face[] faces) {
            Log.i(TAG,"setFaces");
            mFaces = faces;
            invalidate();
        }

        public void setOrientation(int orientation) {
            Log.i(TAG,"setOrientation");
            mOrientation = orientation;
        }

        public void setDisplayOrientation(int displayOrientation) {
            Log.i(TAG,"setDisplayOrientation");
            mDisplayOrientation = displayOrientation;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {   // on this method not sure
            Log.i(TAG,"onDraw");
            Log.i(TAG," Canvas w, h = " + canvas.getWidth() + ", " + canvas.getHeight());
            Log.i(TAG,"mCanvas w, h = " + mCanvas.getWidth() + ", " + mCanvas.getHeight());

            super.onDraw(canvas);
            Util mUtil = new Util();
            if (mFaces != null && mFaces.length > 0) {
                Matrix matrix = new Matrix();
                mUtil.prepareMatrix(matrix, false, mDisplayOrientation, mCanvas.getWidth(), mCanvas.getHeight());
                canvas.save();
                matrix.postRotate(mOrientation);
                canvas.rotate(-mOrientation);
                RectF rectF = new RectF();
                for (Face face : mFaces) {
                    rectF.set(face.rect);
                    matrix.mapRect(rectF);
                    canvas.drawRect(rectF, mPaint);
                    canvas.drawText("Score " + face.score, rectF.right, rectF.top, mTextPaint);
                    Log.i(TAG,"BB SIZE W, H = " + (rectF.right-rectF.left) + " "+ (rectF.bottom -rectF.top) +" " +
                            "     score = " + face.score);
                }
                canvas.restore();
            }
            // execution time between two face detection execution
            long curr = System.currentTimeMillis();
            long duration = curr - prev;
            prev = curr;
            Log.i(TAG,"Exectime in sec = " + duration/1000.0 + " sec");
        }
    }


    public class Util {
        // Orientation hysteresis amount used in rounding, in degrees
        private final int ORIENTATION_HYSTERESIS = 5;

        private final String TAG = "Util";

        /**
         * Gets the current display rotation in angles.
         * @param activity
         * @return
         */
        public int getDisplayRotation(Activity activity) {
            Log.i(TAG,"getDisplayRotation");
            int rotation = activity.getWindowManager().getDefaultDisplay()
                    .getRotation();
            switch (rotation) {
                case Surface.ROTATION_0: return 0;
                case Surface.ROTATION_90: return 90;
                case Surface.ROTATION_180: return 180;
                case Surface.ROTATION_270: return 270;
            }
            return 0;
        }

        public int getDisplayOrientation(int degrees, int cameraId) {
            Log.i(TAG,"getDisplayOrientation");
            // See android.hardware.Camera.setDisplayOrientation for
            // documentation.
            Camera.CameraInfo info = new Camera.CameraInfo();
            Camera.getCameraInfo(cameraId, info);
            int result;
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                result = (info.orientation + degrees) % 360;
                result = (360 - result) % 360;  // compensate the mirror
            } else {  // back-facing
                result = (info.orientation - degrees + 360) % 360;
            }
            return result;
        }

        public void prepareMatrix(Matrix matrix, boolean mirror, int displayOrientation,
                                         int viewWidth, int viewHeight) {

            Log.i(TAG,"prepareMatrix");
            // Need mirror for front camera.
            matrix.setScale(mirror ? -1 : 1, 1);
            // This is the value for android.hardware.Camera.setDisplayOrientation.
            matrix.postRotate(displayOrientation);
            // Camera driver coordinates range from (-1000, -1000) to (1000, 1000).
            // UI coordinates range from (0, 0) to (width, height).
            matrix.postScale(viewWidth / 2000f, viewHeight / 2000f);
            matrix.postTranslate(viewWidth / 2f, viewHeight / 2f);
        }

        public int roundOrientation(int orientation, int orientationHistory) {
            Log.i(TAG,"roundOrientation");
            boolean changeOrientation = false;
            if (orientationHistory == OrientationEventListener.ORIENTATION_UNKNOWN) {
                changeOrientation = true;
            } else {
                int dist = Math.abs(orientation - orientationHistory);
                dist = Math.min( dist, 360 - dist );
                changeOrientation = ( dist >= 45 + ORIENTATION_HYSTERESIS );
            }
            if (changeOrientation) {
                return ((orientation + 45) / 90 * 90) % 360;
            }
            return orientationHistory;
        }

        public Size getOptimalPreviewSize(Activity currentActivity,
                                                 List<Size> sizes, double targetRatio) {
            Log.i(TAG,"getOptimalPreviewSize");
            // Use a very small tolerance because we want an exact match.
            final double ASPECT_TOLERANCE = 0.001;
            if (sizes == null) return null;
            Camera.Size optimalSize = null;
            double minDiff = Double.MAX_VALUE;
            // Because of bugs of overlay and layout, we sometimes will try to
            // layout the viewfinder in the portrait orientation and thus get the
            // wrong size of preview surface. When we change the preview size, the
            // new overlay will be created before the old one closed, which causes
            // an exception. For now, just get the screen size.
            Point point = getDefaultDisplaySize(currentActivity, new Point());
            int targetHeight = Math.min(point.x, point.y);
            // Try to find an size match aspect ratio and size
            for (Size size : sizes) {
                double ratio = (double) size.width / size.height;
                if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE) continue;
                if (Math.abs(size.height - targetHeight) < minDiff) {
                    optimalSize = size;
                    minDiff = Math.abs(size.height - targetHeight);
                }
            }
            // Cannot find the one match the aspect ratio. This should not happen.
            // Ignore the requirement.
            if (optimalSize == null) {
                Log.w(TAG, "No preview size match the aspect ratio");
                minDiff = Double.MAX_VALUE;
                for (Size size : sizes) {
                    if (Math.abs(size.height - targetHeight) < minDiff) {
                        optimalSize = size;
                        minDiff = Math.abs(size.height - targetHeight);
                    }
                }
            }
            return optimalSize;
        }

        @SuppressWarnings("deprecation")
        private Point getDefaultDisplaySize(Activity activity, Point size) {
            Log.i(TAG,"getDefaultDisplaySize");
            Display d = activity.getWindowManager().getDefaultDisplay();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR2) {
                d.getSize(size);
            } else {
                size.set(d.getWidth(), d.getHeight());
            }
            return size;
        }
    }

    public class CameraErrorCallback implements Camera.ErrorCallback {
        private static final String TAG = "CameraErrorCallback";
        @Override
        public void onError(int error, Camera camera) {
            Log.i(TAG,"CameraErrorCallback");
            Log.e(TAG, "Encountered an unexpected camera error: " + error);
        }
    }
}