package com.gathin.tvhome;

import android.net.Uri;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Apple-TV-style top shelf picks: for each streaming app in the dock, what is popular right now and what just
 * came out on that service in the owner's country, from TMDB (themoviedb.org, which knows what streams where).
 * Each time the focus lands on an app, its next pick is shown, full screen, in high resolution.
 * Needs the owner's own free TMDB key (config.xml tmdb_key); without one this class does nothing and the shelf
 * keeps the softened app artwork. The key never goes to the log.
 */
final class Picks {

    static final class Pick {
        String id, title, backdrop, meta, pkg;
        /** Chosen by the owner in lumen.properties (destacados): shown first, also in the screensaver. */
        boolean featured;
    }

    /** Every saved pick of every app, for the screensaver. */
    List<Pick> allPicks() {
        List<Pick> out = new ArrayList<>();
        for (Map.Entry<String, List<Pick>> e : lists.entrySet()) {
            for (Pick p : e.getValue()) {
                if (p.pkg == null) p.pkg = e.getKey();
                out.add(p);
            }
        }
        return out;
    }

    static String label(String pkg) {
        String l = SERVICE_LABEL.get(pkg);
        return l != null ? l : (YOUTUBE.equals(pkg) ? "YouTube" : "");
    }

    /**
     * The detail card's data from TMDB (overview, genres, runtime or seasons, cast, trailer), on the main thread;
     * null when it could not be loaded. YouTube picks have no TMDB entry.
     */
    void details(Pick p, java.util.function.Consumer<JSONObject> done) {
        if (p == null || p.id == null || p.id.startsWith("yt") || key.isEmpty()) {
            done.accept(null);
            return;
        }
        String kind = p.id.startsWith("movie") ? "movie" : "tv";
        String id = p.id.substring(kind.length());
        io.execute(() -> {
            JSONObject j = null;
            try {
                j = new JSONObject(new String(get(API + "/" + kind + "/" + id + "?language=" + language
                        + "&append_to_response=credits,videos&include_video_language=es,en,null", true), "UTF-8"));
                j.put("_kind", kind);
            } catch (Exception e) {
                android.util.Log.w("Picks", "details failed: " + e.getClass().getSimpleName());
            }
            final JSONObject r = j;
            main.post(() -> done.accept(r));
        });
    }

    interface Listener { void onPicksChanged(); }

