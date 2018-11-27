package org.opencv.android.facetracker;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Mat;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.content.Intent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import com.google.android.gms.samples.vision.face.facetracker.R;
import com.google.android.gms.samples.vision.face.facetracker.FaceTrackerActivity;
import java.util.concurrent.LinkedBlockingQueue;//for Data structure
import java.util.Iterator;

public class OpenCvActivity extends AppCompatActivity implements CameraBridgeViewBase.CvCameraViewListener2 {

    private static final String TAG = "OCV-Activity";
    private static final Scalar DETECT_BOX_COLOR   = new Scalar(255, 255, 0, 255);//yellow
    private static final Scalar TRACKER_BOX_COLOR   = new Scalar(0, 0, 255, 255);//blue
    private static final Scalar cyan_BOX_COLOR   = new Scalar(0, 255, 255, 255);//blue
    private Mat mRgba;
    private Mat mGray;
    private CameraBridgeViewBase mOpenCvCameraView;
    HaarDetector hd = new HaarDetector();
    Button mBtnSwitch;
    int counterF=0;

    public String trackerName = "OCV-tracker";
    final DataStructure ds = new DataStructure();//contains all usefull Data (struct)
    final LinkedBlockingQueue<DataStructure> queue = new LinkedBlockingQueue<>(5);//LBQ of all useful Data (struct)

    //Data Structure (that is an equivalent of C++ struct) contains all useful data for drawing
    final public class DataStructure {
        private Mat frame;
        private int FrameNumber;

        public DataStructure() {
            this.frame= mRgba;
            this.FrameNumber = 1000;
        }
        // constructor
        public DataStructure(Mat frame, int FrameNumber) {
            this.frame= frame;
            this.FrameNumber = FrameNumber;
        }

        // getter
        public Mat getFrameDS() { return frame; }
        public int getFrameNumberDS() { return FrameNumber; }

        // setter
        public void storeFrameDS(Mat frame) { this.frame = frame; }
        public void storeFrameNumberDS(int FrameNumber) { this.FrameNumber = FrameNumber; }

    }


    public class DataManager {

        private static final long SLEEP_INTERVAL_MS = 34;

        public void DataManager() {

            System.out.println("DM_Queue contains\t"+queue+" (queue_length: "+queue.toArray().length+")");

            Producer producer = new Producer(queue);//Insert into LBQ all useful Data (struct)
            ObservingConsumer obsConsumer = new ObservingConsumer(queue, producer);
            RemovingConsumer remConsumer = new RemovingConsumer(queue, producer);

            Thread producerThread = new Thread(producer);
            Thread obsConsumerThread = new Thread(obsConsumer);
            Thread remConsumerThread = new Thread(remConsumer);

            producerThread.start();
            Log.i(TAG, "DM\tStarted P "+producerThread.getName()+producerThread.getId() +"(status: "+producerThread.getState()+")");

            try {
                producerThread.join(SLEEP_INTERVAL_MS);
                Log.i(TAG, "DM\tExecuted P "+producerThread.getName()+producerThread.getId() +"(status: "+producerThread.getState()+")");
            } catch(InterruptedException e) {
                e.printStackTrace();
                Log.e(TAG, "DM\tFailed join P: " + e +"(threadName: "+producerThread.getName()+producerThread.getId()+"(status: "+producerThread.getState()+")");
            }

            obsConsumerThread.start();
            try {
                obsConsumerThread.join(SLEEP_INTERVAL_MS);
                Log.e(TAG, "DM\tExecuted OC "+obsConsumerThread.getName()+obsConsumerThread.getId() +"(status: "+obsConsumerThread.getState()+")");
            } catch(InterruptedException e) {
                e.printStackTrace();
                Log.e(TAG, "DM\tFailed join OC: " + e +"(threadName: "+obsConsumerThread.getName()+obsConsumerThread.getId()+"(status: "+obsConsumerThread.getState()+")");
            }


            //remConsumerThread.start();
        }
    }

