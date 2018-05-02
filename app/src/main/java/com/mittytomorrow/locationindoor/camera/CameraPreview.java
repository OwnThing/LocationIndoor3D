package com.mittytomorrow.locationindoor.camera;

import android.content.Context;
import android.hardware.Camera;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.io.IOException;
import java.util.concurrent.LinkedBlockingQueue;

public class CameraPreview extends SurfaceView implements SurfaceHolder.Callback, Camera.PreviewCallback {
    private static final String TAG = "CameraPreview";
    private SurfaceHolder mHolder;
    private Camera mCamera;

    //process frame type
    private static final int PROCESS_WITH_HANDLER_THREAD = 1;
    private static final int PROCESS_WITH_QUEUE = 2;
    private static final int PROCESS_WITH_ASYNC_TASK = 3;
    private static final int PROCESS_WITH_THREAD_POOL = 4;

    private int processType = PROCESS_WITH_THREAD_POOL;

    //HandlerThread
    private ProcessWithHandlerThread processFrameHandlerThread;
    private Handler processFrameHandler;

    //Queue
    private ProcessWithQueue processFrameQueue;
    private LinkedBlockingQueue<byte[]> frameQueue;

    //ThreadPool
    private ProcessWithThreadPool processFrameThreadPool;

    public CameraPreview(Context context) {
        super(context);
        mHolder = getHolder();
        mHolder.addCallback(this);
        setZOrderMediaOverlay(true);
        switch (processType) {
            case PROCESS_WITH_HANDLER_THREAD:
                processFrameHandlerThread = new ProcessWithHandlerThread("process frame");
                processFrameHandler = new Handler(processFrameHandlerThread.getLooper(), processFrameHandlerThread);
                break;
            case PROCESS_WITH_QUEUE:
                frameQueue = new LinkedBlockingQueue<>();
                processFrameQueue = new ProcessWithQueue(frameQueue);
                break;
            case PROCESS_WITH_ASYNC_TASK:
                break;
            case PROCESS_WITH_THREAD_POOL:
                processFrameThreadPool = new ProcessWithThreadPool();
        }
    }

    public void show()
    {
//        setZOrderOnTop(true);//覆盖所有窗口
        setZOrderMediaOverlay(true);
    }

    public void hide()
    {
//        setZOrderOnTop(false);
        setZOrderMediaOverlay(false);
    }

    private void openCameraOriginal() {
        try {
            mCamera = Camera.open();
        } catch (Exception e) {
            Log.d(TAG, "camera is not available");
        }
    }

    public Camera getCameraInstance() {
        if (mCamera == null) {
            CameraHandlerThread mThread = new CameraHandlerThread("camera thread");
            synchronized (mThread) {
                mThread.openCamera();
            }
        }
        return mCamera;
    }

    public void surfaceCreated(SurfaceHolder holder) {
        getCameraInstance();
        mCamera.setPreviewCallback(this);
        try {
            mCamera.setPreviewDisplay(holder);
            mCamera.setDisplayOrientation(90);//旋转90度
            mCamera.startPreview();
        } catch (IOException e) {
            Log.d(TAG, "Error setting camera preview: " + e.getMessage());
        }
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        mHolder.removeCallback(this);
        mCamera.setPreviewCallback(null);
        mCamera.stopPreview();
        mCamera.release();
        mCamera = null;
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
    }

    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
        switch (processType) {
            case PROCESS_WITH_HANDLER_THREAD:
                processFrameHandler.obtainMessage(ProcessWithHandlerThread.WHAT_PROCESS_FRAME, data).sendToTarget();
                break;
            case PROCESS_WITH_QUEUE:
                try {
                    frameQueue.put(data);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                break;
            case PROCESS_WITH_ASYNC_TASK:
                new ProcessWithAsyncTask().execute(data);
                break;
            case PROCESS_WITH_THREAD_POOL:
                processFrameThreadPool.post(data,camera);
                break;
        }
    }

    private class CameraHandlerThread extends HandlerThread {
        Handler mHandler;

        public CameraHandlerThread(String name) {
            super(name);
            start();
            mHandler = new Handler(getLooper());
        }

        synchronized void notifyCameraOpened() {
            notify();
        }

        void openCamera() {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    openCameraOriginal();
                    notifyCameraOpened();
                }
            });
            try {
                wait();
            } catch (InterruptedException e) {
                Log.w(TAG, "wait was interrupted");
            }
        }
    }
}
