package com.yixin.facerecognition;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.hardware.Camera.PreviewCallback;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLSurfaceView.Renderer;
import android.opengl.Matrix;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Base64;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.alibaba.fastjson.JSONObject;
import com.megvii.facepp.sdk.Facepp;
import com.yixin.facerecognition.model.UserModel;
import com.yixin.facerecognition.util.CameraMatrix;
import com.yixin.facerecognition.util.ConUtil;
import com.yixin.facerecognition.util.DialogUtil;
import com.yixin.facerecognition.util.ICamera;
import com.yixin.facerecognition.util.MediaRecorderUtil;
import com.yixin.facerecognition.util.OpenGLDrawRect;
import com.yixin.facerecognition.util.OpenGLUtil;
import com.yixin.facerecognition.util.PointsMatrix;
import com.yixin.facerecognition.util.Screen;
import com.yixin.facerecognition.util.SensorEventUtil;
import com.yixin.facerecognition.util.Util;
import com.zhy.http.okhttp.OkHttpUtils;
import com.zhy.http.okhttp.callback.BitmapCallback;
import com.zhy.http.okhttp.callback.StringCallback;


import java.io.ByteArrayOutputStream;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.Logger;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import okhttp3.Call;

public class OpenglActivity extends Activity implements PreviewCallback, Renderer, SurfaceTexture.OnFrameAvailableListener {
    private static final String Tag = "OpenActivity";
    private ImageView openglImage;
    private ImageView findImage;

    private boolean isStartRecorder, is3DPose, isDebug, isROIDetect, is106Points, isBackCamera, isFaceProperty,
            isOneFaceTrackig;
    private String trackModel;
    private boolean isTiming = true; // 是否是定时去刷新界面;
    private int printTime = 31;
    private GLSurfaceView mGlSurfaceView;
    private ICamera mICamera;
    private Camera mCamera;
    private DialogUtil mDialogUtil;
    private TextView debugInfoText, debugPrinttext, AttriButetext;
    private HandlerThread mHandlerThread = new HandlerThread("facepp");
    private Handler mHandler;
    private Facepp facepp;
    private MediaRecorderUtil mediaRecorderUtil;
    private int min_face_size = 200;
    private int detection_interval = 25;
    private HashMap<String, Integer> resolutionMap;
    private SensorEventUtil sensorUtil;
    private float roi_ratio = 0.8f;
    private int Angle;
    private int mTextureID = -1;
    private SurfaceTexture mSurface;
    private CameraMatrix mCameraMatrix;
    private PointsMatrix mPointsMatrix;
    boolean isSuccess = false;
    float confidence;
    float pitch, yaw, roll;
    long startTime;
    long time_AgeGender_end = 0;
    String AttriButeStr = "";
    int rotation = Angle;
    private final float[] mMVPMatrix = new float[16];
    private final float[] mProjMatrix = new float[16];
    private final float[] mVMatrix = new float[16];
    private final float[] mRotationMatrix = new float[16];
    private boolean isCheck = false;

    private Facepp.Face userFace;
    private boolean isOnline = true;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Screen.initialize(this);
        setContentView(R.layout.activity_opengl);