    //Inserts DataStructure (struct) into the LinkedBlockingQueue
    public class Producer implements Runnable {
        final private LinkedBlockingQueue<DataStructure> queue;
        private boolean running;
        //private static final long SLEEP_INTERVAL_MS = 34;
        public Producer(LinkedBlockingQueue<DataStructure> queue) {
            this.queue = queue;
            running = true;
        }
        // We need to check if the producer thread is
        // Still running, and this method will return
        // the state (running/stopped).
        public boolean isRunning() {
            return running;
        }
        @Override
        public void run() {

            // We are adding elements using put() which waits
            // until it can actually insert elements if there is
            // not space in the queue.
            if (counterF>0) {
                Log.i(TAG, "P\tStarted "+Thread.currentThread().getName()+Thread.currentThread().getId() +"(status: "+Thread.currentThread().getState()+")");
                //store current frame with relative FrameNumber
                try {
                    DataStructure ds=new DataStructure(mRgba,counterF);//contains all usefull Data (struct)
                    queue.put(ds);
                    System.out.println("P\tAdding DataStructure (#frame: " + ds.getFrameNumberDS()+")\t("+Thread.currentThread().getName()+Thread.currentThread().getId()+" -> STATUS: "+Thread.currentThread().getState()+")");
                    //Thread.sleep(SLEEP_INTERVAL_MS); //34 (old value: 1000)
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            System.out.println("P Completed.");
            //===========================
            //running = false;
            synchronized (Producer.this) {
                running = false;
                //use notify() method to send notification to the other threads that are waiting
                Producer.this.notify();
            }
            //===========================

        }
    }

    public class ObservingConsumer implements Runnable {
        private LinkedBlockingQueue<DataStructure>  queue;
        private Producer producer;
        public ObservingConsumer(LinkedBlockingQueue<DataStructure> queue, Producer producer) {
            this.queue = queue;
            this.producer = producer;
        }

        @Override
        public void run() {
            // As long as the producer is running,
            // we want to check for elements.
            while (producer.isRunning()) {
                //System.out.println("OC\tElements right now:\t" + queue);

                // create Iterator of linkedQueue using iterator() method
                Iterator<DataStructure> listOfItems = queue.iterator();

                // find head of linkedQueue using peek() method
                DataStructure head = queue.peek();

                if(head !=null) {
                    // print head of queue
                    System.out.println("OC\tHead of Queue is: " + head);
                    System.out.println("OC\tframe_channel(): " + head.getFrameDS().channels());
                    System.out.println("OC\tframeNumber: " + head.getFrameNumberDS());

                    //print queue items
                    /*while (listOfItems.hasNext()) {
                        System.out.println("listOfItems: (frame_ch:"+listOfItems.next().getFrameDS().channels()+", frameNumber:" +listOfItems.next().getFrameNumberDS());
                    }*/

                    //print queue items
                   /* try {
                        while (listOfItems.hasNext()) {
                            System.out.println("listOfItems: (frame_ch:" + listOfItems.next().getFrameDS().channels() + ", frameNumber:" + listOfItems.next().getFrameNumberDS());
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        Log.e(TAG, "OC\tException");
                    }*/

                }
                else
                {
                    System.out.println("OC\tQueue is empty");
                }



                /*try {
                    Thread.sleep(68);//68//old value:2000)
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }*/
            }
            System.out.println("OC Completed! Final elements in the queue:\t" + queue);
        }
    }

    public class RemovingConsumer implements Runnable {
        //private LinkedBlockingQueue queue;
        private LinkedBlockingQueue<DataStructure>  queue;
        private Producer producer;
        RemovingConsumer(LinkedBlockingQueue<DataStructure> queue, Producer producer) {
            this.queue = queue;
            this.producer = producer;
        }

        @Override
        public void run() {
            // As long as the producer is running,
            // we remove elements from the queue.
            while (producer.isRunning()) {
                try {
                    System.out.println("RC\tRemoving element: " + queue.take());
                    System.out.println("RC\tRemovingConsumer\t(queue.toArray().length:\t" + queue.toArray().length+"\tqueue: "+queue);
                    Thread.sleep(68);//old value:2000)
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            System.out.println("RC completed.");
        }
    }




    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {

        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS: {
                    Log.d(TAG, "OpenCV loaded successfully");
                    // Load native library after(!) OpenCV initialization
                    hd.loadNative();
                    mOpenCvCameraView.enableView();
                }
                break;
                default: {
                    super.onManagerConnected(status);
                }
                break;
            }
        }
    };

    public OpenCvActivity() { Log.i(TAG, "Instantiated " + this.getClass()); }


    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "called onCreate");

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_open_cv);

        mOpenCvCameraView = (CameraBridgeViewBase) findViewById(R.id.main_surface);
        mOpenCvCameraView.setMaxFrameSize(1920, 1080);//1080p: 1920x1080
        mOpenCvCameraView.enableFpsMeter();


        // what are the following used for?
        mOpenCvCameraView.setVisibility(CameraBridgeViewBase.VISIBLE);
        mOpenCvCameraView.setCvCameraViewListener(this);
        Log.i(TAG,"#processor:"+Runtime.getRuntime().availableProcessors());
        onListenButton();
    }


    private void onListenButton() {
        Log.d(TAG, "called onListenButton");
        mBtnSwitch = (Button) findViewById(R.id.OCVbtnSwitch);
        mBtnSwitch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent myIntent = new Intent(OpenCvActivity.this, FaceTrackerActivity.class);
                OpenCvActivity.this.startActivity(myIntent);
            }
        });
    }

    public void onCameraViewStarted(int width, int height) {
        mRgba = new Mat();
        mGray = new Mat();
    }


    public void onCameraViewStopped() {
        mRgba.release();
        mGray.release();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "OpenCV library not found");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION, this, mLoaderCallback);
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mOpenCvCameraView.disableView();
    }


    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        Log.d(TAG, "called onCameraFrame");

        mRgba = inputFrame.rgba();
        mGray = inputFrame.gray();

        counterF++;

        Log.i(TAG, "#frame:" + counterF);
        DataManager dm=new DataManager();
        System.out.println("OpencvActivity -> DataManager CREATED");
        dm.DataManager();
        System.out.println("OpencvActivity -> DataManager STARTED");




        Imgproc.putText(mRgba, String.valueOf(counterF), new Point(50, 50), 3, 3,
                new Scalar(255, 0, 0, 255), 3);

        return mRgba;
    }
}