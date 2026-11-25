package app.seek;

import android.net.Uri;
import android.webkit.WebResourceResponse;

import java.io.ByteArrayInputStream;
import java.util.HashSet;
import java.util.Set;

/**
 * Third-party trackers and ad networks, stopped before the request is made, so there is
 * nothing to render and nothing to slow down. The lists follow Search's Shield.swift.
 */
final class Shield {
    private static final String[] UNWANTED = {
        "doubleclick.net", "googlesyndication.com", "googleadservices.com",
        "googletagservices.com", "google-analytics.com", "googletagmanager.com",
        "adservice.google.com", "amazon-adsystem.com", "adnxs.com", "adsrvr.org",
        "criteo.com", "criteo.net", "taboola.com", "outbrain.com",
        "rubiconproject.com", "pubmatic.com", "openx.net", "casalemedia.com",
        "smartadserver.com", "sharethrough.com", "indexww.com", "bidswitch.net",
        "33across.com", "teads.tv", "moatads.com", "adroll.com",
        "scorecardresearch.com", "quantserve.com", "chartbeat.com",
        "hotjar.com", "mouseflow.com", "fullstory.com", "clarity.ms",
        "mixpanel.com", "amplitude.com", "segment.com", "segment.io",
        "branch.io", "appsflyer.com", "adjust.com", "analytics.tiktok.com",
        "connect.facebook.net", "ads-twitter.com", "analytics.twitter.com",
        "intergient.com", "btloader.com", "ad-delivery.net", "mentorsticks.com",
        "dns-finder.com", "confiant-integrations.net", "cloudflareinsights.com",
        // a few more that are common on phones and tablets
        "app-measurement.com", "adcolony.com", "unityads.unity3d.com", "applovin.com",
        "inmobi.com", "mopub.com", "yieldmo.com", "media.net", "zedo.com", "popads.net",
        "propellerads.com", "adsterra.com", "exoclick.com", "mgid.com", "revcontent.com",
    };

    static final String SLOTS =
        ".adsbygoogle, ins.adsbygoogle, [id^=\"google_ads_\"], [id^=\"div-gpt-ad\"], [id^=\"taboola-\"], "
        + "#taboola-below-article, iframe[src*=\"doubleclick.net\"], iframe[src*=\"googlesyndication\"], "
        + "iframe[src*=\"amazon-adsystem\"], .ad-slot, .ad-slot-container, .top-banner-ad-container, "
        + ".ad-leaderboard, .ad-billboard, .ad-giga, .ad-mpu, .ad-mrec, .ad-unit, .adunit, .adslot, "
        + ".dfp-ad, .gpt-ad, .w_ad, shreddit-ad-post, shreddit-comments-page-ad, shreddit-sidebar-ad, "
        + "shreddit-dynamic-ad-link, comment-tree-ad, .promotedlink";

    private static final Set<String> SET = new HashSet<>();
    /** EasyList and EasyPrivacy's ad and tracker domains, when Stronger blocking is on. */
    static volatile Set<String> extra = new HashSet<>();
    static { for (String s : UNWANTED) SET.add(s); }

    static boolean blocked(String url, String pageHost) {
        Uri u;
        try { u = Uri.parse(url); } catch (Exception e) { return false; }
        String host = u.getHost();
        if (host == null) return false;
        host = host.toLowerCase();
        if (pageHost != null && base(host).equals(base(pageHost))) return false;   // first party
        String h = host;
        while (true) {
            if (SET.contains(h) || extra.contains(h)) return true;
            int dot = h.indexOf('.');
            if (dot < 0 || dot == h.length() - 1) return false;
            h = h.substring(dot + 1);
        }
    }

    /** A rough registrable domain: the last two labels, or three under a two-letter country code with a short second level. */
    static String base(String host) {
        String[] p = host.split("\\.");
        int n = p.length;
        if (n <= 2) return host;
        if (p[n - 1].length() == 2 && p[n - 2].length() <= 3) return p[n - 3] + "." + p[n - 2] + "." + p[n - 1];
        return p[n - 2] + "." + p[n - 1];
    }

    static WebResourceResponse empty() {
        return new WebResourceResponse("text/plain", "utf-8", new ByteArrayInputStream(new byte[0]));
    }

    static final String[][] LISTS = {
        {"https://easylist.to/easylist/easylist.txt", "https://raw.githubusercontent.com/easylist/easylist/gh-pages/easylist.txt"},
        {"https://easylist.to/easylist/easyprivacy.txt", "https://raw.githubusercontent.com/easylist/easylist/gh-pages/easyprivacy.txt"},
    };

    /** Only the plain whole-domain rules ("||ads.example.com^"), which can be checked in a set as fast as the built-in list. */
    static void parse(java.io.BufferedReader in, Set<String> into) throws java.io.IOException {
        String line;
        while ((line = in.readLine()) != null) {
            if (!line.startsWith("||")) continue;
            int caret = line.indexOf('^');
            if (caret < 3) continue;
            String rest = line.substring(caret + 1);
            if (!(rest.isEmpty() || rest.equals("$third-party") || rest.equals("$3p"))) continue;
            String d = line.substring(2, caret).toLowerCase();
            if (d.indexOf('.') < 0 || !d.matches("[a-z0-9.-]+")) continue;
            into.add(d);
        }
    }

    /** Fetches the lists and keeps the domains in a file, one a line. Off the main thread. */
    static int download(java.io.File out) throws Exception {
        Set<String> all = new HashSet<>();
        for (String[] pair : LISTS) {
            Exception last = null;
            for (String u : pair) {
                java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(u).openConnection();
                c.setConnectTimeout(15000);
                c.setReadTimeout(30000);
                try {
                    parse(new java.io.BufferedReader(new java.io.InputStreamReader(c.getInputStream(), "UTF-8")), all);
                    last = null;
                    break;
                } catch (Exception e) { last = e; } finally { c.disconnect(); }
            }
            if (last != null) throw last;
        }
        if (all.size() < 100) throw new java.io.IOException("list too small");
        java.io.File tmp = new java.io.File(out.getPath() + ".tmp");
        java.io.Writer w = new java.io.OutputStreamWriter(new java.io.FileOutputStream(tmp), "UTF-8");
        try { for (String d : all) { w.write(d); w.write('\n'); } } finally { w.close(); }
        tmp.renameTo(out);
        extra = all;
        return all.size();
    }

    static void loadSaved(java.io.File f) {
        if (!f.exists()) return;
        Set<String> s = new HashSet<>();
        try {
            java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(new java.io.FileInputStream(f), "UTF-8"));
            try { String l; while ((l = r.readLine()) != null) if (!l.isEmpty()) s.add(l); } finally { r.close(); }
            extra = s;
        } catch (Exception ignored) {}
    }

    private Shield() {}
}
