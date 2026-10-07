package com.example.microscope;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Log;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

/** محلل ذكي لاكتشاف الكائنات الغريبة على الجلد */
public class AiAnalyzer {

    private static final String TAG = "AiAnalyzer";
    private static final int INPUT_SIZE = 224;

    private Interpreter tflite;

    public enum DetectionType {
        NORMAL_SKIN("جلد طبيعي"),
        TICK("قراد 🕷️"),
        SCABIES_MITE("سوس الجرب 🔬"),
        LARVA("يرقة 🐛"),
        INFLAMMATION("التهاب/احمرار"),
        UNKNOWN("غير معروف");

        private final String arabicName;
        DetectionType(String n) { arabicName = n; }
        public String getArabicName() { return arabicName; }
    }

    public static class AnalysisResult {
        public DetectionType type;
        public float confidence;
        public String description;
        public String recommendation;
    }

    public AiAnalyzer(Context context) {
        try {
            MappedByteBuffer model = loadModelFile(context, "skin_microscope_model.tflite");
            tflite = new Interpreter(model, new Interpreter.Options());
            Log.d(TAG, "تم تحميل النموذج بنجاح");
        } catch (Exception e) {
            Log.w(TAG, "لا يوجد نموذج TFLite - سيتم استخدام التحليل البديل");
            tflite = null;
        }
    }

    private MappedByteBuffer loadModelFile(Context ctx, String name) throws Exception {
        FileInputStream fis = new FileInputStream(
                ctx.getAssets().openFd(name).getFileDescriptor());
        FileChannel fc = fis.getChannel();
        long start = ctx.getAssets().openFd(name).getStartOffset();
        long len = ctx.getAssets().openFd(name).getDeclaredLength();
        return fc.map(FileChannel.MapMode.READ_ONLY, start, len);
    }

    public AnalysisResult analyze(Bitmap bitmap) {
        if (tflite != null) {
            Bitmap resized = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true);
            ByteBuffer input = preprocess(resized);
            float[][] output = new float[1][DetectionType.values().length];
            tflite.run(input, output);
            return interpret(output[0]);
        }
        return fallbackAnalysis(bitmap);
    }

    private ByteBuffer preprocess(Bitmap bmp) {
        ByteBuffer buf = ByteBuffer.allocateDirect(4 * INPUT_SIZE * INPUT_SIZE * 3);
        buf.order(ByteOrder.nativeOrder());
        int[] px = new int[INPUT_SIZE * INPUT_SIZE];
        bmp.getPixels(px, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE);
        for (int p : px) {
            buf.putFloat(((p >> 16) & 0xFF) / 255f);
            buf.putFloat(((p >> 8) & 0xFF) / 255f);
            buf.putFloat((p & 0xFF) / 255f);
        }
        return buf;
    }

    private AnalysisResult interpret(float[] probs) {
        int maxIdx = 0; float maxVal = 0;
        for (int i = 0; i < probs.length; i++)
            if (probs[i] > maxVal) { maxVal = probs[i]; maxIdx = i; }

        AnalysisResult r = new AnalysisResult();
        r.type = DetectionType.values()[maxIdx];
        r.confidence = maxVal * 100;
        fillAdvice(r);
        return r;
    }

    /** تحليل بديل بدون ذكاء اصطناعي (اكتشاف البقع الداكنة والحمراء) */
    private AnalysisResult fallbackAnalysis(Bitmap bitmap) {
        AnalysisResult r = new AnalysisResult();
        int w = bitmap.getWidth(), h = bitmap.getHeight();
        int[] px = new int[w * h];
        bitmap.getPixels(px, 0, w, 0, 0, w, h);

        int dark = 0, red = 0;
        for (int p : px) {
            int cr = Color.red(p), cg = Color.green(p), cb = Color.blue(p);
            if (cr < 50 && cg < 50 && cb < 50) dark++;
            if (cr > 180 && cg < 100 && cb < 100) red++;
        }
        float darkRatio = (float) dark / px.length;
        float redRatio = (float) red / px.length;

        if (darkRatio > 0.15f) {
            r.type = DetectionType.TICK;
            r.confidence = 60;
            r.description = "منطقة داكنة غير طبيعية";
            r.recommendation = "قد تكون كائناً صغيراً. كبّر أكثر للتأكد، " +
                    "وإن أكدت فلا تزله بالقوة - راجع الطبيب";
        } else if (redRatio > 0.30f) {
            r.type = DetectionType.INFLAMMATION;
            r.confidence = 70;
            r.description = "منطقة محمرة";
            r.recommendation = "قد يكون التهاباً أو لدغة. راقب المنطقة، " +
                    "وإن زاد التورم راجع الطبيب";
        } else {
            r.type = DetectionType.UNKNOWN;
            r.confidence = 0;
            r.description = "لم يتم اكتشاف شيء غير طبيعي بوضوح";
            r.recommendation = "جرّب فلاتر مختلفة أو غيّر الإضاءة. " +
                    "إن شعرت بحكة أو ألم استشر طبيباً";
        }
        return r;
    }

    private void fillAdvice(AnalysisResult r) {
        switch (r.type) {
            case TICK:
                r.description = "تم اكتشاف شكل يشبه القراد";
                r.recommendation = "لا تحاول إزالته بالقوة. استخدم ملاقط طبية " +
                        "واسحب بحذر مستقيم. راجع الطبيب فوراً";
                break;
            case SCABIES_MITE:
                r.description = "نمط يشبه سرير سوس الجرب";
                r.recommendation = "قد يكون جرباً. استشر طبيب جلدية لعلاج فوري";
                break;
            case LARVA:
                r.description = "يبدو وجود يرقة تحت الجلد";
                r.recommendation = "لا تحاول إزالتها بنفسك. راجع الطبيب فوراً";
                break;
            case INFLAMMATION:
                r.description = "منطقة ملتهبة أو محمرة";
                r.recommendation = "قد يكون رد فعل تحسسي. راقب المنطقة، " +
                        "وإن زاد التورم راجع الطبيب";
                break;
            default:
                r.description = "لم يتم اكتشاف شيء غير طبيعي بوضوح";
                r.recommendation = "جرّب فلاتر مختلفة أو غيّر الإضاءة";
                break;
        }
    }

    public void close() {
        if (tflite != null) tflite.close();
    }
}
