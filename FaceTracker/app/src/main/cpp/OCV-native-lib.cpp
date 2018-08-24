#include <jni.h>
#include <string>
#include <opencv2/opencv.hpp>

#include <android/log.h>
#include <math.h>
#include <opencv2/tracking/tracker.hpp> //MEDIANFLOW tracker
#include <chrono>


using namespace std;
using namespace cv;


#define  LOG_TAG    "OCV-Native"
#define  LOGI(...)  __android_log_print(ANDROID_LOG_INFO,LOG_TAG,__VA_ARGS__)
#define  LOGE(...)  __android_log_print(ANDROID_LOG_ERROR,LOG_TAG,__VA_ARGS__)
#define LOGD(...) ((void)__android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__))

extern "C"
{

vector<Rect> BBfaces= {};//detectedFaces
vector<Rect> tfaces= {};//trackedFaces



JNIEXPORT void JNICALL
Java_org_opencv_android_facetracker_HaarDetector_OpenCVdetector(JNIEnv *env, jclass instance,
                                                                jlong inputAddrMat, jlong matRects);

JNIEXPORT void JNICALL
Java_org_opencv_android_facetracker_HaarDetector_loadResources(JNIEnv *env, jobject instance);

inline void vector_Rect_to_Mat(std::vector<Rect>& v_rect, Mat& mat)
{
    mat = Mat(v_rect, true);
}


CascadeClassifier face_cascade;

vector<Rect> detect(Mat &gray) {
    std::vector<Rect> faces = {};
    face_cascade.detectMultiScale(gray, faces, 1.1, 3, 0, Size(20, 20), Size(1000, 1000));
    return faces;
}

struct byArea {
    bool operator()(const Rect &a, const Rect &b) {
        return a.width * a.height < b.width * b.height;
    }
};


//New HAAR detection function to reduce false detection
std::vector<Rect> detectRF(Mat &gray) {

    //new part----------------------------------------
    Mat img_hsv,s_img;
    cvtColor(gray,img_hsv,CV_RGB2HSV);
    std::vector<cv::Mat> channels;
    cv::split(img_hsv, channels);//[0] = H;[1] = S;[2] = V;
    s_img=channels[2];//value channel: 2
    //end new part------------------------------------

    double const TH_weight=5.0;//Good empirical threshold values: 5-7 (INDOOR) - 4.0 (outdoor)
    //NOTE: The detection range depends on this threshold value.
    //      By reducing this value, you increase the possibility
    //      to detect a smaller face even at a longer distance,
    // but also to have false detections.
    std::vector<int> reject_levels;
    std::vector<double> weights;

    std::vector<Rect> faces = {};
    std::vector<Rect> realfaces = {};
    //face_cascade.detectMultiScale( gray, faces, reject_levels, weights, 1.1, 5, 0|CV_HAAR_SCALE_IMAGE, Size(), Size(1000,1000), true );
    face_cascade.detectMultiScale(s_img, faces, reject_levels, weights, 1.1, 5, 0|CV_HAAR_SCALE_IMAGE, Size(), Size(1000,1000), true );
    int i=0;
    for(vector<Rect>::const_iterator r = faces.begin(); r != faces.end(); r++, i++ ) {
        LOGI("weights[%i]:%f, sizeFace[i]: %i", i, weights[i], faces[i].width);
        if (weights[i] >= TH_weight)//Good empirical threshold values: 5-7
        {
            realfaces.push_back(*r);
        }
    }
    LOGI("#realFaces: %i (TH_weight= %.2f)", (int)realfaces.size(), TH_weight);
    sort( realfaces.begin(), realfaces.end(), byArea() );
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


// Opencv Tracker creation
Ptr<Tracker> tracker= TrackerMedianFlow::create();

MultiTracker trackers;//This class is used to track multiple objects using the specified tracker algorithm.
MultiTracker* mytrackers=NULL;

std::vector<Ptr<Tracker> > algorithms;//tracking algorithm
// set the default tracking algorithm
String trackingAlg = "MEDIAN_FLOW"; //default tracking Algorithm
// container of the tracked objects
vector<Rect2d> trackedFaces;
bool firstTime = true;
bool updateOK;
bool foundFaces=false;

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
    else if (name == "GOTURN")
        tracker = cv::TrackerGOTURN::create();
    else
        CV_Error(cv::Error::StsBadArg, "Invalid tracking algorithm name\n");

    return tracker;
}


//tracker initialization
MultiTracker trackerInit(Mat &Rgb, vector<Rect> &faces,String &trackingAlg){
    for (size_t i = 0; i <faces.size(); i++)
    {

        //Tracker initialization
        algorithms.push_back(createTrackerByName(trackingAlg));//trackers creation
        trackedFaces.push_back(faces[i]);
        rectangle(Rgb, faces.at(i), Scalar(255,255,0), 4, 8, 0);//Draw detectedFaces (YELLOW)
    } // end for
    firstTime = false;
    trackers.add(algorithms,Rgb,trackedFaces);

    return trackers;
}


// Get tracked object
vector<Rect> GetTrackedOBJ(MultiTracker& trackers,cv::Mat& frame, cv::Scalar& color)
{
    char strID[200];
    int i;
    for (i = 0; i < (int)trackers.getObjects().size(); i++)
    {
        tfaces.push_back(trackers.getObjects().at(i));
    }
    return tfaces;
}


JNIEXPORT void JNICALL
Java_org_opencv_android_facetracker_HaarDetector_OpenCVdetector(JNIEnv *env, jclass instance,
                                                                jlong inputAddrMat, jlong matRects) {
    Mat &origImg = *((Mat *) inputAddrMat);
    Mat mGray;

    auto start_timeD = std::chrono::high_resolution_clock::now();

    BBfaces = detectRF(origImg);//new_version with HSV conversion
    //BBfaces = detect(origImg);//new_version with HSV conversion
    LOGI("#detectedFaces: %i",(int)BBfaces.size());

    auto end_timeD = std::chrono::high_resolution_clock::now();
    std::chrono::duration<double> elapsed_seconds_onlyDet = end_timeD-start_timeD;
    LOGI("OCV-native-lib_elapsedTime-detectONLY: %.3f",elapsed_seconds_onlyDet);

    *((Mat*)matRects) = Mat(BBfaces, true);
}


JNIEXPORT void JNICALL
Java_org_opencv_android_facetracker_HaarDetector_OpenCVtracker(JNIEnv *env, jclass instance,
                                                               jlong inputAddrMat, jlong matRects) {
    Mat &origImg = *((Mat *) inputAddrMat);

    vector<Rect> BBfaces = *((Mat *) matRects);//, oldFaces;
    Scalar color = Scalar(0,0,255);//blue

    auto startT = std::chrono::high_resolution_clock::now();

    //Face tracking
    if(firstTime)
    {
        auto startI = std::chrono::high_resolution_clock::now();

        //Tracker initialization
        trackers = trackerInit(origImg,BBfaces,trackingAlg);

        auto endI = std::chrono::high_resolution_clock::now();
        std::chrono::duration<double> elapsed_secondsI = endI-startI;
        LOGI("OCV-native-lib_elapsedTime_Initialization: %.3f",elapsed_secondsI);
    } // end if first time
    else
    {
        auto startU = std::chrono::high_resolution_clock::now();

        updateOK = trackers.update(origImg);

        //get Bounding Boxes of the tracked faces
        if(updateOK)
        {
            //DrawTrackedOBJ(trackers,origImg,color);
            tfaces = GetTrackedOBJ(trackers,origImg,color);
            LOGI("updateOK: %i",(int)updateOK);
        }
        else {
            LOGI("updateOK: %i -> New detection & initialization", (int) updateOK);

            //remove all trackers
         /*   algorithms.clear();
            trackedFaces.clear();
            mytrackers = trackers.create();
            trackers = *mytrackers;*/

            //start a new tracker initialization
            firstTime=true;
        }

        auto endU = std::chrono::high_resolution_clock::now();
        std::chrono::duration<double> elapsed_secondsU = endU-startU;
        LOGI("OCV-native-lib_elapsedTime_Updating: %.3f",elapsed_secondsU);
    }//else firstTime

    auto endT = std::chrono::high_resolution_clock::now();
    std::chrono::duration<double> elapsed_secondsT = endT-startT;
    LOGI("OCV-native-lib_elapsedTime-MFtracker: %.3f",elapsed_secondsT);


    *((Mat*)matRects) = Mat(tfaces, true);
}


}
