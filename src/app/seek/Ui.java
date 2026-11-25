package app.seek;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.content.res.ColorStateList;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;

/** Every colour is a pair, one for a light window and one for a dark. Nothing else knows which. */
final class Ui {
    static boolean dark;
    /** Pure black, for OLED screens: every ground the browser draws is #000. */
    static boolean black;

    static int ground()   { return black ? 0xFF000000 : dark ? 0xFF1C1C1C : 0xFFFFFFFF; }
    static int ink()      { return dark ? 0xFFEDEDED : 0xFF171717; }
    static int muted()    { return black ? 0xFF8E8E8E : dark ? 0xFF949494 : 0xFF8C8C8C; }
    static int faint()    { return black ? 0xFF4A4A4A : dark ? 0xFF525252 : 0xFFD4D4D4; }
    static int hairline() { return black ? 0xFF222222 : dark ? 0xFF333333 : 0xFFE8E8E8; }
    static int wash()     { return black ? 0xFF1C1C1C : dark ? 0xFF2D2D2D : 0xFFE9E9E9; }
    static int hover()    { return black ? 0xFF121212 : dark ? 0xFF242424 : 0xFFEFEFEF; }
    static int frame()    { return black ? 0xFF000000 : dark ? 0xFF131313 : 0xFFF4F4F4; }
    static int tile()     { return black ? 0xFF0F0F0F : dark ? 0xFF222222 : 0xFFEAEAEA; }
    static int scrim()    { return black ? 0x99000000 : dark ? 0x66000000 : 0x22000000; }

    static float density = 2f;
    /** Inter, when chosen in Settings; otherwise the tablet's own sans. */
    static Typeface font, fontMedium;
    static boolean compact;
    static boolean motion = true;

    static float rowDp() { return compact ? 30 : 36; }
    static float textSp() { return compact ? 13 : 14; }

    static int dp(float v) { return Math.round(v * density); }

