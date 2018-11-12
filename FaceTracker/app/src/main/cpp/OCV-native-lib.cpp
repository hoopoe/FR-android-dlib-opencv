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
vector<Rect> BBfaces_prev= {};
vector<Rect> newBBfaces_prev= {};
////vector<Rect> tfaces= {};//trackedFaces

//new part-------------------------
int currNumFaces=0;
int oldNumFaces=0;
//end new part---------------------

int SKIPPED_FRAMES = 15;
int counter = 0;


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
    cvtColor(gray,gray,CV_RGBA2GRAY);

    face_cascade.detectMultiScale(gray, faces, 1.1, 3, 0, Size(15,15), Size(1000, 1000));
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
    cvtColor(rgba,gray,CV_RGBA2GRAY);

    double const TH_weight=0.0;//5.0;//Good empirical threshold values: 5-7 (INDOOR) - 4.0 (outdoor)
    //NOTE: The detection range depends on this threshold value.
    //      By reducing this value, you increase the possibility
    //      to detect a smaller face even at a longer distance,
    // but also to have false detections.
    std::vector<int> reject_levels;
    std::vector<double> weights;

    std::vector<Rect> faces = {};
    std::vector<Rect> realfaces = {};
    //face_cascade.detectMultiScale( gray, faces, reject_levels, weights, 1.1, 5, 0|CV_HAAR_SCALE_IMAGE, Size(), Size(1000,1000), true );
    face_cascade.detectMultiScale( gray, faces, reject_levels, weights, 1.1, 3, 0 , Size(15,15), Size(1000,1000), true );
    // face_cascade.detectMultiScale(gray, faces, 1.1, 3, 0, Size(15,15), Size(1000, 1000));

    int i=0;
    for(vector<Rect>::const_iterator r = faces.begin(); r != faces.end(); r++, i++ ) {
        // weights[i] = 0; // AL added ==> to be deleted
        LOGI("weights[%i]:%f, sizeFace[i]: %i", i, weights[i], faces[i].width);
        if (weights[i] >= TH_weight)//Good empirical threshold values: 5-7
        {
            realfaces.push_back(*r);
        }
    }
    LOGI("#realFaces: %i (TH_weight= %.2f)", (int)realfaces.size(), TH_weight);
    //// sort( realfaces.begin(), realfaces.end(), byArea() );//new part (usefull for the tracking part)
    return realfaces;
}

