package com.example.microscope;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Environment;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraControl;
import androidx.camera.core.CameraInfo;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.FocusMeteringAction;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.MeteringPoint;
import androidx.camera.core.Preview;
import androidx.camera.core.SurfaceOrientedMeteringPointFactory;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** محرك الكاميرا المجهرية */
public class CameraController {

    private static final String TAG = "CameraController";

    private final Context context;
    private final CameraEventListener listener;
    private final ExecutorService cameraExecutor;

    private ProcessCameraProvider cameraProvider;
    private ImageCapture imageCapture;
    private ImageAnalysis imageAnalysis;
    private CameraControl cameraControl;
    private CameraInfo cameraInfo;

    private float currentZoom = 1.0f;

    public interface CameraEventListener {
        void onZoomChanged(float zoom);
        void onImageCaptured(String path);
        void onError(String message);
        void onFrameAvailable(Bitmap frame);
    }

    public CameraController(Context context, CameraEventListener listener) {
        this.context = context;
        this.listener = listener;
        this.cameraExecutor = Executors.newSingleThreadExecutor();
    }

    @SuppressLint("RestrictedApi")
    public void startCamera(LifecycleOwner lifecycleOwner, PreviewView previewView) {
        ListenableFuture<ProcessCameraProvider> providerFuture =
                ProcessCameraProvider.getInstance(context);

        providerFuture.addListener(() -> {
            try {
                cameraProvider = providerFuture.get();

                Preview preview = new Preview.Builder().build();

                imageCapture = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                        .build();

                imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                        .build();

                imageAnalysis.setAnalyzer(cameraExecutor, image -> {
                    Bitmap bitmap = rgbaToBitmap(image);
                    image.close();
                    if (bitmap != null) {
                        Bitmap scaled = Bitmap.createScaledBitmap(bitmap, 960, 540, true);
                        if (scaled != bitmap) bitmap.recycle();
                        if (listener != null) listener.onFrameAvailable(scaled);
                    }
                });

                cameraProvider.unbindAll();
                Camera camera = cameraProvider.bindToLifecycle(
                        lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA,
                        preview, imageCapture, imageAnalysis);

                preview.setSurfaceProvider(previewView.getSurfaceProvider());
                cameraControl = camera.getCameraControl();
                cameraInfo = camera.getCameraInfo();

                Log.d(TAG, "تم بدء الكاميرا");
            } catch (Exception e) {
                Log.e(TAG, "فشل بدء الكاميرا", e);
                if (listener != null) listener.onError("فشل بدء الكاميرا: " + e.getMessage());
            }
        }, ContextCompat.getMainExecutor(context));
    }

    private Bitmap rgbaToBitmap(ImageProxy image) {
        try {
            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
            buffer.rewind();
            Bitmap bitmap = Bitmap.createBitmap(
                    image.getWidth(), image.getHeight(), Bitmap.Config.ARGB_8888);
            bitmap.copyPixelsFromBuffer(buffer);
            return bitmap;
        } catch (Exception e) {
            return null;
        }
    }

    /** ضبط التكبير مع تقييد حسب حدود الجهاز */
    public void setZoom(float zoomRatio) {
        if (cameraControl == null) return;
        float max = 10f;
        try {
            if (cameraInfo != null && cameraInfo.getZoomState().getValue() != null)
                max = cameraInfo.getZoomState().getValue().getMaxZoomRatio();
        } catch (Exception ignored) {}
        currentZoom = Math.max(1f, Math.min(zoomRatio, max));
        cameraControl.setZoomRatio(currentZoom);
        if (listener != null) listener.onZoomChanged(currentZoom);
    }

    /** تركيز تلقائي على نقطة محددة */
    public void focusOnPoint(float x, float y, int viewWidth, int viewHeight) {
        if (cameraControl == null) return;
        SurfaceOrientedMeteringPointFactory factory =
                new SurfaceOrientedMeteringPointFactory(viewWidth, viewHeight);
        MeteringPoint point = factory.createPoint(x, y);
        FocusMeteringAction action = new FocusMeteringAction.Builder(point,
                FocusMeteringAction.FLAG_AF | FocusMeteringAction.FLAG_AE)
                .setAutoCancelDuration(5, TimeUnit.SECONDS)
                .build();
        cameraControl.startFocusAndMetering(action);
    }

    public void setTorch(boolean enabled) {
        if (cameraControl != null) cameraControl.enableTorch(enabled);
    }

    /** التقاط صورة بجودة عالية (يحفظ في مجلد التطبيق - بدون أذونات) */
    public void captureImage() {
        if (imageCapture == null) return;
        File photoFile = createImageFile();
        ImageCapture.OutputFileOptions options =
                new ImageCapture.OutputFileOptions.Builder(photoFile).build();

        imageCapture.takePicture(options, cameraExecutor,
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults results) {
                        if (listener != null) listener.onImageCaptured(photoFile.getAbsolutePath());
                    }
                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        Log.e(TAG, "فشل التقاط الصورة", exception);
                    }
                });
    }

    private File createImageFile() {
        String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                .format(new Date());
        File dir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        if (dir != null && !dir.exists()) dir.mkdirs();
        return new File(dir, "MICRO_" + ts + ".jpg");
    }

    public void shutdown() {
        if (cameraExecutor != null) cameraExecutor.shutdown();
        if (cameraProvider != null) cameraProvider.unbindAll();
    }

    public float getCurrentZoom() { return currentZoom; }
}
