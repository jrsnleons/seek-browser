package app.seek;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** Asks GitHub for Seek's newest release. Nothing else is sent; the tablet installs it only when you say so. */
final class Updater {
    static final String LATEST = "https://api.github.com/repos/jrsnleons/seek-browser/releases/latest";

    static final class Release {
        String version, apk, notes;
    }

    static Release latest() throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(LATEST).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        c.setRequestProperty("Accept", "application/vnd.github+json");
        c.setRequestProperty("User-Agent", "Seek");
        try {
            if (c.getResponseCode() != 200) throw new java.io.IOException("HTTP " + c.getResponseCode());
            InputStream in = c.getInputStream();
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
            return parse(new JSONObject(b.toString("UTF-8")));
        } finally { c.disconnect(); }
    }

    static Release parse(JSONObject o) {
        Release r = new Release();
        r.version = o.optString("tag_name", "").replaceFirst("^[vV]", "");
        r.notes = o.optString("body", "");
        JSONArray a = o.optJSONArray("assets");
        if (a != null) for (int i = 0; i < a.length(); i++) {
            JSONObject x = a.optJSONObject(i);
            if (x == null) continue;
            String url = x.optString("browser_download_url", "");
            if (x.optString("name", "").endsWith(".apk") && url.startsWith("https://github.com/")) { r.apk = url; break; }
        }
        return r.version.isEmpty() || r.apk == null ? null : r;
    }

    /** True when version a is later than b: 1.10 is later than 1.9. */
    static boolean newer(String a, String b) {
        String[] x = a.split("\\."), y = b.split("\\.");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int p = i < x.length ? num(x[i]) : 0, q = i < y.length ? num(y[i]) : 0;
            if (p != q) return p > q;
        }
        return false;
    }

    static int num(String s) {
        try { return Integer.parseInt(s.replaceAll("[^0-9].*$", "")); } catch (Exception e) { return 0; }
    }

    private Updater() {}
}
