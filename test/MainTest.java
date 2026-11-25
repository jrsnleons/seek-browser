package app.seek;

import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE, qualifiers = "w1280dp-h800dp-land-xhdpi")
public class MainTest {

    static void idle() { ShadowLooper.idleMainLooper(); }

    static void clickAllText(View v, java.util.List<String> out) {
        if (v instanceof TextView) out.add(((TextView) v).getText().toString());
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) clickAllText(((ViewGroup) v).getChildAt(i), out);
    }

    Main launch(Intent i) {
        ActivityController<Main> c = Robolectric.buildActivity(Main.class, i).setup();
        idle();
        return c.get();
    }

    @Test public void everything() throws Exception {
        Main m = launch(new Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com/")));
        assertNotNull(m.current);
        assertEquals("https://example.com/", m.current.url);
        assertNotNull(m.current.view);

        // the field
        m.openField(); idle();
        assertNotNull(m.overlay);
        m.dismissOverlay();
        m.go("hello world"); idle();
        assertTrue(m.current.url.contains("duckduckgo.com/?q=hello"));
        m.go("news.ycombinator.com"); idle();
        assertEquals("https://news.ycombinator.com", m.current.url);

        // tabs
        Tab a = m.current;
        Tab b = m.newTab(null, true); idle();
        assertNotNull(m.overlay);
        m.dismissOverlay();
        m.load("https://wikipedia.org"); idle();
        assertEquals(2, m.tabs.size());
        m.switcher(false); idle(); m.dismissOverlay();

        // split view, up to three
        m.split(a); idle();
        assertEquals(2, m.shown.size());
        assertSame(b, m.shown.get(0)); assertSame(a, m.shown.get(1));
        assertEquals(View.VISIBLE, m.panes[1].getVisibility());
        m.focusPane(a); assertSame(a, m.current);
        m.switcher(true); idle(); m.dismissOverlay();
        Tab c = m.newTab(null, false); c.url = "https://c.example/"; c.host = "c.example";
        m.onTabDropped(c, 2, 100); idle();
        assertNotNull(m.menu);
        java.util.List<String> drop = new java.util.ArrayList<>();
        clickAllText(m.menu.getContentView(), drop);
        System.out.println("DROP: " + drop);
        assertTrue(drop.contains("Split screen into three"));
        m.menu.dismiss();
        m.preview(c, 2); m.hint(null, 0, 0, 0, 0);
        assertTrue(m.addPane(c, 0)); idle();
        assertEquals(3, m.shown.size());
        assertSame(c, m.shown.get(0));
        assertSame(m.panes[0], c.view.getParent());
        assertSame(m.panes[2], a.view.getParent());
        assertEquals(View.VISIBLE, m.dividers[1].getVisibility());
        m.pageMenu(m.root); m.menu.dismiss();
        m.replacePane(1, a); idle();         // a was in front already: the two swap
        assertSame(a, m.shown.get(1)); assertSame(b, m.shown.get(2));
        assertSame(m.panes[1], a.view.getParent());
        m.removePane(c); idle();
        assertEquals(2, m.shown.size());
        assertNull(c.view.getParent());
        m.saveSession();
        assertTrue(m.store.session().toString().contains("\"shown\":[0,2]") || m.store.session().optJSONArray("shown").length() == 2);
        m.endSplit(); idle();
        assertEquals(1, m.shown.size()); assertSame(a, m.current);
        assertSame(m.paneA, a.view.getParent());
        assertEquals(View.GONE, m.panes[1].getVisibility());
        m.split(b); idle();
        m.close(b); idle();
        assertEquals(1, m.shown.size());
        m.close(c); idle();
        assertEquals(1, m.tabs.size());

        // updates
        assertTrue(Updater.newer("1.10", "1.9")); assertTrue(Updater.newer("1.3", "1.2"));
        assertFalse(Updater.newer("1.2", "1.2")); assertFalse(Updater.newer("1.2", "1.3"));
        Updater.Release r = Updater.parse(new org.json.JSONObject("{\"tag_name\":\"v1.4\",\"body\":\"**New** things\",\"assets\":[{\"name\":\"Seek-1.4.apk\",\"browser_download_url\":\"https://github.com/jrsnleons/seek-browser/releases/download/v1.4/Seek-1.4.apk\"}]}"));
        assertEquals("1.4", r.version);
        m.update = r; m.appMenu(m.root); m.menu.dismiss(); m.offerUpdate(); idle(); m.dismissOverlay(); m.update = null;

        // small pieces
        assertEquals(0xFFFF0000, Main.parseColor("#f00"));
        assertEquals(0xFF102030, Main.parseColor("rgb(16, 32, 48)"));
        assertEquals(0, Main.parseColor(""));
        assertTrue(Main.isFont("https://fonts.gstatic.com/s/x.woff2?v=1"));
        assertFalse(Main.isFont("https://a.example/app.js"));

        // private tab
        m.newPrivateTab(); idle(); m.dismissOverlay();
        m.load("https://secret.example"); idle();
        assertTrue(m.current.priv);
        m.saveSession();
        assertFalse(m.store.session().toString().contains("secret"));

        // menus
        m.pageMenu(m.findViewById(android.R.id.content)); idle();
        java.util.List<String> labels = new java.util.ArrayList<>();
        clickAllText(m.menu.getContentView(), labels);
        System.out.println("MENU: " + labels);
        m.menu.dismiss();
        m.appMenu(m.root); idle();
        labels.clear(); clickAllText(m.menu.getContentView(), labels);
        System.out.println("APP MENU: " + labels);
        assertTrue(labels.contains("Settings")); assertFalse(labels.contains("Find on page"));
        m.menu.dismiss();
        m.setLetters(true); assertNull(m.iconOf(m.current)); m.dismissOverlay(); m.setLetters(false); m.dismissOverlay();
        m.siteMenu(m.root, m.current); m.menu.dismiss();
        m.textSize(m.root, m.current); m.menu.dismiss();
        m.setZoom(m.current, 125);
        assertEquals(125, m.store.zoom("secret.example"));
        m.tabMenu(m.current, m.root); m.menu.dismiss();
        m.linkMenu("https://a.example/x", "https://a.example/i.png"); m.menu.dismiss();
        m.listPanel("History", m.store.history, null); m.dismissOverlay();
        m.openFind(); m.closeFind();
        m.startPicking(); m.stopPicking();
        m.floatVideo();
        m.onPictureInPictureModeChanged(true, m.getResources().getConfiguration());
        m.onPictureInPictureModeChanged(false, m.getResources().getConfiguration());
        m.fold(true); m.fold(false);
        m.reopen(); idle();
        m.onBackPressed();

        // settings, every row
        m.settings(); idle();
        labels.clear();
        clickAllText(m.overlay, labels);
        System.out.println("SETTINGS: " + labels);
        m.dismissOverlay();

        // bookmarks out and in
        m.store.toggleBookmark("https://x.example/", "X & Y");
        String html = m.store.exportBookmarks();
        m.store.removeBookmark(m.store.bookmarks.get(0));
        assertEquals(1, m.store.importBookmarks(html));
        assertEquals("X & Y", m.store.bookmarks.get(0).title);

        // blocking
        assertTrue(Shield.blocked("https://www.googletagmanager.com/gtm.js", "example.com"));
        assertFalse(Shield.blocked("https://cdn.example.com/a.js", "example.com"));
        m.saveSession();
        m.onPause();
    }

    @Test public void topAndBlack() {
        android.content.SharedPreferences p = org.robolectric.RuntimeEnvironment.getApplication().getSharedPreferences("seek", 0);
        p.edit().putBoolean("top", true).putBoolean("black", true).putInt("look", 2).putInt("wall", 3)
            .putBoolean("inter", true).putBoolean("compact", true).putBoolean("autohide", true).putBoolean("tint", true)
            .putBoolean("lite", true).putBoolean("motion", false).commit();
        Main m = launch(new Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com/")));
        assertTrue(m.top);
        assertTrue(Ui.black);
        assertEquals(0xFF000000, Ui.ground());
        assertEquals(0xFF000000, Ui.frame());
        Tab x = m.newTab(null, false); x.url = "https://b.example"; x.pinned = true;
        m.refreshTabs();
        m.split(x); idle();
        m.pageMenu(m.root); m.menu.dismiss();
        m.settings(); m.dismissOverlay();
        m.saveSession();
        // the split comes back next launch
        Main again = launch(new Intent());
        assertEquals(2, again.shown.size());
        assertTrue(again.lite);
        again.topBar(true); assertEquals(View.GONE, again.column.getVisibility());
        again.topBar(false); assertEquals(View.VISIBLE, again.column.getVisibility());
        System.out.println("restored tabs: " + again.tabs.size());
    }

    @Test public void freshStart() {
        Main m = launch(new Intent(Intent.ACTION_MAIN));
        assertEquals(1, m.tabs.size());
        assertNull(m.current.url);
        assertNotNull(m.overlay);   // the field opens straight away
        assertEquals(View.VISIBLE, m.home.getVisibility());
        m.store.visit("https://often.example/a", "A"); m.store.visit("https://often.example/b", "B");
        m.showHome();
        assertEquals(1, m.tiles.getChildCount());
        m.onBackPressed();
        assertNull(m.overlay);
    }
}
