package com.gathin.tvhome;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * The weather pill: now, today's high and low, and sunset, from Open-Meteo (free, no key, CC BY 4.0).
 * Coordinates and the city name come from lumen.properties (weather_lat, weather_lon, weather_city). Left empty
 * (or weather_city=auto), the TV finds its own city from its internet connection, so whoever installs Lumen OS
 * gets their own weather; that location is kept for a week.
 */
final class Weather {
    private static final long EVERY_MS = 30 * 60_000L;

    private static final long LOCATE_MS = 7 * 24 * 3600_000L;
    /** Free IP location services that need no key, tried in order. */
    private static final String[] LOCATORS = {
            "https://get.geojs.io/v1/ip/geo.json", "https://ipwho.is/", "https://ipapi.co/json/"};

    private volatile String lat, lon, city;
    private final boolean auto;
    private final android.content.SharedPreferences prefs;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private long fetchedAt;
    private String last;
    private volatile boolean busy;
    /** Today's sunrise and sunset as minutes after midnight (local), -1 until known: the night look follows them. */
    volatile int sunriseMin = -1, sunsetMin = -1;

    Weather(Context ctx) {
        prefs = ctx.getSharedPreferences("lumen_location", Context.MODE_PRIVATE);
        String la = ctx.getString(R.string.weather_lat).trim();
        String lo = ctx.getString(R.string.weather_lon).trim();
        String c = ctx.getString(R.string.weather_city).trim();
        auto = la.isEmpty() || lo.isEmpty() || c.equalsIgnoreCase("auto");
        if (auto) {
            lat = prefs.getString("lat", "");
            lon = prefs.getString("lon", "");
            city = prefs.getString("city", "");
        } else {
            lat = la;
            lon = lo;
            city = c;
        }
    }

    boolean enabled() {
        return true;
    }

    /** Off the main thread: makes sure there are coordinates, finding them from the connection when automatic. */
    private boolean locate() {
        if (!auto) return true;
        if (!lat.isEmpty() && System.currentTimeMillis() - prefs.getLong("at", 0) < LOCATE_MS) return true;
        for (String u : LOCATORS) {
            try {
                JSONObject j = new JSONObject(fetch(u));
                double la = j.getDouble("latitude"), lo = j.getDouble("longitude");
                if (la == 0 && lo == 0) continue;
                lat = String.format(Locale.ROOT, "%.4f", la);
                lon = String.format(Locale.ROOT, "%.4f", lo);
                city = j.optString("city", "").trim();
                prefs.edit().putString("lat", lat).putString("lon", lon).putString("city", city)
                        .putLong("at", System.currentTimeMillis()).apply();
                return true;
            } catch (Exception e) {
                android.util.Log.w("Weather", "locate failed: " + e.getClass().getSimpleName());
            }
        }
        return !lat.isEmpty(); // an older location is better than none
    }

    /** The last text right away (if any), then a fresh one when it is older than half an hour. */
    void update(Consumer<String> show) {
        if (!enabled()) return;
        if (last != null) show.accept(last);
        if (busy || System.currentTimeMillis() - fetchedAt < EVERY_MS) return;
        busy = true;
        io.execute(() -> {
            String text = null;
            try {
                if (!locate()) throw new IllegalStateException("no location");
                String url = "https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon
                        + "&current=temperature_2m,weather_code,is_day"
                        + "&daily=temperature_2m_max,temperature_2m_min,sunrise,sunset&timezone=auto&forecast_days=1" + units();
                JSONObject j = new JSONObject(fetch(url));
                JSONObject cur = j.getJSONObject("current");
                JSONObject daily = j.getJSONObject("daily");
                int t = (int) Math.round(cur.getDouble("temperature_2m"));
                int code = cur.optInt("weather_code", 0);
                boolean day = cur.optInt("is_day", 1) == 1;
                int hi = (int) Math.round(daily.getJSONArray("temperature_2m_max").getDouble(0));
                int lo = (int) Math.round(daily.getJSONArray("temperature_2m_min").getDouble(0));
                JSONArray ss = daily.optJSONArray("sunset");
                String sunset = ss != null && ss.length() > 0 ? ss.getString(0) : "";
                if (sunset.length() >= 16) sunset = sunset.substring(11, 16);
                sunsetMin = minutes(sunset);
                JSONArray sr = daily.optJSONArray("sunrise");
                if (sr != null && sr.length() > 0) {
                    String r = sr.getString(0);
                    sunriseMin = minutes(r.length() >= 16 ? r.substring(11, 16) : "");
                }
                StringBuilder s = new StringBuilder();
                s.append(icon(code, day)).append("  ").append(t).append("°");
                if (!city.isEmpty()) s.append("  ").append(city);
                s.append("   ").append(hi).append("° / ").append(lo).append("°");
                text = s.toString();
            } catch (Exception e) {
                android.util.Log.w("Weather", "failed: " + e.getClass().getSimpleName());
            }
            final String result = text;
            main.post(() -> {
                busy = false;
                if (result == null) return;
                fetchedAt = System.currentTimeMillis();
                last = result;
                show.accept(result);
            });
        });
    }