    static GradientDrawable round(int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    static GradientDrawable card() {
        GradientDrawable g = round(ground(), 14);
        g.setStroke(Math.max(1, dp(0.5f)), hairline());
        return g;
    }

    /** A quiet press state: the hover grey under a finger, nothing at rest. */
    static Drawable press(int rest, float radiusDp) {
        GradientDrawable mask = round(Color.BLACK, radiusDp);
        Drawable content = rest == 0 ? null : round(rest, radiusDp);
        return new RippleDrawable(ColorStateList.valueOf(dark ? 0x22FFFFFF : 0x14000000), content, mask);
    }

    static TextView text(Context c, String s, float sp, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.setIncludeFontPadding(false);
        t.setGravity(Gravity.CENTER_VERTICAL);
        if (font != null) t.setTypeface(font);
        return t;
    }

    static Typeface medium() { return fontMedium != null ? fontMedium : Typeface.create("sans-serif-medium", Typeface.NORMAL); }

    /** Drawn icons, a stroke each, so they look the same whatever fonts the tablet has. */
    static final class Icon extends View {
        static final int BACK = 1, FORWARD = 2, RELOAD = 3, MORE = 4, FOLD = 5, CLOSE = 6, PLUS = 7, UP = 8, DOWN = 9, STOP = 10, TABS = 11;
        int kind;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Path path = new Path();
        int color;

        Icon(Context c, int kind) {
            super(c);
            this.kind = kind;
            color = muted();
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
        }

        void set(int kind) { this.kind = kind; invalidate(); }
        void tint(int c) { color = c; invalidate(); }

        @Override protected void onDraw(Canvas cv) {
            float w = getWidth(), h = getHeight();
            float s = Math.min(w, h) * 0.5f;      // drawing box
            float cx = w / 2f, cy = h / 2f, r = s / 2f;
            p.setColor(isEnabled() ? color : faint());
            p.setStrokeWidth(dp(1.5f));
            p.setStyle(Paint.Style.STROKE);
            path.reset();
            switch (kind) {
                case BACK:
                    path.moveTo(cx + r * 0.35f, cy - r * 0.8f); path.lineTo(cx - r * 0.45f, cy); path.lineTo(cx + r * 0.35f, cy + r * 0.8f);
                    cv.drawPath(path, p); break;
                case FORWARD:
                    path.moveTo(cx - r * 0.35f, cy - r * 0.8f); path.lineTo(cx + r * 0.45f, cy); path.lineTo(cx - r * 0.35f, cy + r * 0.8f);
                    cv.drawPath(path, p); break;
                case UP:
                    path.moveTo(cx - r * 0.8f, cy + r * 0.35f); path.lineTo(cx, cy - r * 0.45f); path.lineTo(cx + r * 0.8f, cy + r * 0.35f);
                    cv.drawPath(path, p); break;
                case DOWN:
                    path.moveTo(cx - r * 0.8f, cy - r * 0.35f); path.lineTo(cx, cy + r * 0.45f); path.lineTo(cx + r * 0.8f, cy - r * 0.35f);
                    cv.drawPath(path, p); break;
                case RELOAD: {
                    RectF o = new RectF(cx - r * 0.8f, cy - r * 0.8f, cx + r * 0.8f, cy + r * 0.8f);
                    cv.drawArc(o, -60, 300, false, p);
                    float ax = cx + r * 0.8f * (float) Math.cos(Math.toRadians(-60));
                    float ay = cy + r * 0.8f * (float) Math.sin(Math.toRadians(-60));
                    path.moveTo(ax - r * 0.5f, ay - r * 0.1f); path.lineTo(ax + r * 0.05f, ay + r * 0.05f); path.lineTo(ax + r * 0.15f, ay - r * 0.55f);
                    cv.drawPath(path, p); break;
                }
                case STOP:
                case CLOSE:
                    cv.drawLine(cx - r * 0.6f, cy - r * 0.6f, cx + r * 0.6f, cy + r * 0.6f, p);
                    cv.drawLine(cx + r * 0.6f, cy - r * 0.6f, cx - r * 0.6f, cy + r * 0.6f, p); break;
                case PLUS:
                    cv.drawLine(cx - r * 0.7f, cy, cx + r * 0.7f, cy, p);
                    cv.drawLine(cx, cy - r * 0.7f, cx, cy + r * 0.7f, p); break;
                case MORE:
                    p.setStyle(Paint.Style.FILL);
                    float d = dp(1.6f);
                    cv.drawCircle(cx - r * 0.75f, cy, d, p); cv.drawCircle(cx, cy, d, p); cv.drawCircle(cx + r * 0.75f, cy, d, p); break;
                case TABS: {
                    RectF o = new RectF(cx - r * 0.75f, cy - r * 0.55f, cx + r * 0.95f, cy + r * 0.95f);
                    cv.drawRoundRect(o, dp(2.5f), dp(2.5f), p);
                    path.moveTo(cx - r * 0.95f, cy + r * 0.6f); path.lineTo(cx - r * 0.95f, cy - r * 0.95f); path.lineTo(cx + r * 0.6f, cy - r * 0.95f);
                    cv.drawPath(path, p); break;
                }
                case FOLD: {
                    RectF o = new RectF(cx - r, cy - r * 0.8f, cx + r, cy + r * 0.8f);
                    cv.drawRoundRect(o, dp(2.5f), dp(2.5f), p);
                    cv.drawLine(cx - r * 0.3f, cy - r * 0.8f, cx - r * 0.3f, cy + r * 0.8f, p); break;
                }
            }
        }
    }

    static Icon icon(Context c, int kind, float sizeDp) {
        Icon i = new Icon(c, kind);
        i.setBackground(press(0, sizeDp / 2f));
        i.setLayoutParams(new android.widget.LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)));
        return i;
    }

    /** A letter on a quiet tile, for a page with no icon yet. */
    static final class Letter extends View {
        String letter = "";
        android.graphics.Bitmap bitmap;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        final RectF box = new RectF();
        Letter(Context c) { super(c); }
        void set(String l, android.graphics.Bitmap b) { letter = l == null ? "" : l; bitmap = b; invalidate(); }
        @Override protected void onDraw(Canvas cv) {
            float w = getWidth(), h = getHeight();
            box.set(0, 0, w, h);
            if (bitmap != null && !bitmap.isRecycled()) {
                cv.drawBitmap(bitmap, null, box, p);
                return;
            }
            p.setStyle(Paint.Style.FILL);
            p.setColor(hairline());
            cv.drawRoundRect(box, dp(3), dp(3), p);
            p.setColor(muted());
            p.setTextSize(h * 0.6f);
            p.setTypeface(medium());
            p.setTextAlign(Paint.Align.CENTER);
            Paint.FontMetrics fm = p.getFontMetrics();
            cv.drawText(letter, w / 2f, h / 2f - (fm.ascent + fm.descent) / 2f, p);
        }
    }

    private Ui() {}
}
