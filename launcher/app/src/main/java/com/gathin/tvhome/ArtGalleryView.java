package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import java.util.Random;

/**
 * Lightweight original full-bleed landscape gallery: no picture frame or black letterbox.
 *
 * All imagery is drawn locally using Canvas; no remote images, bitmap allocations,
 * Samsung artwork/assets, network activity, or continuously running animation.
 * Scenes rotate infrequently to avoid a static image and unnecessary TV CPU/GPU work.
 */
public final class ArtGalleryView extends View {
    private static final long ART_INTERVAL_MS = 6 * 60_000L;
    private static final String[] ART_NAMES = {
            "DUSK IN THE HIGHLANDS", "THE QUIET COAST", "GOLDEN TERRACE",
            "MORNING IN THE VALLEY"
    };
    private static final int[][] COLORS = {
            {0xFF10223C, 0xFFA85D77, 0xFFF8C09A, 0xFF34425F, 0xFF1D3046, 0xFF0C1B29},
            {0xFF132A3B, 0xFF558C9C, 0xFFD7C4AD, 0xFF3F7985, 0xFF22546A, 0xFF102E43},
            {0xFF483132, 0xFFCB8571, 0xFFFFD3A0, 0xFFA76353, 0xFF85514A, 0xFF392D39},
            {0xFF162E3B, 0xFF66968A, 0xFFE0C9A6, 0xFF5E8276, 0xFF305F64, 0xFF173D4B}
    };

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int sceneIndex = (int) ((System.currentTimeMillis() / ART_INTERVAL_MS) % COLORS.length);
    private boolean running;

    private final Runnable nextScene = new Runnable() {
        @Override public void run() {
            if (!running) return;
            sceneIndex = (sceneIndex + 1) % COLORS.length;
            invalidate();
            handler.postDelayed(this, ART_INTERVAL_MS);
        }
    };

    public ArtGalleryView(Context context) { super(context); }
    public ArtGalleryView(Context context, android.util.AttributeSet attrs) { super(context, attrs); }

    public void startGallery() {
        running = true;
        handler.removeCallbacks(nextScene);
        handler.postDelayed(nextScene, ART_INTERVAL_MS);
        invalidate();
    }

    public void stopGallery() {
        running = false;
        handler.removeCallbacks(nextScene);
    }

    private void fill(Canvas canvas, int color, float l, float t, float r, float b) {
        paint.reset();
        paint.setAntiAlias(true);
        paint.setColor(color);
        canvas.drawRect(l, t, r, b, paint);
    }

    private void gradient(Canvas canvas, int top, int bottom, float l, float t, float r, float b) {
        paint.reset();
        paint.setShader(new LinearGradient(l, t, l, b, top, bottom, Shader.TileMode.CLAMP));
        canvas.drawRect(l, t, r, b, paint);
        paint.setShader(null);
    }

    private void hill(Canvas canvas, int color, float y, float bump, float offset) {
        Path path = new Path();
        path.moveTo(0, 1100);
        path.lineTo(0, y);
        path.cubicTo(260, y - bump, 380, y + 130 - bump / 3, 610, y + 18);
        path.cubicTo(880, y - bump * 1.4f, 1060, y + bump, 1340, y - bump * .35f);
        path.cubicTo(1540, y - offset, 1760, y + 85, 1920, y - offset * .5f);
        path.lineTo(1920, 1100);
        path.close();
        paint.reset();
        paint.setAntiAlias(true);
        paint.setColor(color);
        canvas.drawPath(path, paint);
    }

    private void addSubtleGrain(Canvas canvas, int scene) {
        Random random = new Random(3051L + scene);
        paint.reset();
        paint.setAntiAlias(true);
        paint.setColor(0x15FFF3DC);
        paint.setStrokeWidth(1.4f);
        for (int i = 0; i < 185; i++) {
            float x = random.nextInt(1920);
            float y = random.nextInt(1080);
            canvas.drawLine(x, y, x + 5 + random.nextInt(14), y, paint);
        }
    }

    private void sun(Canvas canvas, int tint, float x, float y, float radius) {
        paint.reset();
        paint.setAntiAlias(true);
        paint.setShader(new RadialGradient(x, y, radius * 2.9f,
                new int[]{0x60FFE8C5, 0x25FFE2B3, Color.TRANSPARENT},
                null, Shader.TileMode.CLAMP));
        canvas.drawCircle(x, y, radius * 2.9f, paint);
        paint.setShader(null);
        paint.setColor(tint);
        canvas.drawCircle(x, y, radius, paint);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (getWidth() <= 0 || getHeight() <= 0) return;
        canvas.save();
        canvas.scale(getWidth() / 1920f, getHeight() / 1080f);

        // Fill all pixels: the old 37/84px museum frame looked like TV overscan.
        // Canvas scales 1920x1080 coordinates to any logical/physical screen size.
        int[] c = COLORS[sceneIndex];
        gradient(canvas, c[0], c[2], 0, 0, 1920, 1080);
        if (sceneIndex == 2) {
            sun(canvas, 0xFFF9D6AF, 1490, 350, 120);
        } else if (sceneIndex == 1) {
            sun(canvas, 0xFFF5E0C7, 1450, 290, 99);
        } else {
            sun(canvas, 0xFFF5D6B7, 1350, 334, 108);
        }

        // Hand-drawn layers create depth with very few Android rendering operations.
        hill(canvas, c[3], 620, 210, 140);
        hill(canvas, c[4], 760, 122, 190);
        hill(canvas, c[5], 932, 160, 100);
        if (sceneIndex == 1) {
            gradient(canvas, 0xB05CA2A8, 0xFF173B4D, 0, 914, 1920, 1080);
            paint.reset();
            paint.setAntiAlias(true);
            paint.setColor(0x35EAF4E5);
            for (int i = 0; i < 7; i++) {
                float y = 930 + i * 22;
                canvas.drawRect(240 + i * 22, y, 1740 - i * 31, y + 2, paint);
            }
        }
        addSubtleGrain(canvas, sceneIndex);
        // A soft caption gradient belongs *inside* the artwork, not around it.
        paint.reset();
        paint.setShader(new LinearGradient(0, 900, 0, 1080,
                0x00070D18, 0xB50A101A, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 900, 1920, 1080, paint);
        paint.setShader(null);
        paint.reset();
        paint.setAntiAlias(true);
        paint.setTypeface(android.graphics.Typeface.create("sans-serif-medium", 0));
        paint.setTextSize(21f);
        paint.setColor(0xE8F0F5F4);
        canvas.drawText(ART_NAMES[sceneIndex], 66, 1024, paint);
        paint.setTextSize(14f);
        paint.setColor(0xB6E5F0ED);
        canvas.drawText("CLEAN YOUR TV  •  GALLERY", 1570, 1024, paint);
        canvas.restore();
    }
}
