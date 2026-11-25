package app.seek;

import android.animation.LayoutTransition;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.DownloadManager;
import android.app.PictureInPictureParams;
import android.app.SearchManager;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Rational;
import android.util.TypedValue;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

/**
 * A row of tabs and the page. No start page, nothing that wants your attention.
 * One field: an address goes there, words search.
 */
public class Main extends Activity {

    static final int MAX_PANES = 3;
    static final long NAP_EVERY = 5 * 60 * 1000L, NAP_AFTER = 30 * 60 * 1000L;
    static final String TAB_MIME = "application/x-seek-tab";
    /** Web views kept alive besides the ones in front, set from the tablet's memory at launch. */
    int maxAwake = 4;
    static final int FILE_PICK = 7, EXPORT = 8, IMPORT = 9, WALL = 10;

    Store store;
    final List<Tab> tabs = new ArrayList<>();
    final List<String> closed = new ArrayList<>();
    /** The tab that takes the page commands. */
    Tab current;
    /** The pages in front, left to right, up to three; current is one of them. */
    final List<Tab> shown = new ArrayList<>();
    final float[] weights = {1, 1, 1};
    final Handler ui = new Handler(Looper.getMainLooper());
    final ExecutorService net = Executors.newSingleThreadExecutor();
    final ExecutorService io = Executors.newSingleThreadExecutor();

    // the frame
    boolean top;
    FrameLayout root;
    LinearLayout body, column, tabList;
    GridLayout pins;
    TextView field;
    FrameLayout page, paneA, home, dropZone, dropHint;
    final FrameLayout[] panes = new FrameLayout[MAX_PANES];
    final View[] dividers = new View[MAX_PANES - 1];
    TextView hintText;
    GridLayout tiles;
    LinearLayout stage;
    View progress;
    ImageView blank;
    Ui.Icon back, forward, reload, handle, pullDot;
    LinearLayout findBar, hideBar;
    EditText findInput;
    TextView findCount;
    FrameLayout overlay;
    View fullscreen;
    WebChromeClient.CustomViewCallback fullscreenDone;
    ValueCallback<Uri[]> filePick;
    PopupWindow menu;

    boolean folded, picking, floating, pip, barHidden, autohide, letters;
    Ui.Icon appButton;
    /** A newer Seek on GitHub, once a check has found one. */
    Updater.Release update;
    long updateDownload = -1;
    Uri updateFile;
    volatile boolean shieldOn, lite;
    int tintShown;
    Tab dragTab;
    View dragAnchor;
    boolean dragMoved, dragDropped;
    float dragX0 = -1, dragY0;
    volatile Set<String> paused = new HashSet<>();
    Set<String> awake = new HashSet<>();
    float touchX, touchY;
    String mobileUA, desktopUA;