    /** Package -> TMDB provider names (lowercase, first exact match wins) and fallback ids. */
    private static final Object[][] SERVICES = {
        {"com.netflix.ninja", new String[]{"netflix"}, 8},
        {"com.disney.disneyplus", new String[]{"disney plus", "disney+"}, 337},
        {"com.wbd.stream", new String[]{"max", "hbo max"}, 1899},
        {"com.apple.atve.androidtv.appletv", new String[]{"apple tv+", "apple tv plus"}, 350},
        {"com.amazon.amazonvideo.livingroom", new String[]{"amazon prime video", "prime video"}, 119},
        {"com.crunchyroll.crunchyroid", new String[]{"crunchyroll"}, 283},
        {"com.directv.dtvlatam", new String[]{"directv go", "dgo"}, -1},
        {"tv.pluto.android", new String[]{"pluto tv"}, 300},
        {"com.tubitv", new String[]{"tubi tv", "tubi"}, 73},
        {"com.paramount.android.pplus", new String[]{"paramount plus", "paramount+"}, 531},
        {"com.cbs.ott", new String[]{"paramount plus", "paramount+"}, 531},
        {"com.univision.prendetv", new String[]{"vix", "vix premium"}, -1},
        {"com.mubi", new String[]{"mubi"}, 11},
        {"com.hbo.hbonow", new String[]{"max", "hbo max"}, 1899},
    };
    private static final String YOUTUBE = "com.google.android.youtube.tv";
    /** Short names for lumen.properties (destacados=netflix:tv/1408 ...). A full package name works too. */
    private static final Map<String, String> ALIASES = new HashMap<>();
    static {
        ALIASES.put("appletv", "com.apple.atve.androidtv.appletv");
        ALIASES.put("netflix", "com.netflix.ninja");
        ALIASES.put("youtube", YOUTUBE);
        ALIASES.put("hbo", "com.wbd.stream");
        ALIASES.put("hbomax", "com.wbd.stream");
        ALIASES.put("max", "com.wbd.stream");
        ALIASES.put("disney", "com.disney.disneyplus");
        ALIASES.put("crunchyroll", "com.crunchyroll.crunchyroid");
        ALIASES.put("prime", "com.amazon.amazonvideo.livingroom");
        ALIASES.put("pluto", "tv.pluto.android");
        ALIASES.put("tubi", "com.tubitv");
        ALIASES.put("paramount", "com.paramount.android.pplus");
        ALIASES.put("mubi", "com.mubi");
        ALIASES.put("dgo", "com.directv.dtvlatam");
        ALIASES.put("vix", "com.univision.prendetv");
    }
    private static final Map<String, String> SERVICE_LABEL = new HashMap<>();
    static {
        SERVICE_LABEL.put("com.netflix.ninja", "Netflix");
        SERVICE_LABEL.put("com.disney.disneyplus", "Disney+");
        SERVICE_LABEL.put("com.wbd.stream", "HBO Max");
        SERVICE_LABEL.put("com.apple.atve.androidtv.appletv", "Apple TV");
        SERVICE_LABEL.put("com.amazon.amazonvideo.livingroom", "Prime Video");
        SERVICE_LABEL.put("com.crunchyroll.crunchyroid", "Crunchyroll");
        SERVICE_LABEL.put("com.directv.dtvlatam", "DGO");
        SERVICE_LABEL.put("tv.pluto.android", "Pluto TV");
        SERVICE_LABEL.put("com.tubitv", "Tubi");
        SERVICE_LABEL.put("com.paramount.android.pplus", "Paramount+");
        SERVICE_LABEL.put("com.cbs.ott", "Paramount+");
        SERVICE_LABEL.put("com.univision.prendetv", "ViX");
        SERVICE_LABEL.put("com.mubi", "MUBI");
        SERVICE_LABEL.put("com.hbo.hbonow", "HBO Max");
    }

    private static final String API = "https://api.themoviedb.org/3";
    private static final String IMG = "https://image.tmdb.org/t/p/w1280";
    private static final long REFRESH_MS = 3 * 3600_000L;
    private static final int PER_APP = 16;
    private static final int MEM_IMAGES = 4;

    private final Context ctx;
    private final String key, region, language, ytKey;
    private long lastAttempt;
    private final SharedPreferences prefs;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final ExecutorService img = Executors.newSingleThreadExecutor();
    private final Listener listener;
    private final Map<String, List<Pick>> lists = new HashMap<>();
    private final Map<String, Integer> index = new HashMap<>();
    private final LinkedHashMap<String, Bitmap> memory = new LinkedHashMap<>(8, 0.75f, true);
    private final Set<String> loading = new HashSet<>();
    /** Apps already requested since Home started. */
    private final Set<String> attempted = new HashSet<>();
    /** Package -> the owner's featured titles ("tv/1408", "movie/550" or a YouTube video id), in order. */
    private final Map<String, List<String>> featuredRefs = new java.util.LinkedHashMap<>();
    private final String featuredSig;
    private volatile boolean fetching;

    Picks(Context ctx, Listener listener) {
        this.ctx = ctx.getApplicationContext();
        this.listener = listener;
        this.key = ctx.getString(R.string.tmdb_key).trim();
        String reg = ctx.getString(R.string.tmdb_region).trim().toUpperCase(Locale.ROOT);
        // Empty or AUTO: the country set on the TV (Settings > Language/Region), else the US catalogue.
        if (reg.isEmpty() || reg.equals("AUTO")) reg = Locale.getDefault().getCountry().toUpperCase(Locale.ROOT);
        this.region = reg.length() == 2 ? reg : "US";
        this.language = tmdbLanguage(ctx.getString(R.string.tmdb_language).trim());
        this.ytKey = ctx.getString(R.string.youtube_key).trim();
        this.prefs = ctx.getSharedPreferences("picks", Context.MODE_PRIVATE);
        String f = ctx.getString(R.string.featured_picks).trim();
        featuredSig = f;
        for (String tok : f.split("[\\s,;]+")) {
            int c = tok.indexOf(':');
            if (c <= 0 || c == tok.length() - 1) continue;
            String app = tok.substring(0, c).toLowerCase(Locale.ROOT);
            String pkg = app.contains(".") ? app : ALIASES.get(app);
            if (pkg == null) continue;
            List<String> refs = featuredRefs.get(pkg);
            if (refs == null) featuredRefs.put(pkg, refs = new ArrayList<>());
            refs.add(tok.substring(c + 1));
        }
        if (enabled()) loadSaved();
    }

