package dlib.android;

import android.graphics.Bitmap;

public class FaceRecognizer {

    public FaceRecognizer() { }

    public void loadNative()
    {
        System.loadLibrary("native-lib");
        loadResourcesPart1();
        loadResourcesPart2();
    }

    //https://stackoverflow.com/questions/15440647/scaled-bitmap-maintaining-aspect-ratio
    public static Bitmap resize(Bitmap image, int maxWidth, int maxHeight) {
        if (maxHeight > 0 && maxWidth > 0) {
            int width = image.getWidth();
            int height = image.getHeight();
            float ratioBitmap = (float) width / (float) height;
            float ratioMax = (float) maxWidth / (float) maxHeight;

            int finalWidth = maxWidth;
            int finalHeight = maxHeight;
            if (ratioMax > ratioBitmap) {
                finalWidth = (int) ((float)maxHeight * ratioBitmap);
            } else {
                finalHeight = (int) ((float)maxWidth / ratioBitmap);
            }
            image = Bitmap.createScaledBitmap(image, finalWidth, finalHeight, true);
            return image;
        } else {
            return image;
        }
    }

    private native int loadResourcesPart1();
    private native int loadResourcesPart2();
    public native String[] recognizeFaces(Bitmap bmp); //full image screen
    public native String recognizeFace(Bitmap bmp); //customDetector
    public native int saveFace(String name, Bitmap bmp); //customDetector
}
