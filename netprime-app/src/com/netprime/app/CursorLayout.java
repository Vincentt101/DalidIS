package com.netprime.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

/**
 * Root view of the app. Can deliver taps anywhere on screen, and on TV can show a
 * mouse-style pointer (pointer mode) that the remote's arrow keys move.
 * Pushing the pointer against an edge scrolls the page. OK is handled by MainActivity.
 */
class CursorLayout extends FrameLayout {

    private static final long FRAME_MS = 16;
    private static final long HIDE_AFTER_MS = 6000;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;

    private boolean enabled;
    private boolean visible;
    private boolean placed;
    private float x, y;
    private int dx, dy;           // current direction from held arrow keys
    private float speed;
    private long lastActivity;
    private View scrollTarget;

    CursorLayout(Context context) {
        super(context);
        setWillNotDraw(false);
        density = getResources().getDisplayMetrics().density;
        fill.setColor(0xF2FFFFFF);
        ring.setColor(0xFFE50914);
        shadow.setColor(0x59000000);
        dot.setColor(0xFFE50914);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(2.5f * density);
    }

    void setPointerMode(boolean on) {
        enabled = on;
        visible = on;
        dx = dy = 0;
        speed = 0;
        lastActivity = SystemClock.uptimeMillis();
        invalidate();
        handler.removeCallbacks(hideCheck);
        if (on) handler.postDelayed(hideCheck, HIDE_AFTER_MS);
    }

    boolean isPointerMode() {
        return enabled;
    }

    /** Taps at the pointer. */
    void tapAtPointer() {
        wake();
        tap(x, y);
    }

    /** Taps at a screen position inside this view, like a finger would. */
    void tap(final float tx, final float ty) {
        final long down = SystemClock.uptimeMillis();
        touch(down, down, MotionEvent.ACTION_DOWN, tx, ty);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                touch(down, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, tx, ty);
            }
        }, 60);
    }

    private void touch(long downAt, long now, int action, float tx, float ty) {
        MotionEvent ev = MotionEvent.obtain(downAt, now, action, tx, ty, 0);
        ev.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
        dispatchTouchEvent(ev);
        ev.recycle();
    }

    void setScrollTarget(View v) {
        scrollTarget = v;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (!placed && w > 0 && h > 0) {
            x = w / 2f;
            y = h / 2f;
            placed = true;
        } else {
            x = Math.min(x, w - 1);
            y = Math.min(y, h - 1);
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (!enabled) return super.dispatchKeyEvent(event);
        int code = event.getKeyCode();
        boolean down = event.getAction() == KeyEvent.ACTION_DOWN;

        switch (code) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
                onArrow(code, down, event.getRepeatCount());
                return true;
            default:
                return super.dispatchKeyEvent(event);
        }
    }

    private void onArrow(int code, boolean down, int repeat) {
        int ddx = code == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : code == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : 0;
        int ddy = code == KeyEvent.KEYCODE_DPAD_UP ? -1 : code == KeyEvent.KEYCODE_DPAD_DOWN ? 1 : 0;
        if (down) {
            if (repeat > 0) return; // movement continues from the frame loop while held
            boolean wasHidden = !visible;
            wake();
            if (wasHidden) return; // first press only shows the pointer
            if (ddx != 0) dx = ddx;
            if (ddy != 0) dy = ddy;
            if (speed == 0) {
                speed = 4 * density;
                handler.removeCallbacks(mover);
                handler.post(mover);
            }
        } else {
            if (ddx != 0 && dx == ddx) dx = 0;
            if (ddy != 0 && dy == ddy) dy = 0;
            if (dx == 0 && dy == 0) speed = 0;
        }
    }

    private final Runnable mover = new Runnable() {
        @Override
        public void run() {
            if (speed == 0 || (dx == 0 && dy == 0)) {
                speed = 0;
                return;
            }
            speed = Math.min(speed + 0.6f * density, 22 * density);
            float nx = x + dx * speed;
            float ny = y + dy * speed;
            int w = getWidth(), h = getHeight();
            float margin = 2 * density;

            // Scroll the page when pushing against an edge.
            if (scrollTarget != null && scrollTarget.getVisibility() == VISIBLE) {
                int step = Math.round(speed * 1.5f);
                if (ny < margin && dy < 0) scrollTarget.scrollBy(0, -step);
                if (ny > h - margin && dy > 0) scrollTarget.scrollBy(0, step);
                if (nx < margin && dx < 0) scrollTarget.scrollBy(-step, 0);
                if (nx > w - margin && dx > 0) scrollTarget.scrollBy(step, 0);
            }
            x = Math.max(margin, Math.min(nx, w - margin));
            y = Math.max(margin, Math.min(ny, h - margin));
            lastActivity = SystemClock.uptimeMillis();
            invalidate();
            handler.postDelayed(this, FRAME_MS);
        }
    };

    private final Runnable hideCheck = new Runnable() {
        @Override
        public void run() {
            long idle = SystemClock.uptimeMillis() - lastActivity;
            if (idle >= HIDE_AFTER_MS && speed == 0) {
                visible = false;
                invalidate();
            } else {
                handler.postDelayed(this, HIDE_AFTER_MS - idle + 50);
            }
        }
    };

    private void wake() {
        lastActivity = SystemClock.uptimeMillis();
        if (!visible) {
            visible = true;
            invalidate();
        }
        handler.removeCallbacks(hideCheck);
        handler.postDelayed(hideCheck, HIDE_AFTER_MS);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        if (enabled && visible) {
            float r = 10 * density;
            canvas.drawCircle(x, y + 1.5f * density, r + 3 * density, shadow);
            canvas.drawCircle(x, y, r, fill);
            canvas.drawCircle(x, y, r, ring);
            canvas.drawCircle(x, y, 3 * density, dot);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        handler.removeCallbacksAndMessages(null);
        super.onDetachedFromWindow();
    }
}
