/*
 * Copyright (C) The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.gms.samples.vision.face.facetracker;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.hardware.Camera;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.SparseArray;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.samples.vision.face.facetracker.ui.camera.CameraSourcePreview;
import com.google.android.gms.samples.vision.face.facetracker.ui.camera.GraphicOverlay;
import com.google.android.gms.vision.CameraSource;
import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.MultiProcessor;
import com.google.android.gms.vision.Tracker;
import com.google.android.gms.vision.face.Face;
import com.google.android.gms.vision.face.FaceDetector;

import opencv.android.fdt.FdActivity;
import org.opencv.android.facetracker.OpenCvActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

import dlib.android.FaceRecognizer;
import tensorflow.detector.spc.CameraActivityMainSPC;
import opencv.android.fdt.FdActivity;

import static android.os.Environment.getExternalStorageDirectory;

class DoneOnEditorActionListener implements TextView.OnEditorActionListener {
    FaceTrackerActivity mActivity;
    public DoneOnEditorActionListener(FaceTrackerActivity activity) {
        mActivity = activity;
    }

    @Override
    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
        if (actionId == EditorInfo.IME_ACTION_DONE) {
            InputMethodManager imm = (InputMethodManager)v.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
            v.setVisibility(View.INVISIBLE);

            mActivity.makePhotoAndSave();
            mActivity.hideSaveFaceLabel();
            return true;
        }
        return false;
    }
}
/**
 * Activity for the face tracker app.  This app detects faces with the rear facing camera, and draws
 * overlay graphics to indicate the position, size, and ID of each face.
 */
public final class FaceTrackerActivity extends AppCompatActivity {
    private static final String TAG = "GMS";

    private CameraSource mCameraSource = null;

    private CameraSourcePreview mPreview;
    private GraphicOverlay mGraphicOverlay;
    private Button mBtnDetect, btnTraining, mBtnSwitchCamera;
    private CustomDetector customDetector;
    private EditText mTxtTrainingName;
    private TextView mlblTraining;
    private FaceDetector mPictureDetector;

    private static final int RC_HANDLE_GMS = 9001;
    // permission request codes need to be < 256
    private static final int RC_HANDLE_CAMERA_AND_SDCARD_PERM = 7;

    private int mFrontCamWidth;
    private int mFrontCamHeight;
    private int mBackCamWidth;
    private int mBackCamHeight;

    private FaceRecognizer mFaceRecognizer;
    private boolean mIsFront;
    private Context context;

    //==============================================================================================
    // Activity Methods
    //==============================================================================================

    /**
     * Initializes the UI and initiates the creation of a face detector.
     */
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setContentView(R.layout.main);

        mPreview = (CameraSourcePreview) findViewById(R.id.preview);
        mGraphicOverlay = (GraphicOverlay) findViewById(R.id.faceOverlay);
        mBtnDetect = (Button) findViewById(R.id.btnDetect);
        mBtnSwitchCamera = (Button) findViewById(R.id.btnSwitchCamera);

        btnTraining = (Button) findViewById(R.id.btnTraining);
        mlblTraining = (TextView) findViewById(R.id.lblTraining);

        mTxtTrainingName = (EditText) findViewById(R.id.txtTrainingName);
        mTxtTrainingName.setOnEditorActionListener(new DoneOnEditorActionListener(this));
        mFaceRecognizer = new FaceRecognizer();