        init();
        showUserImage();
        //录制视频，本项目暂不需要
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                startRecorder();
            }
        }, 2000);
    }

    private void init() {
        if (android.os.Build.MODEL.equals("PLK-AL10"))
            printTime = 50;

        isStartRecorder = getIntent().getBooleanExtra("isStartRecorder", false);
        is3DPose = getIntent().getBooleanExtra("is3DPose", false);
        isDebug = getIntent().getBooleanExtra("isdebug", false);
        isROIDetect = getIntent().getBooleanExtra("ROIDetect", false);
        is106Points = getIntent().getBooleanExtra("is106Points", false);
        isBackCamera = getIntent().getBooleanExtra("isBackCamera", false);
        isFaceProperty = getIntent().getBooleanExtra("isFaceProperty", false);
        isOneFaceTrackig = getIntent().getBooleanExtra("isOneFaceTrackig", true);
        trackModel = getIntent().getStringExtra("trackModel");
        if (null == trackModel || trackModel.trim().length() == 0) {
            trackModel = "Normal";
        }

        min_face_size = getIntent().getIntExtra("faceSize", min_face_size);
        detection_interval = getIntent().getIntExtra("interval", detection_interval);
        resolutionMap = (HashMap<String, Integer>) getIntent().getSerializableExtra("resolution");

        facepp = new Facepp();

        sensorUtil = new SensorEventUtil(this);

        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());

        mGlSurfaceView = (GLSurfaceView) findViewById(R.id.opengl_layout_surfaceview);
        mGlSurfaceView.setEGLContextClientVersion(2);// 创建一个OpenGL ES 2.0
        // context
        mGlSurfaceView.setRenderer(this);// 设置渲染器进入gl
        // RENDERMODE_CONTINUOUSLY不停渲染
        // RENDERMODE_WHEN_DIRTY懒惰渲染，需要手动调用 glSurfaceView.requestRender() 才会进行更新
        mGlSurfaceView.setRenderMode(mGlSurfaceView.RENDERMODE_WHEN_DIRTY);// 设置渲染器模式
        mGlSurfaceView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                autoFocus();
            }
        });

        mICamera = new ICamera();
        mDialogUtil = new DialogUtil(this);
        debugInfoText = (TextView) findViewById(R.id.opengl_layout_debugInfotext);
        AttriButetext = (TextView) findViewById(R.id.opengl_layout_AttriButetext);
        debugPrinttext = (TextView) findViewById(R.id.opengl_layout_debugPrinttext);
        if (isDebug)
            debugInfoText.setVisibility(View.VISIBLE);
        else
            debugInfoText.setVisibility(View.INVISIBLE);
    }

    /**
     * 开始录制
     */
    private void startRecorder() {
        if (isStartRecorder) {
            int Angle = 360 - mICamera.Angle;
            if (isBackCamera)
                Angle = mICamera.Angle;
            mediaRecorderUtil = new MediaRecorderUtil(this, mCamera, mICamera.cameraWidth, mICamera.cameraHeight);
            isStartRecorder = mediaRecorderUtil.prepareVideoRecorder(Angle);
            if (isStartRecorder) {
                boolean isRecordSucess = mediaRecorderUtil.start();
                if (isRecordSucess)
                    mICamera.actionDetect(this);
                else
                    mDialogUtil.showDialog(getResources().getString(R.string.no_record));
            }
        }
    }
    private UserModel userModel;
    private void showUserImage() {
        openglImage = (ImageView) findViewById(R.id.opengl_image);
        findImage = (ImageView)findViewById(R.id.find_image);
        Intent intent = getIntent();
        userModel = (UserModel) intent.getSerializableExtra("LoginUser");
        if (null == userModel) {
            Toast.makeText(this, "无法获取用户数据", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        if (null == userModel.getUserImg() || userModel.getUserImg().trim().length() == 0) {
            Toast.makeText(this, "无法获取用户头像地址", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        Logger.getLogger(Tag).info("头像地址:" + userModel.getUserImg());
        OkHttpUtils.get().url(userModel.getUserImg()).build().execute(new BitmapCallback() {
            @Override
            public void onError(Call call, Exception e, int id) {
                Toast.makeText(OpenglActivity.this, "获取用户头像数据错误", Toast.LENGTH_LONG).show();
            }

            @Override
            public void onResponse(Bitmap response, int id) {
                //response = response.copy(Bitmap.Config.RGB_565, true);
                openglImage.setImageBitmap(response);

                if (null == userFace && !isOnline) {
                    new AsyncTask<Bitmap,Integer,Boolean>(){
                        @Override
                        protected Boolean doInBackground(Bitmap... bitmaps) {
                            System.out.println("开始识别用户头像");
                            Bitmap bitmap = bitmaps[0];
                            byte[] imageData = ConUtil.getGrayscale(bitmap);
                            Facepp  bitfacepp = new Facepp();
                            String errorCode = bitfacepp.init(OpenglActivity.this, ConUtil.getFileContent(OpenglActivity.this, R.raw.megviifacepp_0_4_7_model));
                            Facepp.FaceppConfig faceppConfig = bitfacepp.getFaceppConfig();
                            faceppConfig.interval = detection_interval;
                            faceppConfig.minFaceSize = min_face_size;
                            faceppConfig.roi_left = 0;
                            faceppConfig.roi_top = 0;
                            faceppConfig.roi_right = bitmap.getWidth();
                            faceppConfig.roi_bottom = bitmap.getHeight();
                            faceppConfig.detectionMode = Facepp.FaceppConfig.DETECTION_MODE_TRACKING;

                            Facepp.Face[] faces = bitfacepp.detect(imageData, bitmap.getWidth(), bitmap.getHeight(),Facepp.IMAGEMODE_GRAY);
                            boolean isNull = faces == null || faces.length == 0;
                            if(!isNull){
                                userFace = faces[0];
                                if (is106Points)
                                    facepp.getLandmark(userFace, Facepp.FPP_GET_LANDMARK106);
                                else
                                    facepp.getLandmark(userFace, Facepp.FPP_GET_LANDMARK81);
                                if (is3DPose) {
                                    facepp.get3DPose(userFace);
                                }
                                if(null == userFace.feature){
                                    System.out.println("头像特征无法识别");
                                }
                                System.out.println("识别出用户头像中的人脸信息..");
                            }
                           // bitfacepp.release();
                            return isNull;
                        }
                        //主要是更新UI
                        @Override
                        protected void onPostExecute(Boolean result) {
                            if(result){
                                System.out.println("无法识别用户头像中的人脸信息...");
                                Toast.makeText(OpenglActivity.this, "无法失败用户头像中的人脸数据，请上传正确的人脸头像照片", Toast.LENGTH_LONG).show();
                            }
                        }
                    }.execute(response);

                }
            }
        });
    }

    private void autoFocus() {
        if (mCamera != null && isBackCamera) {
            mCamera.cancelAutoFocus();
            Camera.Parameters parameters = mCamera.getParameters();
            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
            mCamera.setParameters(parameters);
            mCamera.autoFocus(null);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        ConUtil.acquireWakeLock(this);
        startTime = System.currentTimeMillis();
        mCamera = mICamera.openCamera(isBackCamera, this, resolutionMap);
        if (mCamera != null) {
            Angle = 360 - mICamera.Angle;
            if (isBackCamera)
                Angle = mICamera.Angle;

            RelativeLayout.LayoutParams layout_params = mICamera.getLayoutParam();
            mGlSurfaceView.setLayoutParams(layout_params);

            int width = mICamera.cameraWidth;
            int height = mICamera.cameraHeight;

            int left = 0;
            int top = 0;
            int right = width;
            int bottom = height;
            if (isROIDetect) {
                float line = height * roi_ratio;
                left = (int) ((width - line) / 2.0f);
                top = (int) ((height - line) / 2.0f);
                right = width - left;
                bottom = height - top;
            }

            String errorCode = facepp.init(this, ConUtil.getFileContent(this, R.raw.megviifacepp_0_4_7_model));

            Facepp.FaceppConfig faceppConfig = facepp.getFaceppConfig();
            faceppConfig.interval = detection_interval;
            faceppConfig.minFaceSize = min_face_size;
            faceppConfig.roi_left = left;
            faceppConfig.roi_top = top;
            faceppConfig.roi_right = right;
            faceppConfig.roi_bottom = bottom;
            if (isOneFaceTrackig)
                faceppConfig.one_face_tracking = 1;
            else
                faceppConfig.one_face_tracking = 0;
            String[] array = getResources().getStringArray(R.array.trackig_mode_array);
            if (trackModel.equals(array[0]))
                faceppConfig.detectionMode = Facepp.FaceppConfig.DETECTION_MODE_TRACKING;
            else if (trackModel.equals(array[1]))
                faceppConfig.detectionMode = Facepp.FaceppConfig.DETECTION_MODE_TRACKING_ROBUST;
            else if (trackModel.equals(array[2]))
                faceppConfig.detectionMode = Facepp.FaceppConfig.DETECTION_MODE_TRACKING_FAST;

            facepp.setFaceppConfig(faceppConfig);
        } else {
            mDialogUtil.showDialog(getResources().getString(R.string.camera_error));
        }
    }

    private void setConfig(int rotation) {
        Facepp.FaceppConfig faceppConfig = facepp.getFaceppConfig();
        if (faceppConfig.rotation != rotation) {
            faceppConfig.rotation = rotation;
            facepp.setFaceppConfig(faceppConfig);
        }
    }

    /**
     * 画绿色框
     */
    private void drawShowRect() {
        mPointsMatrix.vertexBuffers = OpenGLDrawRect.drawCenterShowRect(isBackCamera, mICamera.cameraWidth,
                mICamera.cameraHeight, roi_ratio);
    }

    private Bitmap findBitmap;
    @Override
    public void onPreviewFrame(final byte[] imgData, final Camera camera) {
        if (isSuccess)
            return;
        isSuccess = true;

        mHandler.post(new Runnable() {
            @Override
            public void run() {
                int width = mICamera.cameraWidth;
                int height = mICamera.cameraHeight;

                long faceDetectTime_action = System.currentTimeMillis();
                int orientation = sensorUtil.orientation;
                if (orientation == 0)
                    rotation = Angle;
                else if (orientation == 1)
                    rotation = 0;
                else if (orientation == 2)
                    rotation = 180;
                else if (orientation == 3)
                    rotation = 360 - Angle;

                setConfig(rotation);

                final Facepp.Face[] faces = facepp.detect(imgData, width, height, Facepp.IMAGEMODE_NV21);
                final long algorithmTime = System.currentTimeMillis() - faceDetectTime_action;

                if (faces != null) {
                    long actionMaticsTime = System.currentTimeMillis();
                    ArrayList<ArrayList> pointsOpengl = new ArrayList<ArrayList>();
                    confidence = 0.0f;

                    if (faces.length >= 0) {
                        for (int c = 0; c < faces.length; c++) {
                            if (is106Points)
                                facepp.getLandmark(faces[c], Facepp.FPP_GET_LANDMARK106);
                            else
                                facepp.getLandmark(faces[c], Facepp.FPP_GET_LANDMARK81);

                            if (is3DPose) {
                                facepp.get3DPose(faces[c]);
                            }

                            Facepp.Face face = faces[c];

                            if (isFaceProperty) {
                                long time_AgeGender_action = System.currentTimeMillis();
                                facepp.getAgeGender(faces[c]);
                                time_AgeGender_end = System.currentTimeMillis() - time_AgeGender_action;
                                String gender = "man";
                                if (face.female > face.male)
                                    gender = "woman";
                                AttriButeStr = "\nage: " + (int) Math.max(face.age, 1) + "\ngender: " + gender;
                            }

                            pitch = faces[c].pitch;
                            yaw = faces[c].yaw;
                            roll = faces[c].roll;
                            confidence = faces[c].confidence;

                            if (orientation == 1 || orientation == 2) {
                                width = mICamera.cameraHeight;
                                height = mICamera.cameraWidth;
                            }
                            ArrayList<FloatBuffer> triangleVBList = new ArrayList<FloatBuffer>();
                            for (int i = 0; i < faces[c].points.length; i++) {
                                float x = (faces[c].points[i].x / height) * 2 - 1;
                                if (isBackCamera)
                                    x = -x;
                                float y = 1 - (faces[c].points[i].y / width) * 2;
                                float[] pointf = new float[]{x, y, 0.0f};
                                if (orientation == 1)
                                    pointf = new float[]{-y, x, 0.0f};
                                if (orientation == 2)
                                    pointf = new float[]{y, -x, 0.0f};
                                if (orientation == 3)
                                    pointf = new float[]{-x, -y, 0.0f};

                                FloatBuffer fb = mCameraMatrix.floatBufferUtil(pointf);
                                triangleVBList.add(fb);
                            }

                            pointsOpengl.add(triangleVBList);
                        }


                    } else {
                        pitch = 0.0f;
                        yaw = 0.0f;
                        roll = 0.0f;
                    }
                    if (faces.length > 0 && is3DPose)
                    {
                        mPointsMatrix.bottomVertexBuffer = OpenGLDrawRect.drawBottomShowRect(0.15f, 0, -0.7f, pitch,
                                -yaw, roll, rotation);
                    }
                    else
                        mPointsMatrix.bottomVertexBuffer = null;
                    synchronized (mPointsMatrix) {
                        mPointsMatrix.points = pointsOpengl;
                    }

                    if(faces.length > 0 && isCheck == false){
                        if(isOnline){
                            Camera.Size size = camera.getParameters().getPreviewSize();
                            YuvImage image = new YuvImage(imgData, ImageFormat.NV21, size.width, size.height, null);
                            if(image!=null && findBitmap == null){
                                ByteArrayOutputStream stream = new ByteArrayOutputStream();
                                image.compressToJpeg(new Rect(0, 0, size.width, size.height), 80, stream);
                                findBitmap = BitmapFactory.decodeByteArray(stream.toByteArray(), 0, stream.size());
                                findBitmap = ConUtil.rotateMyBitmap(findBitmap,rotation);
                                stream = new ByteArrayOutputStream();
                                findBitmap.compress(Bitmap.CompressFormat.JPEG,80,stream);
                                String url = "https://api-cn.faceplusplus.com/facepp/v3/compare";
                                String encode =Base64.encodeToString(stream.toByteArray(), Base64.DEFAULT);
                                System.out.println("encode:"+encode);
                                OkHttpUtils
                                        .post()
                                        .url(url)
                                        .addParams("api_key", Util.API_KEY)
                                        .addParams("api_secret", Util.API_SECRET)
                                        .addParams("image_url1",userModel.getUserImg())
                                        .addParams("image_base64_2",encode)
                                        .build()
                                        .execute(new StringCallback()
                                        {
                                            @Override
                                            public void onError(Call call, Exception e, int id) {
                                                runOnUiThread(new Runnable() {
                                                    public void run() {
                                                        Toast.makeText(OpenglActivity.this,"人脸对比AIP访问错误",Toast.LENGTH_LONG).show();
                                                    }
                                                });
                                            }
                                            @Override
                                            public void onResponse(String response, int id) {
                                                JSONObject resJson = JSONObject.parseObject(response);
                                                final Float confidence = resJson.getFloat("confidence");
                                                runOnUiThread(new Runnable() {
                                                    public void run() {
                                                        if(null == confidence){
                                                            Toast.makeText(OpenglActivity.this,"有图片未识别到人脸",Toast.LENGTH_LONG).show();
                                                        }else{
                                                            if(confidence < 50.0){
                                                                Toast.makeText(OpenglActivity.this,"识别对比失败",Toast.LENGTH_LONG).show();
                                                            }else{
                                                                Toast.makeText(OpenglActivity.this,"识别对比成功",Toast.LENGTH_LONG).show();
                                                            }
                                                        }
                                                    }
                                                });

                                            }
                                        });
                            }
                        }else{
                            new AsyncTask<Facepp.Face[],Integer,Boolean>(){
                                @Override
                                protected void onPostExecute(Boolean aBoolean) {
                                    if(null == aBoolean)return;

                                    super.onPostExecute(aBoolean);
                                }
                                @Override
                                protected Boolean doInBackground(Facepp.Face[]... faces) {
                                    if(isCheck)return null;
                                    isCheck = true;
                                    Facepp.Face[] faceList = faces[0];
                                    for(Facepp.Face face : faceList){
                                        double tz =  facepp.faceCompare(userFace,face);
                                        System.out.println("tz:"+tz);
                                    }
                                    return null;
                                }
                            }.execute(faces);
                        }
                    }

                    final long matrixTime = System.currentTimeMillis() - actionMaticsTime;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if(findBitmap != null ){
                                findImage.setImageBitmap(findBitmap);
                            }
                            String logStr = "\ncameraWidth: " + mICamera.cameraWidth + "\ncameraHeight: "
                                    + mICamera.cameraHeight + "\nalgorithmTime: " + algorithmTime + "ms"
                                    + "\nmatrixTime: " + matrixTime + "\nconfidence:" + confidence;
                            debugInfoText.setText(logStr);
                            if (faces.length > 0 && isFaceProperty && AttriButeStr != null && AttriButeStr.length() > 0)
                                AttriButetext.setText(AttriButeStr + "\nAgeGenderTime:" + time_AgeGender_end);
                            else
                                AttriButetext.setText("");
                        }
                    });
                }
                isSuccess = false;
                if (!isTiming) {
                    timeHandle.sendEmptyMessage(1);
                }
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        ConUtil.releaseWakeLock();
        if (mediaRecorderUtil != null) {
            mediaRecorderUtil.releaseMediaRecorder();
        }
        mICamera.closeCamera();
        mCamera = null;

        timeHandle.removeMessages(0);

        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        facepp.release();
    }

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        // TODO Auto-generated method stub

    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        // 黑色背景
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);

        mTextureID = OpenGLUtil.createTextureID();
        mSurface = new SurfaceTexture(mTextureID);
        // 这个接口就干了这么一件事，当有数据上来后会进到onFrameAvailable方法
        mSurface.setOnFrameAvailableListener(this);// 设置照相机有数据时进入
        mCameraMatrix = new CameraMatrix(mTextureID);
        mPointsMatrix = new PointsMatrix();
        mICamera.startPreview(mSurface);// 设置预览容器
        mICamera.actionDetect(this);
        if (isTiming) {
            timeHandle.sendEmptyMessageDelayed(0, printTime);
        }
        if (isROIDetect)
            drawShowRect();
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        // 设置画面的大小
        GLES20.glViewport(0, 0, width, height);

        float ratio = (float) width / height;
        ratio = 1; // 这样OpenGL就可以按照屏幕框来画了，不是一个正方形了

        // this projection matrix is applied to object coordinates
        // in the onDrawFrame() method
        Matrix.frustumM(mProjMatrix, 0, -ratio, ratio, -1, 1, 3, 7);
        // Matrix.perspectiveM(mProjMatrix, 0, 0.382f, ratio, 3, 700);
    }


    @Override
    public void onDrawFrame(GL10 gl) {
        final long actionTime = System.currentTimeMillis();
        // Log.w("ceshi", "onDrawFrame===");
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);// 清除屏幕和深度缓存
        float[] mtx = new float[16];
        mSurface.getTransformMatrix(mtx);
        mCameraMatrix.draw(mtx);
        // Set the camera position (View matrix)
        Matrix.setLookAtM(mVMatrix, 0, 0, 0, -3, 0f, 0f, 0f, 0f, 1f, 0f);

        // Calculate the projection and view transformation
        Matrix.multiplyMM(mMVPMatrix, 0, mProjMatrix, 0, mVMatrix, 0);

        mPointsMatrix.draw(mMVPMatrix);

        if (isDebug) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    final long endTime = System.currentTimeMillis() - actionTime;
                    debugPrinttext.setText("printTime: " + endTime);
                }
            });
        }
        mSurface.updateTexImage();// 更新image，会调用onFrameAvailable方法
    }

    Handler timeHandle = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 0:
                    mGlSurfaceView.requestRender();// 发送去绘制照相机不断去回调
                    timeHandle.sendEmptyMessageDelayed(0, printTime);
                    break;
                case 1:
                    mGlSurfaceView.requestRender();// 发送去绘制照相机不断去回调
                    break;
            }
        }
    };


}
