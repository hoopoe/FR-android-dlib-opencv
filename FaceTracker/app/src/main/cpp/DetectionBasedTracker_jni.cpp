#include "DetectionBasedTracker_jni.h"
#include <opencv2/core.hpp>
#include <opencv2/objdetect.hpp>

#include <string>
#include <vector>

#include <android/log.h>
#include <chrono>
#include <opencv2/imgproc/imgproc.hpp>
#include <opencv2/tracking/tracker.hpp> //MEDIANFLOW tracker

#define LOG_TAG "OCV-DBasedT"
#define LOGD(...) ((void)__android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__))
#define  LOGI(...)  __android_log_print(ANDROID_LOG_INFO,LOG_TAG,__VA_ARGS__)

using namespace std;
using namespace cv;

inline void vector_Rect_to_Mat(vector<Rect>& v_rect, Mat& mat)
{
    mat = Mat(v_rect, true);
}

struct byArea {
    bool operator()(const Rect &a, const Rect &b) {
        return a.width * a.height < b.width * b.height;
    }
};


int frameCounter = 0;
vector<Rect> RectFaces, tfaces;

//Opencv Tracker
// create multitracker
MultiTracker trackers;//This class is used to track multiple objects using the specified tracker algorithm.
MultiTracker *mytrackers = NULL;

std::vector<Ptr<Tracker> > algorithms;//tracking algorithm

// set the default tracking algorithm
String trackingAlg = "MEDIAN_FLOW"; //default tracking Algorithm

// container of the tracked objects
vector<Rect2d> trackedFaces;

bool firstTime = true;
bool initOK = false;
bool updateOK;

cv::Ptr<cv::Tracker> OCVtracker = cv::TrackerMedianFlow::create();


class CascadeDetectorAdapter : public DetectionBasedTracker::IDetector {
public:
    CascadeDetectorAdapter(cv::Ptr<cv::CascadeClassifier> detector) :
            IDetector(),
            Detector(detector) {
        LOGD("CascadeDetectorAdapter::Detect::Detect");
        CV_Assert(detector);
    }

    void detect(const cv::Mat &Image, std::vector<cv::Rect> &objects) {
        LOGI("CascadeDetectorAdapter::Detect: begin---------------");
        //RectFaces.clear();
        double const TH_weight = 2.5; //5.0;//Good empirical threshold values: 5-7
        std::vector<int> reject_levels;
        std::vector<double> weights;
        std::vector<Rect> faces = {};
        double scaleFactor = 1.1;
        int minNeighbours = 5;
        Detector->detectMultiScale(Image, faces, reject_levels, weights, scaleFactor, minNeighbours,
                                   0 | CV_HAAR_SCALE_IMAGE, Size(), Size(1000, 1000), true);
        frameCounter += 1;
        LOGI("CascadeDetectorAdapter::Detect: scaleFactor=%.2f, minNeighbours=%i, TH_weight=%.2f, frameCounter:%i",
             scaleFactor, minNeighbours, TH_weight, frameCounter);

        int i=0;
        for (vector<Rect>::const_iterator r = faces.begin(); r != faces.end(); r++, i++) {
            //LOGI("weights[i]:%f", weights[i]);
            LOGI("detected Faces size (wxh): %i x %i - weight:%.2f", (int) faces.at(i).width,
                 (int) faces.at(i).height, weights[i]);
            if (weights[i] >= TH_weight)//Good empirical threshold values: 5-7
            {
                objects.push_back(*r);
                RectFaces.push_back(*r);
                LOGI("#realFaces (x,y,w,h): %i, %i, %i, %i", (int) objects[i].x, (int) objects[i].y,
                     (int) objects[i].width, (int) objects[i].height);
            }
        }
        sort( RectFaces.begin(), RectFaces.end(), byArea() );
        LOGI("#detectedFaces: %i (TH_weight= %.2f)", (int) faces.size(), TH_weight);
        LOGI("#realFaces: %i (TH_weight= %.2f)", (int) objects.size(), TH_weight);
        LOGI("CascadeDetectorAdapter::Detect: end----------");
    }


    virtual ~CascadeDetectorAdapter() {
        LOGI("CascadeDetectorAdapter::Detect::~Detect");
    }

private:
    CascadeDetectorAdapter();
    cv::Ptr<cv::CascadeClassifier> Detector;

};

struct DetectorAgregator
{
    cv::Ptr<CascadeDetectorAdapter> mainDetector;
    cv::Ptr<CascadeDetectorAdapter> trackingDetector;

