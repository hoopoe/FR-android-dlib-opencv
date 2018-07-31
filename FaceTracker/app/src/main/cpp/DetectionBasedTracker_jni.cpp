#include "DetectionBasedTracker_jni.h"
#include <opencv2/core.hpp>
#include <opencv2/objdetect.hpp>

#include <string>
#include <vector>

#include <android/log.h>
#include <chrono>
#include <opencv2/imgproc/imgproc.hpp>



#define LOG_TAG "OCV-DBasedT"
#define LOGD(...) ((void)__android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__))
#define  LOGI(...)  __android_log_print(ANDROID_LOG_INFO,LOG_TAG,__VA_ARGS__)

using namespace std;
using namespace cv;

inline void vector_Rect_to_Mat(vector<Rect>& v_rect, Mat& mat)
{
    mat = Mat(v_rect, true);
}



class CascadeDetectorAdapter: public DetectionBasedTracker::IDetector
{
public:
    CascadeDetectorAdapter(cv::Ptr<cv::CascadeClassifier> detector):
            IDetector(),
            Detector(detector)
    {
        LOGD("CascadeDetectorAdapter::Detect::Detect");
        CV_Assert(detector);
    }

    void detect(const cv::Mat &Image, std::vector<cv::Rect> &objects)
    {
        LOGI("CascadeDetectorAdapter::Detect: begin");

        double const TH_weight=5.0;//Good empirical threshold values: 5-7
        std::vector<int> reject_levels;
        std::vector<double> weights;
        std::vector<Rect> faces = {};
        double scaleFactor = 1.1;
        int minNeighbours = 5;
        Detector->detectMultiScale(Image, faces, reject_levels, weights, scaleFactor, minNeighbours, 0|CV_HAAR_SCALE_IMAGE, Size(), Size(1000,1000), true );
        LOGI("CascadeDetectorAdapter::Detect: scaleFactor=%.2f, minNeighbours=%i, TH_weight=%.2f", scaleFactor, minNeighbours, TH_weight);
        //LOGI("CascadeDetectorAdapter::Detect: scaleFactor=%.2f, minNeighbours=%i, TH_weight=%.2f, minObjSize=(%dx%d), maxObjSize=(%dx%d)", scaleFactor, minNeighbours, TH_weight,minObjSize.width, minObjSize.height, maxObjSize.width, maxObjSize.height);
        int i=0;
        for(vector<Rect>::const_iterator r = faces.begin(); r != faces.end(); r++, i++ ) {
            //LOGI("weights[i]:%f", weights[i]);
            LOGI("detected Faces size (wxh): %i x %i", (int)faces.at(i).width, (int)faces.at(i).height);
            if (weights[i] >= TH_weight)//Good empirical threshold values: 5-7
            {

                objects.push_back(*r);

                //draw detected faces------------------------------------
              /*  Mat img;
                Image.copyTo(img);
                rectangle(img, objects[i], Scalar(255, 255, 0), 2, 8, 0);//4, 8, 0*/
                //----------------------------------------------------
            }
        }
        LOGI("#realFaces: %i (TH_weight= %.2f)", (int)faces.size(), TH_weight);

        LOGI("CascadeDetectorAdapter::Detect: end");
    }

