package app.seek;

import android.content.Context;
import android.view.MotionEvent;
import android.webkit.WebView;

/** A web view that notices being pulled down past the top of its page, for pull to refresh. */
final class SeekView extends WebView {
    interface Pull { void pulling(SeekView v, float distance); void released(SeekView v, boolean reload); }

    Pull pull;
    private float lastY, from;
    private boolean atTop, active;
    private boolean overTop;

    SeekView(Context c) { super(c); }

    @Override public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                atTop = getScrollY() == 0;
                active = false;
                overTop = false;
                lastY = e.getY();
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                end(false);
                atTop = false;
                break;
            case MotionEvent.ACTION_MOVE:
                lastY = e.getY();
                if (e.getPointerCount() == 1 && atTop && overTop) {
                    if (!active) { active = true; from = lastY; }
                    float d = lastY - from;
                    if (d < 0 || getScrollY() > 0) end(false);
                    else if (pull != null) pull.pulling(this, d);
                }
                break;
            case MotionEvent.ACTION_UP:
                end(active && lastY - from > Ui.dp(110));
                break;
            case MotionEvent.ACTION_CANCEL:
                end(false);
                break;
        }
        return super.onTouchEvent(e);
    }

    private void end(boolean reload) {
        if (active && pull != null) pull.released(this, reload);
        active = false;
        overTop = false;
    }

    @Override protected void onOverScrolled(int x, int y, boolean clampedX, boolean clampedY) {
        super.onOverScrolled(x, y, clampedX, clampedY);
        if (clampedY && y == 0 && atTop) overTop = true;
    }
}