    cv::Ptr<DetectionBasedTracker> tracker;
    DetectorAgregator(cv::Ptr<CascadeDetectorAdapter>& _mainDetector, cv::Ptr<CascadeDetectorAdapter>& _trackingDetector):
            mainDetector(_mainDetector),
            trackingDetector(_trackingDetector)
    {
        CV_Assert(_mainDetector);
        CV_Assert(_trackingDetector);

        DetectionBasedTracker::Parameters DetectorParams;
        tracker = makePtr<DetectionBasedTracker>(mainDetector, trackingDetector, DetectorParams);
    }
};

JNIEXPORT jlong JNICALL Java_opencv_android_fdt_DetectionBasedTracker_nativeCreateObject
        (JNIEnv * jenv, jclass, jstring jFileName, jint faceSize)
{
    LOGD("Java_opencv_android_fdt_DetectionBasedTrackerMOD_nativeCreateObject enter");
    const char* jnamestr = jenv->GetStringUTFChars(jFileName, NULL);
    string stdFileName(jnamestr);
    jlong result = 0;

    LOGI("Java_opencv_android_fdt_DetectionBasedTrackerMOD_nativeCreateObject");

    try
    {
        cv::Ptr<CascadeDetectorAdapter> mainDetector = makePtr<CascadeDetectorAdapter>(
                makePtr<CascadeClassifier>(stdFileName));
        cv::Ptr<CascadeDetectorAdapter> trackingDetector = makePtr<CascadeDetectorAdapter>(
                makePtr<CascadeClassifier>(stdFileName));
        result = (jlong)new DetectorAgregator(mainDetector, trackingDetector);

        if (faceSize > 0)
        {
            mainDetector->setMinObjectSize(Size(faceSize, faceSize));
            //trackingDetector->setMinObjectSize(Size(faceSize, faceSize));//uncommented
        }
    }
    catch(cv::Exception& e)
    {
        //LOGD("nativeCreateObject caught cv::Exception: %s", e.what());
        LOGI("nativeCreateObject caught cv::Exception: %s", e.what());
        jclass je = jenv->FindClass("org/opencv/core/CvException");
        if(!je)
            je = jenv->FindClass("java/lang/Exception");
        jenv->ThrowNew(je, e.what());
    }
    catch (...) {
        LOGI("nativeCreateObject caught unknown exception");
        jclass je = jenv->FindClass("java/lang/Exception");
        jenv->ThrowNew(je,"Unknown exception in JNI code of DetectionBasedTrackerMOD.nativeCreateObject()");
        return 0;
    }

    LOGI("Java_opencv_android_fdt_DetectionBasedTrackerMOD_nativeCreateObject exit");
    return result;
}

JNIEXPORT void JNICALL Java_opencv_android_fdt_DetectionBasedTracker_nativeDestroyObject
        (JNIEnv *jenv, jclass, jlong thiz) {
    LOGD("Java_opencv_android_fdt_DetectionBasedTrackerMOD_nativeDestroyObject");

    try {
        if (thiz != 0) {
            ((DetectorAgregator *) thiz)->tracker->stop();
            delete (DetectorAgregator *) thiz;
        }
    }
    catch (cv::Exception &e) {
        LOGI("nativeDestroyObject caught cv::Exception: %s", e.what());
        jclass je = jenv->FindClass("org/opencv/core/CvException");
        if (!je)
            je = jenv->FindClass("java/lang/Exception");
        jenv->ThrowNew(je, e.what());
    }
    catch (...) {
        LOGI("nativeDestroyObject caught unknown exception");
        jclass je = jenv->FindClass("java/lang/Exception");
        jenv->ThrowNew(je, "Unknown exception in JNI code of DetectionBasedTrackerMOD.nativeDestroyObject()");
    }
    LOGI("Java_opencv_android_fdt_DetectionBasedTrackerMOD_nativeDestroyObject exit");
}

JNIEXPORT void JNICALL Java_opencv_android_fdt_DetectionBasedTracker_nativeStart
        (JNIEnv *jenv, jclass, jlong thiz) {
    LOGI("Java_opencv_android_fdt_DetectionBasedTracker_nativeStart");

    try {
        ((DetectorAgregator *) thiz)->tracker->run();
    }
    catch (cv::Exception &e) {
        LOGI("nativeStart caught cv::Exception: %s", e.what());
        jclass je = jenv->FindClass("org/opencv/core/CvException");
        if (!je)
            je = jenv->FindClass("java/lang/Exception");
        jenv->ThrowNew(je, e.what());
    }
    catch (...) {
        LOGI("nativeStart caught unknown exception");
        jclass je = jenv->FindClass("java/lang/Exception");
        jenv->ThrowNew(je,
                       "Unknown exception in JNI code of DetectionBasedTrackerMOD.nativeStart()");
    }
    LOGI("Java_opencv_android_fdt_DetectionBasedTrackerMOD_nativeStart exit");
}