//Function to crop frame and make the detection part faster
vector<Rect> cropImgAndDetectFaces(Mat& rgba){

    vector<Rect> detfaces = {};
    vector<Rect> BBfaces_tmp = {};

    int h = rgba.rows;//along x-axis
    int w = rgba.cols;//along y-axis
    LOGI("origImg.rows [py]: %i, origImg.cols [px]: %i, (counter: %i)", h ,w);
    double px = 0.2;
    double py = 0.3;
    int offx= (int)(px*h/2);
    int offy= (int)(py*w/2);
    LOGI("offx [px]: %i, offy [px]: %i", offx ,offy);
    cv::Rect ROI_frame = cv::Rect(offy,offx,(w-2*offy),(h-2*offx));
    LOGI("ROI_frame (x,y,w,h): %i %i %i %i", ROI_frame.x, ROI_frame.y, ROI_frame.width, ROI_frame.height);
    rectangle(rgba, ROI_frame, Scalar(0,0,0), 8, 8, 0);//black cropped area

    Mat croppedIm = rgba(ROI_frame);
    BBfaces_tmp = detectRF(croppedIm);//vers.2 without HSV conversion
    if(BBfaces_tmp.size()>0) {
        LOGI("#faces_CroppedFrame: %i", BBfaces_tmp.size());

        LOGI("#detectedFaces_inTheCROPPEDframe: %i (counter: %i)", (int) BBfaces_tmp.size(), counter);
        for (int i = 0; i < BBfaces_tmp.size(); i++) {
            detfaces.push_back(Rect(BBfaces_tmp[i].x + offy,
                                    BBfaces_tmp[i].y + offx,
                                    BBfaces_tmp[i].width, BBfaces_tmp[i].height));
            LOGI("i: %i,faces_CroppedFrame (x,y,w,h): %i %i %i %i", i, detfaces[i].x, detfaces[i].y, detfaces[i].width, detfaces[i].height);
            if(BBfaces_tmp.at(i).width>0 & BBfaces_tmp.at(i).height>0) {
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
MultiTracker* mytrackers=NULL;

std::vector<Ptr<Tracker> > algorithms;//tracking algorithm
// set the default tracking algorithm
String trackingAlg = "MOSSE";
// container of the tracked objects
vector<Rect2d> trackedFaces;
bool firstTime = true;
bool updateOK;
bool foundFaces=false;

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
void infoTracker(Mat& frame,String& trackingAlg){
    char strID[200];
    Scalar color = Scalar(0,0,255);
    sprintf(strID,"%s",trackingAlg.c_str());
    putText(frame, strID, Point(600,50), FONT_HERSHEY_PLAIN, 5, color,3);
}

//tracker initialization
MultiTracker trackerInit(Mat &Rgb, vector<Rect> &faces,String &trackingAlg){
    for (size_t i = 0; i <faces.size(); i++)
    {

        //Tracker initialization
        algorithms.push_back(createTrackerByName(trackingAlg));//trackers creation
        trackedFaces.push_back(faces[i]);
        //rectangle(Rgb, faces.at(i), Scalar(255,0,0), 4, 8, 0);//Draw detectedFaces (RED)////
    } // end for
    firstTime = false;
    trackers.add(algorithms,Rgb,trackedFaces);

    return trackers;
}


//new version GetTrackedOBJ----------------------
vector<Rect> GetTrackedOBJ(MultiTracker& trackers,cv::Mat& frame, cv::Scalar& color)
{
    vector<Rect2d> faces_ ={};
    vector<Rect> faces ={};
    int i;
    LOGI("OCV-native-lib_#trackedFacesToDraw: %i",(int)trackers.getObjects().size());
    for (i = 0; i < (int)trackers.getObjects().size(); i++)
    {
        faces_.push_back(trackers.getObjects().at(i));
        faces.push_back(Rect(faces_[i]));
        LOGI("OCV-native-lib_tfaces[%i] (x,y,w,h) %i,%i,%i,%i ",i,(int)faces[i].x,(int)faces[i].y,(int)faces[i].width,(int)faces[i].height);
        LOGI("NEW_OBJ[%i] (x,y,w,h) %i,%i,%i,%i ",i,(int)trackers.getObjects().at(i).x,(int)trackers.getObjects().at(i).y,
             (int)trackers.getObjects().at(i).width,(int)trackers.getObjects().at(i).height);
    }
    return faces;
}


// Get tracked object
void DrawTrackedOBJ(MultiTracker& trackers,cv::Mat& frame, cv::Scalar& color)
{LOGI("Drawing");
    char strID[200];
    for (size_t i = 0; i < trackers.getObjects().size(); i++)
    {
        rectangle(frame, trackers.getObjects().at(i), color, 18, 8, 0);
        sprintf(strID,"ID: %i",(int)i);
        putText(frame, strID, Point((int)trackers.getObjects().at(i).x+(int)trackers.getObjects().at(i).width,(int)trackers.getObjects().at(i).y+(int)(trackers.getObjects().at(i).height*0.5)), FONT_HERSHEY_PLAIN, 2, color,3);
    }
}


//NEW version (vers.3)
JNIEXPORT void JNICALL
Java_org_opencv_android_facetracker_HaarDetector_OpenCVdetector(JNIEnv *env, jclass instance,
                                                                jlong inputAddrMat, jlong matRects) {
    Mat origImg = *((Mat *) inputAddrMat);

    LOGI("ch_detector: %i",origImg.channels());
    auto start_timeD = std::chrono::high_resolution_clock::now();

    counter++;
    vector<Rect> BBfaces= {};//detectedFaces

    BBfaces = detect(origImg);
    // BBfaces = detectRF(origImg);//The detection is performed on the whole frame

    //Search for faces into a cropped frame to make faster the detection
    //The hypothesis is that the possible faces are inside the central part of the whole frame
    // BBfaces = cropImgAndDetectFaces(origImg); // crops of images

    if(BBfaces.size()>0) {
        BBfaces_prev.clear();
        //create history
        for (size_t i = 0; i < BBfaces.size(); i++) {
            BBfaces_prev.push_back(BBfaces.at(i));
        }
    }//if

    auto end_timeD = std::chrono::high_resolution_clock::now();
    std::chrono::duration<double> elapsed_seconds_onlyDet = end_timeD-start_timeD;
    LOGI("OCV-native-lib_elapsedTime-detectONLY: %.3f",elapsed_seconds_onlyDet);

    LOGI("OCV-native-lib_#BBfacesRETURNED: %i",(int)BBfaces.size());
    *((Mat*)matRects) = Mat(BBfaces, true);
}







JNIEXPORT void JNICALL
Java_org_opencv_android_facetracker_HaarDetector_OpenCVtracker(JNIEnv *env, jclass instance,
                                                               jlong inputAddrMat, jlong matRects) {
    vector<Rect> tfaces= {};//trackedFaces
    ////Mat &origImg = *((Mat *) inputAddrMat);
    Mat origImg = *((Mat *) inputAddrMat);


    LOGI("ch_tracker: %i",origImg.channels());
    infoTracker(origImg,trackingAlg);

    //current detected faces or tracked faces
    vector<Rect> _BBfaces = *((Mat *) matRects);

    if((trackingAlg == "KCF") || (trackingAlg == "MOSSE") ){
        //the MOSSE tracker works with grayscale images
        Mat im;
        LOGI("ch_before %i:",origImg.channels());
        cvtColor(origImg,origImg,CV_RGBA2GRAY);
        LOGI("ch_after %i",origImg.channels());
    }

    //sanity check---------------------------------------------------------------------------------
    //current detected faces or tracked faces
    for (int i=0;i<_BBfaces.size();i++) {
        LOGI("OCV-native-lib_currBBfaces (x,y,w,h): %i,%i,%i,%i", (int)_BBfaces.at(i).x,
             (int)_BBfaces.at(i).y, (int)_BBfaces.at(i).width, (int)_BBfaces.at(i).height);
    }
    //previous detected faces
    for (int i=0;i<BBfaces_prev.size();i++) {
        LOGI("OCV-native-lib_BBfaces_prev (x,y,w,h): %i,%i,%i,%i", (int)BBfaces_prev.at(i).x,
             (int)BBfaces_prev.at(i).y, (int)BBfaces_prev.at(i).width, (int)BBfaces_prev.at(i).height);
    }
    //--------------------------------------------------------------------------------------------

    //new part-------------------
    currNumFaces=(int)_BBfaces.size();
    //face history
    oldNumFaces = (int)BBfaces_prev.size();
    //end new part---------------


    Scalar color = Scalar(0,0,255);//blue
    auto startT = std::chrono::high_resolution_clock::now();

    //Face tracking
    if(firstTime)
    {
        auto startI = std::chrono::high_resolution_clock::now();

        //Tracker initialization
        trackers = trackerInit(origImg,_BBfaces,trackingAlg);
        LOGI("OCV-native-lib_#trackers_INIT: %i",(int)trackers.getObjects().size());

        auto endI = std::chrono::high_resolution_clock::now();
        std::chrono::duration<double> elapsed_secondsI = endI-startI;
        LOGI("OCV-native-lib_elapsedTime_Initialization: %.3f",elapsed_secondsI);
    } // end if first time
    else
    {
        auto startU = std::chrono::high_resolution_clock::now();

        //================
        LOGI("OCV-native-lib_#_BBfaces: %i",(int)_BBfaces.size());
        LOGI("OCV-native-lib_#trackers: %i",(int)trackers.getObjects().size());
        ////LOGI("OCV-native-lib_#tfaces: %i",(int)tfaces.size());

        //new part--------------------------
        //check for the variation of the detected faces number
        if((int)BBfaces_prev.size()!=(int)trackers.getObjects().size())
        {
            //if(currNumFaces>oldNumFaces)
            //Add trackers if the current number of DETECTED faces is grater than the number of trackers
            if((int)BBfaces_prev.size()>(int)trackers.getObjects().size())
            {
                int delta = (int)BBfaces_prev.size() - (int)trackers.getObjects().size();
                LOGI("ADD N. %i",delta," tracker(s)");

                vector<Rect2d> newTrackedFaces;

                /*algorithms.clear();
                trackedFaces.clear();*/
                mytrackers=trackers.create();
                trackers=*mytrackers;

                //for (size_t i = 0; i <currNumFaces; i++)
                for (size_t i = 0; i <delta; i++)
                {
                    LOGI("i %i, pos: %i, _BBfaces_toAdd (x,y,w,h): %i, %i, %i, %i", i,(int)(BBfaces_prev.size()-1-i),
                         BBfaces_prev[BBfaces_prev.size()-1-i].x,BBfaces_prev[BBfaces_prev.size()-1-i].y,
                         BBfaces_prev[BBfaces_prev.size()-1-i].width,BBfaces_prev[BBfaces_prev.size()-1-i].height);
                    //Tracker initialization
                    algorithms.push_back(createTrackerByName(trackingAlg));//trackers creation
                    newTrackedFaces.push_back(BBfaces_prev[BBfaces_prev.size()-1-i]);//add last detected faces
                    ////trackedFaces.push_back(BBfaces_prev[BBfaces_prev.size()-1-i]);//add last detected faces
                } // end for

                //trackers.add(algorithms,origImg,trackedFaces);
                trackers.add(algorithms,origImg,newTrackedFaces);
                tfaces = GetTrackedOBJ(trackers,origImg,color);
                LOGI("#tfaces: %i",(int)tfaces.size());

            }
            LOGI("currNumFaces: %i, oldNumFaces: %i", currNumFaces, oldNumFaces);
            LOGI("currBBfaces(#_BBfaces): %i, #trackers_AFTERnewDetectedFace: %i", (int)_BBfaces.size(), (int)trackers.getObjects().size());
            for (int i = 0; i <(int)trackers.getObjects().size(); i++) {
                LOGI("obj(x,y,z,h): "+(int)trackers.getObjects()[i].x,(int)trackers.getObjects()[i].y,(int)trackers.getObjects()[i].width,(int)trackers.getObjects()[i].height);
            }
        }

        auto startUvero = std::chrono::high_resolution_clock::now();
        updateOK = trackers.update(origImg);
        auto endUvero = std::chrono::high_resolution_clock::now();
        std::chrono::duration<double> elapsed_secondsUvero = endUvero-startUvero;
        LOGI("OCV-native-lib_elapsedTime_UpdatingVERO: %.3f",elapsed_secondsUvero);



        if(updateOK)
        {
            LOGI("updateOK: %i",(int)updateOK);
            tfaces = GetTrackedOBJ(trackers,origImg,color);//commented
            DrawTrackedOBJ(trackers,origImg,color);

            //new part----------------
            LOGI("OCV-native-lib_#tfaces: %i",(int)tfaces.size());
            Scalar cyan = Scalar(0,255,255);
            DrawTrackedOBJ(trackers,origImg,cyan);
            //end new part------------

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

    LOGI("OCV-native-lib_#tfacesRETURNED: %i",(int)tfaces.size());
    *((Mat*)matRects) = Mat(tfaces, true);

}


}