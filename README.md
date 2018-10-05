[![Build Status](https://travis-ci.com/hoopoe/FR-android-dlib-opencv.svg?branch=master)](https://travis-ci.com/hoopoe/FR-android-dlib-opencv)

Facial Recognition on Android using dlib and opencv
============


This app demonstrate semi realtime face detection, tracking and recognition based on predefined face vectors.


Notes:
1. Set env variable OPENCV_ANDROID_SDK 

export OPENCV_ANDROID_SDK=/Users/hoopoe/Tools/OpenCV-android-sdk

2. From https://github.com/davisking/dlib-models

  copy 
  * shape_predictor_5_face_landmarks.dat
  * dlib_face_recognition_resnet_model_v1.dat
  
  to /sdcard/Download 

3. dlib adapted to work with -DANDROID_STL=gnustl_shared


4. To enable Mobilenet/TF activity. You need to:

  Copy some model from 
  https://github.com/tensorflow/models/blob/master/research/slim/nets/mobilenet_v1.md
  
  Change some values manually 

  private static final int MAX_RESULTS = 500;

  private static final int TF_OD_API_INPUT_SIZE = 416;

  private static final String TF_OD_API_MODEL_FILE ="file:///android_asset/spc_mobilenet_v3_1x_0.52_cleaned.pb" 


5. To execute OpenCV and Google Vision related tests for comparison of results on images and video,
   for video input:
	- create "myAppFolder" in phone root folder
	- copy video file in 'myAppFolder'
   Test images come with the application (they are in the ASSET folder)
   Test results are all in the phone root location in the "myAppRes" folder, created automatically.
 

6. To run "OCV-FTD":
   a) download "OpenCV 3.4.0dev Android SDK with contrib modules" from the following link:
      https://github.com/chaoyangnz/opencv3-android-sdk-with-contrib
   b) create "jniLibs" folder into following path: "FaceTracker/app/src/main"
   c) copy the pre-compiled libraries from OpenCV-android-sdk/sdk/native/libs into the newly created jniLibs folder
   d) copy "haarcascade_frontalface_default.xml" into "sdcard/Download"
   
6. To run openalpr activities :
  * download jniLibs from [this link](https://drive.google.com/open?id=13ZlJvIRBpxydJcm64tS_czQm3e0SBwVu). The folder contains precompiled libs for `ANDROID_PLATFORM="android-21"` and `ANDROID_ABI="armeabi-v7a", "arm64-v8a", "x86_64"`. For compling with another `ANDROID_PLATFORM` or `ANDROID_ABI` use [this script](https://gist.github.com/jav974/072425f14927e6ca2c7a4439d8ac5457).
  * set env variable OPENALPR_LIBS
