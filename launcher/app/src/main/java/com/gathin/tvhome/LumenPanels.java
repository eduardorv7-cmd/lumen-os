package com.gathin.tvhome;

import android.app.Activity;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Lumen OS panels, each a full-screen translucent dialog drawn in the same glass as Home:
 * the detail card of a pick (with its trailer and "Mi lista"), the "Mi lista" shelf and the hour-by-hour
 * weather. Back closes any of them.
 */
final class LumenPanels {
    private LumenPanels() {
    }

    // ---- shared look ---------------------------------------------------------------------------

    static float dp(Context c) {
        return c.getResources().getDisplayMetrics().density;
    }

    static Typeface face(Context c, int weight) {
        try {
            Typeface base = c.getResources().getFont(R.font.inter_tight);
            if (android.os.Build.VERSION.SDK_INT >= 28) return Typeface.create(base, weight, false);
            return base;
        } catch (Exception e) {
            return Typeface.DEFAULT;
        }
    }

    static TextView text(Context c, String s, float sp, int weight, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTypeface(face(c, weight));
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        return t;
    }

    /** A focusable glass button: frosted at rest, white with dark text when focused (like Settings). */
    static TextView button(Context c, String label) {
        float d = dp(c);
        TextView b = text(c, label, 17f, 600, 0xFFFFFFFF);
        b.setFocusable(true);
        b.setClickable(true);
        b.setGravity(Gravity.CENTER);
        b.setSingleLine(true);
        b.setPadding((int) (22 * d), 0, (int) (22 * d), 0);
        b.setMinHeight((int) (46 * d));
        StateListDrawable bg = new StateListDrawable();
        GradientDrawable on = new GradientDrawable();
        on.setColor(0xFFFFFFFF);
        on.setCornerRadius(14 * d);
        bg.addState(new int[]{android.R.attr.state_focused}, on);
        bg.addState(new int[0], new GlassBackdrop.Pill(b, 14 * d, d));
        b.setBackground(bg);
        b.setTextColor(new ColorStateList(new int[][]{{android.R.attr.state_focused}, {}},
                new int[]{0xFF111114, 0xFFFFFFFF}));
        b.setOnFocusChangeListener((v, f) -> v.animate().scaleX(f ? 1.06f : 1f).scaleY(f ? 1.06f : 1f)
                .setDuration(140).start());
        return b;
    }

    static Dialog dialog(Activity a, View content) {
        Dialog d = new Dialog(a, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen);
        d.setContentView(content);
        Window w = d.getWindow();
        if (w != null) {
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            w.setDimAmount(0f);
        }
        d.setOnDismissListener(x -> GlassBackdrop.detachTree(content));
        return d;
    }

    static void open(Activity a, Intent i) {
        try {
            a.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(a, a.getString(R.string.lumen_cant_open), Toast.LENGTH_SHORT).show();
        }
    }