    virtual ~CascadeDetectorAdapter()
    {
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
    catch (...)
    {
        //LOGD("nativeCreateObject caught unknown exception");
        LOGI("nativeCreateObject caught unknown exception");
        jclass je = jenv->FindClass("java/lang/Exception");
        jenv->ThrowNew(je, "Unknown exception in JNI code of DetectionBasedTrackerMOD.nativeCreateObject()");
        return 0;
    }

    //LOGD("Java_opencv_android_fdt_DetectionBasedTrackerMOD_nativeCreateObject exit");
    LOGI("Java_opencv_android_fdt_DetectionBasedTrackerMOD_nativeCreateObject exit");
    return result;
}

JNIEXPORT void JNICALL Java_opencv_android_fdt_DetectionBasedTracker_nativeDestroyObject
(JNIEnv * jenv, jclass, jlong thiz)
{
LOGD("Java_opencv_android_fdt_DetectionBasedTrackerMOD_nativeDestroyObject");

try
{
if(thiz != 0)
{
((DetectorAgregator*)thiz)->tracker->stop();
delete (DetectorAgregator*)thiz;
}
}
catch(cv::Exception& e)
{
LOGI("nativeDestroyObject caught cv::Exception: %s", e.what());
jclass je = jenv->FindClass("org/opencv/core/CvException");
if(!je)
je = jenv->FindClass("java/lang/Exception");
jenv->ThrowNew(je, e.what());
}
catch (...)
{
LOGI("nativeDestroyObject caught unknown exception");
jclass je = jenv->FindClass("java/lang/Exception");
jenv->ThrowNew(je, "Unknown exception in JNI code of DetectionBasedTrackerMOD.nativeDestroyObject()");
}
LOGI("Java_opencv_android_fdt_DetectionBasedTrackerMOD_nativeDestroyObject exit");
}

JNIEXPORT void JNICALL Java_opencv_android_fdt_DetectionBasedTracker_nativeStart
(JNIEnv * jenv, jclass, jlong thiz)
{
LOGD("Java_opencv_android_fdt_DetectionBasedTracker_nativeStart");

try
{
((DetectorAgregator*)thiz)->tracker->run();
}
catch(cv::Exception& e)
{
LOGD("nativeStart caught cv::Exception: %s", e.what());
jclass je = jenv->FindClass("org/opencv/core/CvException");
if(!je)
je = jenv->FindClass("java/lang/Exception");
jenv->ThrowNew(je, e.what());
}
catch (...)
{
LOGI("nativeStart caught unknown exception");
jclass je = jenv->FindClass("java/lang/Exception");
jenv->ThrowNew(je, "Unknown exception in JNI code of DetectionBasedTrackerMOD.nativeStart()");
}
LOGI("Java_opencv_android_fdt_DetectionBasedTrackerMOD_nativeStart exit");
}

JNIEXPORT void JNICALL Java_opencv_android_fdt_DetectionBasedTracker_nativeStop
(JNIEnv * jenv, jclass, jlong thiz)
{
LOGD("Java_opencv_android_fdt_DetectionBasedTrackerMOD_nativeStop");

try
{
((DetectorAgregator*)thiz)->tracker->stop();
}
catch(cv::Exception& e)
{
LOGI("nativeStop caught cv::Exception: %s", e.what());
jclass je = jenv->FindClass("org/opencv/core/CvException");
if(!je)
je = jenv->FindClass("java/lang/Exception");
jenv->ThrowNew(je, e.what());
}
catch (...)
{
LOGI("nativeStop caught unknown exception");
jclass je = jenv->FindClass("java/lang/Exception");
jenv->ThrowNew(je, "Unknown exception in JNI code of DetectionBasedTrackerMOD.nativeStop()");
}
LOGI("Java_opencv_android_fdt_DetectionBasedTrackerMOD_nativeStop exit");
}

JNIEXPORT void JNICALL Java_opencv_android_fdt_DetectionBasedTracker_nativeSetFaceSize
(JNIEnv * jenv, jclass, jlong thiz, jint faceSize)
{
LOGI("Java_opencv_android_fdt_DetectionBasedTrackerMOD_nativeSetFaceSize -- BEGIN");

try
{
if (faceSize > 0)
{LOGI("Java_opencv_android_fdt_DetectionBasedTrackerMOD_nativeSetFaceSize -> faceSize:",faceSize);
((DetectorAgregator*)thiz)->mainDetector->setMinObjectSize(Size(faceSize, faceSize));
//((DetectorAgregator*)thiz)->trackingDetector->setMinObjectSize(Size(faceSize, faceSize));//commented
}
}
catch(cv::Exception& e)
{
LOGI("nativeStop caught cv::Exception: %s", e.what());
jclass je = jenv->FindClass("org/opencv/core/CvException");
if(!je)
je = jenv->FindClass("java/lang/Exception");
jenv->ThrowNew(je, e.what());
}
catch (...)
{
LOGI("nativeSetFaceSize caught unknown exception");
jclass je = jenv->FindClass("java/lang/Exception");
jenv->ThrowNew(je, "Unknown exception in JNI code of DetectionBasedTracker.nativeSetFaceSize()");
}
LOGI("Java_opencv_android_fdt_DetectionBasedTracker_nativeSetFaceSize -- END");
}


JNIEXPORT void JNICALL Java_opencv_android_fdt_DetectionBasedTracker_nativeDetect
(JNIEnv * jenv, jclass, jlong thiz, jlong imageGray, jlong faces)
{
LOGD("Java_opencv_android_fdt_DetectionBasedTracker_nativeDetect");

    auto start = std::chrono::high_resolution_clock::now();

try
{
vector<Rect> RectFaces;
((DetectorAgregator*)thiz)->tracker->process(*((Mat*)imageGray));
((DetectorAgregator*)thiz)->tracker->getObjects(RectFaces);
*((Mat*)faces) = Mat(RectFaces, true);
}
catch(cv::Exception& e)
{
LOGD("nativeCreateObject caught cv::Exception: %s", e.what());
jclass je = jenv->FindClass("org/opencv/core/CvException");
if(!je)
je = jenv->FindClass("java/lang/Exception");
jenv->ThrowNew(je, e.what());
}
catch (...)
{
LOGD("nativeDetect caught unknown exception");
jclass je = jenv->FindClass("java/lang/Exception");
jenv->ThrowNew(je, "Unknown exception in JNI code DetectionBasedTracker.nativeDetect()");
}
LOGD("Java_opencv_android_fdt_DetectionBasedTracker_nativeDetect END");

}

