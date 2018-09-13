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

//////vector<Rect> BBfaces= {};//detectedFaces
vector<Rect> tfaces= {};//trackedFaces

//================================
vector<Rect> BBfaces_prev= {};
int SKIPPED_FRAMES = 15;
int counter = 0;
//===============================

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
/*std::vector<Rect> detectRF(Mat &gray) {

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
}*/


//New HAAR detection function to reduce false detection (vers.2)
std::vector<Rect> detectRF(Mat &gray) {


    double const TH_weight=5.0;//Good empirical threshold values: 5-7 (INDOOR) - 4.0 (outdoor)
    //NOTE: The detection range depends on this threshold value.
    //      By reducing this value, you increase the possibility
    //      to detect a smaller face even at a longer distance,
    // but also to have false detections.
    std::vector<int> reject_levels;
    std::vector<double> weights;

    std::vector<Rect> faces = {};
    std::vector<Rect> realfaces = {};
    face_cascade.detectMultiScale( gray, faces, reject_levels, weights, 1.1, 5, 0|CV_HAAR_SCALE_IMAGE, Size(), Size(1000,1000), true );
    int i=0;
    for(vector<Rect>::const_iterator r = faces.begin(); r != faces.end(); r++, i++ ) {
        LOGI("weights[%i]:%f, sizeFace[i]: %i", i, weights[i], faces[i].width);
        if (weights[i] >= TH_weight)//Good empirical threshold values: 5-7
        {
            realfaces.push_back(*r);
        }
    }
    LOGI("#realFaces: %i (TH_weight= %.2f)", (int)realfaces.size(), TH_weight);
    ////sort( realfaces.begin(), realfaces.end(), byArea() );
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
        ////rectangle(Rgb, faces.at(i), Scalar(255,255,0), 4, 8, 0);//Draw detectedFaces (YELLOW)
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
    LOGI("OCV-native-lib_#trackedFacesToDraw: %i",(int)trackers.getObjects().size());
    for (i = 0; i < (int)trackers.getObjects().size(); i++)
    {
        tfaces.push_back(trackers.getObjects().at(i));
        LOGI("OCV-native-lib_tfaces[%i] (x,y,w,h) %i,%i,%i,%i ",i,(int)tfaces[i].x,(int)tfaces[i].y,(int)tfaces[i].width,(int)tfaces[i].height);
    }
    return tfaces;
}


// Get tracked object
void DrawTrackedOBJ(MultiTracker& trackers,cv::Mat& frame, cv::Scalar& color)
{
    char strID[200];
    for (size_t i = 0; i < trackers.getObjects().size(); i++)
    {
        rectangle(frame, trackers.getObjects().at(i), color, 10, 4, 0);
        sprintf(strID,"ID: %i",(int)i);
        putText(frame, strID, Point((int)trackers.getObjects().at(i).x+(int)trackers.getObjects().at(i).width,(int)trackers.getObjects().at(i).y+(int)(trackers.getObjects().at(i).height*0.5)), FONT_HERSHEY_PLAIN, 2, color,3);
    }
}



/*JNIEXPORT void JNICALL
Java_org_opencv_android_facetracker_HaarDetector_OpenCVdetector(JNIEnv *env, jclass instance,
                                                                jlong inputAddrMat, jlong matRects) {
    Mat &origImg = *((Mat *) inputAddrMat);
    Mat mGray;

    auto start_timeD = std::chrono::high_resolution_clock::now();

    BBfaces = detectRF(origImg);//new_version with HSV conversion
    //BBfaces = detect(origImg);//old version with more false detections
    LOGI("#detectedFaces_cpp: %i",(int)BBfaces.size());

    auto end_timeD = std::chrono::high_resolution_clock::now();
    std::chrono::duration<double> elapsed_seconds_onlyDet = end_timeD-start_timeD;
    LOGI("OCV-native-lib_elapsedTime-detectONLY: %.3f",elapsed_seconds_onlyDet);

    *((Mat*)matRects) = Mat(BBfaces, true);
}*/

