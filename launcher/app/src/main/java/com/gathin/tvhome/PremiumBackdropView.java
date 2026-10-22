package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;

/**
 * Built-in full-bleed atmospheric wallpaper used when no user's image exists.
 * A single 480x270 pre-rendered texture is scaled by the GPU; there is no live
 * blur, animation or per-frame gradient work on a low-memory Sony TV.
 */
public final class PremiumBackdropView extends View {
    private final Paint imagePaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Bitmap background;

    public PremiumBackdropView(Context context) {
        super(context);
        background = Bitmap.createBitmap(480, 270, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(background);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(0, 0, 480, 270,
                new int[]{0xFF07121E, 0xFF102338, 0xFF201D32, 0xFF090F19},
                new float[]{0f, 0.34f, 0.68f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, 480, 270, p);
        p.setShader(new RadialGradient(395, 80, 270,
                new int[]{0x95637DAF, 0x485D5984, 0x006A5576},
                new float[]{0f, 0.52f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, 480, 270, p);
        p.setShader(new RadialGradient(334, 230, 215,
                new int[]{0x6FA56E69, 0x329F665B, 0x00BE7B6F},
                new float[]{0f, 0.48f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, 480, 270, p);
        p.setShader(new RadialGradient(50, 140, 245,
                0x9006101B, 0x0006101B, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, 480, 270, p);
        p.setShader(null);

        // Soft satin curves on the open, right-hand side. They read as a
        // cinematic atmosphere rather than an attention-grabbing animated effect.
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(0.65f);
        for (int i = 0; i < 5; i++) {
            p.setColor((0x16 - i * 2) << 24 | 0x00C8D8EC);
            Path curve = new Path();
            curve.moveTo(175, 246 + i * 8);
            curve.cubicTo(300, 163 + i * 10, 365, 84 + i * 12, 525, 100 + i * 10);
            c.drawPath(curve, p);
        }
        p.setStyle(Paint.Style.FILL);
        p.setShader(new LinearGradient(0, 205, 0, 270,
                0x00060D15, 0xCC060B12, Shader.TileMode.CLAMP));
        c.drawRect(0, 205, 480, 270, p);
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (background == null || background.isRecycled()) return;
        canvas.drawBitmap(background, null,
                new android.graphics.Rect(0, 0, getWidth(), getHeight()), imagePaint);
    }

}
