package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.View;
import android.view.animation.LinearInterpolator;

/**
 * Lumen OS boot animation, drawn on one Canvas so it stays light on a TV GPU (transforms, alpha and one moving
 * gradient; no live blur). About 3.4 s:
 *   0.00 s  a beam of light opens in the centre of the black screen and folds away
 *   0.55 s  a glass emblem (violet to teal, with a play mark) pops in; a glint crosses it at 1.1 s
 *   1.00 s  the name comes in letter by letter; a gold sheen runs across it at 1.9 s
 *   2.20 s  the tagline settles in, its letters drawing together
 * An aurora glow breathes behind everything. The splash container fades it out into Home.
 */
final class LumenSplash extends View {
    static final long DURATION_MS = 3400;

    private final String name, suffix, tagline;
    private final float density;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint strong = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint light = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tag = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final Path path = new Path();
    private final Matrix m = new Matrix();
    private Bitmap aurora;
    private ValueAnimator anim;
    private float t = -1f;   // ms since start; -1 = not started (draws black)
    private boolean reduced;
    /** Optional parts (config.xml lumen_emblem, lumen_aurora): off leaves the name on plain black. */
    private final boolean showEmblem, showAurora;

    LumenSplash(Context ctx, String brand, String tagline, boolean reducedMotion) {
        super(ctx);
        showEmblem = ctx.getResources().getBoolean(R.bool.lumen_emblem);
        showAurora = ctx.getResources().getBoolean(R.bool.lumen_aurora);
        density = getResources().getDisplayMetrics().density;
        String b = brand == null ? "" : brand.trim();
        int sp = b.lastIndexOf(' ');
        if (sp > 0 && b.length() - sp <= 4) {
            name = b.substring(0, sp);
            suffix = b.substring(sp + 1);
        } else {
            name = b;
            suffix = "";
        }
        this.tagline = tagline == null ? "" : tagline.trim();
        this.reduced = reducedMotion;
        Typeface face = null;
        try {
            face = getResources().getFont(R.font.inter_tight);
        } catch (Exception ignored) {
        }
        for (Paint q : new Paint[]{strong, light, tag}) {
            if (face != null) q.setTypeface(face);
            q.setColor(0xFFFFFFFF);
        }
        strong.setFontVariationSettings("'wght' 600");
        light.setFontVariationSettings("'wght' 200");
        tag.setFontVariationSettings("'wght' 300");
        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    void start() {
        if (anim != null) anim.cancel();
        anim = ValueAnimator.ofFloat(0f, DURATION_MS);
        anim.setDuration(reduced ? 1 : DURATION_MS);
        anim.setInterpolator(new LinearInterpolator());
        anim.addUpdateListener(a -> {
            t = reduced ? DURATION_MS : (float) a.getAnimatedValue();
            invalidate();
        });
        anim.start();
    }

    /** Back to plain black (the cover shown while the screen is off), ready to play again. */
    void reset() {
        stop();
        t = -1f;
        invalidate();
    }

    void stop() {
        if (anim != null) anim.cancel();
        anim = null;
    }

    @Override
    protected void onDetachedFromWindow() {
        stop();
        super.onDetachedFromWindow();
    }

    private static float clamp(float v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }

    /** 0..1 progress of a step that starts at `from` ms and lasts `ms`. */
    private float step(float from, float ms) {
        return clamp((t - from) / ms);
    }

    private static float easeOut(float x) {
        float a = 1 - x;
        return 1 - a * a * a;
    }

    /** Ease out with a small overshoot, like the emblem popping in. */
    private static float backOut(float x) {
        float c = 1.6f;
        float y = x - 1;
        return 1 + (c + 1) * y * y * y + c * y * y;
    }

    private Bitmap aurora(int w, int h) {
        if (aurora != null) return aurora;
        int bw = 192, bh = 108;
        aurora = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(aurora);
        Paint g = new Paint(Paint.ANTI_ALIAS_FLAG);
        float[][] blobs = {{0.38f, 0.55f, 0.42f}, {0.62f, 0.45f, 0.36f}, {0.52f, 0.62f, 0.30f}};
        int[] colors = {0x8C6D5CFF, 0x6629D3C2, 0x38FFCF8A};
        for (int n = 0; n < blobs.length; n++) {
            float[] o = blobs[n];
            int col = colors[n];
            g.setShader(new RadialGradient(bw * o[0], bh * o[1], bw * o[2], col, col & 0x00FFFFFF, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, bw, bh, g);
        }
        return aurora;
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        c.drawColor(0xFF000000);
        if (t < 0 || w == 0) return;
        float cx = w / 2f, cy = h / 2f;

        // Aurora: fades up and slowly grows.
        float au = step(0, 1400);
        if (au > 0 && showAurora) {
            float s = 0.85f + 0.2f * easeOut(step(0, DURATION_MS));
            p.setShader(null);
            p.setAlpha((int) (255 * Math.min(1f, au) * (t > 1400 ? 0.9f : 1f)));
            float aw = w * 1.4f * s, ah = h * 1.4f * s;
            r.set(cx - aw / 2, cy - ah / 2, cx + aw / 2, cy + ah / 2);
            c.drawBitmap(aurora(w, h), null, r, p);
            p.setAlpha(255);
        }

        // Beam of light: opens to a third of the width by 0.6 s, then folds away by 1 s.
        if (t < 1000) {
            float k = t < 600 ? easeOut(t / 600f) : 1 - easeOut((t - 600) / 400f);
            float bw = w * 0.34f * k;
            float alpha = t < 250 ? t / 250f : (t > 600 ? 1 - (t - 600) / 400f : 1f);
            if (bw > 1) {
                float th = Math.max(1.5f, 0.0018f * w);
                p.setShader(new LinearGradient(cx - bw / 2, 0, cx + bw / 2, 0,
                        new int[]{0x00FFFFFF, 0xFFFFFFFF, 0xFFFFFFFF, 0x00FFFFFF}, new float[]{0f, 0.35f, 0.65f, 1f},
                        Shader.TileMode.CLAMP));
                p.setAlpha((int) (90 * clamp(alpha)));
                c.drawRect(cx - bw / 2, cy - th * 5, cx + bw / 2, cy + th * 5, p);   // glow
                p.setAlpha((int) (255 * clamp(alpha)));
                c.drawRect(cx - bw / 2, cy - th / 2, cx + bw / 2, cy + th / 2, p);
                p.setShader(null);
                p.setAlpha(255);
            }
        }

        // Lockup geometry: emblem + gap + name, centred as one group.
        float em = showEmblem ? w * 0.09f : 0f, gap = showEmblem ? w * 0.024f : 0f;
        float nameSize = w * 0.066f;
        strong.setTextSize(nameSize);
        light.setTextSize(nameSize);
        strong.setLetterSpacing(-0.035f);
        light.setLetterSpacing(-0.035f);
        float nameW = strong.measureText(name);
        float sufGap = suffix.isEmpty() ? 0 : nameSize * 0.25f;
        float sufW = suffix.isEmpty() ? 0 : light.measureText(suffix);
        float wordW = nameW + sufGap + sufW;
        float groupW = em + gap + wordW;
        float x0 = cx - groupW / 2;

        // Emblem.
        float ep = step(550, 900);
        if (ep > 0 && showEmblem) {
            float s = 0.6f + 0.4f * backOut(ep);
            int a = (int) (255 * clamp(ep * 1.6f));
            float ecx = x0 + em / 2, ecy = cy;
            float half = em * s / 2, rad = em * 0.27f * s;
            r.set(ecx - half, ecy - half, ecx + half, ecy + half);
            // Body: violet to deep indigo to teal.
            p.setShader(new LinearGradient(r.left, r.top, r.right, r.bottom,
                    new int[]{0xFF6D5CFF, 0xFF3B2FD6, 0xFF0F8F86, 0xFF29D3C2}, new float[]{0f, 0.45f, 0.85f, 1f},
                    Shader.TileMode.CLAMP));
            p.setAlpha(a);
            c.drawRoundRect(r, rad, rad, p);
            // Top-left highlight.
            p.setShader(new RadialGradient(r.left + r.width() * 0.2f, r.top + r.height() * 0.1f, r.width() * 0.75f,
                    0x8CFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
            p.setAlpha(a);
            c.drawRoundRect(r, rad, rad, p);
            p.setShader(null);
            // Rim.
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(1f, w * 0.0012f));
            p.setColor(0x8CFFFFFF);
            p.setAlpha((int) (140 * a / 255f));
            c.drawRoundRect(r, rad, rad, p);
            p.setStyle(Paint.Style.FILL);
            // Play mark.
            path.reset();
            float pl = r.left + r.width() * 0.38f, pt = r.top + r.height() * 0.30f;
            float pw = r.width() * 0.32f, ph = r.height() * 0.40f;
            path.moveTo(pl, pt);
            path.lineTo(pl + pw, pt + ph / 2);
            path.lineTo(pl, pt + ph);
            path.close();
            p.setColor(0xFFFFFFFF);
            p.setAlpha((int) (235 * a / 255f));
            c.drawPath(path, p);
            // Glint crossing the glass.
            float gp = step(1100, 1100);
            if (gp > 0 && gp < 1) {
                int save = c.save();
                path.reset();
                path.addRoundRect(r, rad, rad, Path.Direction.CW);
                c.clipPath(path);
                float gx = r.left - r.width() + gp * r.width() * 3;
                p.setShader(new LinearGradient(gx - r.width() * 0.25f, r.top, gx + r.width() * 0.25f, r.bottom,
                        new int[]{0x00FFFFFF, 0xA6FFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
                p.setAlpha(255);
                c.drawRect(r, p);
                p.setShader(null);
                c.restoreToCount(save);
            }
            p.setAlpha(255);
        }

        // Name, letter by letter, then the gold sheen.
        float wx = x0 + em + gap;
        float base = cy + nameSize * 0.36f;
        float sheen = step(1900, 1400);
        Shader sheenShader = new LinearGradient(0, 0, wordW, 0,
                new int[]{0xFFE9E7F5, 0xFFFFFFFF, 0xFFFFCF8A, 0xFFFFFFFF, 0xFFD9D6EA},
                new float[]{0f, 0.4f, 0.5f, 0.6f, 1f}, Shader.TileMode.CLAMP);
        m.setTranslate(wx + wordW * (1.0f - 2.0f * sheen), 0);
        sheenShader.setLocalMatrix(m);
        int i = 0;
        float x = wx;
        String all = name + (suffix.isEmpty() ? "" : "\u0000" + suffix);
        boolean inSuffix = false;
        for (int k = 0; k < all.length(); k++) {
            char ch = all.charAt(k);
            if (ch == '\u0000') {
                inSuffix = true;
                x += sufGap;
                i++;
                continue;
            }
            Paint q = inSuffix ? light : strong;
            String s = String.valueOf(ch);
            float cw = q.measureText(s);
            float lp = step(1000 + i * 55, 700);
            if (lp > 0) {
                float e = easeOut(lp);
                q.setShader(sheenShader);
                q.setAlpha((int) (255 * e));
                c.drawText(s, x, base + nameSize * 0.15f * (1 - e), q);
            }
            x += cw;
            i++;
        }

        // Tagline: letters draw together as it fades in.
        if (!tagline.isEmpty()) {
            float tp = step(2200, 900);
            if (tp > 0) {
                float e = easeOut(tp);
                tag.setTextSize(w * 0.0115f);
                tag.setLetterSpacing(0.70f - 0.28f * e);
                tag.setAlpha((int) (184 * e));
                c.drawText(tagline.toUpperCase(java.util.Locale.ROOT), wx + 2, base + nameSize * 0.62f, tag);
            }
        }

    }
}
