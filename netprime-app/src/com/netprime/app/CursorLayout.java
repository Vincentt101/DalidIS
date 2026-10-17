package com.netprime.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Choreographer;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

/**
 * Root view of the app. Can deliver taps anywhere on screen, and on TV can show a
 * mouse-style pointer (pointer mode) that the remote's arrow keys move.
 * The pointer is its own small layer moved with translation, synced to the display's
 * refresh, so moving it never redraws the page. Pushing against an edge scrolls the page.
 * OK is handled by MainActivity.
 */
class CursorLayout extends FrameLayout {

    private static final long HIDE_AFTER_MS = 6000;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final float density;
    private final Pointer pointer;
    private final int half;

    private boolean enabled;
    private boolean placed;
    private float x, y;
    private int dx, dy;            // direction from held arrow keys
    private long moveStart;        // when the current movement started (for acceleration)
    private long lastFrame;
    private boolean moving;
    private long lastActivity;
    private View scrollTarget;

    CursorLayout(Context context) {
        super(context);
        density = getResources().getDisplayMetrics().density;
        pointer = new Pointer(context);
        int size = Math.round(52 * density);
        half = size / 2;
        pointer.setVisibility(GONE);
        addView(pointer, new LayoutParams(size, size));
    }

    void setPointerMode(boolean on) {
        enabled = on;
        dx = dy = 0;
        stopMoving();
        if (on) {
            pointer.bringToFront();
            pointer.setVisibility(VISIBLE);
            pointer.setAlpha(0f);
            pointer.setScaleX(0.6f);
            pointer.setScaleY(0.6f);
            place();
            wake();
        } else {
            handler.removeCallbacks(hideCheck);
            pointer.animate().alpha(0f).scaleX(0.6f).scaleY(0.6f).setDuration(150)
                    .withEndAction(new Runnable() {
                        @Override
                        public void run() {
                            if (!enabled) pointer.setVisibility(GONE);
                        }
                    }).start();
        }
    }

    boolean isPointerMode() {
        return enabled;
    }

    void setScrollTarget(View v) {
        scrollTarget = v;
    }

    /** Taps at the pointer, with a small press animation. */
    void tapAtPointer() {
        wake();
        pointer.animate().scaleX(0.8f).scaleY(0.8f).setDuration(70).withEndAction(new Runnable() {
            @Override
            public void run() {
                pointer.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
            }
        }).start();
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
        ev.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        dispatchTouchEvent(ev);
        ev.recycle();
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
        place();
    }

