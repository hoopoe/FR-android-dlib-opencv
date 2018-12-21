#include <jni.h>
#include <string>
#include <opencv2/opencv.hpp>

#include <android/log.h>
#include <opencv2/tracking/tracker.hpp>
#include <chrono>


using namespace std;
using namespace cv;


#define  LOG_TAG    "OCV-Native"
#define  LOGI(...)  __android_log_print(ANDROID_LOG_INFO,LOG_TAG,__VA_ARGS__)
#define  LOGE(...)  __android_log_print(ANDROID_LOG_ERROR,LOG_TAG,__VA_ARGS__)
#define  LOGD(...) ((void)__android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__))

extern "C"
{

int counter = 0;

JNIEXPORT void JNICALL Java_org_opencv_android_facetracker_HaarDetector_testOpenCVdetectorWeights(JNIEnv *env, jclass instance,
                                                                                           jlong inputAddrMat, jlong matRects);

JNIEXPORT void JNICALL Java_org_opencv_android_facetracker_HaarDetector_testOpenCVdetector(JNIEnv *env, jclass instance,
                                                                                jlong inputAddrMat, jlong matRects);

JNIEXPORT void JNICALL Java_org_opencv_android_facetracker_HaarDetector_OpenCVdetector(
                                                                                JNIEnv *env, jobject instance,
                                                                                jint width, jint height,
                                                                                jbyteArray NV21FrameData,
                                                                                jlong matRects);

JNIEXPORT void JNICALL
Java_org_opencv_android_facetracker_HaarDetector_loadResources(JNIEnv *env, jobject instance);

inline void vector_Rect_to_Mat(std::vector<Rect> &v_rect, Mat &mat) {
    mat = Mat(v_rect, true);
}

Mat *mCanny = NULL;


CascadeClassifier face_cascade;

vector<Rect> detect(Mat &gray) {
    std::vector<Rect> faces = {};
    cvtColor(gray, gray, CV_RGBA2GRAY);

    face_cascade.detectMultiScale(gray, faces, 1.1, 3, 0, Size(0, 0), Size(1000, 1000));
    return faces;
}

struct byArea {
    bool operator()(const Rect &a, const Rect &b) {
        return a.width * a.height < b.width * b.height;
    }
};

//New HAAR detection function to reduce false detection (vers.2)
std::vector<Rect> detectRF(Mat &rgba) {

    Mat gray;
    cvtColor(rgba, gray, CV_RGBA2GRAY);

    double const TH_weight = 0.0;//5.0;//Good empirical threshold values: 5-7 (INDOOR) - 4.0 (outdoor)
    //NOTE: The detection range depends on this threshold value.
    //      By reducing this value, you increase the probability
    //      of detecting smaller faces even at a longer distance,
    //      but also of increasing false detections.
    std::vector<int> reject_levels;
    std::vector<double> weights;

    std::vector<Rect> faces = {};
    std::vector<Rect> realfaces = {};
    //face_cascade.detectMultiScale( gray, faces, reject_levels, weights, 1.1, 5, 0|CV_HAAR_SCALE_IMAGE, Size(), Size(1000,1000), true );
    face_cascade.detectMultiScale(gray, faces, reject_levels, weights, 1.1, 3, 0, Size(15, 15),
                                  Size(1000, 1000), true);
    // face_cascade.detectMultiScale(gray, faces, 1.1, 3, 0, Size(15,15), Size(1000, 1000));

    int i = 0;
    for (vector<Rect>::const_iterator r = faces.begin(); r != faces.end(); r++, i++) {
        LOGI("weights[%i]:%f, sizeFace[i]: %i", i, weights[i], faces[i].width);
        if (weights[i] >= TH_weight)//Good empirical threshold values: 5-7
        {
            realfaces.push_back(*r);
        }
    }
    LOGI("#realFaces: %i (TH_weight= %.2f)", (int) realfaces.size(), TH_weight);
    //// sort( realfaces.begin(), realfaces.end(), byArea() );//new part (usefull for the tracking part)
    return realfaces;
}

//Function to crop frame and make the detection part faster
vector<Rect> cropImgAndDetectFaces(Mat &rgba) {

    vector<Rect> detfaces = {};
    vector<Rect> BBfaces_tmp = {};

    int h = rgba.rows;//along x-axis
    int w = rgba.cols;//along y-axis
    LOGI("origImg.rows [py]: %i, origImg.cols [px]: %i, (counter: %i)", h, w);
    double px = 0.2;
    double py = 0.3;
    int offx = (int) (px * h / 2);
    int offy = (int) (py * w / 2);
    LOGI("offx [px]: %i, offy [px]: %i", offx, offy);
    cv::Rect ROI_frame = cv::Rect(offy, offx, (w - 2 * offy), (h - 2 * offx));
    LOGI("ROI_frame (x,y,w,h): %i %i %i %i", ROI_frame.x, ROI_frame.y, ROI_frame.width,
         ROI_frame.height);
    rectangle(rgba, ROI_frame, Scalar(0, 0, 0), 8, 8, 0);//black cropped area

    Mat croppedIm = rgba(ROI_frame);
    BBfaces_tmp = detectRF(croppedIm);//vers.2 without HSV conversion
    if (BBfaces_tmp.size() > 0) {
        LOGI("#faces_CroppedFrame: %i", BBfaces_tmp.size());

        LOGI("#detectedFaces_inTheCROPPEDframe: %i (counter: %i)", (int) BBfaces_tmp.size(),
             counter);
        for (int i = 0; i < BBfaces_tmp.size(); i++) {
            detfaces.push_back(Rect(BBfaces_tmp[i].x + offy,
                                    BBfaces_tmp[i].y + offx,
                                    BBfaces_tmp[i].width, BBfaces_tmp[i].height));
            LOGI("i: %i,faces_CroppedFrame (x,y,w,h): %i %i %i %i", i, detfaces[i].x, detfaces[i].y,
                 detfaces[i].width, detfaces[i].height);
            if (BBfaces_tmp.at(i).width > 0 & BBfaces_tmp.at(i).height > 0) {
                rectangle(rgba, detfaces.at(i), Scalar(255, 255, 255), 18, 8, 0);
            }
        }
    }
    return detfaces;
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


// Opencv Tracker
MultiTracker trackers;//This class is used to track multiple objects using the specified tracker algorithm.
MultiTracker *mytrackers = NULL;

std::vector<Ptr<Tracker> > algorithms;//tracking algorithm
// set the default tracking algorithm
String trackingAlg = "MOSSE";
// container of the tracked objects
vector<Rect2d> trackedFaces;
bool firstTime = true;

//Note: GOTURN tracker requires the following file: goturn.prototxt
inline cv::Ptr<cv::Tracker> createTrackerByName(cv::String name) {
    cv::Ptr<cv::Tracker> tracker;

    if (name == "KCF")
        tracker = cv::TrackerKCF::create();
    else if (name == "TLD")
        tracker = cv::TrackerTLD::create();
    else if (name == "BOOSTING")
        tracker = cv::TrackerBoosting::create();
    else if (name == "MEDIAN_FLOW")
        tracker = cv::TrackerMedianFlow::create();
    else if (name == "MIL")
        tracker = cv::TrackerMIL::create();
        /* else if (name == "GOTURN")
             tracker = cv::TrackerGOTURN::create();*/
    else if (name == "MOSSE")
        tracker = cv::TrackerMOSSE::create();
    else
        CV_Error(cv::Error::StsBadArg, "Invalid tracking algorithm name\n");

    return tracker;
}

//info tracker algorithm
void infoTracker(Mat &frame, String &trackingAlg) {
    char strID[200];
    Scalar color = Scalar(0, 0, 255);
    sprintf(strID, "%s", trackingAlg.c_str());
    putText(frame, strID, Point(600, 50), FONT_HERSHEY_PLAIN, 5, color, 3);
}

//tracker initialization
MultiTracker trackerInit(Mat &Rgb, vector<Rect> &faces, String &trackingAlg) {
    for (size_t i = 0; i < faces.size(); i++) {

        //Tracker initialization
        algorithms.push_back(createTrackerByName(trackingAlg));//trackers creation
        trackedFaces.push_back(faces[i]);
        //rectangle(Rgb, faces.at(i), Scalar(255,0,0), 4, 8, 0);//Draw detectedFaces (RED)////
    } // end for
    firstTime = false;
    trackers.add(algorithms, Rgb, trackedFaces);

    return trackers;
}


//new version GetTrackedOBJ----------------------
vector<Rect> GetTrackedOBJ(MultiTracker &trackers, cv::Mat &frame, cv::Scalar &color) {
    vector<Rect2d> faces_ = {};
    vector<Rect> faces = {};
    int i;
    LOGI("OCV-native-lib_#trackedFacesToDraw: %i", (int) trackers.getObjects().size());
    for (i = 0; i < (int) trackers.getObjects().size(); i++) {
        faces_.push_back(trackers.getObjects().at(i));
        faces.push_back(Rect(faces_[i]));
        LOGI("OCV-native-lib_tfaces[%i] (x,y,w,h) %i,%i,%i,%i ", i, (int) faces[i].x,
             (int) faces[i].y, (int) faces[i].width, (int) faces[i].height);
        LOGI("NEW_OBJ[%i] (x,y,w,h) %i,%i,%i,%i ", i, (int) trackers.getObjects().at(i).x,
             (int) trackers.getObjects().at(i).y,
             (int) trackers.getObjects().at(i).width, (int) trackers.getObjects().at(i).height);
    }
    return faces;
}


// Get tracked object
void DrawTrackedOBJ(MultiTracker &trackers, cv::Mat &frame, cv::Scalar &color) {
    LOGI("Drawing");
    char strID[200];
    for (size_t i = 0; i < trackers.getObjects().size(); i++) {
        rectangle(frame, trackers.getObjects().at(i), color, 18, 8, 0);
        sprintf(strID, "ID: %i", (int) i);
        putText(frame, strID,
                Point((int) trackers.getObjects().at(i).x + (int) trackers.getObjects().at(i).width,
                      (int) trackers.getObjects().at(i).y +
                      (int) (trackers.getObjects().at(i).height * 0.5)), FONT_HERSHEY_PLAIN, 2,
                color, 3);
    }
}


// Canny edge detector
JNIEXPORT void JNICALL Java_org_opencv_android_facetracker_HaarDetector_mImageProcessing(
        JNIEnv *env, jobject instance,
        jint width, jint height,
        jbyteArray NV21FrameData, jintArray outPixels) {

    LOGI("HaarDetector_mImageProcessing");
    jbyte *pNV21FrameData = env->GetByteArrayElements(NV21FrameData, 0);
    jint *poutPixels = env->GetIntArrayElements(outPixels, 0);

    if (mCanny == NULL) {
        mCanny = new Mat(height, width, CV_8UC1);
    }

    Mat mGray(height, width, CV_8UC1, (unsigned char *) pNV21FrameData);
    Mat mResult(height, width, CV_8UC4, (unsigned char *) poutPixels);
    IplImage srcImg = mGray;
    IplImage ResultImg = mResult;
    IplImage CannyImg = *mCanny;

    cvCanny(&srcImg, &CannyImg, 80, 100, 3);
    cvCvtColor(&CannyImg, &ResultImg, CV_GRAY2BGRA);

    env->ReleaseByteArrayElements(NV21FrameData, pNV21FrameData, 0);
    env->ReleaseIntArrayElements(outPixels, poutPixels, 0);
}


// Haar face detector
JNIEXPORT void JNICALL Java_org_opencv_android_facetracker_HaarDetector_OpenCVdetector(
                                                            JNIEnv *env, jobject instance,
                                                            jint width, jint height,
                                                            jbyteArray NV21FrameData,
                                                            jlong matRects) {
    LOGI("HD-OCVdetector  w, h = %i  %i", width,height);

    std::vector<Rect> faces = {};

    jbyte *pNV21FrameData = env->GetByteArrayElements(NV21FrameData, 0);
    Mat mGray(height, width, CV_8UC1, (unsigned char *) pNV21FrameData);

    face_cascade.detectMultiScale(mGray, faces, 1.1, 3, 0, Size(0, 0), Size(1000, 1000));
    LOGI("HD_OCVdetector numOfFaces = %i ", faces.size());
    *((Mat *) matRects) = Mat(faces, true);

    env->ReleaseByteArrayElements(NV21FrameData, pNV21FrameData, 0);
}



JNIEXPORT void JNICALL Java_org_opencv_android_facetracker_HaarDetector_testOpenCVdetector(
                                                                    JNIEnv *env, jclass instance,
                                                                    jlong inputAddrMat, jlong matRects) {
    LOGI("HD-OCVdetector");
    Mat origImg = *((Mat *) inputAddrMat);
    Mat mGray;
    std::vector<Rect> faces = {};

    cvtColor(origImg, mGray, CV_RGBA2GRAY);

    face_cascade.detectMultiScale(mGray, faces, 1.1, 3, 0, Size(0, 0), Size(1000, 1000));

    LOGI("HD_OCVdetector numOfFaces = %i ", faces.size());
    *((Mat *) matRects) = Mat(faces, true);
}



// the same as testOpenCVdetector but with weights to minimize false positives.
JNIEXPORT void JNICALL Java_org_opencv_android_facetracker_HaarDetector_testOpenCVdetectorWeights(
        JNIEnv *env, jclass instance,
        jlong inputAddrMat, jlong matRects) {

    LOGI("HD-OCVdetector");
    Mat origImg = *((Mat *) inputAddrMat);
    Mat mGray;
    std::vector<Rect> faces = {};
    double const mWeight    = 5.5;
    std::vector<int>    reject_levels;
    std::vector<double> weights;
    std::vector<Rect>   realfaces = {};

    cvtColor(origImg, mGray, CV_RGBA2GRAY);

    face_cascade.detectMultiScale(mGray, faces, reject_levels, weights, 1.1, 3, 0, Size(0,0), Size(1000,1000), true );

    LOGI("HD_OCVdetector numOfFaces = %i ", faces.size()); // number of detected objects

    int i = 0;
    for(vector<Rect>::const_iterator r = faces.begin(); r != faces.end(); r++, i++ ) {
        LOGI("weights[%i]:%f, sizeFace[i]: %i", i, weights[i], faces[i].width);
        if (weights[i] >= mWeight)
        {
            realfaces.push_back(*r);
        }
    }

    LOGI("HD_OCVdetector numOfFaces = %i ", faces.size()); // how many detected objects are more likely to be faces
    *((Mat *) matRects) = Mat(realfaces, true);
}
}