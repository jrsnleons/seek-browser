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

    @Test public void everything() {
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

        // split view
        m.split(a); idle();
        assertSame(a, m.right); assertSame(b, m.left);
        assertEquals(View.VISIBLE, m.paneB.getVisibility());
        m.focusPane(a); assertSame(a, m.current);
        m.switcher(true); idle(); m.dismissOverlay();
        m.endSplit(); idle();
        assertNull(m.right); assertSame(a, m.current);
        assertSame(a.view.getParent(), m.paneA);
        m.split(b); idle();
        m.close(b); idle();
        assertNull(m.right);
        assertEquals(1, m.tabs.size());

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
        p.edit().putBoolean("top", true).putBoolean("black", true).putInt("look", 2).putInt("wall", 3).commit();
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
        assertNotNull(again.right);
        System.out.println("restored tabs: " + again.tabs.size());
    }

    @Test public void freshStart() {
        Main m = launch(new Intent(Intent.ACTION_MAIN));
        assertEquals(1, m.tabs.size());
        assertNull(m.current.url);
        assertNotNull(m.overlay);   // the field opens straight away
        m.onBackPressed();
        assertNull(m.overlay);
    }
}