    boolean enabled() {
        return !key.isEmpty();
    }

    /**
     * "auto" follows the TV's language: Spanish as Latin American (es-MX) unless the TV is set to Spain,
     * English as en-US, Portuguese as pt-BR unless Portugal; anything else as its own language and country.
     */
    static String tmdbLanguage(String configured) {
        if (!configured.isEmpty() && !configured.equalsIgnoreCase("auto")) return configured;
        Locale l = Locale.getDefault();
        String lang = l.getLanguage(), cc = l.getCountry();
        if (lang.equals("es")) return cc.equals("ES") ? "es-ES" : "es-MX";
        if (lang.equals("en")) return cc.equals("GB") ? "en-GB" : "en-US";
        if (lang.equals("pt")) return cc.equals("PT") ? "pt-PT" : "pt-BR";
        return cc.isEmpty() ? lang : lang + "-" + cc;
    }

    static boolean supports(String pkg) {
        return SERVICE_LABEL.containsKey(pkg) || YOUTUBE.equals(pkg);
    }

    /** True when this app has picks to show. */
    boolean has(String pkg) {
        List<Pick> l = pkg == null ? null : lists.get(pkg);
        return l != null && !l.isEmpty();
    }

    Pick current(String pkg) {
        List<Pick> l = lists.get(pkg);
        if (l == null || l.isEmpty()) return null;
        Integer i = index.get(pkg);
        return l.get(Math.floorMod(i == null ? 0 : i, l.size()));
    }

    /** Every app with featured titles starts from its first one again; false when there are none. */
    boolean resetFeatured() {
        if (featuredRefs.isEmpty()) return false;
        SharedPreferences.Editor ed = prefs.edit();
        for (String pkg : featuredRefs.keySet()) {
            index.remove(pkg);
            ed.putInt("i_" + pkg, -1);
        }
        ed.apply();
        return true;
    }

    /** The focus arrived on this app from somewhere else: show its next pick. */
    Pick advance(String pkg) {
        List<Pick> l = lists.get(pkg);
        if (l == null || l.isEmpty()) return null;
        Integer i = index.get(pkg);
        int n = i == null ? prefs.getInt("i_" + pkg, -1) + 1 : i + 1;
        n = Math.floorMod(n, l.size());
        index.put(pkg, n);
        prefs.edit().putInt("i_" + pkg, n).apply();
        return l.get(n);
    }

