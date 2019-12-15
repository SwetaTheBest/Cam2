package com.swetajain.myapplication;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.util.SparseArray;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class MainActivity extends AppCompatActivity {
    Button captureButton;
    TextureView mTextureView;
    public static final int CAMERA_REQUEST_PERMISSION = 1008;
    private String cameraId;
    private CameraDevice mCameraDevice;
    private CameraCaptureSession mCameraCaptureSession;
    private CaptureRequest.Builder captureRequestBuilder;
    private Size imageDimension;
    private ImageReader mImageReader;
    private File mFile;
    private Handler mHandler;
    private HandlerThread mHandlerThread;

    public static final SparseIntArray ORIENTATIONS = new SparseIntArray();

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    private CameraDevice.StateCallback stateCallBack = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            mCameraDevice = cameraDevice;
            createCameraPreview();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
           cameraDevice.close();
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int i) {
              cameraDevice.close();

        }
    };
    private TextureView.SurfaceTextureListener textureListener =
            new TextureView.SurfaceTextureListener() {
                @Override
                public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int i, int i1) {
                    openCamera();
                }

                @Override
                public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int i, int i1) {

                }

                @Override
                public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
                    return false;
                }

                @Override
                public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {

                }
            };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mTextureView = findViewById(R.id.texture_view);
        captureButton = findViewById(R.id.capture_button);
        mTextureView.setSurfaceTextureListener(textureListener);

        captureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                takePicture();
            }
        });
    }

    private void takePicture() {
        if (mCameraDevice == null) {
            return;
        }

        if (android.os.Build.VERSION.SDK_INT >=
                android.os.Build.VERSION_CODES.LOLLIPOP) {
            CameraManager cameraManager = (CameraManager)
                    getSystemService(Context.CAMERA_SERVICE);
            try {

                CameraCharacteristics cameraCharacteristics =
                        cameraManager.getCameraCharacteristics(mCameraDevice.getId());
                Size[] jpegSizes = null;
                if (cameraCharacteristics != null) {
                    jpegSizes = cameraCharacteristics
                            .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                            .getOutputSizes(ImageFormat.JPEG);

                    int width = 640;
                    int height = 480;

                    if (jpegSizes != null && jpegSizes.length > 0) {
                        width = jpegSizes[0].getWidth();
                        height = jpegSizes[0].getHeight();
                    }

                   mImageReader = ImageReader
                            .newInstance(width, height, ImageFormat.JPEG, 1);
                    List<Surface> outputSurface = new ArrayList<>(2);
                    outputSurface.add(mImageReader.getSurface());
                    outputSurface.add(new Surface(mTextureView.getSurfaceTexture()));
                    captureRequestBuilder = mCameraDevice
                            .createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
                    captureRequestBuilder.addTarget(mImageReader.getSurface());
                    captureRequestBuilder.set(CaptureRequest.CONTROL_MODE,
                            CameraMetadata.CONTROL_MODE_AUTO);

                    int rotation = getWindowManager().getDefaultDisplay().getRotation();
                    captureRequestBuilder
                            .set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation));

                    mFile = new File(Environment.getExternalStorageDirectory() +
                            "/" +
                            UUID.randomUUID().toString());

                    ImageReader.OnImageAvailableListener readerListener = new ImageReader.OnImageAvailableListener() {
                        @Override
                        public void onImageAvailable(ImageReader imageReader) {
                            try (Image image = imageReader.acquireLatestImage()) {
                                ByteBuffer byteBuffer = image.getPlanes()[0].getBuffer();
                                byte[] bytes = new byte[byteBuffer.capacity()];
                                byteBuffer.get(bytes);

                                save(bytes);

                            } catch (FileNotFoundException e) {
                                e.printStackTrace();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }

                        private void save(byte[] bytes) throws IOException {
                            try (OutputStream outputStream = new FileOutputStream(mFile)) {
                                outputStream.write(bytes);

                            }
                        }

                    };
                    mImageReader.setOnImageAvailableListener(readerListener, mHandler);
                    final CameraCaptureSession.CaptureCallback captureListener =
                            new CameraCaptureSession.CaptureCallback() {
                                @Override
                                public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                                               @NonNull CaptureRequest request,
                                                               @NonNull TotalCaptureResult result) {
                                    super.onCaptureCompleted(session, request, result);
                                    Toast.makeText(MainActivity.this, "saved " + mFile,
                                            Toast.LENGTH_SHORT).show();
                                    Log.d("IMAGE",mFile.getAbsolutePath());
                                    createCameraPreview();
                                }
                            };
                    mCameraDevice.createCaptureSession(outputSurface,
                            new CameraCaptureSession.StateCallback() {
                                @Override
                                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                                    try {
                                        cameraCaptureSession.capture(captureRequestBuilder.build()
                                                , captureListener, mHandler);
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                }

                                @Override
                                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {

                                }
                            }, mHandler);
                }


            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }


    }

    private void createCameraPreview() {
        SurfaceTexture surfaceTexture = mTextureView.getSurfaceTexture();
        assert surfaceTexture != null;
        surfaceTexture.setDefaultBufferSize(imageDimension.getWidth(),
                imageDimension.getHeight());
        Surface surface = new Surface(surfaceTexture);
        try {
            captureRequestBuilder = mCameraDevice
                    .createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        captureRequestBuilder.addTarget(surface);
        try {
            mCameraDevice.createCaptureSession(Arrays.asList(surface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {

                    if (mCameraDevice == null)
                        return;
                    mCameraCaptureSession = cameraCaptureSession;
                    updatePreview();

                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                    Toast.makeText(MainActivity.this, "Changed", Toast.LENGTH_SHORT).show();
                }
            }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void updatePreview() {
        if (mCameraDevice == null)
            Toast.makeText(this, "Error", Toast.LENGTH_SHORT).show();
        captureRequestBuilder.set(CaptureRequest.CONTROL_MODE,
                CaptureRequest.CONTROL_MODE_AUTO);
        try {
            mCameraCaptureSession.setRepeatingRequest(captureRequestBuilder.build(),
                    null, mHandler);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }


    private void openCamera() {
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            cameraId = manager.getCameraIdList()[0];
            CameraCharacteristics cameraCharacteristics =
                    manager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map =
                    cameraCharacteristics
                            .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            assert map != null;
            imageDimension = map.getOutputSizes(SurfaceTexture.class)[0];

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) !=
            PackageManager.PERMISSION_GRANTED){
               ActivityCompat.requestPermissions(this,new String[]{
                       Manifest.permission.CAMERA,
                       Manifest.permission.WRITE_EXTERNAL_STORAGE
               },CAMERA_REQUEST_PERMISSION);
            }

            manager.openCamera(cameraId,stateCallBack,null);
        } catch (CameraAccessException e) {
               e.printStackTrace();
        }

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults){
        if (requestCode == CAMERA_REQUEST_PERMISSION){

            if (grantResults[0] != PackageManager.PERMISSION_GRANTED){
                Toast.makeText(this, "Permission denied !", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        startBackgroundThread();
        if (mTextureView.isAvailable()){
            openCamera();
        }else {
            mTextureView.setSurfaceTextureListener(textureListener);
        }
    }

    private void startBackgroundThread() {
        mHandlerThread = new HandlerThread("Camera Background");
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());
    }

    @Override
    protected void onPause() {
        stopBackgroundThread();

        super.onPause();
    }

    private void stopBackgroundThread() {
        mHandlerThread.quitSafely();
        try{
            mHandlerThread.join();
            mHandlerThread = null;
            mHandler = null;

        }catch (InterruptedException e){
            e.printStackTrace();
        }
    }
}