JNIEXPORT void JNICALL Java_opencv_android_fdt_DetectionBasedTracker_nativeStop
        (JNIEnv *jenv, jclass, jlong thiz) {
    LOGI("Java_opencv_android_fdt_DetectionBasedTrackerMOD_nativeStop");

    try {
        ((DetectorAgregator *) thiz)->tracker->stop();
    }
    catch (cv::Exception &e) {
        LOGI("nativeStop caught cv::Exception: %s", e.what());
        jclass je = jenv->FindClass("org/opencv/core/CvException");
        if (!je)
            je = jenv->FindClass("java/lang/Exception");
        jenv->ThrowNew(je, e.what());
    }
    catch (...) {
        LOGI("nativeStop caught unknown exception");
        jclass je = jenv->FindClass("java/lang/Exception");
        jenv->ThrowNew(je,"Unknown exception in JNI code of DetectionBasedTrackerMOD.nativeStop()");
    }
    LOGI("Java_opencv_android_fdt_DetectionBasedTrackerMOD_nativeStop exit");
}

JNIEXPORT void JNICALL Java_opencv_android_fdt_DetectionBasedTracker_nativeSetFaceSize
        (JNIEnv *jenv, jclass, jlong thiz, jint faceSize) {
    LOGI("Java_opencv_android_fdt_DetectionBasedTrackerMOD_nativeSetFaceSize -- BEGIN");

    try {
        if (faceSize > 0) {
            LOGI("Java_opencv_android_fdt_DetectionBasedTrackerMOD_nativeSetFaceSize -> faceSize: %i",
                 (int) faceSize);
            ((DetectorAgregator *) thiz)->mainDetector->setMinObjectSize(Size(faceSize, faceSize));
//((DetectorAgregator*)thiz)->trackingDetector->setMinObjectSize(Size(faceSize, faceSize));//commented
        }
    }
    catch (cv::Exception &e) {
        LOGI("nativeStop caught cv::Exception: %s", e.what());
        jclass je = jenv->FindClass("org/opencv/core/CvException");
        if (!je)
            je = jenv->FindClass("java/lang/Exception");
        jenv->ThrowNew(je, e.what());
    }
    catch (...) {
        LOGI("nativeSetFaceSize caught unknown exception");
        jclass je = jenv->FindClass("java/lang/Exception");
        jenv->ThrowNew(je,
                       "Unknown exception in JNI code of DetectionBasedTracker.nativeSetFaceSize()");
    }
    LOGI("Java_opencv_android_fdt_DetectionBasedTracker_nativeSetFaceSize -- END");
}


