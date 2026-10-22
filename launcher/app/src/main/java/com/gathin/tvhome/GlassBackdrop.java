package com.gathin.tvhome;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.NonNull;

/**
 * Apple-TV-style frosted glass without a live blur (Android 11 has none): the picture behind Home is kept as a
 * tiny copy, and every glass surface draws the part of it that lies under itself, stretched (bilinear filtering
 * of a 96x54 image is the blur), then a light veil, a sheen and a bright rim on top.
 * One shared backdrop for the whole screen; the surfaces are told to redraw when it changes.
 */
final class GlassBackdrop {
    private static Bitmap small;
    private static float alpha = 1f;
    private static int screenW = 1920, screenH = 1080;
    private static final java.util.List<View> hosts = new java.util.ArrayList<>();

    private static final Paint IMG = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private static final Paint VEIL = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint SHEEN = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint RIM = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint SPEC = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Path CLIP = new Path();
    private static final RectF R = new RectF();
    private static final Rect SRC = new Rect();
    private static final RectF DST = new RectF();

    private GlassBackdrop() {
    }

    static void setScreen(int w, int h) {
        if (w > 0 && h > 0) {
            screenW = w;
            screenH = h;
        }
    }

    /** Register a view whose glass should follow the backdrop. */
    static void attach(View v) {
        if (!hosts.contains(v)) hosts.add(v);
    }

    static void detachAll() {
        hosts.clear();
    }

    /** Forget every glass host inside this view tree (a closed panel). */
    static void detachTree(View root) {
        for (int i = hosts.size() - 1; i >= 0; i--) {
            View v = hosts.get(i);
            View p = v;
            while (p != null && p != root) {
                p = p.getParent() instanceof View ? (View) p.getParent() : null;
            }
            if (p == root) hosts.remove(i);
        }
    }

    /**
     * The picture now behind Home (a pick's photo, or the softened app artwork), drawn at this alpha;
     * null for none. Shrunk to 96 px wide twice over, which averages it into a soft blur.
     */
    static void set(Bitmap source, float shownAlpha) {
        Bitmap next = null;
        if (source != null && source.getWidth() > 0 && source.getHeight() > 0) {
            int w = 96, h = Math.max(1, Math.round(96f * source.getHeight() / source.getWidth()));
            if (source.getWidth() <= w) {
                next = source;
            } else {
                Bitmap mid = Bitmap.createScaledBitmap(source, w * 3, h * 3, true);
                Bitmap tiny = Bitmap.createScaledBitmap(mid, w, h, true);
                if (mid != tiny) mid.recycle();
                // Back up 4x with bilinear filtering on the CPU: a large panel (the weather) stretches this over
                // the whole screen, and some GPUs draw that stretch without smoothing, in visible squares.
                next = Bitmap.createScaledBitmap(tiny, w * 4, h * 4, true);
                if (tiny != next) tiny.recycle();
            }
        }
        small = next;
        alpha = shownAlpha;
        for (View v : hosts) v.invalidate();
    }

    /**
     * Draws a glass slab at rect (in the canvas of a view whose top-left sits at originX/originY on screen).
     * @param strong a stronger veil, for small pills that carry text
     */
    static void paint(Canvas c, RectF rect, float radius, float originX, float originY, float density, boolean strong) {
        int save = c.save();
        CLIP.reset();
        CLIP.addRoundRect(rect, radius, radius, Path.Direction.CW);
        c.clipPath(CLIP);
        Bitmap b = small;
        if (b != null && !b.isRecycled()) {
            // The same centre-crop the backdrop ImageView uses, in this canvas's coordinates.
            float scale = Math.max((float) screenW / b.getWidth(), (float) screenH / b.getHeight());
            float dw = b.getWidth() * scale, dh = b.getHeight() * scale;
            float left = (screenW - dw) / 2f - originX, top = (screenH - dh) / 2f - originY;
            SRC.set(0, 0, b.getWidth(), b.getHeight());
            DST.set(left, top, left + dw, top + dh);
            c.drawColor(0xFF0B0B0F);
            IMG.setAlpha(Math.round(255 * Math.max(0f, Math.min(1f, alpha))));
            c.drawBitmap(b, SRC, DST, IMG);
            VEIL.setColor(strong ? 0x40FFFFFF : 0x2EFFFFFF);
        } else {
            VEIL.setColor(strong ? 0x4DFFFFFF : 0x38FFFFFF);
        }
        c.drawRect(rect, VEIL);
        // Sheen: brighter at the top, like light on a curved pane.
        SHEEN.setShader(new LinearGradient(0, rect.top, 0, rect.bottom,
                new int[]{0x40FFFFFF, 0x0DFFFFFF, 0x00FFFFFF, 0x14FFFFFF}, new float[]{0f, 0.35f, 0.7f, 1f},
                Shader.TileMode.CLAMP));
        c.drawRect(rect, SHEEN);
        c.restoreToCount(save);
        // Rim: a crisp light edge, brightest along the top.
        RIM.setStyle(Paint.Style.STROKE);
        RIM.setStrokeWidth(Math.max(1f, 1.1f * density));
        RIM.setShader(new LinearGradient(0, rect.top, 0, rect.bottom,
                new int[]{0xB3FFFFFF, 0x40FFFFFF, 0x59FFFFFF}, new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        float inset = RIM.getStrokeWidth() / 2f;
        R.set(rect.left + inset, rect.top + inset, rect.right - inset, rect.bottom - inset);
        c.drawRoundRect(R, radius - inset, radius - inset, RIM);
        // Specular glint along the top edge.
        SPEC.setStyle(Paint.Style.STROKE);
        SPEC.setStrokeWidth(Math.max(1f, 0.8f * density));
        SPEC.setShader(new LinearGradient(rect.left, 0, rect.right, 0,
                new int[]{0x00FFFFFF, 0xCCFFFFFF, 0x00FFFFFF}, new float[]{0.08f, 0.3f, 0.65f}, Shader.TileMode.CLAMP));
        c.drawLine(rect.left + radius, rect.top + 1.2f * density, rect.right - radius, rect.top + 1.2f * density, SPEC);
    }

    /** A view background that is a frosted glass pill over the backdrop. */
    static final class Pill extends Drawable {
        private final View host;
        private final float radius, density;
        private final RectF rect = new RectF();
        private final int[] loc = new int[2];

        Pill(View host, float radiusPx, float density) {
            this.host = host;
            this.radius = radiusPx;
            this.density = density;
            attach(host);
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            Rect b = getBounds();
            if (b.isEmpty()) return;
            host.getLocationOnScreen(loc);
            rect.set(b);
            float r = Math.min(radius, rect.height() / 2f);
            paint(canvas, rect, r, loc[0] - host.getScrollX(), loc[1] - host.getScrollY(), density, true);
        }

        @Override
        public void setAlpha(int a) {
        }

        @Override
        public void setColorFilter(ColorFilter cf) {
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
