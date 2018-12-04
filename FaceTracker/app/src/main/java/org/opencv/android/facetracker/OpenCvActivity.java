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
import java.util.NoSuchElementException;
import java.io.IOException;

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
    int old_s = 0;
    int delta = 0;

    public String trackerName = "OCV-tracker";
    DataStructure ds = new DataStructure();//contains all usefull Data (struct)
    int BOUND = 5;
    final LinkedBlockingQueue<DataStructure> queue = new LinkedBlockingQueue<>(BOUND);//LBQ of all useful Data (struct)
    Thread producerThread = null;
    Thread obsConsumerThread = null;
    Thread remConsumerThread = null;



    //Data Structure (that is an equivalent of C++ struct) contains all useful data for drawing
    public class DataStructure {
        private Mat frame;
        private int FrameNumber;

        public DataStructure() {
            this.frame= mRgba;//eliminare
            this.FrameNumber = counterF;//1000;//eliminare
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

        private static final long SLEEP_INTERVAL_MS = 34;//1/30;


        public DataManager() {

            System.out.println("DM_Queue contains\t"+queue+" (queue_length: "+queue.toArray().length+")");
            if(queue.size()==BOUND){
                queue.clear();
                old_s=0;
                System.out.println("DM_deleted queue");
            }

            Producer producer = new Producer(queue);//Insert into LBQ all useful Data (struct)
            ObservingConsumer obsConsumer = new ObservingConsumer(queue, producer);
            RemovingConsumer remConsumer = new RemovingConsumer(queue, producer);

            producerThread = new Thread(producer);
            obsConsumerThread = new Thread(obsConsumer);
            remConsumerThread = new Thread(remConsumer);

            producerThread.start();
            Log.i(TAG, "DM\tStarted P "+producerThread.getName()+producerThread.getId() +"(status: "+producerThread.getState()+" running: "+producer.isRunning()+")");

            obsConsumerThread.start();

            //remConsumerThread.start();
            //Log.i(TAG, "DM\tStarted RC "+remConsumerThread.getName()+remConsumerThread.getId() +"(status: "+remConsumerThread.getState()+" running: "+producer.isRunning()+")");
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
                //store current frame with relative FrameNumber
                try {
                    //DataStructure ds=new DataStructure(mRgba,counterF);//contains all usefull Data (struct)
                    System.out.println("P\tDS_#frame"+ds.getFrameNumberDS());

                    queue.put(ds);

                    System.out.println("P\tQueue_remainingCapacity:"+queue.remainingCapacity());
                    System.out.println("P\tAdding DataStructure (#frame: " + ds.getFrameNumberDS()+")\t("
                            +Thread.currentThread().getName()+Thread.currentThread().getId()+
                            " -> STATUS: "+Thread.currentThread().getState()+")");
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    Log.e(TAG, "P\tProducer thread has stopped");
                }
            }
           // System.out.println("P Completed. Executed Producer.this: "+Producer.this);
           // System.out.println("P Completed. Executed this: "+this);
            System.out.println("P Completed.");
            //===========================
            //running = false;

            ///System.out.println("this: "+this);
            ////synchronized (Producer.this) {

            //synchronized (remConsumerThread) {
            synchronized (this) {
                running = false;
                ////System.out.println("P running = false -> running: "+running+" this: "+this+" this.isRunning():"+this.isRunning());
                //use notify() method to send notification to the other threads that are waiting
                this.notify();
              /*  try {
                    remConsumerThread.notify();
                } catch (Exception e){
                    e.printStackTrace();
                    Log.e(TAG,"Notify Exception");
                }*/
                System.out.println("P\tNotify threads");
            }
            //===========================

        }
    }

    public class ObservingConsumer implements Runnable {
        private LinkedBlockingQueue<DataStructure>  queue;
        private Producer producer;
        protected boolean flag;
        public ObservingConsumer(LinkedBlockingQueue<DataStructure> queue, Producer producer) {
            this.queue = queue;
            this.producer = producer;
        }

        private void printQueueElements()
        {
            if (!queue.isEmpty()){
                Iterator<DataStructure> it = queue.iterator();
                System.out.println("OC\t ALL queue elements_CONTROLLO:");
                //DataStructure head = queue.peek();
                DataStructure head=it.next();
                for (int i=0; i<queue.size(); i++){
                    System.out.println("OC\tHEAD["+i+"]:"+head);
                    System.out.println("OC\tPRINT listOfItems["+i+"]: (frame_ch:" + head.getFrameDS().channels()
                            + ", frameNumber:" + head.getFrameNumberDS()+")");
                    if(it.hasNext()){
                        head=it.next();
                    }
                    else{
                        break;
                    }
                }

            }
            else{
                System.out.println("NO PRINT: queue.isEmpty():"+queue.isEmpty());
            }
        }

        @Override
        public void run() {
            // As long as the producer is running,
            // we want to check for elements.
            while(producer.isRunning())
            {
                int curr_s=this.queue.size();
                delta=curr_s-old_s;
                if (delta>0) {
                    //print the current queue size
                    System.out.println("OC\tCurrent queue size: "+curr_s);

                    //print queue elements
                    this.printQueueElements();
                    old_s=curr_s;
                    delta=0;
                }
                delta=0;
            }
            System.out.println("OC Completed! Final elements in the queue:\t" + queue);
        }
    }

    public class RemovingConsumer implements Runnable {
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

        ds=new DataStructure(mRgba,counterF);//contains all usefull Data (struct)
        System.out.println("OpencvActivity\tDSmain_#frame"+ds.getFrameNumberDS());

        DataManager dm = new DataManager();//manage Threads
        System.out.println("OpencvActivity -> DataManager CREATED");

        Imgproc.putText(mRgba, String.valueOf(counterF), new Point(50, 50), 3, 3,
                new Scalar(255, 0, 0, 255), 3);

        return mRgba;
    }
}