    private void place() {
        pointer.setTranslationX(x - half);
        pointer.setTranslationY(y - half);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (!enabled) return super.dispatchKeyEvent(event);
        int code = event.getKeyCode();
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
                onArrow(code, event.getAction() == KeyEvent.ACTION_DOWN, event.getRepeatCount());
                return true;
            default:
                return super.dispatchKeyEvent(event);
        }
    }

    private void onArrow(int code, boolean down, int repeat) {
        int ddx = code == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : code == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : 0;
        int ddy = code == KeyEvent.KEYCODE_DPAD_UP ? -1 : code == KeyEvent.KEYCODE_DPAD_DOWN ? 1 : 0;
        if (down) {
            if (repeat > 0) return; // movement continues frame by frame while held
            boolean wasHidden = pointer.getAlpha() < 0.5f;
            wake();
            if (wasHidden) return; // first press only shows the pointer
            if (ddx != 0) dx = ddx;
            if (ddy != 0) dy = ddy;
            if (!moving) {
                moving = true;
                moveStart = lastFrame = System.nanoTime();
                // A single short press still nudges the pointer a little.
                step(1f / 60f, 0f);
                Choreographer.getInstance().postFrameCallback(frame);
            }
        } else {
            if (ddx != 0 && dx == ddx) dx = 0;
            if (ddy != 0 && dy == ddy) dy = 0;
            if (dx == 0 && dy == 0) stopMoving();
        }
    }

    private void stopMoving() {
        moving = false;
        Choreographer.getInstance().removeFrameCallback(frame);
    }

    private final Choreographer.FrameCallback frame = new Choreographer.FrameCallback() {
        @Override
        public void doFrame(long now) {
            if (!moving || (dx == 0 && dy == 0)) {
                moving = false;
                return;
            }
            float dt = Math.min((now - lastFrame) / 1e9f, 0.05f);
            lastFrame = now;
            step(dt, (now - moveStart) / 1e9f);
            Choreographer.getInstance().postFrameCallback(this);
        }
    };

    /** Moves the pointer for dt seconds; speed eases up the longer the key is held. */
    private void step(float dt, float heldFor) {
        float t = Math.min(heldFor / 0.9f, 1f);
        float ease = t * t * (3 - 2 * t);
        float speed = (260 + 1200 * ease) * density; // px per second
        float nx = x + dx * speed * dt;
        float ny = y + dy * speed * dt;
        int w = getWidth(), h = getHeight();
        float m = 2 * density;

        if (scrollTarget != null && scrollTarget.getVisibility() == VISIBLE) {
            int s = Math.round(speed * dt * 1.6f);
            if (ny < m && dy < 0) scrollTarget.scrollBy(0, -s);
            if (ny > h - m && dy > 0) scrollTarget.scrollBy(0, s);
            if (nx < m && dx < 0) scrollTarget.scrollBy(-s, 0);
            if (nx > w - m && dx > 0) scrollTarget.scrollBy(s, 0);
        }
        x = Math.max(m, Math.min(nx, w - m));
        y = Math.max(m, Math.min(ny, h - m));
        place();
        lastActivity = SystemClock.uptimeMillis();
    }

    private final Runnable hideCheck = new Runnable() {
        @Override
        public void run() {
            long idle = SystemClock.uptimeMillis() - lastActivity;
            if (idle >= HIDE_AFTER_MS && !moving) {
                pointer.animate().alpha(0f).setDuration(300).start();
            } else {
                handler.postDelayed(this, HIDE_AFTER_MS - idle + 50);
            }
        }
    };

    private void wake() {
        lastActivity = SystemClock.uptimeMillis();
        if (pointer.getAlpha() < 1f) {
            pointer.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(160).start();
        }
        handler.removeCallbacks(hideCheck);
        handler.postDelayed(hideCheck, HIDE_AFTER_MS);
    }

    @Override
    protected void onDetachedFromWindow() {
        handler.removeCallbacksAndMessages(null);
        stopMoving();
        super.onDetachedFromWindow();
    }

    /** The pointer graphic: white dot, red ring, soft shadow. Drawn once, then only moved. */
    private static final class Pointer extends View {
        private final Paint shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint halo = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float d;

        Pointer(Context c) {
            super(c);
            d = c.getResources().getDisplayMetrics().density;
            shadow.setColor(0x59000000);
            fill.setColor(0xF2FFFFFF);
            ring.setColor(Ui.RED);
            ring.setStyle(Paint.Style.STROKE);
            ring.setStrokeWidth(2.5f * d);
            dot.setColor(Ui.RED);
            setLayerType(LAYER_TYPE_HARDWARE, null);
            setClickable(false);
            setFocusable(false);
        }

        @Override
        protected void onDraw(Canvas c) {
            float cx = getWidth() / 2f, cy = getHeight() / 2f, r = 10 * d;
            float gr = getWidth() / 2f;
            halo.setShader(new RadialGradient(cx, cy, gr, new int[] { 0x80FF3D57, 0x40E50914, 0x00E50914 },
                    new float[] { 0.3f, 0.6f, 1f }, Shader.TileMode.CLAMP));
            c.drawCircle(cx, cy, gr, halo);
            c.drawCircle(cx, cy + 1.5f * d, r + 2.5f * d, shadow);
            c.drawCircle(cx, cy, r, fill);
            c.drawCircle(cx, cy, r, ring);
            c.drawCircle(cx, cy, 3 * d, dot);
        }
    }
}
