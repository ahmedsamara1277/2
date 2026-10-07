package com.example.microscope;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

/** نافذة عرض نتيجة التحليل */
public class ResultDialog {

    public static void showAnalysisResult(Context ctx,
                                          AiAnalyzer.AnalysisResult result,
                                          Bitmap image) {
        Dialog dialog = new Dialog(ctx);
        dialog.setContentView(R.layout.dialog_analysis_result);

        ImageView imageView = dialog.findViewById(R.id.resultImage);
        TextView typeText = dialog.findViewById(R.id.detectionType);
        TextView confText = dialog.findViewById(R.id.confidence);
        TextView descText = dialog.findViewById(R.id.description);
        TextView recText = dialog.findViewById(R.id.recommendation);
        Button closeBtn = dialog.findViewById(R.id.closeButton);

        imageView.setImageBitmap(image);
        typeText.setText(result.type.getArabicName());
        confText.setText(String.format("الثقة: %.0f%%", result.confidence));
        descText.setText(result.description);
        recText.setText(result.recommendation);

        int color;
        switch (result.type) {
            case TICK: case SCABIES_MITE: case LARVA:
                color = 0xFFE53935; break;
            case INFLAMMATION:
                color = 0xFFFF9800; break;
            default:
                color = 0xFF4CAF50; break;
        }
        typeText.setTextColor(color);

        closeBtn.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }
}