        // Check for the camera permission before accessing the camera.  If the
        // permission is not granted yet, request permission.
        int rc = ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA);
        int rs = ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE);

        if (rc == PackageManager.PERMISSION_GRANTED && rs == PackageManager.PERMISSION_GRANTED) {
            createCameraSource(CameraSource.CAMERA_FACING_BACK);
        } else {
            requestCameraAndSdCardPermission();
        }
    }

    /**
     * Handles the requesting of the camera permission.  This includes
     * showing a "Snackbar" message of why the permission is needed then
     * sending the request.
     */
    private void requestCameraAndSdCardPermission() {
        Log.w(TAG, "Camera and sdcard permissions are not granted. Requesting permission");

        final String[] permissions = new String[]{Manifest.permission.CAMERA,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE};

        if (!ActivityCompat.shouldShowRequestPermissionRationale(this,
                Manifest.permission.CAMERA)) {
            ActivityCompat.requestPermissions(this, permissions, RC_HANDLE_CAMERA_AND_SDCARD_PERM);
            return;
        }

        final Activity thisActivity = this;

        View.OnClickListener listener = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ActivityCompat.requestPermissions(thisActivity, permissions,
                        RC_HANDLE_CAMERA_AND_SDCARD_PERM);
            }
        };

        Snackbar.make(mGraphicOverlay, R.string.permission_camera_and_sdcard_rationale,
                Snackbar.LENGTH_INDEFINITE)
                .setAction(R.string.ok, listener)
                .show();
    }

    private void calcCameraFrameSize()
    {
        int numCameras = Camera.getNumberOfCameras();

        for (int i = 0; i < numCameras; i++)
        {
            Camera.CameraInfo cameraInfo=new Camera.CameraInfo();
            Camera.getCameraInfo(i, cameraInfo);
            if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT)
            {
                Camera camera= Camera.open(i);
                Camera.Parameters cameraParams=camera.getParameters();
                List<Camera.Size> sizes= cameraParams.getSupportedPreviewSizes();
                mFrontCamWidth = sizes.get(0).width;
                mFrontCamHeight = sizes.get(0).height;
                camera.release();
            } else if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                Camera camera = Camera.open(i);
                Camera.Parameters cameraParams = camera.getParameters();
                List<Camera.Size> sizes = cameraParams.getSupportedPreviewSizes();
                mBackCamWidth = sizes.get(0).width;
                mBackCamHeight = sizes.get(0).height;
                camera.release();
            }
        }
    }


    /**
     * Creates and starts the camera.  Note that this uses a higher resolution in comparison
     * to other detection examples to enable the barcode detector to detect small barcodes
     * at long distances.
     */
    private void createCameraSource(int cameraFacing) {

        context = getApplicationContext();


        FaceDetector detector = new FaceDetector.Builder(context)
                .setTrackingEnabled(true)
                .setClassificationType(FaceDetector.NO_CLASSIFICATIONS)
                .setProminentFaceOnly(false)
                .setMode(FaceDetector.ACCURATE_MODE)
                .setMinFaceSize(0.015f)
                .build();

        mPictureDetector = new FaceDetector.Builder(context)
                .setTrackingEnabled(false)
                .setClassificationType(FaceDetector.NO_CLASSIFICATIONS)
                .setProminentFaceOnly(true)
                .setMode(FaceDetector.ACCURATE_MODE)
                .setMinFaceSize(0.015f)  // 80 / 5312 detect up to 80 pixels head width
                .build();

        customDetector = new CustomDetector(detector, mFaceRecognizer);
        customDetector.setProcessor(
                new MultiProcessor.Builder<>(new GraphicFaceTrackerFactory())
                        .build());

        if (!customDetector.isOperational()) {
            // Note: The first time that an app using face API is installed on a device, GMS will
            // download a native library to the device in order to do detection.  Usually this
            // completes before the app is run for the first time.  But if that download has not yet
            // completed, then the above call will not detect any faces.
            //
            // isOperational() can be used to check if the required native library is currently
            // available.  The detector will automatically become operational once the library
            // download completes on device.
            Log.w(TAG, "Face detector dependencies are not yet available.");
        }

        if (!mPictureDetector.isOperational()) {
            Log.w(TAG, "mPictureDetector dependencies are not yet available.");
        }

        calcCameraFrameSize();

        int w = mBackCamWidth;
        int h = mBackCamHeight;
        int orientation = context.getResources().getConfiguration().orientation;
        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            w = mBackCamHeight;
            h = mBackCamWidth;
        } else{
            w = mFrontCamHeight;
            h = mFrontCamWidth;
        }

        mCameraSource = new CameraSource.Builder(context, customDetector)
                .setRequestedPreviewSize(w, h)
                .setFacing(cameraFacing)
                .setAutoFocusEnabled(true)
                .setRequestedFps(10)
                .build();

        mBtnDetect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent myIntent = new Intent(FaceTrackerActivity.this, CameraActivityMainSPC.class);
                //Intent myIntent = new Intent(FaceTrackerActivity.this, FdActivity.class);
                FaceTrackerActivity.this.startActivity(myIntent);
            }
        });

        btnTraining.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mTxtTrainingName.setVisibility(View.VISIBLE);
                mlblTraining.setVisibility(View.VISIBLE);
                mTxtTrainingName.requestFocus();

                InputMethodManager imm = (InputMethodManager)
                        getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
            }
        });

        mBtnSwitchCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mIsFront = !mIsFront;
                customDetector.setHandlerListener(null);
                customDetector.release();
                mCameraSource.release();

                if (mIsFront) {
                    createCameraSource(CameraSource.CAMERA_FACING_FRONT);
                } else{
                    createCameraSource(CameraSource.CAMERA_FACING_BACK);
                }
                startCameraSource();
            }
        });
    }

    public void hideSaveFaceLabel() {
        mlblTraining.setVisibility(View.INVISIBLE);
    }

    public void makePhotoAndSave() {
        mCameraSource.takePicture(null, new CameraSource.PictureCallback() {
            @Override
            public void onPictureTaken(byte[] bytes) {
                Bitmap tmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                int frameRotation = customDetector.frameRotation;
                Frame frame = new Frame.Builder()
                        .setBitmap(tmp)
                        .setRotation(frameRotation)
                        .build();
                SparseArray<Face> faces = mPictureDetector.detect(frame);
                if (faces.size() != 1) {
                    Toast toast = Toast.makeText(getApplicationContext(),
                            "Please make photo only of one face",
                            Toast.LENGTH_SHORT);
                    toast.show();
                    Log.w(TAG, "Can't find faces");
                } else{
                    Face face = faces.valueAt(0);
                    int x = (int)face.getPosition().x;
                    int y = (int)face.getPosition().y;
                    int w = (int)face.getWidth();
                    int h = (int)face.getHeight();
                    int fHeight = tmp.getHeight();
                    int fWidth = tmp.getWidth();
                    Bitmap cropped_orig;
                    Bitmap cropped_and_resized; //max 250
                    Matrix rot = new Matrix();
                    switch (frameRotation)
                    {
                        case 1:
                            rot.postRotate(90);
                            cropped_orig = Bitmap.createBitmap(tmp, y, fHeight - (x + w), h, w,
                                    rot,false );
                            cropped_and_resized = FaceRecognizer.resize(cropped_orig, 250, 250);
                            break;
                        case 2:
                            rot.postRotate(180);
                            cropped_orig = Bitmap.createBitmap(tmp, fWidth - (x + w),
                                    fHeight - (y + h), w, h, rot, false);
                            cropped_and_resized = FaceRecognizer.resize(cropped_orig, 250, 250);
                            break;
                        case 3:
                            rot.postRotate(270);
                            cropped_orig = Bitmap.createBitmap(tmp, fWidth - (y + h), x, h, w,
                                    rot, false);
                            cropped_and_resized = FaceRecognizer.resize(cropped_orig, 250, 250);
                            break;
                        default:
                            cropped_orig = Bitmap.createBitmap(tmp, x, y, w, h);
                            cropped_and_resized = FaceRecognizer.resize(cropped_orig, 250, 250);
                            break;
                    }


                    int res = mFaceRecognizer.saveFace(mTxtTrainingName.getText().toString(), cropped_and_resized);
                    if (res != 0) {
                        Toast toast = Toast.makeText(getApplicationContext(),
                                "Error occurred duding face saving",
                                Toast.LENGTH_SHORT);
                        toast.show();
                    } else{
                        Toast toast = Toast.makeText(getApplicationContext(),
                                "Face vector saved!",
                                Toast.LENGTH_SHORT);
                        toast.show();
                    }
                }
            }
        });
    }

    /**
     * Restarts the camera.
     */
    @Override
    protected void onResume() {
        super.onResume();
        mFaceRecognizer.loadNative();
        startCameraSource();
    }

    /**
     * Stops the camera.
     */
    @Override
    protected void onPause() {
        super.onPause();
        mPreview.stop();
    }

    /**
     * Releases the resources associated with the camera source, the associated detector, and the
     * rest of the processing pipeline.
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mCameraSource != null) {
            mCameraSource.release();
        }
    }

    /**
     * Callback for the result from requesting permissions. This method
     * is invoked for every call on {@link #requestPermissions(String[], int)}.
     * <p>
     * <strong>Note:</strong> It is possible that the permissions request interaction
     * with the user is interrupted. In this case you will receive empty permissions
     * and results arrays which should be treated as a cancellation.
     * </p>
     *
     * @param requestCode  The request code passed in {@link #requestPermissions(String[], int)}.
     * @param permissions  The requested permissions. Never null.
     * @param grantResults The grant results for the corresponding permissions
     *                     which is either {@link PackageManager#PERMISSION_GRANTED}
     *                     or {@link PackageManager#PERMISSION_DENIED}. Never null.
     * @see #requestPermissions(String[], int)
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode != RC_HANDLE_CAMERA_AND_SDCARD_PERM) {
            Log.d(TAG, "Got unexpected permission result: " + requestCode);
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            return;
        }

        if (grantResults.length != 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Camera permission granted - initialize the camera source");
            // we have permission, so create the camerasource
            mFaceRecognizer.loadNative();
            createCameraSource(CameraSource.CAMERA_FACING_BACK);

            return;
        }

        Log.e(TAG, "Permission not granted: results len = " + grantResults.length +
                " Result code = " + (grantResults.length > 0 ? grantResults[0] : "(empty)"));

        DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                finish();
            }
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("FR demo")
                .setMessage(R.string.no_camera_sdcard_permission)
                .setPositiveButton(R.string.ok, listener)
                .show();
    }

    //==============================================================================================
    // Camera Source Preview
    //==============================================================================================

    /**
     * Starts or restarts the camera source, if it exists.  If the camera source doesn't exist yet
     * (e.g., because onResume was called before the camera source was created), this will be called
     * again when the camera source is created.
     */
    private void startCameraSource() {

        // check that the device has play services available.
        int code = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(
                getApplicationContext());
        if (code != ConnectionResult.SUCCESS) {
            Dialog dlg =
                    GoogleApiAvailability.getInstance().getErrorDialog(this, code, RC_HANDLE_GMS);
            dlg.show();
        }

        if (mCameraSource != null) {
            try {
                mPreview.start(mCameraSource, mGraphicOverlay);
            } catch (IOException e) {
                Log.e(TAG, "Unable to start camera source.", e);
                mCameraSource.release();
                mCameraSource = null;
            }
        }
    }




    //==============================================================================================
    // Graphic Face Tracker
    //==============================================================================================

    /**
     * Factory for creating a face tracker to be associated with a new face.  The multiprocessor
     * uses this factory to create face trackers as needed -- one for each individual.
     */
    private class GraphicFaceTrackerFactory implements MultiProcessor.Factory<Face> {
        @Override
        public Tracker<Face> create(Face face) {
            return new GraphicFaceTracker(mGraphicOverlay);
        }
    }

    /**
     * Face tracker for each detected individual. This maintains a face graphic within the app's
     * associated face overlay.
     */
    private class GraphicFaceTracker extends Tracker<Face> {
        private GraphicOverlay mOverlay;
        private FaceGraphic mFaceGraphic;

        GraphicFaceTracker(GraphicOverlay overlay) {
            mOverlay = overlay;
            mFaceGraphic = new FaceGraphic(overlay, customDetector);
        }

        /**
         * Start tracking the detected face instance within the face overlay.
         */
        @Override
        public void onNewItem(int faceId, Face item) {
            mFaceGraphic.setId(faceId);
        }

        /**
         * Update the position/characteristics of the face within the overlay.
         */
        @Override
        public void onUpdate(FaceDetector.Detections<Face> detectionResults, Face face) {
            mOverlay.add(mFaceGraphic);
            mFaceGraphic.updateFace(face);
        }

        /**
         * Hide the graphic when the corresponding face was not detected.  This can happen for
         * intermediate frames temporarily (e.g., if the face was momentarily blocked from
         * view).
         */
        @Override
        public void onMissing(FaceDetector.Detections<Face> detectionResults) {
            mOverlay.remove(mFaceGraphic);
        }

        /**
         * Called when the face is assumed to be gone for good. Remove the graphic annotation from
         * the overlay.
         */
        @Override
        public void onDone() {
            mOverlay.remove(mFaceGraphic);
        }
    }
}
