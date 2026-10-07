package com.example.microscope;

import android.Manifest;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.Locale;

public class MainActivity extends AppCompatActivity
        implements CameraController.CameraEventListener {

    private static final int CAMERA_PERMISSION_CODE = 100;

    private CameraController cameraController;
    private ImageEnhancer imageEnhancer;
    private AiAnalyzer aiAnalyzer;
    private MeasurementTool measurementTool;

    private PreviewView previewView;
    private FocusOverlayView focusOverlay;
    private SeekBar zoomSeekBar;
    private TextView zoomLevelText;
    private TextView magnificationLabel;
    private ImageButton torchButton;
    private boolean torchOn = false;

    private Bitmap lastFrame = null;
    private int filterIndex = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        initTools();
        setupListeners();

        if (checkPermission()) startCamera();
        else requestPermission();
    }

    private void initViews() {
        previewView = findViewById(R.id.previewView);
        focusOverlay = findViewById(R.id.focusOverlay);
        zoomSeekBar = findViewById(R.id.zoomSeekBar);
        zoomLevelText = findViewById(R.id.zoomLevelText);
        magnificationLabel = findViewById(R.id.magnificationLabel);
        torchButton = findViewById(R.id.torchButton);
    }

    private void initTools() {
        imageEnhancer = new ImageEnhancer();
        aiAnalyzer = new AiAnalyzer(this);
        measurementTool = new MeasurementTool();
    }

    private void setupListeners() {

        // شريط التكبير
        zoomSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (fromUser && cameraController != null) {
                    float zoom = 1.0f + (progress / 100.0f) * 49.0f;
                    cameraController.setZoom(zoom);
                    zoomLevelText.setText(String.format(Locale.US, "x%.1f",
                            cameraController.getCurrentZoom()));
                }
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });

        // الفلاش
        torchButton.setOnClickListener(v -> {
            torchOn = !torchOn;
            if (cameraController != null) cameraController.setTorch(torchOn);
            torchButton.setAlpha(torchOn ? 1.0f : 0.5f);
        });

        // التقاط صورة
        findViewById(R.id.captureButton).setOnClickListener(v -> {
            if (cameraController != null) cameraController.captureImage();
        });

        // زر التحليل
        findViewById(R.id.analyzeButton).setOnClickListener(v -> {
            Toast.makeText(this, "جارٍ تحليل الصورة...", Toast.LENGTH_SHORT).show();
            if (cameraController != null) cameraController.captureImage();
        });

        // زر الفلاتر: تطبيق الفلتر التالي على آخر إطار
        findViewById(R.id.filterButton).setOnClickListener(v -> {
            if (lastFrame == null) {
                Toast.makeText(this, "وجّه الكاميرا أولاً", Toast.LENGTH_SHORT).show();
                return;
            }
            String[] names = {"عادي", "تباين", "تنعيم ضوضاء",
                    "أوعية دموية", "UV محاكى", "حدة عالية"};
            filterIndex = (filterIndex + 1) % 6;
            Bitmap filtered = lastFrame;
            switch (filterIndex) {
                case 1: filtered = imageEnhancer.enhanceContrast(lastFrame, 60); break;
                case 2: filtered = imageEnhancer.denoise(lastFrame); break;
                case 3: filtered = imageEnhancer.vascularView(lastFrame); break;
                case 4: filtered = imageEnhancer.uvSimulation(lastFrame); break;
                case 5: filtered = imageEnhancer.sharpen(lastFrame); break;
            }
            Toast.makeText(this, "فلتر: " + names[filterIndex], Toast.LENGTH_SHORT).show();
            showBitmapDialog(filtered);
        });

        // زر القياس
        findViewById(R.id.measureButton).setOnClickListener(v -> {
            if (lastFrame == null) {
                Toast.makeText(this, "وجّه الكاميرا أولاً", Toast.LENGTH_SHORT).show();
                return;
            }
            int wPx = lastFrame.getWidth() / 4;
            int hPx = lastFrame.getHeight() / 4;
            MeasurementTool.Measurement m = measurementTool.measureObject(wPx, hPx);
            Toast.makeText(this, String.format(Locale.US,
                    "العرض: %.2fmm | الارتفاع: %.2fmm | %s",
                    m.widthMm, m.heightMm, m.objectType),
                    Toast.LENGTH_LONG).show();
        });

        // لمس = تركيز | قرص بإصبعين = تكبير
        previewView.setOnTouchListener(new View.OnTouchListener() {
            private float startDist = 0;
            private float startZoom = 1f;
            private boolean wasPinching = false;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (cameraController == null) return false;

                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_POINTER_DOWN:
                        if (event.getPointerCount() == 2) {
                            startDist = dist(event);
                            startZoom = cameraController.getCurrentZoom();
                            wasPinching = true;
                        }
                        break;
                    case MotionEvent.ACTION_MOVE:
                        if (event.getPointerCount() == 2 && startDist > 0) {
                            float scale = dist(event) / startDist;
                            cameraController.setZoom(startZoom * scale);
                            float z = cameraController.getCurrentZoom();
                            zoomSeekBar.setProgress((int) ((z - 1) / 49f * 100));
                            zoomLevelText.setText(String.format(Locale.US, "x%.1f", z));
                        }
                        break;
                    case MotionEvent.ACTION_UP:
                        if (!wasPinching) {
                            cameraController.focusOnPoint(event.getX(), event.getY(),
                                    v.getWidth(), v.getHeight());
                            focusOverlay.showFocus(event.getX(), event.getY());
                        }
                        startDist = 0;
                        wasPinching = false;
                        break;
                    case MotionEvent.ACTION_POINTER_UP:
                        startDist = 0;
                        break;
                }
                return true;
            }

            private float dist(MotionEvent e) {
                float dx = e.getX(0) - e.getX(1);
                float dy = e.getY(0) - e.getY(1);
                return (float) Math.sqrt(dx * dx + dy * dy);
            }
        });
    }

    private void showBitmapDialog(Bitmap bmp) {
        ImageView iv = new ImageView(this);
        iv.setImageBitmap(bmp);
        new AlertDialog.Builder(this)
                .setView(iv)
                .setPositiveButton("إغلاق", null)
                .show();
    }

    private void startCamera() {
        cameraController = new CameraController(this, this);
        cameraController.startCamera(this, previewView);
    }

    // ===== CameraEventListener =====

    @Override
    public void onZoomChanged(float zoom) {
        runOnUiThread(() -> {
            magnificationLabel.setText(String.format(Locale.US, "التكبير: %.1fx", zoom));
            measurementTool.setCurrentZoom(zoom);
        });
    }

    @Override
    public void onImageCaptured(String path) {
        runOnUiThread(() -> {
            Toast.makeText(this, "📸 تم الحفظ", Toast.LENGTH_SHORT).show();
            Bitmap bitmap = BitmapFactory.decodeFile(path);
            if (bitmap != null) {
                AiAnalyzer.AnalysisResult result = aiAnalyzer.analyze(bitmap);
                ResultDialog.showAnalysisResult(this, result, bitmap);
            }
        });
    }

    @Override
    public void onError(String message) {
        runOnUiThread(() ->
                Toast.makeText(this, "❌ " + message, Toast.LENGTH_LONG).show());
    }

    @Override
    public void onFrameAvailable(Bitmap frame) {
        lastFrame = frame;
    }

    // ===== الأذونات =====

    private boolean checkPermission() {
        return ContextCompat.checkSelfPermission(this,
                Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermission() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
            @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_CODE
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            Toast.makeText(this, "إذن الكاميرا مطلوب", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraController != null) cameraController.shutdown();
        if (aiAnalyzer != null) aiAnalyzer.close();
    }
}