    String city() {
        return city;
    }

    /** True after sunset and before sunrise (18:30 to 06:15 until the forecast has loaded). */
    boolean isNight() {
        java.util.Calendar c = java.util.Calendar.getInstance();
        int now = c.get(java.util.Calendar.HOUR_OF_DAY) * 60 + c.get(java.util.Calendar.MINUTE);
        int rise = sunriseMin >= 0 ? sunriseMin : 6 * 60 + 15;
        int set = sunsetMin >= 0 ? sunsetMin : 18 * 60 + 30;
        return now < rise || now >= set;
    }

    private static int minutes(String hhmm) {
        try {
            return Integer.parseInt(hhmm.substring(0, 2)) * 60 + Integer.parseInt(hhmm.substring(3, 5));
        } catch (Exception e) {
            return -1;
        }
    }

    /** The full forecast for the weather panel (current, next hours, today and tomorrow); null on failure. */
    void detail(Consumer<JSONObject> done) {
        if (!enabled()) {
            done.accept(null);
            return;
        }
        io.execute(() -> {
            JSONObject j = null;
            try {
                if (!locate()) throw new IllegalStateException("no location");
                String url = "https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon
                        + "&current=temperature_2m,apparent_temperature,weather_code,is_day,wind_speed_10m"
                        + "&hourly=temperature_2m,weather_code,is_day,precipitation_probability"
                        + "&daily=temperature_2m_max,temperature_2m_min,sunrise,sunset,precipitation_probability_max"
                        + "&timezone=auto&forecast_days=2&forecast_hours=8" + units();
                j = new JSONObject(fetch(url));
            } catch (Exception e) {
                android.util.Log.w("Weather", "detail failed: " + e.getClass().getSimpleName());
            }
            final JSONObject r = j;
            main.post(() -> done.accept(r));
        });
    }

    /** Words for a WMO weather code, in the TV's language. */
    static String describe(Context c, int code) {
        int r;
        if (code == 0) r = R.string.lumen_w_clear;
        else if (code <= 2) r = R.string.lumen_w_partly;
        else if (code == 3) r = R.string.lumen_w_cloudy;
        else if (code == 45 || code == 48) r = R.string.lumen_w_fog;
        else if (code >= 51 && code <= 57) r = R.string.lumen_w_drizzle;
        else if (code >= 61 && code <= 67) r = R.string.lumen_w_rain;
        else if (code >= 71 && code <= 77) r = R.string.lumen_w_snow;
        else if (code >= 80 && code <= 82) r = R.string.lumen_w_showers;
        else if (code >= 95) r = R.string.lumen_w_storm;
        else r = R.string.lumen_w_variable;
        return c.getString(r);
    }

    /** Fahrenheit and mph where people use them (United States, Liberia, Myanmar); Celsius and km/h elsewhere. */
    static boolean imperial() {
        String cc = Locale.getDefault().getCountry();
        return "US".equals(cc) || "LR".equals(cc) || "MM".equals(cc);
    }

    private static String units() {
        return imperial() ? "&temperature_unit=fahrenheit&wind_speed_unit=mph" : "";
    }

    static String icon(int code, boolean day) {
        if (code == 0) return day ? "☀️" : "🌙";
        if (code <= 2) return day ? "⛅" : "☁️";
        if (code == 3) return "☁️";
        if (code == 45 || code == 48) return "🌫️";
        if (code >= 51 && code <= 67) return "🌧️";
        if (code >= 71 && code <= 77) return "❄️";
        if (code >= 80 && code <= 82) return "🌦️";
        if (code >= 95) return "⛈️";
        return day ? "⛅" : "☁️";
    }

    private static String fetch(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(12000);
        try {
            if (c.getResponseCode() != 200) throw new java.io.IOException("HTTP " + c.getResponseCode());
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                byte[] tmp = new byte[8192];
                int n;
                while ((n = in.read(tmp)) > 0) buf.write(tmp, 0, n);
                return buf.toString("UTF-8");
            }
        } finally {
            c.disconnect();
        }
    }
}
