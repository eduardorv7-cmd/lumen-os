package com.gathin.tvhome;

import android.content.Context;
import android.content.res.Configuration;
import android.util.DisplayMetrics;

/**
 * Screen-size scaling. Android TVs report the same density whatever the panel size, so a 32" and an 85" TV
 * would show the interface at the same proportion of the screen. People sit further from a bigger TV, but not in
 * proportion, so Lumen OS grows the interface a little on small panels and shrinks it a little on big ones.
 * The size comes from "pulgadas" in lumen.properties (tv_inches); 55" is the reference.
 */
final class LumenScale {
    static final int REFERENCE_INCHES = 55;

    private LumenScale() {
    }

    /** 32" 1.15, 43" 1.06, 55" 1.00, 65" 0.96, 75" 0.93, 85" 0.90, 98" and up 0.87. */
    static float factor(Context c) {
        int in = c.getResources().getInteger(R.integer.tv_inches);
        if (in < 19 || in > 150) return 1f;
        float f = (float) Math.pow(REFERENCE_INCHES / (double) in, 0.25);
        return Math.max(0.85f, Math.min(1.2f, f));
    }

    /**
     * The configuration that makes the screen design_width_dp wide (divided by the size factor), or null when the
     * TV already matches. Applied before any resource is read.
     */
    static Configuration config(Context base) {
        int designWidthDp = base.getResources().getInteger(R.integer.design_width_dp);
        if (designWidthDp <= 0) return null;
        designWidthDp = Math.round(designWidthDp / factor(base));
        DisplayMetrics dm = base.getResources().getDisplayMetrics();
        int widthPx = Math.max(dm.widthPixels, dm.heightPixels), heightPx = Math.min(dm.widthPixels, dm.heightPixels);
        int dpi = Math.round(widthPx * 160f / designWidthDp);
        if (Math.abs(dpi - dm.densityDpi) <= 1) return null;
        Configuration c = new Configuration();
        c.densityDpi = dpi;
        c.screenWidthDp = designWidthDp;
        c.screenHeightDp = Math.round(heightPx * 160f / dpi);
        c.smallestScreenWidthDp = c.screenHeightDp;
        return c;
    }

    /** For a Service such as the screensaver, which has no applyOverrideConfiguration. */
    static Context wrap(Context base) {
        Configuration c = config(base);
        return c == null ? base : base.createConfigurationContext(c);
    }
}
