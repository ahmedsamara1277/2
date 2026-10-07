package com.example.microscope;

/** قياس حجم الكائنات بالمليمتر حسب التكبير */
public class MeasurementTool {

    private float pixelsPerMm = 100.0f;
    private float currentZoom = 1.0f;

    public static class Measurement {
        public float widthMm;
        public float heightMm;
        public float areaMm2;
        public String objectType;
    }

    public Measurement measureObject(int objectWidthPx, int objectHeightPx) {
        Measurement m = new Measurement();
        float eff = pixelsPerMm * currentZoom;
        m.widthMm = objectWidthPx / eff;
        m.heightMm = objectHeightPx / eff;
        m.areaMm2 = m.widthMm * m.heightMm;
        m.objectType = classifyBySize(m.widthMm, m.heightMm);
        return m;
    }

    private String classifyBySize(float w, float h) {
        float max = Math.max(w, h);
        if (max < 0.3f)  return "سوس/ذرة (ميكروسكوبي)";
        if (max < 1.0f)  return "قمل/سوس كبير";
        if (max < 3.0f)  return "برغوث/قراد صغير";
        if (max < 10.0f) return "حشرة متوسطة";
        return "كائن كبير";
    }

    public void setPixelsPerMm(float v) { pixelsPerMm = v; }
    public void setCurrentZoom(float z) { currentZoom = z; }
}