    // ── life ────────────────────────────────────────────────────

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        store = new Store(this);
        Icons.dir = new File(getCacheDir(), "icons");
        Ui.density = getResources().getDisplayMetrics().density;
        Ui.motion = store.flag("motion", true);
        Ui.compact = store.flag("compact", false);
        fonts();
        lite = store.flag("lite", false);
        autohide = store.flag("autohide", false);
        letters = store.flag("letters", false);
        maxAwake = memoryBudget();
        Ui.dark = wantsDark(getResources().getConfiguration());
        Ui.black = Ui.dark && store.flag("black", false);
        setTheme(getResources().getIdentifier(Ui.black ? "Black" : Ui.dark ? "Dark" : "Light", "style", getPackageName()));
        shieldOn = store.flag("shield", true);
        paused = store.paused();
        awake = store.awake();
        folded = store.flag("folded", false);
        top = store.flag("top", false);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        build();
        restore();
        if (!handle(getIntent()) && tabs.isEmpty()) newTab(null, true);
        if (shieldOn && store.flag("easylist", false)) loadLists(false);
        IntentFilter done = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(downloaded, done, Context.RECEIVER_EXPORTED); else registerReceiver(downloaded, done);
        ui.postDelayed(new Runnable() { public void run() { checkUpdate(false); } }, 6000);
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handle(intent);
    }

    boolean inPip() { return Build.VERSION.SDK_INT >= 24 && isInPictureInPictureMode(); }

    void fonts() {
        Ui.font = Ui.fontMedium = null;
        if (!store.flag("inter", false)) return;
        try {
            Ui.font = Typeface.createFromAsset(getAssets(), "inter-regular.otf");
            Ui.fontMedium = Typeface.createFromAsset(getAssets(), "inter-medium.otf");
        } catch (Exception e) { Ui.font = Ui.fontMedium = null; }
    }

    /** How many pages stay loaded in the background, from how much memory the tablet has. */
    int memoryBudget() {
        try {
            ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
            if (am.isLowRamDevice()) return 1;
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            double gb = 1024.0 * 1024 * 1024;
            if (mi.totalMem <= 0) return 4;
            return mi.totalMem < 3.5 * gb ? 2 : mi.totalMem < 6 * gb ? 4 : 6;
        } catch (Exception e) { return 4; }
    }

    /** Every few minutes: tabs nobody has looked at for half an hour let go of their page. */
    final Runnable napper = new Runnable() { public void run() {
        if (store.flag("nap", true)) {
            long now = System.currentTimeMillis();
            for (Tab t : new ArrayList<>(tabs)) if (t.view != null && !keptAwake(t) && now - t.used > NAP_AFTER) sleep(t);
        }
        ui.postDelayed(this, NAP_EVERY);
    } };

    @Override protected void onPause() {
        super.onPause();
        saveSession();
        store.flush();
        CookieManager.getInstance().flush();
        ui.removeCallbacks(napper);
        if (inPip()) return;   // the floating video keeps playing
        for (Tab t : front()) if (t.view != null) t.view.onPause();
    }

    @Override protected void onResume() {
        super.onResume();
        for (Tab t : front()) if (t.view != null) t.view.onResume();
        ui.removeCallbacks(napper);
        ui.postDelayed(napper, NAP_EVERY);
        if (updateFile != null && canInstall()) install(updateFile);   // back from allowing installs
    }

    @Override protected void onDestroy() {
        for (Tab t : tabs) if (t.view != null) { t.view.destroy(); t.view = null; }
        try { unregisterReceiver(downloaded); } catch (Exception ignored) {}
        net.shutdownNow();
        io.shutdownNow();
        super.onDestroy();
    }

    @Override public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= TRIM_MEMORY_RUNNING_LOW) for (Tab t : new ArrayList<>(tabs)) if (!keptAwake(t)) sleep(t);
    }

    @Override public void onConfigurationChanged(Configuration c) {
        super.onConfigurationChanged(c);
        if (wantsDark(c) != Ui.dark && !inPip()) { saveSession(); recreate(); }
    }

    boolean wantsDark(Configuration c) {
        int look = store.look();
        if (look == 1) return false;
        if (look == 2) return true;
        return (c.uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    List<Tab> front() {
        List<Tab> l = new ArrayList<>(shown);
        if (current != null && !l.contains(current)) l.add(current);
        return l;
    }

    // ── building the frame ──────────────────────────────────────

    void bars() {
        Window w = getWindow();
        w.setStatusBarColor(Ui.frame());
        w.setNavigationBarColor(Ui.frame());
        int flags = 0;
        if (!Ui.dark) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= 26) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        w.getDecorView().setSystemUiVisibility(flags);
    }

    void build() {
        bars();
        root = new FrameLayout(this);
        root.setBackgroundColor(Ui.frame());
        tintShown = Ui.frame();

        body = new LinearLayout(this);
        body.setOrientation(top ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        root.addView(body, new FrameLayout.LayoutParams(-1, -1));

        field = Ui.text(this, "", 14, Ui.muted());
        field.setPadding(Ui.dp(12), 0, Ui.dp(12), 0);
        field.setBackground(Ui.press(Ui.tile(), 9));
        field.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { openField(); } });
        field.setOnLongClickListener(new View.OnLongClickListener() { public boolean onLongClick(View v) { switcher(false); return true; } });

        back = Ui.icon(this, Ui.Icon.BACK, 38);
        forward = Ui.icon(this, Ui.Icon.FORWARD, 38);
        reload = Ui.icon(this, Ui.Icon.RELOAD, 38);
        Ui.Icon more = Ui.icon(this, Ui.Icon.MORE, 38);
        appButton = Ui.icon(this, Ui.Icon.MENU, 38);
        appButton.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { appMenu(v); } });
        Ui.Icon fold = Ui.icon(this, Ui.Icon.FOLD, 38);
        back.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { goBack(); } });
        forward.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { goForward(); } });
        reload.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            WebView wv = live();
            if (wv == null) return;
            if (current.progress < 100) wv.stopLoading(); else wv.reload();
        } });
        more.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { pageMenu(v); } });
        fold.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { fold(true); } });

        tabList = new LinearLayout(this);
        column = new LinearLayout(this);
        if (top) {
            // tabs across the top: one row with the field and the buttons on it
            column.setOrientation(LinearLayout.HORIZONTAL);
            column.setGravity(Gravity.CENTER_VERTICAL);
            column.setPadding(Ui.dp(6), Ui.dp(4), Ui.dp(6), Ui.dp(4));
            body.addView(column, new LinearLayout.LayoutParams(-1, Ui.dp(Ui.compact ? 46 : 52)));
            column.addView(back); column.addView(forward); column.addView(reload);
            LinearLayout.LayoutParams fl = new LinearLayout.LayoutParams(Ui.dp(250), Ui.dp(36));
            fl.setMargins(Ui.dp(6), 0, Ui.dp(10), 0);
            column.addView(field, fl);
            HorizontalScrollView hs = new HorizontalScrollView(this);
            hs.setHorizontalScrollBarEnabled(false);
            hs.setOverScrollMode(View.OVER_SCROLL_NEVER);
            tabList.setOrientation(LinearLayout.HORIZONTAL);
            tabList.setGravity(Gravity.CENTER_VERTICAL);
            hs.addView(tabList, new FrameLayout.LayoutParams(-2, -1));
            column.addView(hs, new LinearLayout.LayoutParams(0, -1, 1));
            column.addView(more); column.addView(appButton); column.addView(fold);
        } else {
            column.setOrientation(LinearLayout.VERTICAL);
            column.setPadding(Ui.dp(8), Ui.dp(8), Ui.dp(8), Ui.dp(4));
            body.addView(column, new LinearLayout.LayoutParams(Ui.dp(Ui.compact ? 212 : 236), -1));
            column.addView(field, new LinearLayout.LayoutParams(-1, Ui.dp(Ui.compact ? 34 : 38)));

            pins = new GridLayout(this);
            pins.setColumnCount(5);
            LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(-1, -2);
            pl.topMargin = Ui.dp(10);
            column.addView(pins, pl);

            ScrollView scroll = new ScrollView(this);
            scroll.setVerticalScrollBarEnabled(false);
            scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
            tabList.setOrientation(LinearLayout.VERTICAL);
            scroll.addView(tabList, new FrameLayout.LayoutParams(-1, -2));
            LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-1, 0, 1);
            sl.topMargin = Ui.dp(8);
            column.addView(scroll, sl);

            LinearLayout footer = new LinearLayout(this);
            footer.setOrientation(LinearLayout.HORIZONTAL);
            footer.setGravity(Gravity.CENTER_VERTICAL);
            footer.addView(back); footer.addView(forward); footer.addView(reload);
            footer.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
            footer.addView(more); footer.addView(appButton); footer.addView(fold);
            column.addView(footer, new LinearLayout.LayoutParams(-1, Ui.dp(44)));
        }

        if (Ui.motion) {
            tabList.setLayoutTransition(quick());
            if (pins != null) pins.setLayoutTransition(quick());
        }

        // the page, a card beside the tabs; with Split View, up to three
        page = new FrameLayout(this);
        body.addView(page, top ? new LinearLayout.LayoutParams(-1, 0, 1) : new LinearLayout.LayoutParams(0, -1, 1));

        stage = new LinearLayout(this);
        stage.setOrientation(LinearLayout.HORIZONTAL);
        page.addView(stage, new FrameLayout.LayoutParams(-1, -1));
        for (int i = 0; i < MAX_PANES; i++) {
            if (i > 0) {
                dividers[i - 1] = buildDivider(i - 1);
                dividers[i - 1].setVisibility(View.GONE);
                stage.addView(dividers[i - 1], new LinearLayout.LayoutParams(Ui.dp(10), -1));
            }
            panes[i] = pane();
            if (i > 0) panes[i].setVisibility(View.GONE);
            stage.addView(panes[i], new LinearLayout.LayoutParams(0, -1, 1));
        }
        paneA = panes[0];
        if (Ui.motion) {
            LayoutTransition lt = new LayoutTransition();
            lt.enableTransitionType(LayoutTransition.CHANGING);
            lt.setDuration(170);
            stage.setLayoutTransition(lt);
        }

        // the empty tab: the wall, and the sites you visit most
        home = new FrameLayout(this);
        blank = new ImageView(this);
        blank.setScaleType(ImageView.ScaleType.CENTER_CROP);
        home.addView(blank, new FrameLayout.LayoutParams(-1, -1));
        home.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { openField(); } });
        tiles = new GridLayout(this);
        FrameLayout.LayoutParams tl = new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER_HORIZONTAL | Gravity.TOP);
        tl.topMargin = Ui.dp(110);
        home.addView(tiles, tl);
        paneA.addView(home, new FrameLayout.LayoutParams(-1, -1));
        wallpaper();

        buildDropZone();

        progress = new View(this);
        progress.setBackgroundColor(Ui.ink());
        progress.setPivotX(0);
        progress.setVisibility(View.GONE);
        page.addView(progress, new FrameLayout.LayoutParams(-1, Ui.dp(2), Gravity.TOP));

        pullDot = Ui.icon(this, Ui.Icon.RELOAD, 38);
        pullDot.setBackground(Ui.card());
        pullDot.setElevation(Ui.dp(4));
        pullDot.setVisibility(View.GONE);
        page.addView(pullDot, new FrameLayout.LayoutParams(Ui.dp(38), Ui.dp(38), Gravity.TOP | Gravity.CENTER_HORIZONTAL));

        buildFindBar();
        buildHideBar();

        handle = Ui.icon(this, Ui.Icon.FOLD, 42);
        handle.setBackground(Ui.press(Ui.dark ? Ui.wash() : Ui.frame(), 21));
        handle.setElevation(Ui.dp(3));
        FrameLayout.LayoutParams hl = new FrameLayout.LayoutParams(Ui.dp(42), Ui.dp(42), Gravity.BOTTOM | Gravity.LEFT);
        hl.leftMargin = Ui.dp(12); hl.bottomMargin = Ui.dp(14);
        root.addView(handle, hl);
        handle.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { fold(false); } });
        handle.setOnLongClickListener(new View.OnLongClickListener() { public boolean onLongClick(View v) { switcher(false); return true; } });

        root.setOnDragListener(rootDrag);
        setContentView(root);
        fold(folded);
    }

    static LayoutTransition quick() {
        LayoutTransition lt = new LayoutTransition();
        lt.setDuration(150);
        return lt;
    }

    FrameLayout pane() {
        FrameLayout p = new FrameLayout(this);
        p.setBackground(Ui.round(Ui.ground(), 12));
        p.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        p.setClipToOutline(true);
        return p;
    }

    View buildDivider(final int k) {
        FrameLayout d = new FrameLayout(this);
        View grip = new View(this);
        grip.setBackground(Ui.round(Ui.faint(), 2));
        d.addView(grip, new FrameLayout.LayoutParams(Ui.dp(3), Ui.dp(36), Gravity.CENTER));
        d.setOnTouchListener(new View.OnTouchListener() { public boolean onTouch(View v, MotionEvent e) {
            LayoutTransition lt = stage.getLayoutTransition();
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    if (lt != null) lt.disableTransitionType(LayoutTransition.CHANGING);
                    break;
                case MotionEvent.ACTION_MOVE: {
                    View a = panes[k], b = panes[k + 1];
                    int[] at = new int[2];
                    stage.getLocationOnScreen(at);
                    float x = e.getRawX() - at[0];
                    float from = a.getLeft(), to = b.getRight();
                    float f = Math.max(0.15f, Math.min(0.85f, (x - from) / Math.max(1f, to - from)));
                    float sum = weights[k] + weights[k + 1];
                    weights[k] = sum * f;
                    weights[k + 1] = sum - weights[k];
                    applyWeights();
                    break;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (lt != null) lt.enableTransitionType(LayoutTransition.CHANGING);
                    saveSoon();
                    break;
            }
            return true;
        } });
        return d;
    }

    void applyWeights() {
        for (int i = 0; i < MAX_PANES; i++) {
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) panes[i].getLayoutParams();
            lp.weight = i < shown.size() ? weights[i] : 1;
            panes[i].setLayoutParams(lp);
        }
    }

    /** Puts each page in front into its pane, and shows as many panes as there are pages. */
    void layoutPanes() {
        int n = shown.size();
        for (int i = 0; i < MAX_PANES; i++) {
            FrameLayout p = panes[i];
            Tab t = i < n ? shown.get(i) : null;
            for (int c = p.getChildCount() - 1; c >= 0; c--) {
                View ch = p.getChildAt(c);
                if (ch instanceof WebView && (t == null || ch != t.view)) p.removeViewAt(c);
            }
            if (t != null && t.view != null && t.view.getParent() != p) {
                detach(t.view);
                p.addView(t.view, 0, new FrameLayout.LayoutParams(-1, -1));
            }
            boolean on = t != null && (!pip || n == 1 || t == current);
            p.setVisibility(on ? View.VISIBLE : View.GONE);
            if (i > 0) dividers[i - 1].setVisibility(on && !pip ? View.VISIBLE : View.GONE);
        }
        applyWeights();
        showHome();
        fold(folded);
    }

    /** The start page shows in the first pane while its tab has no page yet. */
    void showHome() {
        Tab t = shown.isEmpty() ? null : shown.get(0);
        boolean on = t == null || t.view == null;
        home.setVisibility(on ? View.VISIBLE : View.GONE);
        if (on) fillTiles();
    }

    static String letterOf(String host) {
        if (host == null || host.isEmpty()) return "\u00b7";
        if (host.startsWith("www.") && host.length() > 4) host = host.substring(4);
        return host.substring(0, 1).toUpperCase();
    }

    /** Tiles for the sites you go to most: bookmarks first, then the ones in your history. */
    void fillTiles() {
        tiles.removeAllViews();
        if (!store.flag("home", true) || (current != null && current.priv)) return;
        List<Store.Entry> top = store.topSites(12);
        if (top.isEmpty()) return;
        int width = paneA.getWidth() > 0 ? paneA.getWidth() : getResources().getDisplayMetrics().widthPixels - Ui.dp(260);
        int cols = Math.max(2, Math.min(6, width / Ui.dp(112)));
        tiles.setColumnCount(Math.min(cols, top.size()));
        boolean photo = store.prefs.getInt("wall", 0) == WALLS.length - 1;
        for (final Store.Entry e : top) {
            String host = Tab.hostOf(e.url);
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            cell.setPadding(Ui.dp(6), Ui.dp(10), Ui.dp(6), Ui.dp(8));
            cell.setBackground(Ui.press(photo ? (Ui.ground() & 0x00FFFFFF) | 0xD9000000 : 0, 12));
            FrameLayout chip = new FrameLayout(this);
            chip.setBackground(Ui.round(Ui.dark ? Ui.wash() : Ui.ground(), 14));
            chip.setElevation(Ui.dp(1));
            Ui.Letter icon = new Ui.Letter(this);
            icon.set(letterOf(host), letters ? null : Icons.get(host));
            chip.addView(icon, new FrameLayout.LayoutParams(Ui.dp(24), Ui.dp(24), Gravity.CENTER));
            cell.addView(chip, new LinearLayout.LayoutParams(Ui.dp(52), Ui.dp(52)));
            String name = host == null ? e.title : host.startsWith("www.") ? host.substring(4) : host;
            TextView label = Ui.text(this, name, 12, photo ? Ui.ink() : Ui.muted());
            label.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(Ui.dp(88), Ui.dp(16));
            ll.topMargin = Ui.dp(8);
            cell.addView(label, ll);
            cell.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { load(e.url); } });
            GridLayout.LayoutParams gl = new GridLayout.LayoutParams();
            gl.setMargins(Ui.dp(4), Ui.dp(4), Ui.dp(4), Ui.dp(4));
            tiles.addView(cell, gl);
        }
    }

    void fold(boolean f) {
        if (f != folded) { folded = f; store.set("folded", f); }
        boolean hide = f || pip;
        column.setVisibility(hide || barHidden ? View.GONE : View.VISIBLE);
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) page.getLayoutParams();
        int m = hide ? 0 : Ui.dp(8);
        if (top) lp.setMargins(m, barHidden && !hide ? m : 0, m, m); else lp.setMargins(0, m, m, m);
        page.setLayoutParams(lp);
        float r = hide && shown.size() < 2 ? 0 : 12;
        for (int i = 0; i < MAX_PANES; i++) panes[i].setBackground(Ui.round(Ui.ground(), i == 0 ? r : 12));
        handle.setVisibility(f && !pip ? View.VISIBLE : View.GONE);
        handle.setAlpha(0.85f);
        outlines();
    }

    /** Tabs across the top: the bar slides away while you read down a page, and comes back when you scroll up. */
    void topBar(boolean hide) {
        if (hide == barHidden) return;
        barHidden = hide;
        fold(folded);
    }

    /** With pages side by side, the one that gets page commands wears a thin outline. */
    void outlines() {
        boolean on = shown.size() > 1 && !pip;
        GradientDrawable o = null;
        if (on) {
            o = new GradientDrawable();
            o.setColor(0);
            o.setCornerRadius(Ui.dp(12));
            o.setStroke(Ui.dp(1.5f), Ui.dark ? 0xFF5A5A5A : 0xFFB8B8B8);
        }
        for (int i = 0; i < MAX_PANES; i++) panes[i].setForeground(on && i < shown.size() && shown.get(i) == current ? o : null);
    }

    // ── dragging a tab onto the page ────────────────────────────

    void buildDropZone() {
        dropZone = new FrameLayout(this);
        dropHint = new FrameLayout(this);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(Ui.dp(12));
        g.setColor(Ui.dark ? 0x22FFFFFF : 0x14000000);
        g.setStroke(Ui.dp(2), Ui.dark ? 0x88FFFFFF : 0x55000000);
        dropHint.setBackground(g);
        hintText = Ui.text(this, "", 15, Ui.ink());
        hintText.setTypeface(Ui.medium());
        hintText.setPadding(Ui.dp(14), Ui.dp(8), Ui.dp(14), Ui.dp(8));
        hintText.setBackground(Ui.card());
        dropHint.addView(hintText, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        dropHint.setVisibility(View.GONE);
        dropZone.addView(dropHint, new FrameLayout.LayoutParams(0, 0));
        dropZone.setOnDragListener(new View.OnDragListener() { public boolean onDrag(View v, DragEvent e) {
            if (!(e.getLocalState() instanceof Tab)) return false;
            Tab t = (Tab) e.getLocalState();
            switch (e.getAction()) {
                case DragEvent.ACTION_DRAG_ENTERED: dragMoved = true; break;
                case DragEvent.ACTION_DRAG_LOCATION: preview(t, e.getX()); break;
                case DragEvent.ACTION_DRAG_EXITED:
                case DragEvent.ACTION_DRAG_ENDED: hint(null, 0, 0, 0, 0); break;
                case DragEvent.ACTION_DROP:
                    dragMoved = true; dragDropped = true;
                    hint(null, 0, 0, 0, 0);
                    onTabDropped(t, e.getX(), e.getY());
                    break;
            }
            return true;
        } });
        page.addView(dropZone, new FrameLayout.LayoutParams(-1, -1));
    }

    /** Hold a tab and it lifts; let go over the page to split. Held without moving, it shows the tab's menu. */
    void held(Tab t, View v) {
        if (!startTabDrag(t, v)) tabMenu(t, v);
    }

    boolean startTabDrag(Tab t, View v) {
        if (Build.VERSION.SDK_INT < 24 || pip) return false;
        ClipData data = new ClipData(new ClipDescription(t.label(), new String[]{TAB_MIME}), new ClipData.Item(t.url == null ? "" : t.url));
        dragTab = t; dragAnchor = v; dragMoved = false; dragDropped = false; dragX0 = -1;
        try {
            return v.startDragAndDrop(data, new View.DragShadowBuilder(v), t, 0);
        } catch (Exception e) { dragTab = null; return false; }
    }

    /** Anywhere in the window: notices whether a held tab moved at all; if it did not, the tab's menu opens. */
    final View.OnDragListener rootDrag = new View.OnDragListener() { public boolean onDrag(View v, DragEvent e) {
        if (!(e.getLocalState() instanceof Tab)) return false;
        switch (e.getAction()) {
            case DragEvent.ACTION_DRAG_LOCATION:
                if (dragX0 < 0) { dragX0 = e.getX(); dragY0 = e.getY(); }
                else if (Math.hypot(e.getX() - dragX0, e.getY() - dragY0) > Ui.dp(24)) dragMoved = true;
                break;
            case DragEvent.ACTION_DRAG_ENDED: {
                final Tab t = dragTab;
                final View a = dragAnchor;
                boolean menu = t != null && !dragMoved && !dragDropped;
                dragTab = null; dragAnchor = null;
                hint(null, 0, 0, 0, 0);
                if (menu && tabs.contains(t) && a != null && a.isAttachedToWindow())
                    ui.post(new Runnable() { public void run() { tabMenu(t, a); } });
                break;
            }
        }
        return true;
    } };

    int paneAt(float x) {
        for (int i = 0; i < shown.size(); i++) if (panes[i].getVisibility() == View.VISIBLE && x < panes[i].getRight()) return i;
        return Math.max(0, shown.size() - 1);
    }

    /** Shows where a tab let go at x would land: half a pane for a new split, the whole pane to open it there. */
    void preview(Tab t, float x) {
        if (shown.isEmpty()) return;
        int i = paneAt(x);
        if (shown.get(i) == t) { hint(null, 0, 0, 0, 0); return; }
        View p = panes[i];
        int l = p.getLeft(), r = p.getRight(), tp = p.getTop(), b = p.getBottom();
        if (shown.size() < MAX_PANES && !shown.contains(t)) {
            int mid = (l + r) / 2;
            if (x < mid) hint("Split screen", l, tp, mid, b); else hint("Split screen", mid, tp, r, b);
        } else hint(shown.contains(t) ? "Swap places" : "Open here", l, tp, r, b);
    }

    void hint(String label, int l, int t, int r, int b) {
        if (dropHint == null) return;
        if (label == null) { dropHint.setVisibility(View.GONE); return; }
        int in = Ui.dp(6);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) dropHint.getLayoutParams();
        if (lp.leftMargin != l + in || lp.width != Math.max(0, r - l - 2 * in) || lp.height != Math.max(0, b - t - 2 * in)) {
            lp.leftMargin = l + in; lp.topMargin = t + in;
            lp.width = Math.max(0, r - l - 2 * in); lp.height = Math.max(0, b - t - 2 * in);
            dropHint.setLayoutParams(lp);
        }
        hintText.setText(label);
        if (dropHint.getVisibility() != View.VISIBLE) {
            dropHint.setVisibility(View.VISIBLE);
            dropHint.bringToFront();
            if (Ui.motion) { dropHint.setAlpha(0); dropHint.animate().alpha(1).setDuration(120); }
        }
    }

    /** A tab let go over the page: asks whether to split the screen or open it there. */
    void onTabDropped(final Tab t, float x, float y) {
        if (t == null || !tabs.contains(t) || shown.isEmpty()) return;
        final int i = paneAt(x);
        if (shown.get(i) == t) return;
        View p = panes[i];
        final boolean before = x < (p.getLeft() + p.getRight()) / 2f;
        List<String> l = new ArrayList<>();
        List<Runnable> a = new ArrayList<>();
        if (shown.size() < MAX_PANES && !shown.contains(t)) {
            l.add(shown.size() == 1 ? "Split screen" : "Split screen into three");
            a.add(new Runnable() { public void run() {
                if (addPane(t, before ? i : i + 1)) { focusPane(t); if (t.url == null) openField(); }
            } });
        }
        if (shown.contains(t)) { l.add("Swap places"); a.add(new Runnable() { public void run() { replacePane(i, t); } }); }
        else { l.add(shown.size() > 1 ? "Open in this pane" : "Open here"); a.add(new Runnable() { public void run() {
            replacePane(i, t); if (t.url == null) openField(); } }); }
        l.add(null); a.add(null);
        l.add("Cancel"); a.add(null);
        int[] at = new int[2];
        page.getLocationInWindow(at);
        popupAt(at[0] + (int) x, at[1] + (int) y, l.toArray(new String[0]), a.toArray(new Runnable[0]));
    }

    /** The empty tab: the page's own ground, or the wall chosen in Settings. */
    static final String[] WALLS = {"None", "Paper", "Sage", "Dusk", "Sea", "Sand", "Photo…"};
    static final int[][] WALL_LIGHT = {{}, {0xFFF4F1EA, 0xFFF4F1EA}, {0xFFDDE6D8, 0xFFF4F1E8}, {0xFFEAD9E5, 0xFFD6DCF0}, {0xFFD3E6EE, 0xFFF1F6F4}, {0xFFF0E1CC, 0xFFF8F2E8}};
    static final int[][] WALL_DARK = {{}, {0xFF1A1815, 0xFF1A1815}, {0xFF17221A, 0xFF0B0F0C}, {0xFF261B2C, 0xFF121829}, {0xFF0E2129, 0xFF070E11}, {0xFF2A2015, 0xFF120E09}};

    void wallpaper() {
        int w = Math.max(0, Math.min(WALLS.length - 1, store.prefs.getInt("wall", 0)));
        blank.setImageDrawable(null);
        if (w == 0) { blank.setBackgroundColor(Ui.ground()); return; }
        if (w == WALLS.length - 1) {
            blank.setBackgroundColor(Ui.ground());
            File f = new File(getFilesDir(), "wall.jpg");
            if (f.exists()) {
                Bitmap b = BitmapFactory.decodeFile(f.getPath());
                if (b != null) blank.setImageBitmap(b);
            }
            return;
        }
        int[] c = (Ui.dark ? WALL_DARK : WALL_LIGHT)[w];
        blank.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR, c));
    }

    void buildFindBar() {
        findBar = new LinearLayout(this);
        findBar.setOrientation(LinearLayout.HORIZONTAL);
        findBar.setGravity(Gravity.CENTER_VERTICAL);
        findBar.setBackground(Ui.card());
        findBar.setElevation(Ui.dp(4));
        findBar.setPadding(Ui.dp(14), 0, Ui.dp(6), 0);
        findInput = input("Find on page", 15);
        findBar.addView(findInput, new LinearLayout.LayoutParams(0, -1, 1));
        findCount = Ui.text(this, "", 13, Ui.muted());
        findCount.setPadding(Ui.dp(8), 0, Ui.dp(8), 0);
        findBar.addView(findCount, new LinearLayout.LayoutParams(-2, -1));
        Ui.Icon up = Ui.icon(this, Ui.Icon.UP, 36), down = Ui.icon(this, Ui.Icon.DOWN, 36), x = Ui.icon(this, Ui.Icon.CLOSE, 36);
        findBar.addView(up); findBar.addView(down); findBar.addView(x);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Ui.dp(420), Ui.dp(48), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        lp.bottomMargin = Ui.dp(14);
        findBar.setVisibility(View.GONE);
        page.addView(findBar, lp);
        findInput.addTextChangedListener(new Watch() { public void afterTextChanged(Editable s) {
            if (live() != null) { live().findAllAsync(s.toString()); if (s.length() == 0) findCount.setText(""); }
        } });
        findInput.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        findInput.setOnEditorActionListener(new TextView.OnEditorActionListener() { public boolean onEditorAction(TextView v, int a, KeyEvent e) {
            if (live() != null) live().findNext(true); return true; } });
        up.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { if (live() != null) live().findNext(false); } });
        down.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { if (live() != null) live().findNext(true); } });
        x.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { closeFind(); } });
    }

    void openFind() {
        if (live() == null) return;
        findBar.setVisibility(View.VISIBLE);
        findBar.bringToFront();
        findInput.requestFocus();
        keyboard(findInput, true);
        if (findInput.length() > 0) live().findAllAsync(findInput.getText().toString());
    }

    void closeFind() {
        findBar.setVisibility(View.GONE);
        keyboard(findInput, false);
        if (live() != null) live().clearMatches();
    }

    void buildHideBar() {
        hideBar = new LinearLayout(this);
        hideBar.setOrientation(LinearLayout.HORIZONTAL);
        hideBar.setGravity(Gravity.CENTER_VERTICAL);
        hideBar.setBackground(Ui.card());
        hideBar.setElevation(Ui.dp(4));
        hideBar.setPadding(Ui.dp(16), 0, Ui.dp(6), 0);
        TextView t = Ui.text(this, "Tap anything to hide it, here and on every visit", 14, Ui.ink());
        hideBar.addView(t, new LinearLayout.LayoutParams(0, -1, 1));
        TextView done = Ui.text(this, "Done", 14, Ui.ink());
        done.setTypeface(Ui.medium());
        done.setPadding(Ui.dp(14), 0, Ui.dp(14), 0);
        done.setBackground(Ui.press(Ui.tile(), 8));
        hideBar.addView(done, new LinearLayout.LayoutParams(-2, Ui.dp(34)));
        done.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { stopPicking(); } });
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Ui.dp(460), Ui.dp(50), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        lp.bottomMargin = Ui.dp(14);
        hideBar.setVisibility(View.GONE);
        page.addView(hideBar, lp);
    }

    void startPicking() {
        if (live() == null) return;
        picking = true;
        hideBar.setVisibility(View.VISIBLE);
        hideBar.bringToFront();
        live().evaluateJavascript(Scripts.PICK, null);
    }

    void stopPicking() {
        picking = false;
        hideBar.setVisibility(View.GONE);
        for (Tab t : front()) if (t.view != null) t.view.evaluateJavascript(Scripts.UNPICK, null);
    }

    // ── tabs ────────────────────────────────────────────────────

    WebView live() { return current == null ? null : current.view; }

    Tab newTab(String url, boolean select) {
        Tab t = new Tab(url, null, false);
        int at = current == null ? tabs.size() : tabs.indexOf(current) + 1;
        if (current != null && current.pinned) at = tabs.size();
        tabs.add(Math.max(0, Math.min(at, tabs.size())), t);
        if (select) select(t);
        else refreshTabs();
        if (select && url == null) openField();
        saveSoon();
        return t;
    }

    void newPrivateTab() {
        Tab t = new Tab(null, null, false);
        t.priv = true;
        tabs.add(t);
        select(t);
        openField();
    }

    static void detach(View v) {
        if (v != null && v.getParent() != null) ((ViewGroup) v.getParent()).removeView(v);
    }

    void select(Tab t) {
        if (picking) stopPicking();
        if (findBar.getVisibility() == View.VISIBLE) closeFind();
        t.used = System.currentTimeMillis();
        if (shown.size() > 1 && shown.contains(t)) {   // one of the pages in front: it just takes the page commands
            current = t;
            outlines(); refreshTabs(); refreshChrome();
            return;
        }
        if (shown.size() > 1) endSplit();
        Tab old = shown.isEmpty() ? null : shown.get(0);
        if (old != null && old != t && old.view != null) { background(old); detach(old.view); }
        shown.clear();
        shown.add(t);
        current = t;
        barHidden = false;
        if (t.view == null && t.url != null) wake(t);
        layoutPanes();
        if (t.view != null) {
            t.view.onResume();
            t.view.requestFocus();
            if (Ui.motion && old != t) { t.view.setAlpha(0.5f); t.view.animate().alpha(1).setDuration(150); }
        }
        trim();
        refreshTabs();
        refreshChrome();
        saveSoon();
    }

    /** A page leaving the front is paused, unless its site is kept awake. */
    void background(Tab t) {
        if (t.view == null) return;
        if (t.host != null && awake.contains(t.host)) return;
        t.view.onPause();
    }

    /** Builds a page's view if it has none: its address, or a blank page for a new tab. */
    void ensureView(Tab t) {
        if (t.view != null) return;
        if (t.url == null) t.view = makeView(t); else wake(t);
    }

    /** Another page beside the ones in front, at position at (0 is the left). Three at most. */
    boolean addPane(Tab t, int at) {
        if (t == null || shown.contains(t)) return false;
        if (current == null || shown.isEmpty()) { select(t); return true; }
        if (shown.size() >= MAX_PANES) { toast("Three pages side by side at most"); return false; }
        for (Tab o : shown) ensureView(o);
        ensureView(t);
        t.used = System.currentTimeMillis();
        shown.add(Math.max(0, Math.min(at, shown.size())), t);
        Arrays.fill(weights, 1);
        layoutPanes();
        t.view.onResume();
        trim();
        refreshTabs();
        refreshChrome();
        saveSoon();
        return true;
    }

    /** Split View: this page beside the one in front, on its right. */
    void split(Tab other) {
        if (other == null || other == current) return;
        addPane(other, shown.indexOf(current) + 1);
    }

    /** Puts a tab in pane i instead of the page there; if it is already in front elsewhere, the two swap. */
    void replacePane(int i, Tab t) {
        if (shown.size() < 2) { select(t); return; }
        if (i < 0 || i >= shown.size()) return;
        Tab old = shown.get(i);
        if (old == t) return;
        int j = shown.indexOf(t);
        if (j >= 0) {
            shown.set(j, old);
            shown.set(i, t);
        } else {
            ensureView(t);
            shown.set(i, t);
            if (old.view != null) { background(old); detach(old.view); }
            if (t.view != null) t.view.onResume();
        }
        current = t;
        t.used = System.currentTimeMillis();
        layoutPanes();
        trim();
        refreshTabs();
        refreshChrome();
        saveSoon();
    }

    /** One page leaves Split View; its tab stays open. */
    void removePane(Tab t) {
        int i = shown.indexOf(t);
        if (shown.size() < 2 || i < 0) return;
        shown.remove(i);
        if (t.view != null) { background(t); detach(t.view); }
        if (current == t) current = shown.get(Math.min(i, shown.size() - 1));
        Arrays.fill(weights, 1);
        layoutPanes();
        trim();
        refreshTabs();
        refreshChrome();
        saveSoon();
    }

    /** Back to one page: the one that had the page commands keeps the whole area. */
    void endSplit() {
        if (shown.size() < 2) return;
        for (Tab o : new ArrayList<>(shown)) {
            if (o == current) continue;
            shown.remove(o);
            if (o.view != null) { background(o); detach(o.view); }
        }
        Arrays.fill(weights, 1);
        layoutPanes();
        trim();
        refreshTabs();
        refreshChrome();
        saveSoon();
    }

    void focusPane(Tab t) {
        if (shown.size() < 2 || t == current || !shown.contains(t)) return;
        current = t;
        t.used = System.currentTimeMillis();
        outlines(); refreshTabs(); refreshChrome();
    }

    void close(Tab t) {
        int i = tabs.indexOf(t);
        if (i < 0) return;
        if (shown.size() > 1 && shown.contains(t)) removePane(t);
        if (t.url != null && !t.priv) { closed.add(t.url); if (closed.size() > 20) closed.remove(0); }
        tabs.remove(i);
        if (t.view != null) {
            detach(t.view);
            t.view.destroy();
            t.view = null;
        }
        if (t == current) {
            current = null;
            shown.clear();
            Tab next = null;
            if (t.opener != null && tabs.contains(t.opener)) next = t.opener;
            else if (!tabs.isEmpty()) next = tabs.get(Math.min(i, tabs.size() - 1));
            if (next != null) select(next);
            else newTab(null, true);
        } else refreshTabs();
        saveSoon();
    }

    void reopen() {
        if (closed.isEmpty()) return;
        String u = closed.remove(closed.size() - 1);
        Tab t = newTab(null, false);
        t.url = u;
        t.host = Tab.hostOf(u);
        select(t);
    }

    boolean keptAwake(Tab t) {
        return t == current || shown.contains(t) || (t.host != null && awake.contains(t.host));
    }

    /** Put to sleep: the page goes, the tab and its address stay. */
    void sleep(Tab t) {
        if (t.view == null || t == current || shown.contains(t)) return;
        detach(t.view);
        t.view.destroy();
        t.view = null;
    }

    void trim() {
        List<Tab> sleepy = new ArrayList<>();
        for (Tab t : tabs) if (t.view != null && !keptAwake(t)) sleepy.add(t);
        while (sleepy.size() > maxAwake) {
            Tab oldest = sleepy.get(0);
            for (Tab t : sleepy) if (t.used < oldest.used) oldest = t;
            sleepy.remove(oldest);
            sleep(oldest);
        }
    }

    void wake(Tab t) {
        t.view = makeView(t);
        if (t.url != null) t.view.loadUrl(t.url);
    }

    void load(String url) {
        if (current == null) select(newTab(null, false));
        Tab t = current;
        t.url = url;
        t.host = Tab.hostOf(url);
        if (t.view == null) {
            if (shown.size() > 1) { ensureView(t); layoutPanes(); }
            else select(t);   // builds and loads it
        } else t.view.loadUrl(url);
        showHome();
        refreshChrome();
    }

    // ── the row of tabs ─────────────────────────────────────────

    /** A tab's row, made once and then kept up to date in place. */
    static final class Row {
        View view, close;
        Ui.Letter icon;
        TextView title, tag;
        boolean pinned;
        int state = -1;
    }

    final Map<Tab, Row> rows = new HashMap<>();
    View addRow;

    /** The site's icon, or nothing so a letter shows, when Settings asks for letters like Search. */
    Bitmap iconOf(Tab t) { return letters ? null : t.icon != null ? t.icon : Icons.get(t.host); }

    /** Brings the list in line with the tabs, touching only the rows that changed. */
    void refreshTabs() {
        List<View> list = new ArrayList<>(), grid = new ArrayList<>();
        Set<Tab> open = new HashSet<>(tabs);
        for (Tab t : new ArrayList<>(rows.keySet())) if (!open.contains(t)) rows.remove(t);
        for (Tab t : tabs) {
            Row r = rows.get(t);
            if (r == null || r.pinned != t.pinned) { r = t.pinned ? makePin(t) : makeRow(t); rows.put(t, r); }
            bind(t, r);
            if (t.pinned && pins != null) grid.add(r.view); else list.add(r.view);
        }
        if (addRow == null) addRow = makeAdd();
        list.add(addRow);
        sync(tabList, list);
        if (pins != null) {
            sync(pins, grid);
            pins.setVisibility(grid.isEmpty() ? View.GONE : View.VISIBLE);
        }
    }

    /** Makes g's children exactly want, in order, moving only what has to move. */
    static void sync(ViewGroup g, List<View> want) {
        Set<View> keep = new HashSet<>(want);
        for (int i = g.getChildCount() - 1; i >= 0; i--) if (!keep.contains(g.getChildAt(i))) g.removeViewAt(i);
        for (int i = 0; i < want.size(); i++) {
            View v = want.get(i);
            if (i < g.getChildCount() && g.getChildAt(i) == v) continue;
            if (v.getParent() != null) ((ViewGroup) v.getParent()).removeView(v);
            g.endViewTransition(v);
            g.addView(v, Math.min(i, g.getChildCount()));
        }
    }

    void updateRow(Tab t) {
        Row r = rows.get(t);
        if (r != null) bind(t, r);
    }

    /** Title, icon, and whether it is in front; a drawable is only replaced when that changes. */
    void bind(Tab t, Row r) {
        boolean live = t == current;
        boolean beside = !live && shown.size() > 1 && shown.contains(t);
        int state = live ? 2 : beside ? 1 : 0;
        if (state != r.state) {
            r.state = state;
            if (r.pinned) r.view.setBackground(Ui.press(live ? Ui.wash() : Ui.tile(), 9));
            else r.view.setBackground(Ui.press(live ? Ui.wash() : beside ? Ui.tile() : 0, 8));
            if (r.title != null) r.title.setTextColor(live ? Ui.ink() : Ui.muted());
            if (r.close != null) r.close.setVisibility(live ? View.VISIBLE : View.GONE);
        }
        r.icon.set(t.letter(), iconOf(t));
        if (r.title != null) {
            String l = t.label();
            if (!l.contentEquals(r.title.getText())) r.title.setText(l);
            r.tag.setVisibility(t.priv ? View.VISIBLE : View.GONE);
        }
    }

    Row makeRow(final Tab t) {
        Row r = new Row();
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(8), 0, Ui.dp(2), 0);
        r.icon = new Ui.Letter(this);
        row.addView(r.icon, new LinearLayout.LayoutParams(Ui.dp(16), Ui.dp(16)));
        r.title = Ui.text(this, "", Ui.textSp(), Ui.muted());
        r.title.setPadding(Ui.dp(10), 0, Ui.dp(4), 0);
        row.addView(r.title, new LinearLayout.LayoutParams(0, -1, 1));
        r.tag = Ui.text(this, "private", 11, Ui.muted());
        r.tag.setPadding(Ui.dp(6), Ui.dp(2), Ui.dp(6), Ui.dp(2));
        r.tag.setBackground(Ui.round(Ui.hairline(), 6));
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-2, -2);
        tl.rightMargin = Ui.dp(4);
        row.addView(r.tag, tl);
        Ui.Icon x = Ui.icon(this, Ui.Icon.CLOSE, Ui.rowDp() - 6);
        x.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { close(t); } });
        row.addView(x);
        r.close = x;
        row.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { if (t != current) select(t); } });
        row.setOnLongClickListener(new View.OnLongClickListener() { public boolean onLongClick(View v) { held(t, v); return true; } });
        if (!top) swipeToClose(row, t);
        int h = Ui.dp(Ui.rowDp());
        LinearLayout.LayoutParams lp = top ? new LinearLayout.LayoutParams(Ui.dp(Ui.compact ? 170 : 190), h) : new LinearLayout.LayoutParams(-1, h);
        if (top) lp.rightMargin = Ui.dp(4); else lp.bottomMargin = Ui.dp(2);
        row.setLayoutParams(lp);
        r.view = row;
        return r;
    }

    Row makePin(final Tab t) {
        Row r = new Row();
        r.pinned = true;
        FrameLayout tile = new FrameLayout(this);
        r.icon = new Ui.Letter(this);
        tile.addView(r.icon, new FrameLayout.LayoutParams(Ui.dp(16), Ui.dp(16), Gravity.CENTER));
        tile.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { if (t != current) select(t); } });
        tile.setOnLongClickListener(new View.OnLongClickListener() { public boolean onLongClick(View v) { held(t, v); return true; } });
        int w = Ui.dp(38), h = Ui.dp(Ui.rowDp() - 2);
        if (pins == null) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, h);
            lp.rightMargin = Ui.dp(4);
            tile.setLayoutParams(lp);
        } else {
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = w; lp.height = h;
            lp.setMargins(0, 0, Ui.dp(6), Ui.dp(6));
            tile.setLayoutParams(lp);
        }
        r.view = tile;
        return r;
    }

    /** New tab, faint, at the end of the row; held down, a private one. */
    View makeAdd() {
        LinearLayout add = new LinearLayout(this);
        add.setOrientation(LinearLayout.HORIZONTAL);
        add.setGravity(Gravity.CENTER_VERTICAL);
        add.setPadding(Ui.dp(6), 0, Ui.dp(8), 0);
        add.setBackground(Ui.press(0, 8));
        Ui.Icon plus = new Ui.Icon(this, Ui.Icon.PLUS);
        plus.tint(Ui.faint());
        add.addView(plus, new LinearLayout.LayoutParams(Ui.dp(22), Ui.dp(22)));
        if (!top) {
            TextView l = Ui.text(this, "New tab", Ui.textSp(), Ui.faint());
            l.setPadding(Ui.dp(8), 0, 0, 0);
            add.addView(l);
        }
        add.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { newTab(null, true); } });
        add.setOnLongClickListener(new View.OnLongClickListener() { public boolean onLongClick(View v) { newPrivateTab(); return true; } });
        add.setLayoutParams(new LinearLayout.LayoutParams(top ? Ui.dp(36) : -1, Ui.dp(Ui.rowDp())));
        return add;
    }

    /** Swipe a tab sideways and it closes. */
    void swipeToClose(final View row, final Tab t) {
        row.setOnTouchListener(new View.OnTouchListener() {
            float sx, sy;
            boolean drag;
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        sx = e.getRawX(); sy = e.getRawY(); drag = false; return false;
                    case MotionEvent.ACTION_MOVE: {
                        float dx = e.getRawX() - sx, dy = e.getRawY() - sy;
                        if (!drag && Math.abs(dx) > Ui.dp(14) && Math.abs(dx) > Math.abs(dy) * 1.5f) {
                            drag = true;
                            v.getParent().requestDisallowInterceptTouchEvent(true);
                            v.cancelLongPress();
                            v.setPressed(false);
                        }
                        if (drag) {
                            v.setTranslationX(dx);
                            v.setAlpha(Math.max(0.15f, 1 - Math.abs(dx) / Math.max(1f, v.getWidth())));
                            return true;
                        }
                        return false;
                    }
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL: {
                        if (!drag) return false;
                        float dx = e.getRawX() - sx;
                        drag = false;
                        if (e.getActionMasked() == MotionEvent.ACTION_UP && Math.abs(dx) > v.getWidth() * 0.35f) {
                            v.animate().translationX(Math.signum(dx) * v.getWidth()).alpha(0).setDuration(140)
                                .withEndAction(new Runnable() { public void run() { close(t); } });
                        } else {
                            v.animate().translationX(0).alpha(1).setDuration(140);
                        }
                        return true;
                    }
                }
                return false;
            }
        });
    }

    void refreshChrome() {
        String u = current == null ? null : current.url;
        if (u == null) {
            field.setText(current != null && current.priv ? "Private · search or enter address" : "Search or enter address");
            field.setTextColor(Ui.faint());
        } else {
            String h = current.host == null ? Store.bare(u) : current.host;
            if (h.startsWith("www.")) h = h.substring(4);
            field.setText(current.reading ? h + "  ·  reading" : current.priv ? h + "  ·  private" : h);
            field.setTextColor(Ui.muted());
        }
        WebView wv = live();
        back.setEnabled(wv != null && (wv.canGoBack() || (current.opener != null && tabs.contains(current.opener))));
        forward.setEnabled(wv != null && wv.canGoForward());
        reload.setEnabled(wv != null);
        reload.set(current != null && current.progress < 100 ? Ui.Icon.STOP : Ui.Icon.RELOAD);
        back.invalidate(); forward.invalidate();
        applyTint();
    }

    // ── the frame takes the site's colour ───────────────────────

    void readTint(final Tab t) {
        if (t.view == null) return;
        t.view.evaluateJavascript(Scripts.THEME, new ValueCallback<String>() { public void onReceiveValue(String r) {
            int c = parseColor(r == null ? "" : r.replace("\"", ""));
            if (c == t.tint) return;
            t.tint = c;
            if (t == current) applyTint();
        } });
    }

    static int parseColor(String s) {
        try {
            s = s.trim().toLowerCase();
            if (s.isEmpty() || s.equals("null")) return 0;
            if (s.startsWith("rgb")) {
                String[] p = s.substring(s.indexOf('(') + 1, s.indexOf(')')).trim().split("[,\\s/]+");
                return 0xFF000000 | (channel(p[0]) << 16) | (channel(p[1]) << 8) | channel(p[2]);
            }
            if (s.startsWith("#") && s.length() == 4)
                s = "#" + s.charAt(1) + s.charAt(1) + s.charAt(2) + s.charAt(2) + s.charAt(3) + s.charAt(3);
            if (s.startsWith("#") && s.length() == 9) s = s.substring(0, 7);
            return Color.parseColor(s) | 0xFF000000;
        } catch (Exception e) { return 0; }
    }

    static int channel(String v) { return Math.max(0, Math.min(255, Math.round(Float.parseFloat(v)))); }

    static int mix(int a, int b, float f) {
        int r = Math.round(((a >> 16) & 255) * (1 - f) + ((b >> 16) & 255) * f);
        int g = Math.round(((a >> 8) & 255) * (1 - f) + ((b >> 8) & 255) * f);
        int bl = Math.round((a & 255) * (1 - f) + (b & 255) * f);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    static double luminance(int c) { return (0.2126 * ((c >> 16) & 255) + 0.7152 * ((c >> 8) & 255) + 0.0722 * (c & 255)) / 255; }

    /** When turned on, the frame takes a little of the site's own colour. Never in pure black. */
    void applyTint() {
        int want = Ui.frame();
        if (!Ui.black && current != null && current.tint != 0 && store.flag("tint", false)) want = mix(Ui.frame(), current.tint, 0.3f);
        if (want == tintShown) return;
        int from = tintShown;
        tintShown = want;
        if (Ui.motion) {
            ValueAnimator va = ValueAnimator.ofArgb(from, want);
            va.setDuration(220);
            va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() { public void onAnimationUpdate(ValueAnimator a) {
                paintFrame((Integer) a.getAnimatedValue()); } });
            va.start();
        } else paintFrame(want);
    }

    void paintFrame(int c) {
        root.setBackgroundColor(c);
        if (fullscreen != null) return;
        Window w = getWindow();
        w.setStatusBarColor(c);
        w.setNavigationBarColor(c);
        int flags = w.getDecorView().getSystemUiVisibility();
        boolean light = luminance(c) > 0.55;
        int lightFlags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | (Build.VERSION.SDK_INT >= 26 ? View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR : 0);
        w.getDecorView().setSystemUiVisibility(light ? flags | lightFlags : flags & ~lightFlags);
    }

    void setProgress(Tab t, int p) {
        t.progress = p;
        if (t != current) return;
        if (p >= 100) {
            progress.animate().alpha(0).setDuration(250).withEndAction(new Runnable() { public void run() { progress.setVisibility(View.GONE); } });
        } else {
            progress.animate().cancel();
            progress.setAlpha(1);
            progress.setVisibility(View.VISIBLE);
            progress.bringToFront();
            progress.setScaleX(Math.max(0.05f, p / 100f));
        }
        reload.set(p < 100 ? Ui.Icon.STOP : Ui.Icon.RELOAD);
    }

    // ── the one field ───────────────────────────────────────────

    void openField() {
        warmed.clear();
        String start = current != null && current.url != null ? current.url : "";
        final EditText in = input(current != null && current.priv ? "Private · search or enter address" : "Search or enter address", 17);
        in.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        in.setImeOptions(EditorInfo.IME_ACTION_GO);
        in.setText(start);
        in.selectAll();
        in.setPadding(Ui.dp(18), 0, Ui.dp(18), 0);

        final LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);

        sheet(in, Ui.dp(56), list, 0.14f);
        final boolean[] deleting = {false}, inside = {false};

        in.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int a) { deleting[0] = a < c; }
            public void onTextChanged(CharSequence s, int st, int b, int c) {}
            public void afterTextChanged(Editable s) {
                if (inside[0]) return;
                String typed = s.toString();
                int selStart = in.getSelectionStart();
                if (!deleting[0] && selStart == typed.length()) {
                    String done = store.complete(typed);
                    if (done != null && done.length() > typed.length() && done.toLowerCase().startsWith(typed.toLowerCase())) {
                        inside[0] = true;
                        s.append(done.substring(typed.length()));
                        in.setSelection(typed.length(), s.length());
                        inside[0] = false;
                    }
                }
                warmUp(s.toString());
                String head = typed.substring(0, Math.min(typed.length(), Math.max(0, in.getSelectionStart())));
                suggestions(list, head, s.toString());
                remoteSuggestions(in, list, head);
            }
        });
        in.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            public boolean onEditorAction(TextView v, int a, KeyEvent e) {
                go(in.getText().toString());
                dismissOverlay();
                return true;
            }
        });
        suggestions(list, "", "");
        in.requestFocus();
        keyboard(in, true);
    }

    /** While you type, the site's address is looked up, so the page starts sooner when you press Go. */
    final Set<String> warmed = new HashSet<>();

    void warmUp(String text) {
        if (!store.flag("warm", true) || (current != null && current.priv)) return;
        String q = text.trim();
        if (q.length() < 2) return;
        final String host = looksLikeUrl(q) ? Tab.hostOf(fix(q)) : Tab.hostOf(store.searchUrl("seek"));
        if (host == null || !warmed.add(host)) return;
        io.execute(new Runnable() { public void run() {
            try { java.net.InetAddress.getAllByName(host); } catch (Exception ignored) {}
        } });
    }

    void suggestions(final LinearLayout list, String typed, final String full) {
        list.removeAllViews();
        String q = full.trim();
        if (q.isEmpty()) {
            if (current != null && current.priv) return;
            int n = 0;
            for (final Store.Entry e : store.history) {
                if (n++ >= 6) break;
                list.addView(suggestionRow(e.title, Store.bare(e.url), e.url));
            }
            return;
        }
        boolean url = looksLikeUrl(q);
        list.addView(suggestionRow(q, url ? "Go to address" : "Search " + Store.ENGINES[store.engine()][0], url ? fix(q) : store.searchUrl(q)));
        String key = typed.trim().isEmpty() ? q : typed.trim();
        for (Store.Entry e : store.suggest(key, 7)) list.addView(suggestionRow(e.title, Store.bare(e.url), e.url));
    }

    /** Only when turned on in Settings: what you type then goes to your search engine as you type it. */
    final Object typedLock = new Object();
    volatile String typedNow = "";

    void remoteSuggestions(final EditText in, final LinearLayout list, final String typed) {
        final String q = typed.trim();
        typedNow = q;
        if (!store.flag("suggest", false) || q.length() < 2 || looksLikeUrl(q) || (current != null && current.priv)) return;
        final String url = store.suggestUrl(q);
        if (url == null) return;
        final FrameLayout mine = overlay;
        final String ua = mobileUA;
        net.execute(new Runnable() { public void run() {
            final List<String> got = new ArrayList<>();
            try {
                Thread.sleep(150);
                if (!q.equals(typedNow)) return;     // typing went on: only ask for the latest
                java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
                c.setConnectTimeout(4000); c.setReadTimeout(4000);
                if (ua != null) c.setRequestProperty("User-Agent", ua);
                try {
                    InputStream s = c.getInputStream();
                    java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
                    byte[] buf = new byte[4096]; int n;
                    while ((n = s.read(buf)) > 0) b.write(buf, 0, n);
                    JSONArray a = new JSONArray(b.toString("UTF-8")).getJSONArray(1);
                    for (int i = 0; i < a.length() && got.size() < 5; i++) {
                        String sug = a.optString(i);
                        if (!sug.isEmpty() && !sug.equalsIgnoreCase(q)) got.add(sug);
                    }
                } finally { c.disconnect(); }
            } catch (Exception ignored) { return; }
            ui.post(new Runnable() { public void run() {
                if (overlay != mine || overlay == null || !q.equals(typedNow)) return;
                int at = Math.min(1, list.getChildCount());
                for (String s : got) list.addView(suggestionRow(s, "Search " + Store.ENGINES[store.engine()][0], store.searchUrl(s)), at++);
            } });
        } });
    }

    View suggestionRow(String title, String sub, final String target) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(18), 0, Ui.dp(18), 0);
        row.setBackground(Ui.press(0, 0));
        TextView a = Ui.text(this, title == null || title.isEmpty() ? sub : title, 15, Ui.ink());
        TextView b = Ui.text(this, sub, 12.5f, Ui.muted());
        b.setPadding(0, Ui.dp(4), 0, 0);
        row.addView(a); row.addView(b);
        row.setLayoutParams(new LinearLayout.LayoutParams(-1, Ui.dp(54)));
        row.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { dismissOverlay(); load(target); } });
        return row;
    }

    static final Pattern HOSTLIKE = Pattern.compile("^([a-z0-9-]+\\.)+[a-z]{2,}(:\\d+)?(/.*)?$|^localhost(:\\d+)?(/.*)?$|^\\d{1,3}(\\.\\d{1,3}){3}(:\\d+)?(/.*)?$", Pattern.CASE_INSENSITIVE);

    static boolean looksLikeUrl(String s) {
        s = s.trim();
        if (s.contains(" ")) return false;
        if (s.matches("(?i)^(https?|file|about|data|view-source):.*")) return true;
        return HOSTLIKE.matcher(s).matches();
    }

    static String fix(String s) {
        s = s.trim();
        if (s.matches("(?i)^[a-z][a-z0-9+.-]*:.*") && !s.matches("(?i)^[a-z0-9.-]+:\\d+.*")) return s;
        return "https://" + s;
    }

    void go(String text) {
        String q = text.trim();
        if (q.isEmpty()) return;
        load(looksLikeUrl(q) ? fix(q) : store.searchUrl(q));
    }

    // ── overlays: the field, lists, the tab switcher, settings ──

    /** A card over a quiet scrim, near the top of the page. */
    LinearLayout sheet(View head, int headHeight, View content, float top) {
        dismissOverlay();
        overlay = new FrameLayout(this);
        overlay.setBackgroundColor(Ui.scrim());
        overlay.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { dismissOverlay(); } });
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.card());
        card.setElevation(Ui.dp(12));
        card.setClickable(true);
        card.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        card.setClipToOutline(true);
        if (head != null) card.addView(head, new LinearLayout.LayoutParams(-1, headHeight));
        if (head != null && content != null) {
            View hair = new View(this);
            hair.setBackgroundColor(Ui.hairline());
            card.addView(hair, new LinearLayout.LayoutParams(-1, Math.max(1, Ui.dp(0.5f))));
        }
        if (content != null) {
            ScrollView sc = new ScrollView(this);
            sc.setVerticalScrollBarEnabled(false);
            sc.addView(content);
            card.addView(sc, new LinearLayout.LayoutParams(-1, -2));
        }
        int sw = getResources().getDisplayMetrics().widthPixels;
        int sh = getResources().getDisplayMetrics().heightPixels;
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Math.min(Ui.dp(620), sw - Ui.dp(32)), -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        lp.topMargin = (int) (sh * top);
        lp.bottomMargin = Ui.dp(24);
        overlay.addView(card, lp);
        root.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
        if (Ui.motion) {
            overlay.setAlpha(0);
            overlay.animate().alpha(1).setDuration(140);
            card.setScaleX(0.97f); card.setScaleY(0.97f); card.setTranslationY(-Ui.dp(8));
            card.animate().scaleX(1).scaleY(1).translationY(0).setDuration(180).setInterpolator(new android.view.animation.DecelerateInterpolator());
        }
        return card;
    }

    void dismissOverlay() {
        if (overlay == null) return;
        View f = getCurrentFocus();
        if (f != null) keyboard(f, false);
        root.removeView(overlay);
        overlay = null;
    }

    interface Pick { void pick(Store.Entry e); }

    void listPanel(final String title, final List<Store.Entry> source, final Pick longPick) {
        final EditText in = input("Search " + title.toLowerCase(), 16);
        in.setPadding(Ui.dp(18), 0, Ui.dp(18), 0);
        final LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        sheet(in, Ui.dp(54), list, 0.08f);
        final Runnable fill = new Runnable() { public void run() {
            list.removeAllViews();
            String q = in.getText().toString().toLowerCase().trim();
            int n = 0;
            for (final Store.Entry e : new ArrayList<>(source)) {
                if (!q.isEmpty() && !(e.url.toLowerCase().contains(q) || e.title.toLowerCase().contains(q))) continue;
                if (n++ >= 150) break;
                View row = suggestionRow(e.title, Store.bare(e.url), e.url);
                if (longPick != null) row.setOnLongClickListener(new View.OnLongClickListener() { public boolean onLongClick(View v) {
                    longPick.pick(e); run(); return true; } });
                list.addView(row);
            }
            if (n == 0) {
                TextView none = Ui.text(Main.this, source.isEmpty() ? "No " + title.toLowerCase() + " yet" : "Nothing matches", 14, Ui.muted());
                none.setPadding(Ui.dp(18), 0, 0, 0);
                list.addView(none, new LinearLayout.LayoutParams(-1, Ui.dp(54)));
            }
        } };
        in.addTextChangedListener(new Watch() { public void afterTextChanged(Editable s) { fill.run(); } });
        fill.run();
    }

    /** Your open tabs by name. With forSplit, the one picked goes beside the page in front. */
    void switcher(final boolean forSplit) {
        final EditText in = input(forSplit ? "Put which tab beside this one?" : "Switch to a tab", 16);
        in.setPadding(Ui.dp(18), 0, Ui.dp(18), 0);
        final LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        sheet(in, Ui.dp(54), list, 0.08f);
        final Runnable fill = new Runnable() { public void run() {
            list.removeAllViews();
            String q = in.getText().toString().toLowerCase().trim();
            int n = 0;
            for (final Tab t : new ArrayList<>(tabs)) {
                if (forSplit && (shown.contains(t) || t.url == null)) continue;
                String hay = (t.label() + " " + (t.url == null ? "" : t.url)).toLowerCase();
                if (!q.isEmpty() && !hay.contains(q)) continue;
                n++;
                LinearLayout row = new LinearLayout(Main.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(Ui.dp(18), 0, Ui.dp(8), 0);
                row.setBackground(Ui.press(t == current ? Ui.hover() : 0, 0));
                Ui.Letter icon = new Ui.Letter(Main.this);
                icon.set(t.letter(), iconOf(t));
                row.addView(icon, new LinearLayout.LayoutParams(Ui.dp(18), Ui.dp(18)));
                LinearLayout texts = new LinearLayout(Main.this);
                texts.setOrientation(LinearLayout.VERTICAL);
                texts.setPadding(Ui.dp(14), 0, Ui.dp(8), 0);
                texts.addView(Ui.text(Main.this, t.label(), 15, Ui.ink()));
                TextView sub = Ui.text(Main.this, t.url == null ? "New tab" : Store.bare(t.url) + (t.priv ? "  ·  private" : ""), 12.5f, Ui.muted());
                sub.setPadding(0, Ui.dp(4), 0, 0);
                texts.addView(sub);
                row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));
                if (!forSplit) {
                    Ui.Icon x = Ui.icon(Main.this, Ui.Icon.CLOSE, 36);
                    final Runnable again = this;
                    x.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { close(t); again.run(); } });
                    row.addView(x);
                }
                row.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
                    dismissOverlay();
                    if (forSplit) split(t); else select(t);
                } });
                list.addView(row, new LinearLayout.LayoutParams(-1, Ui.dp(56)));
            }
            if (forSplit) {
                list.addView(settingRow("A new page beside this one", "", new View.OnClickListener() { public void onClick(View v) {
                    dismissOverlay();
                    Tab t = newTab(null, false);
                    t.priv = current != null && current.priv;
                    t.view = makeView(t);
                    split(t);
                    focusPane(t);
                    openField();
                } }));
            } else if (n == 0) {
                TextView none = Ui.text(Main.this, "Nothing matches", 14, Ui.muted());
                none.setPadding(Ui.dp(18), 0, 0, 0);
                list.addView(none, new LinearLayout.LayoutParams(-1, Ui.dp(54)));
            }
        } };
        in.addTextChangedListener(new Watch() { public void afterTextChanged(Editable s) { fill.run(); } });
        in.setImeOptions(EditorInfo.IME_ACTION_GO);
        in.setOnEditorActionListener(new TextView.OnEditorActionListener() { public boolean onEditorAction(TextView v, int a, KeyEvent e) {
            if (list.getChildCount() > 0) list.getChildAt(0).performClick();
            return true;
        } });
        fill.run();
    }

    void settings() {
        final LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, Ui.dp(6), 0, Ui.dp(6));
        TextView head = Ui.text(this, "Settings", 16, Ui.ink());
        head.setTypeface(Ui.medium());
        head.setPadding(Ui.dp(18), 0, 0, 0);
        sheet(head, Ui.dp(54), list, 0.06f);

        final Runnable rebuild = new Runnable() { public void run() { dismissOverlay(); saveSession(); recreate(); } };
        final String[] looks = {"System", "Light", "Dark"};
        list.addView(section("Look"));
        list.addView(settingRow("Appearance", looks[store.look()], new View.OnClickListener() { public void onClick(View v) {
            Runnable[] acts = new Runnable[3];
            for (int i = 0; i < 3; i++) {
                final int k = i;
                acts[i] = new Runnable() { public void run() { store.look(k); rebuild.run(); } };
            }
            popup(v, looks, acts);
        } }));
        list.addView(toggleRow("Pure black in dark mode (AMOLED)", "black", false, rebuild));
        list.addView(toggleRow("Darken bright sites in dark mode", "darken", false, new Runnable() { public void run() {
            for (Tab t : tabs) if (t.view != null) t.view.reload(); } }));
        list.addView(settingRow("Tabs", top ? "Across the top" : "Down the left", new View.OnClickListener() { public void onClick(View v) {
            popup(v, new String[]{"Down the left", "Across the top"}, new Runnable[]{
                new Runnable() { public void run() { store.set("top", false); rebuild.run(); } },
                new Runnable() { public void run() { store.set("top", true); rebuild.run(); } } });
        } }));
        if (top) list.addView(toggleRow("Hide the top bar while scrolling down", "autohide", false, new Runnable() { public void run() {
            autohide = store.flag("autohide", false);
            if (!autohide) topBar(false); } }));
        list.addView(settingRow("Density", Ui.compact ? "Compact" : "Comfortable", new View.OnClickListener() { public void onClick(View v) {
            popup(v, new String[]{"Comfortable", "Compact"}, new Runnable[]{
                new Runnable() { public void run() { store.set("compact", false); rebuild.run(); } },
                new Runnable() { public void run() { store.set("compact", true); rebuild.run(); } } });
        } }));
        list.addView(settingRow("Font", store.flag("inter", false) ? "Inter" : "System", new View.OnClickListener() { public void onClick(View v) {
            popup(v, new String[]{"System", "Inter"}, new Runnable[]{
                new Runnable() { public void run() { store.set("inter", false); rebuild.run(); } },
                new Runnable() { public void run() { store.set("inter", true); rebuild.run(); } } });
        } }));
        list.addView(toggleRow("Motion", "motion", true, rebuild));
        list.addView(toggleRow("Tint the frame to match each site", "tint", false, new Runnable() { public void run() {
            if (store.flag("tint", false)) for (Tab t : front()) readTint(t);
            applyTint(); } }));
        list.addView(toggleRow("Your top sites on new tabs", "home", true, new Runnable() { public void run() { showHome(); } }));
        list.addView(settingRow("Tab icons", letters ? "Letters, like Search" : "Site icons", new View.OnClickListener() { public void onClick(View v) {
            popup(v, new String[]{"Site icons", "Letters, like Search"}, new Runnable[]{
                new Runnable() { public void run() { setLetters(false); } },
                new Runnable() { public void run() { setLetters(true); } } });
        } }));
        int w = Math.max(0, Math.min(WALLS.length - 1, store.prefs.getInt("wall", 0)));
        list.addView(settingRow("New tab background", WALLS[w], new View.OnClickListener() { public void onClick(View v) {
            Runnable[] acts = new Runnable[WALLS.length];
            for (int i = 0; i < WALLS.length; i++) {
                final int k = i;
                acts[i] = new Runnable() { public void run() {
                    if (k == WALLS.length - 1) {
                        Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                        pick.addCategory(Intent.CATEGORY_OPENABLE);
                        pick.setType("image/*");
                        try { startActivityForResult(pick, WALL); } catch (Exception e) { toast("No photo picker on this tablet"); }
                        return;
                    }
                    store.prefs.edit().putInt("wall", k).apply();
                    wallpaper(); settings();
                } };
            }
            popup(v, WALLS, acts);
        } }));

        list.addView(section("Searching"));
        list.addView(settingRow("Search with", Store.ENGINES[store.engine()][0], new View.OnClickListener() { public void onClick(View v) {
            String[] names = new String[Store.ENGINES.length];
            Runnable[] acts = new Runnable[names.length];
            for (int i = 0; i < names.length; i++) {
                final int k = i;
                names[i] = Store.ENGINES[i][0];
                acts[i] = new Runnable() { public void run() { store.engine(k); settings(); } };
            }
            popup(v, names, acts);
        } }));
        list.addView(toggleRow("Suggestions as you type (sends it to " + Store.ENGINES[store.engine()][0] + ")", "suggest", false, null));

        list.addView(section("Speed"));
        list.addView(toggleRow("Lite mode: no autoplay video, no web fonts", "lite", false, new Runnable() { public void run() {
            lite = store.flag("lite", false);
            for (Tab t : tabs) if (t.view != null) t.view.reload(); } }));
        list.addView(toggleRow("Put tabs to sleep after 30 idle minutes", "nap", true, null));
        list.addView(toggleRow("Look up sites while you type", "warm", true, null));
        list.addView(settingRow("Pages kept loaded in the background", maxAwake + " (from this tablet's memory)", null));

        list.addView(section("Privacy and blocking"));
        list.addView(toggleRow("Block ads and trackers", "shield", true, new Runnable() { public void run() { shieldOn = store.flag("shield", true); } }));
        list.addView(toggleRow("Stronger blocking with EasyList", "easylist", false, new Runnable() { public void run() {
            if (store.flag("easylist", false)) loadLists(true);
            else Shield.extra = new HashSet<>();
        } }));
        list.addView(toggleRow("Keep a history", "history", true, null));
        list.addView(toggleRow("Bring back tabs from last time", "restore", true, null));
        list.addView(toggleRow("Ask sites for their desktop version", "desktop", false, null));
        list.addView(settingRow("Clear history", "", new View.OnClickListener() { public void onClick(View v) {
            store.clearHistory(); toast("History cleared"); } }));
        list.addView(settingRow("Clear cookies and site data", "", new View.OnClickListener() { public void onClick(View v) {
            CookieManager.getInstance().removeAllCookies(null);
            WebStorage.getInstance().deleteAllData();
            for (Tab t : tabs) if (t.view != null) t.view.clearCache(true);
            toast("Cookies and site data cleared"); } }));
        list.addView(settingRow("Hidden elements", "Bring back everywhere", new View.OnClickListener() { public void onClick(View v) {
            store.clearHidden(); toast("Everything hidden is back"); } }));

        list.addView(section("Bookmarks"));
        list.addView(settingRow("Import bookmarks", "From an HTML file", new View.OnClickListener() { public void onClick(View v) {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("*/*");
            try { startActivityForResult(i, IMPORT); } catch (Exception e) { toast("No file picker on this tablet"); }
        } }));
        list.addView(settingRow("Export bookmarks", store.bookmarks.size() + " saved", new View.OnClickListener() { public void onClick(View v) {
            Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("text/html");
            i.putExtra(Intent.EXTRA_TITLE, "Seek bookmarks.html");
            try { startActivityForResult(i, EXPORT); } catch (Exception e) { toast("No file picker on this tablet"); }
        } }));

        list.addView(section("Updates"));
        list.addView(toggleRow("Check GitHub for a new Seek once a day", "autoupdate", true, null));
        list.addView(settingRow("Check now", "You have " + version(), new View.OnClickListener() { public void onClick(View v) {
            dismissOverlay(); checkUpdate(true); } }));

        list.addView(gap());
        TextView about = Ui.text(this, "Seek " + version() + " · a quiet browser after Search by Office Commun. No account, no telemetry; everything stays on this tablet."
            + "\n\nHold a tab and drag it onto the page to split the screen, up to three pages. Swipe two fingers right to go back, left to go forward. Pull a page down from the top to reload. Swipe a tab sideways to close it. Hold New tab for a private one."
            + "\n\nInter typeface by Rasmus Andersson, SIL Open Font License 1.1.", 12.5f, Ui.muted());
        about.setSingleLine(false);
        about.setPadding(Ui.dp(18), Ui.dp(8), Ui.dp(18), Ui.dp(10));
        list.addView(about);
    }

    View section(String name) {
        TextView t = Ui.text(this, name.toUpperCase(), 11, Ui.muted());
        t.setLetterSpacing(0.08f);
        t.setPadding(Ui.dp(18), Ui.dp(14), 0, Ui.dp(4));
        return t;
    }

    View gap() {
        View v = new View(this);
        v.setBackgroundColor(Ui.hairline());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, Math.max(1, Ui.dp(0.5f)));
        lp.setMargins(Ui.dp(18), Ui.dp(6), Ui.dp(18), Ui.dp(6));
        v.setLayoutParams(lp);
        return v;
    }

    View settingRow(String label, String value, View.OnClickListener click) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(18), 0, Ui.dp(18), 0);
        row.setBackground(Ui.press(0, 0));
        row.addView(Ui.text(this, label, 15, Ui.ink()), new LinearLayout.LayoutParams(0, -1, 1));
        row.addView(Ui.text(this, value, 14, Ui.muted()), new LinearLayout.LayoutParams(-2, -1));
        row.setOnClickListener(click);
        row.setLayoutParams(new LinearLayout.LayoutParams(-1, Ui.dp(48)));
        return row;
    }

    View toggleRow(String label, final String key, final boolean def, final Runnable after) {
        final View row = settingRow(label, store.flag(key, def) ? "On" : "Off", null);
        row.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            boolean now = !store.flag(key, def);
            store.set(key, now);
            ((TextView) ((LinearLayout) row).getChildAt(1)).setText(now ? "On" : "Off");
            if (after != null) after.run();
        } });
        return row;
    }

    // ── EasyList ────────────────────────────────────────────────

    void loadLists(final boolean announce) {
        final File f = new File(getFilesDir(), "easylist.txt");
        net.execute(new Runnable() { public void run() {
            Shield.loadSaved(f);
            boolean stale = !f.exists() || System.currentTimeMillis() - f.lastModified() > 7L * 24 * 3600 * 1000;
            if (!stale) {
                if (announce) {
                    final int n = Shield.extra.size();
                    ui.post(new Runnable() { public void run() { toast("Blocking " + n + " more ad and tracker sites"); } });
                }
                return;
            }
            if (announce) ui.post(new Runnable() { public void run() { toast("Downloading EasyList…"); } });
            try {
                final int n = Shield.download(f);
                if (announce) ui.post(new Runnable() { public void run() { toast("Blocking " + n + " more ad and tracker sites"); } });
            } catch (Exception e) {
                if (announce) ui.post(new Runnable() { public void run() { toast("Couldn't download EasyList. It will try again next launch"); } });
            }
        } });
    }

    // ── menus ───────────────────────────────────────────────────

    void popup(View anchor, String[] labels, final Runnable[] acts) {
        int[] at = new int[2];
        anchor.getLocationInWindow(at);
        popupAt(at[0] + Ui.dp(8), at[1] + anchor.getHeight() / 2, labels, acts);
    }

    void popupAt(int x, int y, String[] labels, final Runnable[] acts) {
        if (menu != null) menu.dismiss();
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, Ui.dp(6), 0, Ui.dp(6));
        for (int i = 0; i < labels.length; i++) {
            if (labels[i] == null) { box.addView(gap()); continue; }
            final int k = i;
            TextView t = Ui.text(this, labels[i], 15, Ui.ink());
            t.setPadding(Ui.dp(16), 0, Ui.dp(24), 0);
            t.setBackground(Ui.press(0, 0));
            t.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
                if (menu != null) menu.dismiss();
                if (acts[k] != null) acts[k].run();
            } });
            box.addView(t, new LinearLayout.LayoutParams(-1, Ui.dp(42)));
        }
        ScrollView sc = new ScrollView(this);
        sc.setVerticalScrollBarEnabled(false);
        sc.setBackground(Ui.card());
        sc.addView(box);
        box.measure(View.MeasureSpec.makeMeasureSpec(Ui.dp(340), View.MeasureSpec.AT_MOST), View.MeasureSpec.UNSPECIFIED);
        int sw = root.getWidth(), sh = root.getHeight();
        int w = Math.max(Ui.dp(210), box.getMeasuredWidth()), h = Math.min(box.getMeasuredHeight(), sh - Ui.dp(16));
        menu = new PopupWindow(sc, w, h, true);
        menu.setElevation(Ui.dp(10));
        menu.setAnimationStyle(Ui.motion ? android.R.style.Animation_Dialog : 0);
        menu.setOutsideTouchable(true);
        menu.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0));
        int[] r = new int[2];
        root.getLocationInWindow(r);
        x = Math.max(r[0] + Ui.dp(8), Math.min(x, r[0] + sw - w - Ui.dp(8)));
        y = Math.max(r[1] + Ui.dp(8), Math.min(y, r[1] + sh - h - Ui.dp(8)));
        menu.showAtLocation(root, Gravity.TOP | Gravity.LEFT, x, y);
    }

    void tabMenu(final Tab t, View v) {
        List<String> l = new ArrayList<>();
        List<Runnable> a = new ArrayList<>();
        if (!t.priv) {
            l.add(t.pinned ? "Unpin" : "Pin"); a.add(new Runnable() { public void run() {
                t.pinned = !t.pinned;
                tabs.remove(t);
                int i = 0;
                while (i < tabs.size() && tabs.get(i).pinned) i++;
                tabs.add(i, t);
                refreshTabs(); saveSoon(); } });
        }
        if (shown.size() > 1 && shown.contains(t)) { l.add("Take out of split view"); a.add(new Runnable() { public void run() { removePane(t); } }); }
        else if (t != current && t.url != null && shown.size() < MAX_PANES) { l.add("Open beside this page"); a.add(new Runnable() { public void run() { split(t); } }); }
        l.add("Duplicate"); a.add(new Runnable() { public void run() {
            if (t.url == null) return;
            Tab d = newTab(null, false); d.url = t.url; d.host = t.host; d.title = t.title; d.priv = t.priv; select(d); } });
        l.add("Copy address"); a.add(new Runnable() { public void run() { copy(t.url); } });
        l.add(null); a.add(null);
        l.add("Close tab"); a.add(new Runnable() { public void run() { close(t); } });
        l.add("Close other tabs"); a.add(new Runnable() { public void run() {
            for (Tab o : new ArrayList<>(tabs)) if (o != t && !o.pinned) close(o);
            select(t); } });
        popup(v, l.toArray(new String[0]), a.toArray(new Runnable[0]));
    }

    /** The page menu, for the page in front: reading, finding, splitting, sharing. */
    void pageMenu(final View v) {
        final Tab t = current;
        if (t == null || t.view == null || t.url == null) { appMenu(v); return; }
        List<String> l = new ArrayList<>();
        List<Runnable> a = new ArrayList<>();
        l.add("Find on page"); a.add(new Runnable() { public void run() { openFind(); } });
        if (t.reading) { l.add("Leave reading mode"); a.add(new Runnable() { public void run() { t.reading = false; t.view.reload(); refreshChrome(); } }); }
        else { l.add("Reading mode"); a.add(new Runnable() { public void run() { t.readerAsked = true; t.view.evaluateJavascript(Scripts.reader(Ui.black), null); } }); }
        l.add("Text size (" + store.zoom(t.host) + "%)"); a.add(new Runnable() { public void run() { textSize(v, t); } });
        l.add(null); a.add(null);
        if (shown.size() < MAX_PANES) { l.add(shown.size() > 1 ? "Add a third page" : "Split with another tab"); a.add(new Runnable() { public void run() { switcher(true); } }); }
        if (shown.size() > 1) {
            l.add("Take this page out"); a.add(new Runnable() { public void run() { removePane(t); } });
            l.add("One page again"); a.add(new Runnable() { public void run() { endSplit(); } });
        }
        if (Build.VERSION.SDK_INT >= 26) { l.add("Float the video"); a.add(new Runnable() { public void run() { floatVideo(); } }); }
        l.add(null); a.add(null);
        l.add("Hide something"); a.add(new Runnable() { public void run() { startPicking(); } });
        int n = store.hiddenOn(t.host).size();
        if (n > 0) { l.add("Show what is hidden here (" + n + ")"); a.add(new Runnable() { public void run() {
            store.unhideAll(t.host); t.view.reload(); } }); }
        l.add("This site…"); a.add(new Runnable() { public void run() { siteMenu(v, t); } });
        l.add(null); a.add(null);
        l.add(store.isBookmarked(t.url) ? "Remove bookmark" : "Bookmark this page"); a.add(new Runnable() { public void run() {
            toast(store.toggleBookmark(t.url, t.title) ? "Bookmarked" : "Bookmark removed"); } });
        l.add("Share"); a.add(new Runnable() { public void run() { share(t.url, t.title); } });
        l.add("Copy address"); a.add(new Runnable() { public void run() { copy(t.url); } });
        menuBy(v, l, a);
    }

    /** The app menu: tabs, your lists, settings, and an update when there is one. */
    void appMenu(View v) {
        List<String> l = new ArrayList<>();
        List<Runnable> a = new ArrayList<>();
        if (update != null) {
            l.add("Update to Seek " + update.version); a.add(new Runnable() { public void run() { offerUpdate(); } });
            l.add(null); a.add(null);
        }
        l.add("New tab"); a.add(new Runnable() { public void run() { newTab(null, true); } });
        l.add("New private tab"); a.add(new Runnable() { public void run() { newPrivateTab(); } });
        l.add("All tabs"); a.add(new Runnable() { public void run() { switcher(false); } });
        if (!closed.isEmpty()) { l.add("Reopen closed tab"); a.add(new Runnable() { public void run() { reopen(); } }); }
        l.add(null); a.add(null);
        l.add("Bookmarks"); a.add(new Runnable() { public void run() { listPanel("Bookmarks", store.bookmarks, new Pick() { public void pick(Store.Entry e) {
            store.removeBookmark(e); toast("Bookmark removed"); } }); } });
        l.add("History"); a.add(new Runnable() { public void run() { listPanel("History", store.history, null); } });
        l.add("Downloads"); a.add(new Runnable() { public void run() {
            try { startActivity(new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)); } catch (Exception e) { toast("No downloads app on this tablet"); } } });
        l.add(null); a.add(null);
        l.add("Settings"); a.add(new Runnable() { public void run() { settings(); } });
        menuBy(v, l, a);
    }

    /** A menu beside its button: below it with tabs across the top, above it otherwise. */
    void menuBy(View v, List<String> l, List<Runnable> a) {
        int h = 0;
        for (String s : l) h += s == null ? Ui.dp(13) : Ui.dp(42);
        int[] at = new int[2];
        v.getLocationInWindow(at);
        popupAt(at[0], top ? at[1] + v.getHeight() : at[1] - h - Ui.dp(12), l.toArray(new String[0]), a.toArray(new Runnable[0]));
    }

    // ── updates ─────────────────────────────────────────────────

    String version() {
        try {
            String v = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            return v != null ? v : "0";
        } catch (Exception e) { return "0"; }
    }

    /** Once a day at most, unless asked: is there a newer Seek on GitHub? */
    void checkUpdate(final boolean asked) {
        long last = store.prefs.getLong("updateChecked", 0);
        if (!asked && (!store.flag("autoupdate", true) || System.currentTimeMillis() - last < 20L * 3600 * 1000)) return;
        if (asked) toast("Checking for updates…");
        net.execute(new Runnable() { public void run() {
            Updater.Release r = null;
            boolean failed = false;
            try { r = Updater.latest(); } catch (Exception e) { failed = true; }
            final Updater.Release got = r;
            final boolean err = failed;
            ui.post(new Runnable() { public void run() {
                if (isFinishing()) return;
                if (!err) store.prefs.edit().putLong("updateChecked", System.currentTimeMillis()).apply();
                String mine = version();
                if (got != null && Updater.newer(got.version, mine)) {
                    update = got;
                    appButton.tint(Ui.ink());
                    if (asked) offerUpdate();
                    else toast("Seek " + got.version + " is ready. It's at the top of the menu.");
                } else if (asked) toast(err ? "Couldn't reach GitHub. Try again later" : "Seek " + mine + " is the newest");
            } });
        } });
    }

    void offerUpdate() {
        if (update == null) return;
        final LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        TextView head = Ui.text(this, "Seek " + update.version + " is ready  ·  you have " + version(), 16, Ui.ink());
        head.setTypeface(Ui.medium());
        head.setPadding(Ui.dp(18), 0, Ui.dp(18), 0);
        sheet(head, Ui.dp(54), list, 0.1f);
        String notes = update.notes == null ? "" : update.notes.replace("**", "").replace("`", "").trim();
        if (notes.length() > 900) notes = notes.substring(0, 900) + "…";
        if (!notes.isEmpty()) {
            TextView t = Ui.text(this, notes, 13.5f, Ui.muted());
            t.setSingleLine(false);
            t.setLineSpacing(Ui.dp(3), 1);
            t.setPadding(Ui.dp(18), Ui.dp(12), Ui.dp(18), Ui.dp(10));
            list.addView(t);
        }
        list.addView(settingRow("Download and install", "", new View.OnClickListener() { public void onClick(View v) { dismissOverlay(); downloadUpdate(); } }));
        list.addView(settingRow("Later", "", new View.OnClickListener() { public void onClick(View v) { dismissOverlay(); } }));
    }

    void downloadUpdate() {
        if (update == null) return;
        try {
            String name = "Seek-" + update.version + ".apk";
            File old = new File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), name);
            if (old.exists()) old.delete();
            DownloadManager.Request r = new DownloadManager.Request(Uri.parse(update.apk));
            r.setTitle(name);
            r.setMimeType("application/vnd.android.package-archive");
            r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE);
            r.setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS, name);
            updateDownload = ((DownloadManager) getSystemService(DOWNLOAD_SERVICE)).enqueue(r);
            toast("Downloading Seek " + update.version + "…");
        } catch (Exception e) {
            toast("Couldn't start the download");
        }
    }

    final BroadcastReceiver downloaded = new BroadcastReceiver() { public void onReceive(Context c, Intent i) {
        long id = i.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
        if (id < 0 || id != updateDownload) return;
        updateDownload = -1;
        Uri u = ((DownloadManager) getSystemService(DOWNLOAD_SERVICE)).getUriForDownloadedFile(id);
        if (u == null) { toast("The update didn't download. Try again from the menu"); return; }
        updateFile = u;
        if (canInstall()) install(u);
        else {
            toast("Allow Seek to install apps, then come back");
            try {
                startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName())));
            } catch (Exception e) { install(u); }
        }
    } };

    boolean canInstall() { return Build.VERSION.SDK_INT < 26 || getPackageManager().canRequestPackageInstalls(); }

    void install(Uri u) {
        updateFile = null;
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(u, "application/vnd.android.package-archive");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        try { startActivity(i); } catch (Exception e) { toast("This tablet has no installer for it. Open it from Downloads"); }
    }

    void siteMenu(View v, final Tab t) {
        final String host = t.host;
        boolean p = host != null && paused.contains(host);
        boolean aw = host != null && awake.contains(host);
        List<String> l = new ArrayList<>();
        List<Runnable> a = new ArrayList<>();
        if (shieldOn && host != null) { l.add(p ? "Block ads on this site" : "Allow ads on this site"); a.add(new Runnable() { public void run() {
            store.pause(host, !paused.contains(host)); paused = store.paused(); t.view.reload(); } }); }
        if (host != null) { l.add(aw ? "Let this site sleep" : "Keep this site awake"); a.add(new Runnable() { public void run() {
            boolean on = !awake.contains(host);
            store.awake(host, on); awake = store.awake();
            toast(on ? "This site stays loaded in the background, at the cost of memory" : "This site can sleep again"); } }); }
        l.add(t.desktop ? "Mobile site" : "Desktop site"); a.add(new Runnable() { public void run() {
            t.desktop = !t.desktop; t.view.getSettings().setUserAgentString(t.desktop ? desktopUA : mobileUA); t.view.reload(); } });
        popup(v, l.toArray(new String[0]), a.toArray(new Runnable[0]));
    }

    static final int[] SIZES = {80, 90, 100, 110, 125, 150, 175};

    void textSize(View v, final Tab t) {
        String[] l = new String[SIZES.length];
        Runnable[] a = new Runnable[SIZES.length];
        for (int i = 0; i < SIZES.length; i++) {
            final int pct = SIZES[i];
            l[i] = pct + "%" + (pct == store.zoom(t.host) ? "  ✓" : "");
            a[i] = new Runnable() { public void run() { setZoom(t, pct); } };
        }
        popup(v, l, a);
    }

    void setLetters(boolean on) {
        letters = on;
        store.set("letters", on);
        refreshTabs();
        showHome();
        settings();
    }

    void setZoom(Tab t, int pct) {
        store.zoom(t.host, pct);
        for (Tab o : tabs) if (o.view != null && o.host != null && o.host.equals(t.host)) o.view.getSettings().setTextZoom(pct);
    }

    void stepZoom(int dir) {
        if (current == null || current.view == null) return;
        int z = store.zoom(current.host), i = 0;
        while (i < SIZES.length - 1 && SIZES[i] < z) i++;
        i = Math.max(0, Math.min(SIZES.length - 1, i + dir));
        setZoom(current, SIZES[i]);
        toast("Text " + SIZES[i] + "%");
    }

    void linkMenu(final String link, final String image) {
        List<String> l = new ArrayList<>();
        List<Runnable> a = new ArrayList<>();
        final boolean priv = current != null && current.priv;
        if (link != null) {
            l.add("Open in new tab"); a.add(new Runnable() { public void run() { Tab t = newTab(null, false); t.url = link; t.host = Tab.hostOf(link); t.opener = current; t.priv = priv; refreshTabs(); saveSoon(); } });
            l.add("Open in new tab and go"); a.add(new Runnable() { public void run() { Tab o = current; Tab t = newTab(null, false); t.url = link; t.host = Tab.hostOf(link); t.opener = o; t.priv = priv; select(t); } });
            l.add("Open beside this page"); a.add(new Runnable() { public void run() { Tab t = newTab(null, false); t.url = link; t.host = Tab.hostOf(link); t.priv = priv; split(t); } });
            if (!priv) { l.add("Open in private tab"); a.add(new Runnable() { public void run() { Tab t = newTab(null, false); t.url = link; t.host = Tab.hostOf(link); t.priv = true; select(t); } }); }
            l.add("Copy link"); a.add(new Runnable() { public void run() { copy(link); } });
            l.add("Share link"); a.add(new Runnable() { public void run() { share(link, null); } });
        }
        if (image != null && (image.startsWith("http") || image.startsWith("data:"))) {
            if (!l.isEmpty()) { l.add(null); a.add(null); }
            l.add("Open image in new tab"); a.add(new Runnable() { public void run() { Tab t = newTab(null, false); t.url = image; t.host = Tab.hostOf(image); t.priv = priv; refreshTabs(); } });
            if (image.startsWith("http")) { l.add("Save image"); a.add(new Runnable() { public void run() { download(image, null, null, null); } }); }
        }
        if (l.isEmpty()) return;
        popupAt((int) touchX, (int) touchY, l.toArray(new String[0]), a.toArray(new Runnable[0]));
    }

    // ── floating video ──────────────────────────────────────────

    void floatVideo() {
        if (Build.VERSION.SDK_INT < 26 || live() == null) return;
        final WebView wv = live();
        wv.evaluateJavascript(Scripts.FLOAT, new ValueCallback<String>() { public void onReceiveValue(String r) {
            if (!"true".equals(r)) { toast("No video on this page"); return; }
            floating = true;
            if (!enterPip()) {
                floating = false;
                wv.evaluateJavascript(Scripts.UNFLOAT, null);
                toast("Floating windows are turned off for Seek in this tablet's settings");
            }
        } });
    }

    boolean enterPip() {
        if (Build.VERSION.SDK_INT < 26) return false;
        try {
            return enterPictureInPictureMode(new PictureInPictureParams.Builder().setAspectRatio(new Rational(16, 9)).build());
        } catch (Exception e) { return false; }
    }

    @Override protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        if (fullscreen != null) enterPip();   // a full-screen video keeps playing in a small window
    }

    @Override public void onPictureInPictureModeChanged(boolean in, Configuration c) {
        super.onPictureInPictureModeChanged(in, c);
        pip = in;
        if (in) {
            dismissOverlay();
            if (menu != null) menu.dismiss();
            if (findBar.getVisibility() == View.VISIBLE) closeFind();
        } else {
            if (floating) for (Tab t : front()) if (t.view != null) t.view.evaluateJavascript(Scripts.UNFLOAT, null);
            floating = false;
        }
        layoutPanes();   // while it floats, only the page with the video
    }

    // ── the web view ────────────────────────────────────────────

    final class Bridge {
        final Tab tab;
        Bridge(Tab t) { tab = t; }
        @JavascriptInterface public void hide(final String selector) {
            ui.post(new Runnable() { public void run() {
                if (!picking || tab != current) return;
                store.hide(tab.host, selector);
            } });
        }
        @JavascriptInterface public void reader(final boolean ok) {
            ui.post(new Runnable() { public void run() {
                if (!tab.readerAsked) return;
                tab.readerAsked = false;
                tab.reading = ok;
                if (!ok) toast("Nothing here to read");
                if (tab == current) refreshChrome();
            } });
        }
    }

    final SeekView.Pull pull = new SeekView.Pull() {
        public void pulling(SeekView v, float d) {
            if (v != live() || picking) return;
            pullDot.animate().cancel();
            pullDot.setVisibility(View.VISIBLE);
            pullDot.bringToFront();
            float travel = Math.min(d * 0.5f, Ui.dp(80));
            pullDot.setTranslationY(travel - Ui.dp(10));
            pullDot.setRotation(d * 1.2f);
            pullDot.setAlpha(Math.min(1f, d / Ui.dp(110)));
            pullDot.tint(d > Ui.dp(110) ? Ui.ink() : Ui.muted());
            View pane = (View) v.getParent();
            if (shown.size() > 1 && pane != null) {
                int[] a = new int[2], p = new int[2];
                pane.getLocationInWindow(a);
                page.getLocationInWindow(p);
                pullDot.setTranslationX(a[0] - p[0] + pane.getWidth() / 2f - page.getWidth() / 2f);
            } else pullDot.setTranslationX(0);
        }
        public void released(SeekView v, boolean go) {
            if (pullDot.getVisibility() != View.VISIBLE) return;
            pullDot.animate().alpha(0).translationY(-Ui.dp(20)).setDuration(180).withEndAction(new Runnable() { public void run() { pullDot.setVisibility(View.GONE); } });
            if (go && !picking) v.reload();
        }
    };

    WebView makeView(final Tab t) {
        final SeekView v = new SeekView(this);
        v.pull = pull;
        v.setBackgroundColor(Ui.ground());
        WebSettings s = v.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setSupportMultipleWindows(true);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setMediaPlaybackRequiresUserGesture(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setGeolocationEnabled(false);
        s.setSaveFormData(!t.priv);
        s.setTextZoom(store.zoom(t.host));
        if (mobileUA == null) userAgents(s.getUserAgentString());
        t.desktop = t.desktop || store.flag("desktop", false);
        s.setUserAgentString(t.desktop ? desktopUA : mobileUA);
        CookieManager.getInstance().setAcceptThirdPartyCookies(v, true);
        v.addJavascriptInterface(new Bridge(t), "SeekBridge");
        v.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        v.setOnScrollChangeListener(new View.OnScrollChangeListener() { public void onScrollChange(View view, int x, int y, int ox, int oy) {
            if (!autohide || !top || folded || t != current || overlay != null) return;
            int dy = y - oy;
            if (y < Ui.dp(24) || dy < -Ui.dp(12)) topBar(false);
            else if (dy > Ui.dp(4)) topBar(true);
        } });

        v.setOnTouchListener(new View.OnTouchListener() { public boolean onTouch(View view, MotionEvent e) {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) focusPane(t);
            touchX = e.getRawX(); touchY = e.getRawY(); return false; } });
        v.setOnLongClickListener(new View.OnLongClickListener() { public boolean onLongClick(View view) {
            if (picking) return true;
            WebView.HitTestResult h = v.getHitTestResult();
            int type = h.getType();
            final String extra = h.getExtra();
            if (type == WebView.HitTestResult.SRC_ANCHOR_TYPE) { linkMenu(extra, null); return true; }
            if (type == WebView.HitTestResult.IMAGE_TYPE) { linkMenu(null, extra); return true; }
            if (type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) {
                Message m = new Handler(Looper.getMainLooper()) {
                    @Override public void handleMessage(Message msg) {
                        String href = msg.getData().getString("url");
                        linkMenu(href != null && !href.isEmpty() ? href : null, extra);
                    }
                }.obtainMessage();
                v.requestFocusNodeHref(m);
                return true;
            }
            return false;
        } });

        v.setDownloadListener(new android.webkit.DownloadListener() {
            public void onDownloadStart(String url, String ua, String disposition, String mime, long length) {
                download(url, ua, disposition, mime);
            }
        });

        v.setFindListener(new WebView.FindListener() {
            public void onFindResultReceived(int at, int count, boolean done) {
                if (t != current) return;
                findCount.setText(count == 0 ? (findInput.length() == 0 ? "" : "None") : (at + 1) + " of " + count);
            }
        });

        v.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return external(url);
            }

            @Override public void onPageStarted(WebView view, String url, Bitmap favicon) {
                String before = t.host;
                t.url = url;
                t.host = Tab.hostOf(url);
                t.reading = false;
                if (favicon != null) t.icon = favicon;
                if (t.host != null && !t.host.equals(before)) { view.getSettings().setTextZoom(store.zoom(t.host)); t.tint = 0; }
                t.curtained = false;
                curtain(t);
                setProgress(t, 5);
                if (t == current) refreshChrome();
            }

            @Override public void onPageCommitVisible(WebView view, String url) {
                curtain(t);
                if (store.flag("tint", false)) readTint(t);
            }

            @Override public void onPageFinished(WebView view, String url) {
                curtain(t);
                if (picking && t == current) view.evaluateJavascript(Scripts.PICK, null);
                if (store.flag("tint", false)) readTint(t);
                setProgress(t, 100);
                if (!t.priv && store.flag("history", true)) store.visit(url, view.getTitle());
                if (t == current) refreshChrome();
                saveSoon();
            }

            @Override public void doUpdateVisitedHistory(WebView view, String url, boolean reload) {
                if (url == null || url.startsWith("data:") || url.equals("about:blank")) return;
                t.url = url;
                t.host = Tab.hostOf(url);
                if (t == current) refreshChrome();
            }

            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest req) {
                if (req.isForMainFrame()) return null;
                if (lite && isFont(req.getUrl().toString())) return Shield.empty();
                if (!shieldOn) return null;
                String page = t.host;
                if (page != null && paused.contains(page)) return null;
                if (Shield.blocked(req.getUrl().toString(), page)) return Shield.empty();
                return null;
            }
        });

        v.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView view, int p) {
                if (p < 100) setProgress(t, p);
                if (p >= 10 && !t.curtained && t.url != null) { t.curtained = true; curtain(t); }   // before the page is drawn
            }

            @Override public void onReceivedTitle(WebView view, String title) {
                t.title = title == null ? "" : title;
                if (!t.priv) store.retitle(view.getUrl(), title);
                updateRow(t);
            }

            @Override public void onReceivedIcon(WebView view, Bitmap icon) {
                t.icon = icon;
                if (!t.priv) Icons.put(t.host, icon, io);
                updateRow(t);
            }

            @Override public boolean onCreateWindow(WebView view, boolean dialog, boolean gesture, Message result) {
                if (!gesture) return false;     // popups nobody asked for
                Tab n = new Tab(null, null, false);
                n.opener = t;
                n.priv = t.priv;
                int at = tabs.indexOf(t);
                tabs.add(t.pinned ? tabs.size() : at + 1, n);
                n.view = makeView(n);
                WebView.WebViewTransport tr = (WebView.WebViewTransport) result.obj;
                tr.setWebView(n.view);
                result.sendToTarget();
                select(n);
                return true;
            }

            @Override public void onCloseWindow(WebView window) { close(t); }

            @Override public void onShowCustomView(View view, CustomViewCallback done) {
                if (fullscreen != null) { done.onCustomViewHidden(); return; }
                fullscreen = view;
                fullscreenDone = done;
                view.setBackgroundColor(0xFF000000);
                root.addView(view, new FrameLayout.LayoutParams(-1, -1));
                getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
            }

            @Override public void onHideCustomView() { leaveFullscreen(); }

            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> cb, FileChooserParams params) {
                if (filePick != null) filePick.onReceiveValue(null);
                filePick = cb;
                try {
                    startActivityForResult(params.createIntent(), FILE_PICK);
                } catch (ActivityNotFoundException e) {
                    filePick = null;
                    return false;
                }
                return true;
            }

            @Override public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback cb) {
                cb.invoke(origin, false, false);
            }

            @Override public void onPermissionRequest(PermissionRequest r) { r.deny(); }
        });
        return v;
    }

    void userAgents(String ua) {
        String m = ua.replace("; wv", "").replaceAll("Version/\\d+(\\.\\d+)* ", "");
        boolean tablet = getResources().getConfiguration().smallestScreenWidthDp >= 600;
        if (tablet) m = m.replace(" Mobile ", " ");
        mobileUA = m;
        java.util.regex.Matcher cv = Pattern.compile("Chrome/([\\d.]+)").matcher(ua);
        String ver = cv.find() ? cv.group(1) : "120.0.0.0";
        desktopUA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/" + ver + " Safari/537.36";
    }

    void curtain(Tab t) {
        if (t.view == null || t.url == null) return;
        if (Ui.dark && store.flag("darken", false)) t.view.evaluateJavascript(Scripts.DARKEN, null);
        if (lite) t.view.evaluateJavascript(Scripts.LITE, null);
        boolean shield = shieldOn && (t.host == null || !paused.contains(t.host));
        List<String> hidden = store.hiddenOn(t.host);
        if (!shield && hidden.isEmpty()) return;
        t.view.evaluateJavascript(Scripts.curtain(shield, hidden), null);
    }

    /** Lite mode leaves web fonts out: pages use the tablet's own. */
    static boolean isFont(String u) {
        String l = u.toLowerCase();
        int q = l.indexOf('?');
        if (q > 0) l = l.substring(0, q);
        return l.endsWith(".woff2") || l.endsWith(".woff") || l.endsWith(".ttf") || l.endsWith(".otf") || l.endsWith(".eot")
            || l.contains("://fonts.gstatic.com/");
    }

    boolean external(String url) {
        if (url == null) return false;
        String lower = url.toLowerCase();
        if (lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("about:")
            || lower.startsWith("data:") || lower.startsWith("blob:") || lower.startsWith("javascript:")) return false;
        try {
            if (lower.startsWith("intent:")) {
                Intent i = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
                i.addCategory(Intent.CATEGORY_BROWSABLE);
                i.setComponent(null);
                i.setSelector(null);
                try { startActivity(i); }
                catch (ActivityNotFoundException e) {
                    String fallback = i.getStringExtra("browser_fallback_url");
                    if (fallback != null && (fallback.startsWith("http://") || fallback.startsWith("https://"))) load(fallback);
                }
                return true;
            }
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            i.addCategory(Intent.CATEGORY_BROWSABLE);
            startActivity(i);
        } catch (Exception e) {
            toast("No app here opens that link");
        }
        return true;
    }

    void leaveFullscreen() {
        if (fullscreen == null) return;
        root.removeView(fullscreen);
        fullscreen = null;
        if (fullscreenDone != null) fullscreenDone.onCustomViewHidden();
        fullscreenDone = null;
        bars();
        tintShown = Ui.frame();
        root.setBackgroundColor(tintShown);
        applyTint();
    }

    @Override protected void onActivityResult(int req, int result, Intent data) {
        if (req == FILE_PICK) {
            if (filePick != null) filePick.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result, data));
            filePick = null;
            return;
        }
        if (result != RESULT_OK || data == null || data.getData() == null) { super.onActivityResult(req, result, data); return; }
        Uri uri = data.getData();
        try {
            if (req == EXPORT) {
                OutputStream o = getContentResolver().openOutputStream(uri);
                try { o.write(store.exportBookmarks().getBytes("UTF-8")); } finally { o.close(); }
                toast("Exported " + store.bookmarks.size() + " bookmarks");
            } else if (req == IMPORT) {
                InputStream i = getContentResolver().openInputStream(uri);
                java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
                try { byte[] buf = new byte[8192]; int n; while ((n = i.read(buf)) > 0) b.write(buf, 0, n); } finally { i.close(); }
                int n = store.importBookmarks(b.toString("UTF-8"));
                toast(n == 0 ? "No new bookmarks in that file" : "Imported " + n + " bookmarks");
            } else if (req == WALL) {
                saveWall(uri);
                store.prefs.edit().putInt("wall", WALLS.length - 1).apply();
                wallpaper();
                toast("New tab background set");
            }
        } catch (Exception e) {
            toast("That didn't work: " + e.getClass().getSimpleName());
        }
    }

    /** The photo, scaled to the screen once and kept, so it costs nothing while browsing. */
    void saveWall(Uri uri) throws Exception {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        InputStream in = getContentResolver().openInputStream(uri);
        try { BitmapFactory.decodeStream(in, null, o); } finally { in.close(); }
        int target = Math.max(getResources().getDisplayMetrics().widthPixels, getResources().getDisplayMetrics().heightPixels);
        int sample = 1;
        while (Math.max(o.outWidth, o.outHeight) / (sample * 2) >= target) sample *= 2;
        BitmapFactory.Options d = new BitmapFactory.Options();
        d.inSampleSize = sample;
        in = getContentResolver().openInputStream(uri);
        Bitmap b;
        try { b = BitmapFactory.decodeStream(in, null, d); } finally { in.close(); }
        if (b == null) throw new java.io.IOException("not an image");
        java.io.FileOutputStream out = new java.io.FileOutputStream(new File(getFilesDir(), "wall.jpg"));
        try { b.compress(Bitmap.CompressFormat.JPEG, 88, out); } finally { out.close(); }
    }

    void download(String url, String ua, String disposition, String mime) {
        if (url == null || !(url.startsWith("http://") || url.startsWith("https://"))) {
            toast("This download can't be saved here");
            return;
        }
        try {
            String name = URLUtil.guessFileName(url, disposition, mime);
            DownloadManager.Request r = new DownloadManager.Request(Uri.parse(url));
            String cookie = CookieManager.getInstance().getCookie(url);
            if (cookie != null) r.addRequestHeader("Cookie", cookie);
            r.addRequestHeader("User-Agent", ua != null ? ua : mobileUA != null ? mobileUA : "");
            if (current != null && current.url != null) r.addRequestHeader("Referer", current.url);
            if (mime != null) r.setMimeType(mime);
            r.setTitle(name);
            r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            r.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name);
            ((DownloadManager) getSystemService(DOWNLOAD_SERVICE)).enqueue(r);
            toast("Downloading " + name);
        } catch (Exception e) {
            toast("Couldn't start the download");
        }
    }

    // ── going back, gestures, keys ──────────────────────────────

    void goBack() {
        WebView wv = live();
        if (wv != null && wv.canGoBack()) { wv.goBack(); return; }
        if (current != null && current.opener != null && tabs.contains(current.opener)) close(current);
    }

    void goForward() {
        WebView wv = live();
        if (wv != null && wv.canGoForward()) wv.goForward();
    }

    @Override public void onBackPressed() {
        if (fullscreen != null) { leaveFullscreen(); return; }
        if (menu != null && menu.isShowing()) { menu.dismiss(); return; }
        if (overlay != null) { dismissOverlay(); return; }
        if (picking) { stopPicking(); return; }
        if (findBar.getVisibility() == View.VISIBLE) { closeFind(); return; }
        if (current != null && current.reading && current.view != null) { current.reading = false; current.view.reload(); refreshChrome(); return; }
        WebView wv = live();
        if (wv != null && wv.canGoBack()) { wv.goBack(); return; }
        if (current != null && current.opener != null && tabs.contains(current.opener)) { close(current); return; }
        moveTaskToBack(true);
    }

    // two fingers swiped sideways: back and forward
    float f0x, f0y, f1x, f1y;
    boolean twoOn, twoDone;

    @Override public boolean dispatchTouchEvent(MotionEvent e) {
        int a = e.getActionMasked();
        if (a == MotionEvent.ACTION_POINTER_DOWN && e.getPointerCount() == 2) {
            f0x = e.getX(0); f0y = e.getY(0); f1x = e.getX(1); f1y = e.getY(1);
            twoOn = true; twoDone = false;
        } else if (a == MotionEvent.ACTION_MOVE && twoOn && !twoDone && e.getPointerCount() == 2 && overlay == null && fullscreen == null) {
            float dx0 = e.getX(0) - f0x, dx1 = e.getX(1) - f1x;
            float dy0 = Math.abs(e.getY(0) - f0y), dy1 = Math.abs(e.getY(1) - f1y);
            if (Math.signum(dx0) == Math.signum(dx1) && Math.min(Math.abs(dx0), Math.abs(dx1)) > Ui.dp(110)
                && Math.max(dy0, dy1) < Ui.dp(70)) {
                twoDone = true;
                if (dx0 > 0) goBack(); else goForward();
            }
        } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL || (a == MotionEvent.ACTION_POINTER_DOWN && e.getPointerCount() > 2)) {
            twoOn = false;
        }
        return super.dispatchTouchEvent(e);
    }

    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        if (e.getAction() == KeyEvent.ACTION_DOWN && e.isCtrlPressed()) {
            int k = e.getKeyCode();
            boolean shift = e.isShiftPressed();
            switch (k) {
                case KeyEvent.KEYCODE_L: openField(); return true;
                case KeyEvent.KEYCODE_K: switcher(false); return true;
                case KeyEvent.KEYCODE_T: if (shift) reopen(); else newTab(null, true); return true;
                case KeyEvent.KEYCODE_N: if (shift) newPrivateTab(); else newTab(null, true); return true;
                case KeyEvent.KEYCODE_W: if (current != null) close(current); return true;
                case KeyEvent.KEYCODE_R: if (live() != null) live().reload(); return true;
                case KeyEvent.KEYCODE_F: openFind(); return true;
                case KeyEvent.KEYCODE_S: fold(!folded); return true;
                case KeyEvent.KEYCODE_D: if (current != null && current.url != null) toast(store.toggleBookmark(current.url, current.title) ? "Bookmarked" : "Bookmark removed"); return true;
                case KeyEvent.KEYCODE_Y: listPanel("History", store.history, null); return true;
                case KeyEvent.KEYCODE_EQUALS: case KeyEvent.KEYCODE_PLUS: stepZoom(1); return true;
                case KeyEvent.KEYCODE_MINUS: stepZoom(-1); return true;
                case KeyEvent.KEYCODE_0: if (current != null) setZoom(current, 100); return true;
                case KeyEvent.KEYCODE_LEFT_BRACKET: goBack(); return true;
                case KeyEvent.KEYCODE_RIGHT_BRACKET: goForward(); return true;
                case KeyEvent.KEYCODE_TAB: {
                    if (tabs.size() < 2 || current == null) return true;
                    int i = tabs.indexOf(current) + (shift ? -1 : 1);
                    select(tabs.get((i + tabs.size()) % tabs.size()));
                    return true;
                }
            }
            if (k >= KeyEvent.KEYCODE_1 && k <= KeyEvent.KEYCODE_9) {
                int i = k == KeyEvent.KEYCODE_9 ? tabs.size() - 1 : k - KeyEvent.KEYCODE_1;
                if (i < tabs.size()) select(tabs.get(i));
                return true;
            }
        }
        return super.dispatchKeyEvent(e);
    }

    // ── intents and the session ─────────────────────────────────

    boolean handle(Intent i) {
        if (i == null) return false;
        String act = i.getAction();
        String url = null;
        if (Intent.ACTION_VIEW.equals(act) && i.getData() != null) url = i.getDataString();
        else if (Intent.ACTION_WEB_SEARCH.equals(act)) {
            String q = i.getStringExtra(SearchManager.QUERY);
            if (q != null) url = looksLikeUrl(q) ? fix(q) : store.searchUrl(q);
        } else if (Intent.ACTION_SEND.equals(act)) {
            String q = i.getStringExtra(Intent.EXTRA_TEXT);
            if (q != null) {
                java.util.regex.Matcher m = android.util.Patterns.WEB_URL.matcher(q);
                url = m.find() && q.trim().contains("://") ? fix(m.group()) : looksLikeUrl(q) ? fix(q) : store.searchUrl(q);
            }
        }
        if (url == null) return false;
        if (current != null && current.url == null) load(url);
        else {
            Tab t = newTab(null, false);
            t.url = url; t.host = Tab.hostOf(url);
            select(t);
        }
        setIntent(new Intent());
        return true;
    }

    void restore() {
        if (!store.flag("restore", true)) return;
        JSONObject s = store.session();
        if (s == null) return;
        JSONArray a = s.optJSONArray("tabs");
        if (a == null || a.length() == 0) return;
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o == null || o.optString("u", "").isEmpty()) continue;
            Tab t = new Tab(o.optString("u"), o.optString("t"), o.optBoolean("p"));
            t.desktop = o.optBoolean("d");
            tabs.add(t);
        }
        if (tabs.isEmpty()) return;
        int sel = Math.max(0, Math.min(s.optInt("selected", 0), tabs.size() - 1));
        select(tabs.get(sel));
        List<Tab> front = new ArrayList<>();
        JSONArray f = s.optJSONArray("shown");
        if (f != null) {
            for (int i = 0; i < f.length() && front.size() < MAX_PANES; i++) {
                int k = f.optInt(i, -1);
                if (k >= 0 && k < tabs.size() && !front.contains(tabs.get(k))) front.add(tabs.get(k));
            }
        } else {   // from 1.1: one page on the right
            int sp = s.optInt("split", -1);
            if (sp >= 0 && sp < tabs.size() && sp != sel) { front.add(current); front.add(tabs.get(sp)); }
        }
        if (front.size() > 1 && front.contains(current)) {
            for (Tab t : front) ensureView(t);
            shown.clear();
            shown.addAll(front);
            JSONArray w = s.optJSONArray("weights");
            for (int i = 0; i < MAX_PANES; i++) weights[i] = w != null && i < w.length() ? (float) Math.max(0.2, Math.min(5, w.optDouble(i, 1))) : 1;
            layoutPanes();
            for (Tab t : front) if (t.view != null) t.view.onResume();
            refreshTabs();
            refreshChrome();
        }
    }

    final Runnable saver = new Runnable() { public void run() { saveSession(); } };

    void saveSoon() {
        ui.removeCallbacks(saver);
        ui.postDelayed(saver, 1500);
    }

    void saveSession() {
        try {
            JSONArray a = new JSONArray();
            int sel = 0, n = 0;
            Map<Tab, Integer> at = new HashMap<>();
            for (Tab t : tabs) {
                if (t.priv || t.url == null || !(t.url.startsWith("http://") || t.url.startsWith("https://"))) continue;
                JSONObject o = new JSONObject();
                o.put("u", t.url); o.put("t", t.title); o.put("p", t.pinned);
                if (t.desktop) o.put("d", true);
                a.put(o);
                if (t == current) sel = n;
                at.put(t, n);
                n++;
            }
            JSONObject s = new JSONObject();
            s.put("tabs", a);
            s.put("selected", sel);
            JSONArray front = new JSONArray(), w = new JSONArray();
            if (shown.size() > 1) for (int i = 0; i < shown.size(); i++) {
                Integer k = at.get(shown.get(i));
                if (k == null) continue;
                front.put(k);
                w.put((double) weights[i]);
            }
            s.put("shown", front);
            s.put("weights", w);
            store.saveSessionRaw(s);
        } catch (Exception ignored) {}
    }

    final Runnable tabsRefresher = new Runnable() { public void run() { refreshTabs(); } };

    void refreshTabsSoon() {
        ui.removeCallbacks(tabsRefresher);
        ui.postDelayed(tabsRefresher, 120);
    }

    // ── small things ────────────────────────────────────────────

    EditText input(String hint, float sp) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setHintTextColor(Ui.faint());
        e.setTextColor(Ui.ink());
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        e.setSingleLine(true);
        e.setBackground(null);
        e.setGravity(Gravity.CENTER_VERTICAL);
        e.setPadding(0, 0, 0, 0);
        e.setHighlightColor(Ui.dark ? 0x553D7BFF : 0x333D7BFF);
        if (Ui.font != null) e.setTypeface(Ui.font);
        return e;
    }

    void keyboard(final View v, boolean show) {
        final InputMethodManager im = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (show) v.postDelayed(new Runnable() { public void run() { im.showSoftInput(v, InputMethodManager.SHOW_IMPLICIT); } }, 60);
        else im.hideSoftInputFromWindow(v.getWindowToken(), 0);
    }

    void copy(String s) {
        if (s == null) return;
        ((ClipboardManager) getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Address", s));
        toast("Copied");
    }

    void share(String url, String title) {
        if (url == null) return;
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TEXT, url);
        if (title != null) i.putExtra(Intent.EXTRA_SUBJECT, title);
        try { startActivity(Intent.createChooser(i, null)); } catch (Exception ignored) {}
    }

    void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    abstract static class Watch implements TextWatcher {
        public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
        public void onTextChanged(CharSequence s, int a, int b, int c) {}
    }
}
