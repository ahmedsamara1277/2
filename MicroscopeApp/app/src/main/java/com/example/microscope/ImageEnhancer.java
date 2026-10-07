package com.example.microscope;

import android.graphics.Bitmap;
import android.graphics.Color;

import java.util.Arrays;

/** محسن الصور الطبية: فلاتر تباين، ضوضاء، أوعية، UV، حدة */
public class ImageEnhancer {

    public Bitmap enhanceContrast(Bitmap original, float amount) {
        Bitmap result = original.copy(Bitmap.Config.ARGB_8888, true);
        int w = result.getWidth(), h = result.getHeight();
        int[] px = new int[w * h];
        result.getPixels(px, 0, w, 0, 0, w, h);
        for (int i = 0; i < px.length; i++) {
            px[i] = Color.rgb(
                    clamp(contrastAdjust(Color.red(px[i]), amount)),
                    clamp(contrastAdjust(Color.green(px[i]), amount)),
                    clamp(contrastAdjust(Color.blue(px[i]), amount)));
        }
        result.setPixels(px, 0, w, 0, 0, w, h);
        return result;
    }

    private int contrastAdjust(int v, float amount) {
        float f = (259f * (amount + 255)) / (255f * (259 - amount));
        return (int) (f * (v - 128) + 128);
    }

    public Bitmap denoise(Bitmap original) {
        Bitmap result = original.copy(Bitmap.Config.ARGB_8888, true);
        int w = result.getWidth(), h = result.getHeight();
        int[] px = new int[w * h];
        result.getPixels(px, 0, w, 0, 0, w, h);
        int[] out = new int[px.length];
        for (int y = 1; y < h - 1; y++)
            for (int x = 1; x < w - 1; x++)
                out[y * w + x] = medianFilter(px, x, y, w);
        result.setPixels(out, 0, w, 0, 0, w, h);
        return result;
    }

    private int medianFilter(int[] px, int x, int y, int w) {
        int[] r = new int[9], g = new int[9], b = new int[9];
        int idx = 0;
        for (int dy = -1; dy <= 1; dy++)
            for (int dx = -1; dx <= 1; dx++) {
                int p = px[(y + dy) * w + (x + dx)];
                r[idx] = Color.red(p); g[idx] = Color.green(p); b[idx] = Color.blue(p);
                idx++;
            }
        Arrays.sort(r); Arrays.sort(g); Arrays.sort(b);
        return Color.rgb(r[4], g[4], b[4]);
    }

    /** إبراز الأوعية الدموية والمناطق الحمراء */
    public Bitmap vascularView(Bitmap original) {
        Bitmap result = original.copy(Bitmap.Config.ARGB_8888, true);
        int w = result.getWidth(), h = result.getHeight();
        int[] px = new int[w * h];
        result.getPixels(px, 0, w, 0, 0, w, h);
        for (int i = 0; i < px.length; i++) {
            int r = Color.red(px[i]), g = Color.green(px[i]), b = Color.blue(px[i]);
            int avg = (r + g + b) / 3;
            if (r - avg > 10) {
                r = clamp(r + 40); g = clamp(g - 20); b = clamp(b - 20);
            }
            px[i] = Color.rgb(r, g, b);
        }
        result.setPixels(px, 0, w, 0, 0, w, h);
        return result;
    }

    /** محاكاة فلتر UV لكشف آفات الجلد */
    public Bitmap uvSimulation(Bitmap original) {
        Bitmap result = original.copy(Bitmap.Config.ARGB_8888, true);
        int w = result.getWidth(), h = result.getHeight();
        int[] px = new int[w * h];
        result.getPixels(px, 0, w, 0, 0, w, h);
        for (int i = 0; i < px.length; i++) {
            int uv = (int) (0.3 * Color.red(px[i])
                    + 0.59 * Color.green(px[i]) + 0.11 * Color.blue(px[i]));
            uv = 255 - uv;
            px[i] = Color.rgb(uv, uv / 2, 255 - uv / 2);
        }
        result.setPixels(px, 0, w, 0, 0, w, h);
        return result;
    }

    /** زيادة الحدة لرؤية التفاصيل الدقيقة */
    public Bitmap sharpen(Bitmap original) {
        Bitmap result = original.copy(Bitmap.Config.ARGB_8888, true);
        int w = result.getWidth(), h = result.getHeight();
        int[] px = new int[w * h];
        result.getPixels(px, 0, w, 0, 0, w, h);
        int[] out = new int[px.length];
        float[][] k = {{0, -1, 0}, {-1, 5, -1}, {0, -1, 0}};
        for (int y = 1; y < h - 1; y++)
            for (int x = 1; x < w - 1; x++) {
                float r = 0, g = 0, b = 0;
                for (int ky = -1; ky <= 1; ky++)
                    for (int kx = -1; kx <= 1; kx++) {
                        int p = px[(y + ky) * w + (x + kx)];
                        r += Color.red(p) * k[ky + 1][kx + 1];
                        g += Color.green(p) * k[ky + 1][kx + 1];
                        b += Color.blue(p) * k[ky + 1][kx + 1];
                    }
                out[y * w + x] = Color.rgb(clamp((int) r), clamp((int) g), clamp((int) b));
            }
        result.setPixels(out, 0, w, 0, 0, w, h);
        return result;
    }

    private int clamp(int v) { return Math.max(0, Math.min(255, v)); }
}
