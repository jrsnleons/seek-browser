package app.seek;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Small JSON files in the app's own folder. Nothing leaves the tablet. */
final class Store {
    static final class Entry {
        String url, title;
        long time;
        Entry(String u, String t, long time) { url = u; title = t; this.time = time; }
    }

    final SharedPreferences prefs;
    final File dir;
    final List<Entry> history = new ArrayList<>();
    final List<Entry> bookmarks = new ArrayList<>();
    private JSONObject hidden = new JSONObject();
    private boolean historyDirty;

    static final String[][] ENGINES = {
        {"DuckDuckGo", "https://duckduckgo.com/?q=%s", "https://duckduckgo.com/ac/?q=%s&type=list"},
        {"Google", "https://www.google.com/search?q=%s", "https://suggestqueries.google.com/complete/search?client=firefox&q=%s"},
        {"Bing", "https://www.bing.com/search?q=%s", "https://api.bing.com/osjson.aspx?query=%s"},
        {"Brave", "https://search.brave.com/search?q=%s", "https://search.brave.com/api/suggest?q=%s"},
        {"Startpage", "https://www.startpage.com/do/search?q=%s", "https://www.startpage.com/suggestions?q=%s&format=opensearch"},
        {"Ecosia", "https://www.ecosia.org/search?q=%s", "https://ac.ecosia.org/autocomplete?q=%s&type=list"},
        {"Kagi", "https://kagi.com/search?q=%s", "https://kagi.com/api/autosuggest?q=%s"},
    };

    Store(Context c) {
        prefs = c.getSharedPreferences("seek", Context.MODE_PRIVATE);
        dir = c.getFilesDir();
        load(history, "history.json");
        load(bookmarks, "bookmarks.json");
        try { hidden = new JSONObject(read("hidden.json")); } catch (Exception e) { hidden = new JSONObject(); }
    }

    // ── settings ────────────────────────────────────────────────
    int engine() { return Math.max(0, Math.min(ENGINES.length - 1, prefs.getInt("engine", 0))); }
    void engine(int i) { prefs.edit().putInt("engine", i).apply(); }
    /** 0 system · 1 light · 2 dark */
    int look() { return prefs.getInt("look", 0); }
    void look(int i) { prefs.edit().putInt("look", i).apply(); }
    boolean flag(String k, boolean d) { return prefs.getBoolean(k, d); }
    void set(String k, boolean v) { prefs.edit().putBoolean(k, v).apply(); }

    Set<String> awake() { return new HashSet<>(prefs.getStringSet("awake", new HashSet<String>())); }
    void awake(String host, boolean on) {
        Set<String> s = awake();
        if (on) s.add(host); else s.remove(host);
        prefs.edit().putStringSet("awake", s).apply();
    }

    /** Text size per site, in percent. */
    int zoom(String host) {
        if (host == null) return 100;
        try { return new JSONObject(prefs.getString("zoom", "{}")).optInt(host, 100); } catch (Exception e) { return 100; }
    }
    void zoom(String host, int pct) {
        if (host == null) return;
        try {
            JSONObject o = new JSONObject(prefs.getString("zoom", "{}"));
            if (pct == 100) o.remove(host); else o.put(host, pct);
            prefs.edit().putString("zoom", o.toString()).apply();
        } catch (Exception ignored) {}
    }


    String suggestUrl(String q) {
        try { return ENGINES[engine()][2].replace("%s", java.net.URLEncoder.encode(q, "UTF-8")); } catch (Exception e) { return null; }
    }

    // ── bookmarks in and out, as the HTML file every browser reads ──
    String exportBookmarks() {
        StringBuilder b = new StringBuilder("<!DOCTYPE NETSCAPE-Bookmark-file-1>\n<META HTTP-EQUIV=\"Content-Type\" CONTENT=\"text/html; charset=UTF-8\">\n<TITLE>Bookmarks</TITLE>\n<H1>Bookmarks</H1>\n<DL><p>\n");
        for (Entry e : bookmarks) {
            b.append("    <DT><A HREF=\"").append(esc(e.url)).append("\" ADD_DATE=\"").append(e.time / 1000).append("\">")
             .append(esc(e.title)).append("</A>\n");
        }
        return b.append("</DL><p>\n").toString();
    }

