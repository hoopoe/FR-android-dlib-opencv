#include <opencv2/core.hpp>
#include <opencv2/objdetect.hpp>
#include <string>
#include <vector>
//#include <opencv2/opencv.hpp>
#include <android/log.h>
#include <chrono>
//#include <opencv2/imgproc.hpp>



#define LOG_TAG "Fd-DBasedT"
#define LOGD(...) ((void)__android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__))
#define LOGI(...)  __android_log_print(ANDROID_LOG_INFO,LOG_TAG,__VA_ARGS__)



using namespace std;
using namespace cv;

int numframe        = 0;
int nativeNumFrame  = 0;
int firstDetection  = 0;
vector<Rect> prevRectFaces = {};


CascadeClassifier face_cascade;

class CascadeDetectorAdapter : public DetectionBasedTracker::IDetector {
public:
    CascadeDetectorAdapter(cv::Ptr<cv::CascadeClassifier> detector) :
            IDetector(),
            Detector(detector) {
        LOGD("CascadeDetectorAdapter: CV_Assert(detector)");
        CV_Assert(detector);
    }


    void detect(const cv::Mat &Image, std::vector<cv::Rect> &objects) {
        LOGD("CascadeDetectorAdapter::Detect: BEGIN");

        // LOGD("CascadeDetectorAdapter::Detect: scaleFactor=%.2f, minNeighbours=%d, minObjSize=(%dx%d), maxObjSize=(%dx%d)",
        // scaleFactor, minNeighbours, minObjSize.width, minObjSize.height, maxObjSize.width, maxObjSize.height);

        //Detector->detectMultiScale(Image, objects, scaleFactor, minNeighbours, 0, minObjSize, maxObjSize);
        Detector->detectMultiScale(Image, objects, 1.1, 3, 0, Size(0, 0), Size(1000, 1000));
        LOGD("CALLED DetectMultiScale");
        LOGD("CascadeDetectorAdapter::Detect: NUM FRAME = %d", numframe++);

        LOGD("CascadeDetectorAdapter::Detect: NUM of faces = %zu", objects.size());
        LOGD("CascadeDetectorAdapter::Detect: END");
    }

    virtual ~CascadeDetectorAdapter() {
        LOGD("CascadeDetectorAdapter::Detect::~Detect");
    }


private:
    CascadeDetectorAdapter();

    cv::Ptr<cv::CascadeClassifier> Detector;

};

struct DetectorAgregator {
    cv::Ptr<CascadeDetectorAdapter> mainDetector;
    cv::Ptr<CascadeDetectorAdapter> trackingDetector;

    cv::Ptr<DetectionBasedTracker> tracker;

    DetectorAgregator(cv::Ptr<CascadeDetectorAdapter> &_mainDetector,
                      cv::Ptr<CascadeDetectorAdapter> &_trackingDetector) :
            mainDetector(_mainDetector),
            trackingDetector(_trackingDetector) {
        CV_Assert(_mainDetector);
        CV_Assert(_trackingDetector);

        DetectionBasedTracker::Parameters DetectorParams;
        tracker = makePtr<DetectionBasedTracker>(mainDetector, trackingDetector, DetectorParams);
    }
};


