package app.seek;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.LruCache;

import java.io.File;
import java.io.FileOutputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executor;

/** Site icons, kept small on disk so tabs brought back at launch show theirs straight away. */
final class Icons {
    static File dir;
    private static final LruCache<String, Bitmap> MEM = new LruCache<>(96);
    private static final Set<String> MISSING = new HashSet<>();

    static String name(String host) { return host.replaceAll("[^a-z0-9.-]", "_") + ".png"; }

    static Bitmap get(String host) {
        if (host == null || dir == null) return null;
        Bitmap b = MEM.get(host);
        if (b != null || MISSING.contains(host)) return b;
        File f = new File(dir, name(host));
        if (f.exists()) b = BitmapFactory.decodeFile(f.getPath());
        if (b != null) MEM.put(host, b); else MISSING.add(host);
        return b;
    }

    static void put(final String host, Bitmap icon, Executor io) {
        if (host == null || icon == null || dir == null) return;
        int s = Math.min(64, Math.max(icon.getWidth(), icon.getHeight()));
        final Bitmap b = icon.getWidth() == s && icon.getHeight() == s ? icon : Bitmap.createScaledBitmap(icon, s, s, true);
        MEM.put(host, b);
        MISSING.remove(host);
        io.execute(new Runnable() { public void run() {
            try {
                dir.mkdirs();
                FileOutputStream out = new FileOutputStream(new File(dir, name(host)));
                try { b.compress(Bitmap.CompressFormat.PNG, 100, out); } finally { out.close(); }
            } catch (Exception ignored) {}
        } });
    }

    private Icons() {}
}