    int importBookmarks(String html) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("<a\\s[^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>",
            java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.DOTALL).matcher(html);
        int n = 0;
        List<Entry> found = new ArrayList<>();
        while (m.find()) {
            String url = unesc(m.group(1));
            if (!(url.startsWith("http://") || url.startsWith("https://")) || isBookmarked(url)) continue;
            String title = unesc(m.group(2).replaceAll("<[^>]+>", "")).trim();
            found.add(new Entry(url, title.isEmpty() ? url : title, System.currentTimeMillis()));
            n++;
        }
        bookmarks.addAll(found);
        save(bookmarks, "bookmarks.json");
        return n;
    }

    static String esc(String s) { return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;"); }
    static String unesc(String s) { return s.replace("&quot;", "\"").replace("&lt;", "<").replace("&gt;", ">").replace("&#39;", "'").replace("&amp;", "&"); }

    Set<String> paused() { return new HashSet<>(prefs.getStringSet("shield.paused", new HashSet<String>())); }
    void pause(String host, boolean off) {
        Set<String> s = paused();
        if (off) s.add(host); else s.remove(host);
        prefs.edit().putStringSet("shield.paused", s).apply();
    }

    String searchUrl(String q) {
        try {
            return ENGINES[engine()][1].replace("%s", java.net.URLEncoder.encode(q, "UTF-8"));
        } catch (Exception e) { return ENGINES[0][1].replace("%s", q); }
    }

    // ── history ─────────────────────────────────────────────────
    void visit(String url, String title) {
        if (url == null || !(url.startsWith("http://") || url.startsWith("https://"))) return;
        for (int i = 0; i < history.size(); i++) {
            if (history.get(i).url.equals(url)) { history.remove(i); break; }
        }
        history.add(0, new Entry(url, title == null ? "" : title, System.currentTimeMillis()));
        while (history.size() > 2000) history.remove(history.size() - 1);
        historyDirty = true;
    }

    void retitle(String url, String title) {
        if (url == null || title == null) return;
        for (Entry e : history) if (e.url.equals(url)) { e.title = title; historyDirty = true; return; }
    }

    void clearHistory() { history.clear(); historyDirty = true; flush(); }

    // ── bookmarks ───────────────────────────────────────────────
    boolean isBookmarked(String url) {
        if (url == null) return false;
        for (Entry e : bookmarks) if (e.url.equals(url)) return true;
        return false;
    }

    /** Adds it, or takes it away if it was there. Returns whether it is bookmarked now. */
    boolean toggleBookmark(String url, String title) {
        for (int i = 0; i < bookmarks.size(); i++) {
            if (bookmarks.get(i).url.equals(url)) { bookmarks.remove(i); save(bookmarks, "bookmarks.json"); return false; }
        }
        bookmarks.add(0, new Entry(url, title == null ? url : title, System.currentTimeMillis()));
        save(bookmarks, "bookmarks.json");
        return true;
    }

    void removeBookmark(Entry e) { bookmarks.remove(e); save(bookmarks, "bookmarks.json"); }

    // ── hidden elements, per site ───────────────────────────────
    List<String> hiddenOn(String host) {
        List<String> out = new ArrayList<>();
        if (host == null) return out;
        JSONArray a = hidden.optJSONArray(host);
        if (a != null) for (int i = 0; i < a.length(); i++) out.add(a.optString(i));
        return out;
    }

    void hide(String host, String selector) {
        if (host == null || selector == null || selector.isEmpty()) return;
        try {
            JSONArray a = hidden.optJSONArray(host);
            if (a == null) { a = new JSONArray(); hidden.put(host, a); }
            for (int i = 0; i < a.length(); i++) if (selector.equals(a.optString(i))) return;
            a.put(selector);
            write("hidden.json", hidden.toString());
        } catch (Exception ignored) {}
    }

    void unhideAll(String host) {
        if (host == null) return;
        hidden.remove(host);
        write("hidden.json", hidden.toString());
    }

    void clearHidden() {
        hidden = new JSONObject();
        write("hidden.json", "{}");
    }

    // ── what comes back at launch ───────────────────────────────
    void saveSession(JSONArray tabs, int selected) {
        try {
            JSONObject o = new JSONObject();
            o.put("tabs", tabs);
            o.put("selected", selected);
            write("session.json", o.toString());
        } catch (Exception ignored) {}
    }

    void saveSessionRaw(JSONObject o) { write("session.json", o.toString()); }

    JSONObject session() {
        try { return new JSONObject(read("session.json")); } catch (Exception e) { return null; }
    }

    // ── suggestions, from your own history and bookmarks only ───
    List<Entry> suggest(String q, int max) {
        List<Entry> out = new ArrayList<>();
        String[] words = q.toLowerCase(Locale.ROOT).trim().split("\\s+");
        if (words.length == 0 || words[0].isEmpty()) return out;
        Set<String> seen = new HashSet<>();
        for (List<Entry> src : new List[]{bookmarks, history}) {
            for (Entry e : src) {
                if (out.size() >= max) return out;
                if (seen.contains(e.url)) continue;
                String hay = (bare(e.url) + " " + e.title).toLowerCase(Locale.ROOT);
                boolean all = true;
                for (String w : words) if (!hay.contains(w)) { all = false; break; }
                if (all) { out.add(e); seen.add(e.url); }
            }
        }
        return out;
    }

    /** The first address in history that starts with what was typed, for finishing it inline. */
    String complete(String typed) {
        String t = typed.toLowerCase(Locale.ROOT);
        if (t.length() < 2 || t.contains(" ")) return null;
        for (Entry e : history) {
            String b = bare(e.url);
            String host = b.contains("/") ? b.substring(0, b.indexOf('/')) : b;
            if (host.startsWith(t)) return host;
            if (host.startsWith("www.") && host.substring(4).startsWith(t)) return host.substring(4);
        }
        return null;
    }

    static String bare(String url) {
        String s = url;
        if (s.startsWith("https://")) s = s.substring(8); else if (s.startsWith("http://")) s = s.substring(7);
        if (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s;
    }

    void flush() {
        if (historyDirty) { save(history, "history.json"); historyDirty = false; }
    }

    // ── files ───────────────────────────────────────────────────
    private void load(List<Entry> into, String name) {
        try {
            JSONArray a = new JSONArray(read(name));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                into.add(new Entry(o.getString("u"), o.optString("t"), o.optLong("d")));
            }
        } catch (Exception ignored) {}
    }

    private void save(List<Entry> list, String name) {
        try {
            JSONArray a = new JSONArray();
            for (Entry e : list) {
                JSONObject o = new JSONObject();
                o.put("u", e.url); o.put("t", e.title); o.put("d", e.time);
                a.put(o);
            }
            write(name, a.toString());
        } catch (Exception ignored) {}
    }

    private String read(String name) throws Exception {
        File f = new File(dir, name);
        FileInputStream in = new FileInputStream(f);
        try {
            byte[] b = new byte[(int) f.length()];
            int off = 0;
            while (off < b.length) { int n = in.read(b, off, b.length - off); if (n < 0) break; off += n; }
            return new String(b, 0, off, "UTF-8");
        } finally { in.close(); }
    }

    private void write(String name, String body) {
        try {
            File tmp = new File(dir, name + ".tmp");
            FileOutputStream out = new FileOutputStream(tmp);
            try { out.write(body.getBytes("UTF-8")); } finally { out.close(); }
            tmp.renameTo(new File(dir, name));
        } catch (Exception ignored) {}
    }
}
