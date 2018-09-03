#include <jni.h>

#ifndef FACETRACKER_DETECTIONBASEDTRACKER_JNI_H
#define FACETRACKER_DETECTIONBASEDTRACKER_JNI_H

#ifdef __cplusplus
extern "C" {
#endif
/*
 * Class:     org_opencv_samples_fd_DetectionBasedTracker
 * Method:    nativeCreateObject
 * Signature: (Ljava/lang/String;F)J
 */
JNIEXPORT jlong JNICALL Java_opencv_android_fdt_DetectionBasedTracker_nativeCreateObject
        (JNIEnv *, jclass, jstring, jint);

/*
 * Class:     org_opencv_samples_fd_DetectionBasedTracker
 * Method:    nativeDestroyObject
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_opencv_android_fdt_DetectionBasedTracker_nativeDestroyObject
        (JNIEnv *, jclass, jlong);


/*
 * Class:     org_opencv_samples_fd_DetectionBasedTracker
 * Method:    nativeDetect
 * Signature: (JJJ)V
 */
JNIEXPORT void JNICALL Java_opencv_android_fdt_DetectionBasedTracker_nativeDetect
        (JNIEnv *, jclass, jlong, jlong, jlong);

#ifdef __cplusplus
}
#endif
#endif //FACETRACKER_DETECTIONBASEDTRACKER_JNI_H
