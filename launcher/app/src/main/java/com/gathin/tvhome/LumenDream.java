package com.gathin.tvhome;

import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.service.dreams.DreamService;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextClock;
import android.widget.TextView;

import java.util.Collections;
import java.util.List;

/**
 * Lumen OS screensaver: the pictures of what is on your apps (the same picks as the Home shelf), full screen,
 * changing every 25 s with a slow cross-fade and drift, under a large thin clock, the date and the weather.
 * Every image is already cached by Home, so it costs little. Any key returns to where the TV was.
 */
public class LumenDream extends DreamService {
    private static final long EVERY_MS = 25_000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private ImageView a, b;
    private TextView now;
    private TextView weatherText;
    private Picks picks;
    private List<Picks.Pick> list;
    private int index;
    private boolean showingA;

    private final Runnable next = new Runnable() {
        @Override
        public void run() {
            showNext();
            handler.postDelayed(this, EVERY_MS);
        }
    };

    @Override
    protected void attachBaseContext(android.content.Context base) {
        // Same size as Home, adjusted for the TV's inches.
        super.attachBaseContext(LumenScale.wrap(base));
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        float d = getResources().getDisplayMetrics().density;
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);
        a = image();
        b = image();
        root.addView(a, match());
        root.addView(b, match());

        View shade = new View(this);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{0xF2000000, 0x99000000, 0x00000000}));
        root.addView(shade, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (300 * d),
                Gravity.BOTTOM));
        View top = new View(this);
        top.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x99000000, 0x00000000}));
        root.addView(top, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (110 * d),
                Gravity.TOP));

        TextView brand = LumenPanels.text(this, getString(R.string.brand_name), 22f, 600, 0xD9FFFFFF);
        FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START);
        bl.setMargins((int) (75 * d), (int) (36 * d), 0, 0);
        root.addView(brand, bl);

        now = LumenPanels.text(this, "", 15f, 500, 0xFFFFFFFF);
        now.setGravity(Gravity.END);
        now.setLineSpacing(0, 1.3f);
        FrameLayout.LayoutParams nl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END);
        nl.setMargins(0, (int) (36 * d), (int) (75 * d), 0);
        root.addView(now, nl);

        TextClock clock = new TextClock(this);
        clock.setFormat12Hour("h:mm");
        clock.setFormat24Hour("HH:mm");
        clock.setTextSize(TypedValue.COMPLEX_UNIT_SP, 110f);
        clock.setTypeface(LumenPanels.face(this, 200));
        clock.setTextColor(0xFFFFFFFF);
        clock.setLetterSpacing(-0.04f);
        clock.setIncludeFontPadding(false);
        FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.START);
        cl.setMargins((int) (70 * d), 0, 0, (int) (78 * d));
        root.addView(clock, cl);

        TextClock date = new TextClock(this);
        String datePattern = android.text.format.DateFormat.getBestDateTimePattern(java.util.Locale.getDefault(), "EEEEdMMMM");
        date.setFormat12Hour(datePattern);
        date.setFormat24Hour(datePattern);
        date.setTextSize(TypedValue.COMPLEX_UNIT_SP, 19f);
        date.setTypeface(LumenPanels.face(this, 300));
        date.setTextColor(0xD9FFFFFF);
        FrameLayout.LayoutParams dl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.START);
        dl.setMargins((int) (78 * d), 0, 0, (int) (52 * d));
        root.addView(date, dl);

        weatherText = LumenPanels.text(this, "", 20f, 400, 0xFFFFFFFF);
        weatherText.setShadowLayer(8 * d, 0, d, 0x80000000);
        FrameLayout.LayoutParams wl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.END);
        wl.setMargins(0, 0, (int) (75 * d), (int) (62 * d));
        root.addView(weatherText, wl);

        setContentView(root);
    }

    private ImageView image() {
        ImageView v = new ImageView(this);
        v.setScaleType(ImageView.ScaleType.CENTER_CROP);
        v.setAlpha(0f);
        return v;
    }

    private static FrameLayout.LayoutParams match() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        picks = new Picks(this, null);
        list = picks.allPicks();
        // The owner's featured titles first, then film and series stills (YouTube thumbnails carry big baked-in
        // text, which looks busy full screen, unless the owner featured that video).
        List<Picks.Pick> featured = new java.util.ArrayList<>(), stills = new java.util.ArrayList<>();
        for (Picks.Pick p : list) {
            if (p.featured) featured.add(p);
            else if (p.id == null || !p.id.startsWith("yt")) stills.add(p);
        }
        if (!featured.isEmpty() || !stills.isEmpty()) {
            Collections.shuffle(featured);
            Collections.shuffle(stills);
            featured.addAll(stills);
            list = featured;
        } else {
            Collections.shuffle(list);
        }
        index = 0;
        Weather w = new Weather(this);
        w.update(t -> weatherText.setText(t));
        handler.post(next);
    }

    @Override
    public void onDreamingStopped() {
        handler.removeCallbacksAndMessages(null);
        super.onDreamingStopped();
    }

    private void showNext() {
        if (list == null || list.isEmpty()) {
            now.setText("");
            return;
        }
        Picks.Pick p = list.get(index % list.size());
        index++;
        picks.image(p, bmp -> {
            if (bmp == null) return;
            ImageView in = showingA ? b : a;
            ImageView out = showingA ? a : b;
            showingA = !showingA;
            in.setImageBitmap(bmp);
            in.setScaleX(1.0f);
            in.setScaleY(1.0f);
            in.animate().alpha(1f).setDuration(1600).start();
            // Slow drift so nothing stays still on the panel.
            android.animation.AnimatorSet drift = new android.animation.AnimatorSet();
            drift.playTogether(android.animation.ObjectAnimator.ofFloat(in, View.SCALE_X, 1f, 1.06f),
                    android.animation.ObjectAnimator.ofFloat(in, View.SCALE_Y, 1f, 1.06f));
            drift.setDuration(EVERY_MS + 2000);
            drift.start();
            out.animate().alpha(0f).setDuration(1600).start();
            String label = Picks.label(p.pkg);
            now.setText((label.isEmpty() ? getString(R.string.lumen_now) : getString(R.string.lumen_now_on, label)).toUpperCase() + "\n" + p.title);
        });
    }
}