    /** Fetch again when the saved lists are older than a few hours (or from another day). */
    void refreshIfStale(List<String> dockPackages) {
        if (!enabled() || fetching) return;
        long at = prefs.getLong("fetched_at", 0);
        String day = prefs.getString("fetched_day", "");
        boolean stale = System.currentTimeMillis() - at > REFRESH_MS || !LocalDate.now().toString().equals(day)
                || !(featuredSig + "|" + language + "|" + region).equals(prefs.getString("featured_sig", ""));
        // Apps never asked for yet (one just installed, or the grid before it had loaded) are fetched right away;
        // an app that came back empty (no catalogue in this country) waits 20 minutes, not every focus.
        List<String> neverAsked = new ArrayList<>(), empty = new ArrayList<>();
        for (String p : dockPackages) {
            if (!supports(p) || lists.containsKey(p)) continue;
            if (attempted.contains(p)) empty.add(p);
            else neverAsked.add(p);
        }
        boolean retryEmpty = !empty.isEmpty() && System.currentTimeMillis() - lastAttempt >= 20 * 60_000L;
        if (!stale && neverAsked.isEmpty() && !retryEmpty) return;
        lastAttempt = System.currentTimeMillis();
        fetching = true;
        final boolean full = stale;
        final List<String> pkgs = new ArrayList<>(stale ? dockPackages : neverAsked);
        if (!stale && retryEmpty) pkgs.addAll(empty);
        attempted.addAll(pkgs);
        io.execute(() -> {
            Map<String, List<Pick>> fresh = new HashMap<>();
            try {
                Map<String, Integer> ids = providerIds();
                for (String pkg : pkgs) {
                    if (!supports(pkg)) continue;
                    if (YOUTUBE.equals(pkg)) {
                        if (!ytKey.isEmpty()) {
                            try {
                                List<Pick> yt = withFeatured(pkg, youtubeTrending());
                                if (!yt.isEmpty()) fresh.put(pkg, yt);
                            } catch (Exception e) {
                                android.util.Log.w("Picks", "youtube failed: " + e.getClass().getSimpleName() + " " + (e.getMessage() != null && e.getMessage().startsWith("HTTP") ? e.getMessage() : ""));
                            }
                        }
                        continue;
                    }
                    Integer id = ids.get(pkg);
                    // One app failing (a timeout) must not leave the ones after it without picks.
                    try {
                        List<Pick> l = withFeatured(pkg, id == null || id < 0 ? new ArrayList<>() : fetchFor(pkg, id));
                        if (!l.isEmpty()) fresh.put(pkg, l);
                    } catch (Exception e) {
                        android.util.Log.w("Picks", "fetch " + pkg + " failed: " + e.getClass().getSimpleName());
                    }
                }
            } catch (Exception e) {
                android.util.Log.w("Picks", "fetch failed: " + e.getClass().getSimpleName());
            }
            main.post(() -> {
                fetching = false;
                if (fresh.isEmpty()) return;
                lists.putAll(fresh);
                // A full refresh starts each app with featured titles on its first one again.
                if (full) {
                    SharedPreferences.Editor ed = prefs.edit();
                    for (String pkg : fresh.keySet()) {
                        if (!featuredRefs.containsKey(pkg)) continue;
                        index.remove(pkg);
                        ed.remove("i_" + pkg);
                    }
                    ed.apply();
                }
                save(full);
                if (listener != null) listener.onPicksChanged();
            });
        });
    }

    /** The backdrop, from memory, disk or the network; the callback runs on the main thread (null on failure). */
    void image(Pick p, java.util.function.Consumer<Bitmap> done) {
        Bitmap b = memory.get(p.backdrop);
        if (b != null) { done.accept(b); return; }
        if (!loading.add(p.backdrop)) return;
        img.execute(() -> {
            Bitmap bmp = null;
            try {
                File dir = new File(ctx.getCacheDir(), "picks");
                if (!dir.isDirectory()) dir.mkdirs();
                String name = p.backdrop.startsWith("http")
                        ? "yt" + Integer.toHexString(p.backdrop.hashCode()) + ".jpg" : p.backdrop.replace("/", "");
                File f = new File(dir, name);
                if (!f.isFile() || f.length() == 0) {
                    byte[] data = get(p.backdrop.startsWith("http") ? p.backdrop : IMG + p.backdrop, false);
                    try (FileOutputStream out = new FileOutputStream(f)) { out.write(data); }
                    trimDisk(dir);
                }
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inPreferredConfig = Bitmap.Config.RGB_565;
                bmp = BitmapFactory.decodeFile(f.getAbsolutePath(), o);
            } catch (Exception e) {
                android.util.Log.w("Picks", "image failed: " + e.getClass().getSimpleName());
            }
            final Bitmap result = bmp;
            main.post(() -> {
                loading.remove(p.backdrop);
                if (result != null) {
                    memory.put(p.backdrop, result);
                    while (memory.size() > MEM_IMAGES) {
                        String eldest = memory.keySet().iterator().next();
                        memory.remove(eldest);
                    }
                }
                done.accept(result);
            });
        });
    }

    // ---- TMDB ------------------------------------------------------------------------------------