//NEW version (vers.2)
JNIEXPORT void JNICALL
Java_org_opencv_android_facetracker_HaarDetector_OpenCVdetector(JNIEnv *env, jclass instance,
                                                                jlong inputAddrMat, jlong matRects) {
    Mat &origImg = *((Mat *) inputAddrMat);
    Mat mGray;

    auto start_timeD = std::chrono::high_resolution_clock::now();

    counter++;
    vector<Rect> BBfaces= {};//detectedFaces

    //The detection is performed on the whole frame every each 2 frame (SKIPPED_FRAMES = 2)
    //In the other cases, the detection is performed only around the previous detected faces.
    if((counter==1) || (counter % SKIPPED_FRAMES ==0)) {
        //if(counter % SKIPPED_FRAMES ==0) {
        BBfaces = detectRF(origImg);//vers.2 without HSV conversion
        LOGI("#detectedFaces_onWHOLEframe: %i", (int) BBfaces.size());

        if(BBfaces.size()>0) {
            BBfaces_prev.clear();
            //create history
            for (size_t i = 0; i < BBfaces.size(); i++) {
                BBfaces_prev.push_back(BBfaces.at(i));

                /*   rectangle(origImg, BBfaces.at(i),
                         Scalar(255, 255, 255), 18, 8,
                         0);//bianco
                */
            }
        }//if
    }
    else{
        if(BBfaces_prev.size()>0) {
            Mat cropI, cropI_;
            vector<Rect> tmp = {};
            //int i, j;
            size_t i,j;
            for ( i = 0; i < BBfaces_prev.size(); i++) {


                //detect faces in ROI around the previously detected faces
                double s = 0.7;//0.5; //0.2;//scale factor
                Rect2d ROI = BBfaces_prev.at(i);
                LOGI("controllo_ROI_BEFORE (x,y,w,h): %i %i %i %i", (int) ROI.x, (int) ROI.y,
                     (int) ROI.width, (int) ROI.height);

                cv::Rect newROI = cv::Rect((int) (ROI.x * (1 - s)), (int) (ROI.y * (1 - s)),
                                           (int) (ROI.width + ROI.x * 2 * s),
                                           (int) (ROI.height + ROI.y * 2 * s));
                LOGI("controllo_newROI_BEFORE (x,y,w,h): %i %i %i %i", (int) newROI.x,
                     (int) newROI.y, (int) newROI.width, (int) newROI.height);

                //Valid ROI--------------------
                Rect frameSize = cv::Rect(0, 0, origImg.cols, origImg.rows);
                if (newROI.x < frameSize.x) {
                    newROI.width += newROI.x;
                    newROI.x = frameSize.x;
                }
                if (newROI.y < frameSize.y) {
                    newROI.height += newROI.y;
                }
                if (newROI.x + newROI.width > frameSize.width) {
                    newROI.width = frameSize.width - newROI.x;
                }
                if (newROI.y + newROI.height > frameSize.height) {
                    newROI.height = frameSize.height - newROI.y;
                }
                LOGI("controllo_newROI_AFTER (x,y,w,h): %i %i %i %i", newROI.x,
                      newROI.y, newROI.width, newROI.height);
                //------------------------------

                cropI = origImg(newROI);
                tmp = detectRF(cropI);
                LOGI("#detectedFaces_onROIs: %i", (int) tmp.size());

                if(tmp.size()>0) {

                    //update faces history
                    BBfaces_prev.clear();
                    BBfaces.clear();
                    for (j = 0; j < tmp.size(); j++) {
                        if (tmp.at(j).width > 0 && tmp.at(j).height > 0) {
                            BBfaces.push_back(Rect(tmp.at(j).x + newROI.x, tmp.at(j).y + newROI.y,
                                                   tmp.at(j).width, tmp.at(j).height));

                            BBfaces_prev.push_back(Rect(tmp.at(j).x + newROI.x, tmp.at(j).y + newROI.y,
                                                        tmp.at(j).width, tmp.at(j).height));
                            LOGI("controllo_BBfacesTMP (x,y,w,h): %i %i %i %i",
                                 tmp.at(j).x + newROI.x, tmp.at(j).y + newROI.y,
                                 tmp.at(j).width, tmp.at(j).height);

                           /* rectangle(origImg, Rect(tmp.at(j).x + newROI.x, tmp.at(j).y + newROI.y,
                                                    tmp.at(j).width, tmp.at(j).height),
                                      Scalar(255, 0, 0), 18, 8,
                                      0);//RED for new detected faces in a ROIs
                           */
                        }
                    }// for (j = 0; j < tmp.size(); j++)
                }

            }//for
        }
    }//else

    auto end_timeD = std::chrono::high_resolution_clock::now();
    std::chrono::duration<double> elapsed_seconds_onlyDet = end_timeD-start_timeD;
    LOGI("OCV-native-lib_elapsedTime-detectONLY: %.3f",elapsed_seconds_onlyDet);

    LOGI("OCV-native-lib_#BBfacesRETURNED: %i",(int)BBfaces.size());
    *((Mat*)matRects) = Mat(BBfaces, true);
}


JNIEXPORT void JNICALL
Java_org_opencv_android_facetracker_HaarDetector_OpenCVtracker(JNIEnv *env, jclass instance,
                                                               jlong inputAddrMat, jlong matRects) {
    Mat &origImg = *((Mat *) inputAddrMat);

    vector<Rect> BBfaces = *((Mat *) matRects);//, oldFaces;
    for (int i=0;i<BBfaces.size();i++) {
        LOGI("OCV-native-lib_currBBfaces (x,y,w,h): %i,%i,%i,%i", (int)BBfaces.at(i).x,
             (int)BBfaces.at(i).y, (int)BBfaces.at(i).width, (int)BBfaces.at(i).height);
    }
    Scalar color = Scalar(0,0,255);//blue
    tfaces.clear();
    auto startT = std::chrono::high_resolution_clock::now();

    //Face tracking
    if(firstTime)
    {
        auto startI = std::chrono::high_resolution_clock::now();

        //Tracker initialization
        trackers = trackerInit(origImg,BBfaces,trackingAlg);
        LOGI("OCV-native-lib_#trackers_INIT: %i",(int)trackers.getObjects().size());

        auto endI = std::chrono::high_resolution_clock::now();
        std::chrono::duration<double> elapsed_secondsI = endI-startI;
        LOGI("OCV-native-lib_elapsedTime_Initialization: %.3f",elapsed_secondsI);
    } // end if first time
    else
    {
        auto startU = std::chrono::high_resolution_clock::now();

        //================
        LOGI("OCV-native-lib_#BBfaces: %i",(int)BBfaces.size());
        LOGI("OCV-native-lib_#trackers: %i",(int)trackers.getObjects().size());
        LOGI("OCV-native-lib_#tfaces: %i",(int)tfaces.size());

        updateOK = trackers.update(origImg);

        if(updateOK)
        {
            LOGI("updateOK: %i",(int)updateOK);
            tfaces = GetTrackedOBJ(trackers,origImg,color);//commented
            //DrawTrackedOBJ(trackers,origImg,color);

        }
        else {
            LOGI("updateOK: %i -> New detection & initialization", (int) updateOK);

            //remove all trackers
            algorithms.clear();
            trackedFaces.clear();
            mytrackers = trackers.create();
            trackers = *mytrackers;

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