    static void openApp(Activity a, String pkg) {
        if (pkg == null) return;
        Intent i = a.getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) i = a.getPackageManager().getLaunchIntentForPackage(pkg);
        if (i != null) open(a, i);
    }

    static void openYouTube(Activity a, String videoId) {
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=" + videoId));
        i.setPackage("com.google.android.youtube.tv");
        try {
            a.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException e) {
            open(a, new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=" + videoId)));
        }
    }

    // ---- Mi lista ------------------------------------------------------------------------------

    static final class MyList {
        private static SharedPreferences prefs(Context c) {
            return c.getSharedPreferences("milista", Context.MODE_PRIVATE);
        }

        static List<Picks.Pick> load(Context c) {
            List<Picks.Pick> out = new ArrayList<>();
            try {
                JSONArray a = new JSONArray(prefs(c).getString("items", "[]"));
                for (int i = 0; i < a.length(); i++) {
                    JSONObject o = a.getJSONObject(i);
                    Picks.Pick p = new Picks.Pick();
                    p.id = o.optString("id");
                    p.title = o.optString("t");
                    p.backdrop = o.optString("b");
                    p.meta = o.optString("m");
                    p.pkg = o.optString("p");
                    out.add(p);
                }
            } catch (Exception ignored) {
            }
            return out;
        }

        static boolean contains(Context c, String id) {
            for (Picks.Pick p : load(c)) if (p.id.equals(id)) return true;
            return false;
        }

        /** Adds or removes; returns true when the pick is now in the list. */
        static boolean toggle(Context c, Picks.Pick pick) {
            List<Picks.Pick> l = load(c);
            boolean removed = false;
            for (int i = l.size() - 1; i >= 0; i--) {
                if (l.get(i).id.equals(pick.id)) {
                    l.remove(i);
                    removed = true;
                }
            }
            if (!removed) l.add(0, pick);
            JSONArray a = new JSONArray();
            try {
                for (Picks.Pick p : l) {
                    a.put(new JSONObject().put("id", p.id).put("t", p.title).put("b", p.backdrop)
                            .put("m", p.meta).put("p", p.pkg));
                }
            } catch (Exception ignored) {
            }
            prefs(c).edit().putString("items", a.toString()).apply();
            return !removed;
        }
    }

    // ---- detail card ---------------------------------------------------------------------------

    /** The detail card for a pick: overview, genres, cast, and Watch / Trailer / Mi lista. */
    static void showDetail(Activity a, Picks picks, Picks.Pick pick, Runnable listChanged) {
        float d = dp(a);
        FrameLayout root = new FrameLayout(a);
        // Its own full-screen picture, so the card reads the same opened from the dock or from the app grid.
        root.setBackgroundColor(0xFF050508);
        android.widget.ImageView art = new android.widget.ImageView(a);
        art.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
        art.setAlpha(0f);
        root.addView(art, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        picks.image(pick, bmp -> {
            if (bmp == null) return;
            art.setImageBitmap(bmp);
            art.animate().alpha(1f).setDuration(350).start();
        });
        View side = new View(a);
        side.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xF5050508, 0xE6050508, 0x99050508, 0x00050508}));
        root.addView(side, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        View bottom = new View(a);
        bottom.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{0xF2050508, 0x00050508}));
        root.addView(bottom, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (220 * d),
                Gravity.BOTTOM));

        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams((int) (540 * d), ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.START | Gravity.CENTER_VERTICAL);
        clp.leftMargin = (int) (75 * d);
        root.addView(col, clp);

        TextView title = text(a, pick.title, 46f, 600, 0xFFFFFFFF);
        title.setLetterSpacing(-0.02f);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        col.addView(title);

        LinearLayout chips = new LinearLayout(a);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams chlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        chlp.topMargin = (int) (14 * d);
        col.addView(chips, chlp);
        String[] parts = pick.meta == null ? new String[0] : pick.meta.split("\\s+·\\s+");
        for (int i = 0; i < parts.length; i++) addChip(a, chips, parts[i].trim(), i == 0);

        TextView overview = text(a, a.getString(R.string.lumen_loading), 17f, 400, 0xE0FFFFFF);
        overview.setLineSpacing(0, 1.3f);
        overview.setMaxLines(6);
        overview.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        olp.topMargin = (int) (18 * d);
        col.addView(overview, olp);

        TextView cast = text(a, "", 13f, 400, 0x99FFFFFF);
        LinearLayout.LayoutParams calp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        calp.topMargin = (int) (12 * d);
        col.addView(cast, calp);

        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = (int) (26 * d);
        col.setClipChildren(false);
        col.addView(row, rlp);

        boolean yt = pick.id != null && pick.id.startsWith("yt");
        String label = Picks.label(pick.pkg);
        TextView watch = button(a, "▶  " + a.getString(R.string.lumen_watch_on, label.isEmpty() ? a.getString(R.string.lumen_the_app) : label));
        TextView trailer = button(a, a.getString(R.string.lumen_trailer));
        trailer.setVisibility(View.GONE);
        TextView mine = button(a, MyList.contains(a, pick.id) ? "✓  " + a.getString(R.string.lumen_in_my_list) : "＋  " + a.getString(R.string.lumen_my_list));
        addButton(row, watch, 0, d);
        addButton(row, trailer, 12, d);
        addButton(row, mine, 12, d);

        Dialog dlg = dialog(a, root);
        watch.setOnClickListener(v -> {
            dlg.dismiss();
            if (yt) openYouTube(a, pick.id.substring(2));
            else openApp(a, pick.pkg);
        });
        mine.setOnClickListener(v -> {
            boolean now = MyList.toggle(a, pick);
            mine.setText(now ? "✓  " + a.getString(R.string.lumen_in_my_list) : "＋  " + a.getString(R.string.lumen_my_list));
            Toast.makeText(a, a.getString(now ? R.string.lumen_added : R.string.lumen_removed), Toast.LENGTH_SHORT).show();
            if (listChanged != null) listChanged.run();
        });

        if (yt) {
            String m = pick.meta == null ? "" : pick.meta;
            int at = m.lastIndexOf("·");
            overview.setText(at >= 0 ? a.getString(R.string.lumen_yt_overview, m.substring(at + 1).trim()) : a.getString(R.string.lumen_yt_overview_plain));
        } else {
            picks.details(pick, j -> {
                if (j == null) {
                    overview.setText(a.getString(R.string.lumen_overview_failed));
                    return;
                }
                String ov = j.optString("overview", "").trim();
                overview.setText(ov.isEmpty() ? a.getString(R.string.lumen_no_overview) : ov);
                JSONArray genres = j.optJSONArray("genres");
                if (genres != null) {
                    StringBuilder g = new StringBuilder();
                    for (int i = 0; i < Math.min(2, genres.length()); i++) {
                        if (g.length() > 0) g.append(" · ");
                        g.append(genres.optJSONObject(i).optString("name"));
                    }
                    if (g.length() > 0) addChip(a, chips, g.toString(), false);
                }
                if ("tv".equals(j.optString("_kind"))) {
                    int seasons = j.optInt("number_of_seasons", 0);
                    if (seasons > 0) addChip(a, chips, a.getResources().getQuantityString(R.plurals.lumen_seasons, seasons, seasons), false);
                } else {
                    int min = j.optInt("runtime", 0);
                    if (min > 0) addChip(a, chips, (min / 60) + " h " + (min % 60) + " min", false);
                }
                JSONObject credits = j.optJSONObject("credits");
                JSONArray castArr = credits == null ? null : credits.optJSONArray("cast");
                if (castArr != null && castArr.length() > 0) {
                    StringBuilder cs = new StringBuilder();
                    for (int i = 0; i < Math.min(4, castArr.length()); i++) {
                        if (i > 0) cs.append(", ");
                        cs.append(castArr.optJSONObject(i).optString("name"));
                    }
                    cast.setText(a.getString(R.string.lumen_with, cs.toString()));
                }
                String key = trailerKey(j);
                if (key != null) {
                    trailer.setVisibility(View.VISIBLE);
                    trailer.setOnClickListener(v -> {
                        dlg.dismiss();
                        openYouTube(a, key);
                    });
                }
            });
        }
        dlg.show();
        watch.requestFocus();
    }

    private static String trailerKey(JSONObject j) {
        JSONObject videos = j.optJSONObject("videos");
        JSONArray r = videos == null ? null : videos.optJSONArray("results");
        if (r == null) return null;
        String best = null;
        int bestScore = -1;
        for (int i = 0; i < r.length(); i++) {
            JSONObject v = r.optJSONObject(i);
            if (v == null || !"YouTube".equals(v.optString("site"))) continue;
            int score = 0;
            if ("Trailer".equals(v.optString("type"))) score += 4;
            else if ("Teaser".equals(v.optString("type"))) score += 2;
            if ("es".equals(v.optString("iso_639_1"))) score += 3;
            if (v.optBoolean("official")) score += 1;
            if (score > bestScore) {
                bestScore = score;
                best = v.optString("key");
            }
        }
        return best;
    }

    private static void addChip(Context c, LinearLayout chips, String s, boolean accent) {
        if (s.isEmpty()) return;
        float d = dp(c);
        TextView t = text(c, s, 13f, accent ? 600 : 500, 0xFFFFFFFF);
        t.setPadding((int) (10 * d), (int) (5 * d), (int) (10 * d), (int) (5 * d));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(7 * d);
        bg.setColor(accent ? 0x40FFFFFF : 0x24FFFFFF);
        bg.setStroke(Math.max(1, (int) d), 0x4DFFFFFF);
        t.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        if (chips.getChildCount() > 0) lp.leftMargin = (int) (7 * d);
        chips.addView(t, lp);
    }

    private static void addButton(LinearLayout row, View b, int leftDp, float d) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                (int) (46 * d));
        lp.leftMargin = (int) (leftDp * d);
        row.addView(b, lp);
    }

    // ---- Mi lista shelf ------------------------------------------------------------------------

    static void showMyList(Activity a, Picks picks, Runnable listChanged) {
        float d = dp(a);
        FrameLayout root = new FrameLayout(a);
        root.setBackgroundColor(0xB3050508);
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setClipChildren(false);
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_VERTICAL);
        clp.leftMargin = (int) (75 * d);
        root.addView(col, clp);
        col.addView(text(a, a.getString(R.string.lumen_my_list), 34f, 600, 0xFFFFFFFF));
        List<Picks.Pick> items = MyList.load(a);
        Dialog dlg = dialog(a, root);
        if (items.isEmpty()) {
            TextView empty = text(a, a.getString(R.string.lumen_list_empty),
                    17f, 400, 0xCCFFFFFF);
            empty.setFocusable(true);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = (int) (16 * d);
            col.addView(empty, lp);
            empty.setOnClickListener(v -> dlg.dismiss());
            dlg.show();
            empty.requestFocus();
            return;
        }
        HorizontalScrollView scroll = new HorizontalScrollView(a);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setClipChildren(false);
        scroll.setClipToPadding(false);
        scroll.setPadding(0, (int) (14 * d), (int) (75 * d), (int) (14 * d));
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        scroll.addView(row);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = (int) (10 * d);
        col.addView(scroll, slp);
        View first = null;
        for (Picks.Pick p : items) {
            FrameLayout card = new FrameLayout(a);
            card.setFocusable(true);
            card.setClickable(true);
            GradientDrawable clip = new GradientDrawable();
            clip.setCornerRadius(14 * d);
            clip.setColor(0xFF1C1C22);
            card.setBackground(clip);
            card.setClipToOutline(true);
            ImageView img = new ImageView(a);
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            card.addView(img, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            View shade = new View(a);
            shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                    new int[]{0xE6000000, 0x00000000}));
            card.addView(shade, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (70 * d),
                    Gravity.BOTTOM));
            TextView t = text(a, p.title, 15f, 600, 0xFFFFFFFF);
            t.setSingleLine(true);
            t.setEllipsize(TextUtils.TruncateAt.END);
            FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
            tlp.setMargins((int) (12 * d), 0, (int) (12 * d), (int) (10 * d));
            card.addView(t, tlp);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams((int) (230 * d), (int) (130 * d));
            if (row.getChildCount() > 0) lp.leftMargin = (int) (18 * d);
            row.addView(card, lp);
            card.setOnFocusChangeListener((v, f) -> {
                v.animate().scaleX(f ? 1.08f : 1f).scaleY(f ? 1.08f : 1f).setDuration(150).start();
                v.setForeground(f ? focusRing(d) : null);
            });
            card.setOnClickListener(v -> {
                dlg.dismiss();
                showDetail(a, picks, p, listChanged);
            });
            if (picks != null && p.backdrop != null && !p.backdrop.isEmpty()) picks.image(p, bmp -> {
                if (bmp != null) img.setImageBitmap(bmp);
            });
            if (first == null) first = card;
        }
        dlg.show();
        if (first != null) first.requestFocus();
    }

    private static GradientDrawable focusRing(float d) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(14 * d);
        g.setStroke((int) (3 * d), 0xFFFFFFFF);
        g.setColor(0x00000000);
        return g;
    }

    // ---- weather -------------------------------------------------------------------------------

    static void showWeather(Activity a, Weather w) {
        float d = dp(a);
        FrameLayout root = new FrameLayout(a);
        root.setBackgroundColor(0x59000000);
        LinearLayout panel = new LinearLayout(a);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding((int) (24 * d), (int) (22 * d), (int) (24 * d), (int) (22 * d));
        panel.setBackground(new GlassBackdrop.Pill(panel, 26 * d, d));
        panel.setFocusable(true);
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams((int) (540 * d), ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.END);
        plp.topMargin = (int) (72 * d);
        plp.rightMargin = (int) (74 * d);
        root.addView(panel, plp);

        LinearLayout head = new LinearLayout(a);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView big = text(a, "…", 54f, 300, 0xFFFFFFFF);
        TextView desc = text(a, w.city() + "\n" + a.getString(R.string.lumen_forecast_loading), 15f, 500, 0xFFFFFFFF);
        desc.setLineSpacing(0, 1.35f);
        head.addView(big);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        dlp.leftMargin = (int) (16 * d);
        head.addView(desc, dlp);
        panel.addView(head);

        LinearLayout hours = new LinearLayout(a);
        hours.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        hlp.topMargin = (int) (18 * d);
        panel.addView(hours, hlp);

        View line = new View(a);
        line.setBackgroundColor(0x40FFFFFF);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                Math.max(1, (int) d));
        llp.topMargin = (int) (16 * d);
        panel.addView(line, llp);

        LinearLayout stats = new LinearLayout(a);
        stats.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams stlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        stlp.topMargin = (int) (14 * d);
        panel.addView(stats, stlp);

        Dialog dlg = dialog(a, root);
        panel.setOnClickListener(v -> dlg.dismiss());
        dlg.show();
        panel.requestFocus();

        w.detail(j -> {
            if (j == null) {
                desc.setText(w.city() + "\n" + a.getString(R.string.lumen_forecast_failed));
                return;
            }
            try {
                JSONObject cur = j.getJSONObject("current");
                int code = cur.optInt("weather_code");
                boolean day = cur.optInt("is_day", 1) == 1;
                big.setText(Weather.icon(code, day) + " " + Math.round(cur.optDouble("temperature_2m")) + "°");
                JSONObject daily = j.getJSONObject("daily");
                int hi = (int) Math.round(daily.getJSONArray("temperature_2m_max").getDouble(0));
                int lo = (int) Math.round(daily.getJSONArray("temperature_2m_min").getDouble(0));
                desc.setText(w.city() + " · " + Weather.describe(a, code) + "\n" + a.getString(R.string.lumen_feels,
                        (int) Math.round(cur.optDouble("apparent_temperature")), hi, lo));
                JSONObject h = j.getJSONObject("hourly");
                JSONArray time = h.getJSONArray("time");
                JSONArray temp = h.getJSONArray("temperature_2m");
                JSONArray hc = h.getJSONArray("weather_code");
                JSONArray hd = h.optJSONArray("is_day");
                for (int i = 0; i < Math.min(7, time.length()); i++) {
                    String hh = i == 0 ? a.getString(R.string.lumen_now) : time.getString(i).substring(11, 16);
                    boolean hdDay = hd == null || hd.optInt(i, 1) == 1;
                    TextView cell = text(a, hh + "\n" + Weather.icon(hc.optInt(i), hdDay) + "\n"
                            + Math.round(temp.optDouble(i)) + "°", 14f, 500, 0xFFFFFFFF);
                    cell.setGravity(Gravity.CENTER);
                    cell.setLineSpacing(0, 1.45f);
                    cell.setPadding(0, (int) (8 * d), 0, (int) (8 * d));
                    if (i == 0) {
                        GradientDrawable g = new GradientDrawable();
                        g.setCornerRadius(12 * d);
                        g.setColor(0x2EFFFFFF);
                        cell.setBackground(g);
                    }
                    hours.addView(cell, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                }
                JSONArray rain = daily.optJSONArray("precipitation_probability_max");
                stat(a, stats, a.getString(R.string.lumen_rain), (rain == null ? "–" : rain.optInt(0) + " %"));
                stat(a, stats, a.getString(R.string.lumen_wind), Math.round(cur.optDouble("wind_speed_10m")) + (Weather.imperial() ? " mph" : " km/h"));
                stat(a, stats, a.getString(R.string.lumen_sunrise), hm(daily.getJSONArray("sunrise").optString(0)));
                stat(a, stats, a.getString(R.string.lumen_sunset), hm(daily.getJSONArray("sunset").optString(0)));
                stat(a, stats, a.getString(R.string.lumen_tomorrow), Math.round(daily.getJSONArray("temperature_2m_max").optDouble(1)) + "° / "
                        + Math.round(daily.getJSONArray("temperature_2m_min").optDouble(1)) + "°");
            } catch (Exception e) {
                desc.setText(w.city() + "\n" + a.getString(R.string.lumen_forecast_partial));
            }
        });
    }

    private static String hm(String iso) {
        return iso != null && iso.length() >= 16 ? iso.substring(11, 16) : "–";
    }

    private static void stat(Context c, LinearLayout stats, String label, String value) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(text(c, label, 12f, 500, 0xCCFFFFFF));
        TextView v = text(c, value, 16f, 600, 0xFFFFFFFF);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = (int) (4 * dp(c));
        box.addView(v, lp);
        stats.addView(box, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    }
}