    private Map<String, Integer> providerIds() throws Exception {
        Map<String, Integer> out = new HashMap<>();
        Map<String, Integer> byName = new HashMap<>();
        for (String kind : new String[]{"movie", "tv"}) {
            JSONObject j = new JSONObject(new String(get(API + "/watch/providers/" + kind + "?watch_region=" + region, true), "UTF-8"));
            JSONArray r = j.optJSONArray("results");
            if (r == null) continue;
            for (int i = 0; i < r.length(); i++) {
                JSONObject o = r.getJSONObject(i);
                String name = o.optString("provider_name").toLowerCase(Locale.ROOT).trim();
                if (!byName.containsKey(name)) byName.put(name, o.optInt("provider_id"));
            }
        }
        for (Object[] s : SERVICES) {
            String pkg = (String) s[0];
            Integer found = null;
            for (String n : (String[]) s[1]) {
                if (byName.containsKey(n)) { found = byName.get(n); break; }
            }
            if (found == null) {
                for (String n : (String[]) s[1]) {
                    for (Map.Entry<String, Integer> e : byName.entrySet()) {
                        String k = e.getKey();
                        if (k.startsWith(n) && !k.contains("channel") && !k.contains("with ads") && !k.contains("amazon")
                                || (n.contains("amazon") && k.equals(n))) {
                            found = e.getValue();
                            break;
                        }
                    }
                    if (found != null) break;
                }
            }
            out.put(pkg, found != null ? found : (Integer) s[2]);
        }
        return out;
    }

    private List<Pick> fetchFor(String pkg, int provider) throws Exception {
        String today = LocalDate.now().toString();
        String recent = LocalDate.now().minusDays(75).toString();
        String base = "&watch_region=" + region + "&with_watch_providers=" + provider
                + "&language=" + language + "&include_adult=false&sort_by=popularity.desc";
        List<Pick> fresh = parse(API + "/discover/movie?primary_release_date.gte=" + recent
                + "&primary_release_date.lte=" + today + base, "movie", pkg, true);
        fresh.addAll(parse(API + "/discover/tv?first_air_date.gte=" + recent
                + "&first_air_date.lte=" + today + base, "tv", pkg, true));
        List<Pick> popular = parse(API + "/discover/tv?vote_count.gte=50" + base, "tv", pkg, false);
        popular.addAll(parse(API + "/discover/movie?vote_count.gte=50" + base, "movie", pkg, false));
        // New releases first, then interleave with what is popular; no duplicates.
        List<Pick> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int a = 0, b = 0;
        while (out.size() < PER_APP && (a < fresh.size() || b < popular.size())) {
            if (a < fresh.size() && (out.size() % 2 == 0 || b >= popular.size())) {
                Pick p = fresh.get(a++);
                if (seen.add(p.id)) out.add(p);
            } else if (b < popular.size()) {
                Pick p = popular.get(b++);
                if (seen.add(p.id)) out.add(p);
            }
        }
        return out;
    }

    /** YouTube's trending videos in this country (YouTube Data API v3, the owner's own key; 1 unit per call). */
    private List<Pick> youtubeTrending() throws Exception {
        List<Pick> out = new ArrayList<>();
        String hl = language.length() >= 2 ? language.substring(0, 2) : "es";
        String url = "https://www.googleapis.com/youtube/v3/videos?part=snippet&chart=mostPopular&maxResults=25&regionCode="
                + region + "&hl=" + hl + "&key=" + ytKey;
        JSONObject j = new JSONObject(new String(get(url, false), "UTF-8"));
        JSONArray items = j.optJSONArray("items");
        if (items == null) return out;
        for (int i = 0; i < items.length() && out.size() < PER_APP; i++) {
            Pick p = ytPick(items.getJSONObject(i), ctx.getString(R.string.lumen_trending));
            if (p != null) out.add(p);
        }
        return out;
    }