JNIEXPORT jlong JNICALL Java_opencv_android_fdt_DetectionBasedTracker_nativeCreateObject
        (JNIEnv *jenv, jclass, jstring jFileName, jint faceSize) {
    LOGD("Java_opencv_android_fdt_DetectionBasedTrackerMOD_nativeCreateObject enter");
    const char *jnamestr = jenv->GetStringUTFChars(jFileName, NULL);
    string stdFileName(jnamestr);
    jlong result = 0;


    try {
        cv::Ptr<CascadeDetectorAdapter> mainDetector = makePtr<CascadeDetectorAdapter>(
                makePtr<CascadeClassifier>(stdFileName));

        cv::Ptr<CascadeDetectorAdapter> trackingDetector = makePtr<CascadeDetectorAdapter>(
                makePtr<CascadeClassifier>(stdFileName));

        if ( !face_cascade.load(stdFileName.c_str()) ) {
            LOGI("OCV resources NOT loaded");
            return 0;
        } else {
            LOGI("OCV resources loaded");
        }

        // initialization after run from mobile
        numframe        = 0;
        nativeNumFrame  = 0;
        firstDetection  = 0;
        prevRectFaces = {};

        result = (jlong) new DetectorAgregator(mainDetector, trackingDetector);
        //if (faceSize > 0)
        //{
        mainDetector->setMinObjectSize(Size(faceSize, faceSize));
        //    trackingDetector->setMinObjectSize(Size(faceSize, faceSize));//uncommented
        //}
    }
    catch (cv::Exception &e) {
        LOGI("nativeCreateObject caught cv::Exception: %s", e.what());
        jclass je = jenv->FindClass("org/opencv/core/CvException");
        if (!je)
            je = jenv->FindClass("java/lang/Exception");
        jenv->ThrowNew(je, e.what());
    }
    catch (...) {
        LOGI("nativeCreateObject caught unknown exception");
        jclass je = jenv->FindClass("java/lang/Exception");
        jenv->ThrowNew(je,
                       "Unknown exception in JNI code of DetectionBasedTrackerMOD.nativeCreateObject()");
        return 0;
    }

    LOGI("Java_opencv_android_fdt_DetectionBasedTrackerMOD_nativeCreateObject exit");
    return result;
}

JNIEXPORT void JNICALL Java_opencv_android_fdt_DetectionBasedTracker_nativeDestroyObject
        (JNIEnv *jenv, jclass, jlong thiz) {
    LOGD("Java_opencv_android_fdt_DetectionBasedTrackerMOD_nativeDestroyObject enter");

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
        jenv->ThrowNew(je,
                       "Unknown exception in JNI code of DetectionBasedTrackerMOD.nativeDestroyObject()");
    }
    LOGI("Java_opencv_android_fdt_DetectionBasedTrackerMOD_nativeDestroyObject exit");
}


JNIEXPORT void JNICALL Java_opencv_android_fdt_DetectionBasedTracker_nativeDetect
        (JNIEnv *jenv, jclass, jlong thiz, jlong imageGray, jlong faces) {
    LOGD("Java_opencv_android_fdt_DetectionBasedTracker_nativeDetect ENTER");

    vector<Rect> RectFaces;

    try {

        ((DetectorAgregator *) thiz)->tracker->process(*((Mat *) imageGray));
        ((DetectorAgregator *) thiz)->tracker->getObjects(RectFaces);

        LOGD("DetectionBasedTracker_nativeDetect NativeRectFaces = %zu", RectFaces.size());

        // if no faces from native detector
        if (firstDetection == 0) {
            if(RectFaces.size() > 0)  firstDetection = 1;
            else {
                // if first time or every while (= six frames)
                if ((prevRectFaces.size() == 0) || ((nativeNumFrame % 6) == 0)) {

                    face_cascade.detectMultiScale(*((Mat *) imageGray), RectFaces, 1.1, 3, 0,
                                                  Size(0, 0), Size(1000, 1000));
                    LOGD("DetectionBasedTracker_nativeDetect AFTER MY detection: RectFaces size = %zu",RectFaces.size());
                    prevRectFaces = RectFaces;

                } else  RectFaces = prevRectFaces; // there were previous face detected
            }
        }

        LOGD("DetectionBasedTracker_nativeDetect NUM FRAME = %d", nativeNumFrame++);

    }
    catch (cv::Exception &e) {
        LOGD("nativeCreateObject caught cv::Exception: %s", e.what());
        jclass je = jenv->FindClass("org/opencv/core/CvException");
        if (!je)
            je = jenv->FindClass("java/lang/Exception");
        jenv->ThrowNew(je, e.what());
    }
    catch (...) {
        LOGD("nativeDetect caught unknown exception");
        jclass je = jenv->FindClass("java/lang/Exception");
        jenv->ThrowNew(je, "Unknown exception in JNI code DetectionBasedTracker.nativeDetect()");
    }

    /*
    for (int i = 0; i < RectFaces.size(); i++)
        rectangle(*((Mat *) imageGray), Point(RectFaces[i].x, RectFaces[i].y),
                  Point(RectFaces[i].x + RectFaces[i].width,
                        RectFaces[i].y + RectFaces[i].height),
                  Scalar(255, 255, 255), 6);
    */

    *((Mat *) faces) = Mat(RectFaces, true);

    LOGD("Java_opencv_android_fdt_DetectionBasedTracker_nativeDetect EXIT");
}