JNIEXPORT void JNICALL Java_opencv_android_fdt_DetectionBasedTracker_nativeDetect
        (JNIEnv *jenv, jclass, jlong thiz, jlong imageGray, jlong faces) {
    LOGI("Java_opencv_android_fdt_DetectionBasedTracker_nativeDetect");

    auto start = std::chrono::high_resolution_clock::now();

    try {
//vector<Rect> RectFaces;
        ((DetectorAgregator *) thiz)->tracker->process(*((Mat *) imageGray));//start detector too
/*((DetectorAgregator*)thiz)->tracker->getObjects(RectFaces);*/

        LOGI("DBT_nativeDetect #RectFaces(tracked): %i", (int) RectFaces.size());

       /* for (int i = 0; i < (int) RectFaces.size(); i++) {
            //rectangle(*((Mat *) imageGray), RectFaces[i], Scalar(255,255,255), 4, 8, 0);
            rectangle(*((Mat *) imageGray), Point(RectFaces[i].x,RectFaces[i].y),
                      Point(RectFaces[i].x+RectFaces[i].width,RectFaces[i].y+RectFaces[i].height), Scalar(255,255,255), 4, 8, 0);
        }*/



        //-------------------
        //Added MF tracker
          vector<Rect> oldFaces;//, tfaces;
          int currNumFaces=0;
          int oldNumFaces=0;
          bool foundFaces=false;
          if(RectFaces.size()>0) {
            // RectFaces.erase (RectFaces.end());//to remove a systematic false detection
              /*for (int i = 0; i < (int) RectFaces.size(); i++) {
                  rectangle(*((Mat *) imageGray), RectFaces[i], Scalar(255,255,255), 4, 8, 0);
              }*/
              if(firstTime)
              {
                  for (size_t i = 0; i <RectFaces.size(); i++)
                  {
                      foundFaces =true;

                      //Tracker initialization
                      algorithms.push_back(OCVtracker);//trackers creation
                      trackedFaces.push_back(RectFaces[i]);
                      LOGI("#trackedFaces:%i", (int)trackedFaces.size());
                      tfaces.push_back(trackedFaces.at(i));//new part

                      //create faces history
                      oldFaces.push_back(RectFaces.at(i));//create history faces
                  } // end for
                  firstTime = false;
                  oldNumFaces = (int)oldFaces.size();
                  LOGI("#oldNumFaces:%i", oldNumFaces);
                  trackers.add(algorithms,*((Mat *) imageGray),trackedFaces);
                  LOGI("#OCVtrackes:%i", algorithms.size());//to cancel
              } // end if first time
              else
              {

                  //check for the variation of the detected faces number
            /*      if(currNumFaces!=trackers.getObjects().size())
                  {
                      if(currNumFaces>oldNumFaces)
                      {
                          vector<Rect2d> newTrackedFaces;

                          algorithms.clear();
                          trackedFaces.clear();
                          mytrackers=trackers.create();
                          trackers=*mytrackers;

                          for (size_t i = 0; i <currNumFaces; i++)
                          {
                              //Tracker initialization
                              algorithms.push_back(OCVtracker);//trackers creation
                              newTrackedFaces.push_back(RectFaces[RectFaces.size()-1-i]);//add last detected faces
                              tfaces.push_back(newTrackedFaces.at(i));//new part
                          } // end for
                          trackers.add(algorithms,*((Mat *) imageGray),newTrackedFaces);

                      }
                      else if (currNumFaces<oldNumFaces)
                      {

                          if(currNumFaces<trackers.getObjects().size())
                          {
                              __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, "REMOVE tracker(s)_Num: %i",(int)trackers.getObjects().size());
                          }
                      }
                  }//if(currNumFaces!=trackers.targetNum)
*/
                  for(int i=0;i<RectFaces.size();i++)
                  {

                      vector<Rect2d> roi;
                      roi.push_back(RectFaces[RectFaces.size()-1+i]);
                      updateOK = trackers.update(*((Mat *) imageGray),roi);
                      LOGI("updateOK: %i",(int)updateOK);
                      if(updateOK)
                      {
                          for (size_t i = 0; i < trackers.getObjects().size(); i++) {
                              tfaces.push_back(trackers.getObjects().at(i));
                          }
                          LOGI("updateOK: %i -> DrawTrackedFaces",(int)updateOK);
                      }
                      else
                      {
                          LOGI(" if(updateOK==0), #algorithms: %i", (int)algorithms.size());
                          algorithms.clear();
                          trackedFaces.clear();
                          mytrackers=trackers.create();
                          trackers=*mytrackers;
                          trackers.create();
                          firstTime=true;

                      }
                  }


                  //clear faces history & update it
                  oldFaces.clear();
                  for (size_t i = 0; i < RectFaces.size(); i++)
                  {
                      oldFaces.push_back(RectFaces.at(i));
                  }
                  oldNumFaces = (int)oldFaces.size();
              }//else firstTime
          }
          else // there are no faces
          {
              if (oldNumFaces >0)
              {
                  //continue to track the old faces (only trackers updating)
                  updateOK=trackers.update(*((Mat *) imageGray));
                  if(updateOK)
                  {
                      for (size_t i = 0; i < trackers.getObjects().size(); i++) {
                          tfaces.push_back(trackers.getObjects().at(i));
                      }
                  }
                  else
                  {
                      foundFaces=false;
                  }
              }//if (oldNumFaces !=0)
              else
              {
                  __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, "No faces history");
              }
          }
         *((Mat*)faces) = Mat(tfaces, true);

        //-------------------

        //*((Mat *) faces) = Mat(RectFaces, true);


    }
    catch (cv::Exception &e) {
        LOGI("nativeCreateObject caught cv::Exception: %s", e.what());
        jclass je = jenv->FindClass("org/opencv/core/CvException");
        if (!je)
            je = jenv->FindClass("java/lang/Exception");
        jenv->ThrowNew(je, e.what());
    }
    catch (...) {
        LOGI("nativeDetect caught unknown exception");
        jclass je = jenv->FindClass("java/lang/Exception");
        jenv->ThrowNew(je, "Unknown exception in JNI code DetectionBasedTracker.nativeDetect()");
    }
    LOGI("Java_opencv_android_fdt_DetectionBasedTracker_nativeDetect END");
    tfaces.clear();
    RectFaces.clear();
}