    /** A YouTube video (videos.list item) as a pick; null without a usable thumbnail or title. */
    private static Pick ytPick(JSONObject it, String kind) {
        JSONObject sn = it.optJSONObject("snippet");
        if (sn == null) return null;
        JSONObject th = sn.optJSONObject("thumbnails");
        if (th == null) return null;
        JSONObject best = th.optJSONObject("maxres");
        if (best == null) best = th.optJSONObject("standard");
        if (best == null) best = th.optJSONObject("high");
        if (best == null) return null;
        JSONObject loc = sn.optJSONObject("localized");
        Pick p = new Pick();
        p.id = "yt" + it.optString("id");
        p.title = loc != null ? loc.optString("title", sn.optString("title")) : sn.optString("title");
        if (p.title.isEmpty()) return null;
        p.backdrop = best.optString("url");
        p.meta = "YouTube  ·  " + kind + "  ·  " + sn.optString("channelTitle", "");
        p.pkg = YOUTUBE;
        return p;
    }

    /** The owner's featured titles for this app first, then the rest without repeats. */
    private List<Pick> withFeatured(String pkg, List<Pick> rest) {
        List<String> refs = featuredRefs.get(pkg);
        if (refs == null) return rest;
        List<Pick> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String ref : refs) {
            try {
                Pick p = YOUTUBE.equals(pkg) ? featuredVideo(ref) : featuredTitle(pkg, ref);
                if (p != null && seen.add(p.id)) {
                    p.featured = true;
                    out.add(p);
                }
            } catch (Exception e) {
                android.util.Log.w("Picks", "featured " + ref + " failed: " + e.getClass().getSimpleName());
            }
        }
        for (Pick p : rest) if (seen.add(p.id)) out.add(p);
        return out;
    }

    private Pick featuredVideo(String id) throws Exception {
        if (ytKey.isEmpty()) return null;
        String hl = language.length() >= 2 ? language.substring(0, 2) : "es";
        JSONObject j = new JSONObject(new String(get("https://www.googleapis.com/youtube/v3/videos?part=snippet&id="
                + Uri.encode(id) + "&hl=" + hl + "&key=" + ytKey, false), "UTF-8"));
        JSONArray items = j.optJSONArray("items");
        return items == null || items.length() == 0 ? null : ytPick(items.getJSONObject(0), ctx.getString(R.string.lumen_featured));
    }

    /** "tv/1408" or "movie/550", with its best text-free backdrop (the most voted one, in high resolution). */
    private Pick featuredTitle(String pkg, String ref) throws Exception {
        String[] kv = ref.split("/");
        if (kv.length != 2 || !(kv[0].equals("tv") || kv[0].equals("movie"))) return null;
        JSONObject o = new JSONObject(new String(get(API + "/" + kv[0] + "/" + kv[1] + "?language=" + language
                + "&append_to_response=images&include_image_language=null", true), "UTF-8"));
        JSONObject images = o.optJSONObject("images");
        JSONArray bd = images == null ? null : images.optJSONArray("backdrops");
        if (bd != null) {
            double bestVote = -1;
            for (int i = 0; i < bd.length(); i++) {
                JSONObject b = bd.getJSONObject(i);
                if (b.optInt("width", 0) < 1280) continue;
                double v = b.optDouble("vote_average", 0);
                if (v > bestVote) {
                    bestVote = v;
                    o.put("backdrop_path", b.optString("file_path"));
                }
            }
        }
        return pickFrom(o, kv[0], pkg, false);
    }

    private List<Pick> parse(String url, String kind, String pkg, boolean isNew) throws Exception {
        List<Pick> out = new ArrayList<>();
        JSONObject j = new JSONObject(new String(get(url, true), "UTF-8"));
        JSONArray r = j.optJSONArray("results");
        if (r == null) return out;
        for (int i = 0; i < r.length() && out.size() < PER_APP; i++) {
            Pick p = pickFrom(r.getJSONObject(i), kind, pkg, isNew);
            if (p != null) out.add(p);
        }
        return out;
    }

    /** A TMDB movie or series as a pick; null without a backdrop or a title. */
    private Pick pickFrom(JSONObject o, String kind, String pkg, boolean isNew) {
        {
            String backdrop = o.optString("backdrop_path", "");
            if (backdrop.isEmpty() || "null".equals(backdrop)) return null;
            Pick p = new Pick();
            p.id = kind + o.optInt("id");
            p.title = o.optString(kind.equals("tv") ? "name" : "title", "");
            if (p.title.isEmpty()) return null;
            p.backdrop = backdrop;
            p.pkg = pkg;
            String date = o.optString(kind.equals("tv") ? "first_air_date" : "release_date", "");
            StringBuilder m = new StringBuilder(SERVICE_LABEL.get(pkg));
            m.append("  ·  ").append(ctx.getString(kind.equals("tv") ? R.string.lumen_series : R.string.lumen_movie));
            if (date.length() >= 4) m.append("  ·  ").append(date, 0, 4);
            double vote = o.optDouble("vote_average", 0);
            if (vote >= 1 && o.optInt("vote_count", 0) >= 20) m.append(String.format(Locale.ROOT, "  ·  ★ %.1f", vote));
            if (isNew) m.append("  ·  ").append(ctx.getString(R.string.lumen_new));
            p.meta = m.toString();
            return p;
        }
    }

    /** v3 keys (32 hex characters) go in the query; v4 read tokens go in an Authorization header. */
    private byte[] get(String url, boolean api) throws Exception {
        boolean bearer = api && key.length() > 40;
        if (api && !bearer) url += (url.contains("?") ? "&" : "?") + "api_key=" + key;
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(15000);
        if (bearer) c.setRequestProperty("Authorization", "Bearer " + key);
        if (api) c.setRequestProperty("Accept", "application/json");
        try {
            int code = c.getResponseCode();
            if (code != 200) throw new java.io.IOException("HTTP " + code);
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                byte[] tmp = new byte[16384];
                int n;
                while ((n = in.read(tmp)) > 0) buf.write(tmp, 0, n);
                return buf.toByteArray();
            }
        } finally {
            c.disconnect();
        }
    }

    private static void trimDisk(File dir) {
        File[] files = dir.listFiles();
        if (files == null || files.length <= 60) return;
        java.util.Arrays.sort(files, (x, y) -> Long.compare(x.lastModified(), y.lastModified()));
        for (int i = 0; i < files.length - 60; i++) files[i].delete();
    }

    // ---- saved between runs, so the shelf has pictures before the network is up ------------------

    /** @param fresh true after a full refresh: only then do the lists count as fetched now. */
    private void save(boolean fresh) {
        try {
            JSONObject all = new JSONObject();
            for (Map.Entry<String, List<Pick>> e : lists.entrySet()) {
                JSONArray a = new JSONArray();
                for (Pick p : e.getValue()) {
                    a.put(new JSONObject().put("id", p.id).put("t", p.title).put("b", p.backdrop).put("m", p.meta).put("p", p.pkg == null ? e.getKey() : p.pkg).put("f", p.featured));
                }
                all.put(e.getKey(), a);
            }
            SharedPreferences.Editor ed = prefs.edit().putString("lists", all.toString());
            if (fresh) ed.putLong("fetched_at", System.currentTimeMillis()).putString("fetched_day", LocalDate.now().toString())
                    .putString("featured_sig", featuredSig + "|" + language + "|" + region);
            ed.apply();
        } catch (Exception ignored) {
        }
    }

    private void loadSaved() {
        try {
            String s = prefs.getString("lists", null);
            if (s == null) return;
            JSONObject all = new JSONObject(s);
            JSONArray names = all.names();
            if (names == null) return;
            for (int i = 0; i < names.length(); i++) {
                String pkg = names.getString(i);
                JSONArray a = all.getJSONArray(pkg);
                List<Pick> l = new ArrayList<>();
                for (int k = 0; k < a.length(); k++) {
                    JSONObject o = a.getJSONObject(k);
                    Pick p = new Pick();
                    p.id = o.optString("id");
                    p.title = o.optString("t");
                    p.backdrop = o.optString("b");
                    p.meta = o.optString("m");
                    p.pkg = o.optString("p", pkg);
                    p.featured = o.optBoolean("f", false);
                    l.add(p);
                }
                if (!l.isEmpty()) lists.put(pkg, l);
            }
        } catch (Exception ignored) {
        }
    }
}
