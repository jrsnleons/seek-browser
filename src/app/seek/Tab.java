package app.seek;

import android.graphics.Bitmap;
import android.webkit.WebView;

/** One page. Its web view is built lazily, so a tab restored from last time costs nothing until it is opened. */
final class Tab {
    String url;
    String title = "";
    boolean pinned;
    Bitmap icon;
    WebView view;
    long used;
    Tab opener;
    boolean desktop;
    /** No history, not brought back next time. Cookies are still shared with other tabs: Android keeps one jar per app. */
    boolean priv;
    boolean reading;
    boolean readerAsked;
    int progress = 100;
    volatile String host;

    Tab(String url, String title, boolean pinned) {
        this.url = url;
        this.title = title == null ? "" : title;
        this.pinned = pinned;
        this.host = hostOf(url);
    }

    String label() {
        if (title != null && !title.trim().isEmpty()) return title.trim();
        if (url == null) return "New tab";
        return Store.bare(url);
    }

    String letter() {
        String h = host;
        if (h == null || h.isEmpty()) return label().substring(0, 1).toUpperCase();
        if (h.startsWith("www.")) h = h.substring(4);
        return h.substring(0, 1).toUpperCase();
    }

    static String hostOf(String url) {
        if (url == null) return null;
        try {
            String h = android.net.Uri.parse(url).getHost();
            return h == null ? null : h.toLowerCase();
        } catch (Exception e) { return null; }
    }

}
