#include <jni.h>
#include <string>
#include <opencv2/opencv.hpp>

#include <android/log.h>
#include <chrono>

using namespace std;
using namespace cv;


#define  LOG_TAG    "OCV-Native"
#define  LOGI(...)  __android_log_print(ANDROID_LOG_INFO,LOG_TAG,__VA_ARGS__)
#define  LOGE(...)  __android_log_print(ANDROID_LOG_ERROR,LOG_TAG,__VA_ARGS__)


extern "C"
{

JNIEXPORT void JNICALL
Java_org_opencv_android_facetracker_HaarDetector_OpenCVdetector(JNIEnv *env, jclass instance,
                                                                jlong inputAddrMat, jlong matRects);

JNIEXPORT void JNICALL
Java_org_opencv_android_facetracker_HaarDetector_loadResources(JNIEnv *env, jobject instance);


CascadeClassifier face_cascade;



//New HAAR detection function to reduce false detection
std::vector<Rect> detectRF(Mat &gray) {

    double const TH_weight = 0.0;//Good empirical threshold values: 5-7
    std::vector<int> reject_levels;
    std::vector<double> weights;

    std::vector<Rect> faces = {};
    std::vector<Rect> realfaces = {};
    face_cascade.detectMultiScale( gray, faces, reject_levels, weights, 1.1, 3, 0|CV_HAAR_SCALE_IMAGE, Size(), Size(1000,1000), true );

    int i=0;
    for(vector<Rect>::const_iterator r = faces.begin(); r != faces.end(); r++, i++ )
    {

        if (weights[i] >= TH_weight)//Good empirical threshold values: 5-7
        {
            LOGI("weights[%i]:%f", i, weights[i]);
            LOGI("width = ", faces[i].width, "height =", faces[i].height);
            realfaces.push_back(*r);
        }
    }
    LOGI("#realFaces: %i", (int)realfaces.size());
    return realfaces;
}



JNIEXPORT void JNICALL
Java_org_opencv_android_facetracker_HaarDetector_loadResources(JNIEnv *env, jobject instance) {

    String face_cascade_name = "/sdcard/Download/haarcascade_frontalface_default.xml";

    if (!face_cascade.load(face_cascade_name)) {
        LOGE("OCV resources NOT loaded");
        return;
    } else {
        LOGI("OCV resources loaded");
    }
}


JNIEXPORT void JNICALL
Java_org_opencv_android_facetracker_HaarDetector_OpenCVdetector(JNIEnv *env, jclass instance,
                                                                jlong inputAddrMat, jlong matRects) {

    int i=0;
    vector<Rect> faces = {};

    Mat &origImg = *((Mat *)inputAddrMat);
    /*Mat mGray;
    cv::cvtColor(origImg, mGray, CV_BGR2GRAY);

     */
    auto start = std::chrono::high_resolution_clock::now();
    face_cascade.detectMultiScale(origImg, faces, 1.1, 3, 0, Size(20, 20), Size(1000, 1000));

    //faces = detectRF(origImg);

    //
    for (int i = 0; i < faces.size(); i++)
        rectangle(origImg, Point(faces[i].x,faces[i].y),
                           Point(faces[i].x+faces[i].width,faces[i].y+faces[i].height),
                           Scalar(255, 255, 255), 3);

     //
    auto finish = std::chrono::high_resolution_clock::now();
    std::chrono::duration<double> elapsed = finish - start;
    __android_log_print(ANDROID_LOG_INFO, LOG_TAG, "Time-FD = %0.3fsec ", elapsed.count());


    for(vector<Rect>::const_iterator r = faces.begin(); r != faces.end(); r++, i++ )
        LOGI("width = ", faces[i].width, "height =", faces[i].height);


    *((Mat*)matRects) = Mat(faces, true);
}
}
