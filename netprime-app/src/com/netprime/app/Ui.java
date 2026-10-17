package com.netprime.app;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.RadialGradient;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** The app's own screens: loading screen, top progress line, hint pill and error screen. */
final class Ui {

    // Design tokens
    static final int BG = 0xFF0A0A0F;
    static final int SURFACE = 0xFF16161D;
    static final int STROKE = 0x1FFFFFFF;
    static final int TEXT = 0xFFFFFFFF;
    static final int MUTED = 0xFFA1A1AA;
    static final int RED = 0xFFE50914;
    static final int RED_LIGHT = 0xFFFF3D57;
    static final int RED_DARK = 0xFFB20710;

    private static Typeface bold, semibold, medium;

    private Ui() {}

    static void loadFonts(Context c) {
        bold = font(c, "fonts/Inter-Bold.otf", Typeface.DEFAULT_BOLD);
        semibold = font(c, "fonts/Inter-SemiBold.otf", Typeface.DEFAULT_BOLD);
        medium = font(c, "fonts/Inter-Medium.otf", Typeface.DEFAULT);
    }

    private static Typeface font(Context c, String path, Typeface fallback) {
        try {
            return Typeface.createFromAsset(c.getAssets(), path);
        } catch (Exception e) {
            return fallback;
        }
    }

    static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                c.getResources().getDisplayMetrics()));
    }

    static TextView text(Context c, String s, float sp, int color, Typeface tf) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(tf);
        t.setIncludeFontPadding(false);
        return t;
    }

    /** Red glow behind a view using its shadow (Android 9+; older versions get a normal shadow). */
    static void glow(View v, float elevationDp) {
        v.setElevation(dp(v.getContext(), elevationDp));
        if (Build.VERSION.SDK_INT >= 28) {
            v.setOutlineAmbientShadowColor(RED);
            v.setOutlineSpotShadowColor(RED_LIGHT);
        }
    }

    static GradientDrawable rounded(int color, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        return g;
    }

    // ---------------------------------------------------------------- Loading screen

    static final class Splash extends FrameLayout {
        private boolean gone;
        private Runnable onGone;
        private final long shownAt = System.currentTimeMillis();

        Splash(Context c) {
            super(c);
            GradientDrawable bg = new GradientDrawable();
            bg.setGradientType(GradientDrawable.RADIAL_GRADIENT);
            bg.setColors(new int[] { 0xFF2A0B10, BG });
            bg.setGradientRadius(Math.max(c.getResources().getDisplayMetrics().widthPixels,
                    c.getResources().getDisplayMetrics().heightPixels) * 0.6f);
            setBackground(bg);
            setClickable(true);

            LinearLayout col = new LinearLayout(c);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER_HORIZONTAL);

            ImageView logo = new ImageView(c);
            logo.setImageResource(c.getResources().getIdentifier(
                    "logo_mark", "drawable", c.getPackageName()));
            col.addView(logo, new LinearLayout.LayoutParams(dp(c, 104), dp(c, 104)));

            TextView name = text(c, "NetPrime", 34, TEXT, bold);
            name.setLetterSpacing(-0.02f);
            LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            nlp.topMargin = dp(c, 20);
            col.addView(name, nlp);

            TextView tag = text(c, "Movies & Shows", 15, MUTED, medium);
            tag.setLetterSpacing(0.04f);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tlp.topMargin = dp(c, 8);
            col.addView(tag, tlp);

            LoaderBar bar = new LoaderBar(c);
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(dp(c, 180), dp(c, 16));
            blp.topMargin = dp(c, 30);
            col.addView(bar, blp);

            addView(col, new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));

            col.setAlpha(0f);
            col.setScaleX(0.94f);
            col.setScaleY(0.94f);
            col.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(450)
                    .setInterpolator(new DecelerateInterpolator()).start();

            // Never block the app for too long.
            postDelayed(new Runnable() {
                @Override
                public void run() {
                    dismiss();
                }
            }, 15000);
        }

        void setOnGone(Runnable r) {
            onGone = r;
        }

        void dismiss() {
            if (gone) return;
            gone = true;
            long wait = Math.max(0, 900 - (System.currentTimeMillis() - shownAt));
            animate().alpha(0f).setStartDelay(wait).setDuration(350)
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator a) {
                            setVisibility(GONE);
                            if (getParent() instanceof ViewGroup) ((ViewGroup) getParent()).removeView(Splash.this);
                            if (onGone != null) onGone.run();
                        }
                    }).start();
        }
    }

    /** Thin bar with a red segment sliding across it. */
    static final class LoaderBar extends View {
        private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint seg = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private float t;
        private ValueAnimator anim;

        LoaderBar(Context c) {
            super(c);
            track.setColor(0x26FFFFFF);
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            anim = ValueAnimator.ofFloat(0f, 1f);
            anim.setDuration(1300);
            anim.setRepeatCount(ValueAnimator.INFINITE);
            anim.setInterpolator(new LinearInterpolator());
            anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator a) {
                    t = (Float) a.getAnimatedValue();
                    invalidate();
                }
            });
            anim.start();
        }

        @Override
        protected void onDetachedFromWindow() {
            if (anim != null) anim.cancel();
            super.onDetachedFromWindow();
        }

        private final Paint halo = new Paint(Paint.ANTI_ALIAS_FLAG);

        @Override
        protected void onDraw(Canvas c) {
            // 3dp pill track in the middle; the extra height is room for the glow.
            float w = getWidth(), bh = dp(getContext(), 3), top = (getHeight() - bh) / 2, rad = bh / 2;
            r.set(0, top, w, top + bh);
            c.drawRoundRect(r, rad, rad, track);
            float segW = w * 0.35f;
            float x = -segW + (w + segW) * t;
            float head = Math.min(Math.max(x + segW, 0), w), cy = getHeight() / 2f, gr = getHeight() * 0.9f;
            halo.setShader(new RadialGradient(head, cy, gr, 0x99FF3D57, 0x00E50914, Shader.TileMode.CLAMP));
            c.drawCircle(head, cy, gr, halo);
            seg.setShader(new LinearGradient(x, 0, x + segW, 0,
                    new int[] { 0x00E50914, RED, 0xFFFF6B7F }, null, Shader.TileMode.CLAMP));
            c.save();
            c.clipRect(0, 0, w, getHeight());
            r.set(x, top, x + segW, top + bh);
            c.drawRoundRect(r, rad, rad, seg);
            c.restore();
        }
    }

    // ---------------------------------------------------------------- Top progress line

    static final class TopProgress extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float shown;      // 0..1 currently drawn
        private ValueAnimator anim;

        TopProgress(Context c) {
            super(c);
            setAlpha(0f);
        }

        void setProgress(int percent) {
            float target = percent / 100f;
            if (anim != null) anim.cancel();
            if (percent < 100) animate().alpha(1f).setStartDelay(0).setDuration(150).start();
            if (target < shown) shown = 0;
            anim = ValueAnimator.ofFloat(shown, target);
            anim.setDuration(250);
            anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator a) {
                    shown = (Float) a.getAnimatedValue();
                    invalidate();
                }
            });
            anim.start();
            if (percent >= 100) animate().alpha(0f).setStartDelay(250).setDuration(300).start();
        }

        private final Paint halo = new Paint(Paint.ANTI_ALIAS_FLAG);

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth() * shown;
            if (w <= 0) return;
            float bh = dp(getContext(), 3), gr = dp(getContext(), 14);
            // soft glow under the line and a brighter glow at its tip
            halo.setShader(new LinearGradient(0, 0, 0, gr, 0x40E50914, 0x00E50914, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, gr, halo);
            halo.setShader(new RadialGradient(w, bh / 2, gr, 0xB3FF3D57, 0x00E50914, Shader.TileMode.CLAMP));
            c.drawCircle(w, bh / 2, gr, halo);
            p.setShader(new LinearGradient(0, 0, Math.max(w, 1), 0,
                    RED_DARK, 0xFFFF6B7F, Shader.TileMode.CLAMP));
            c.drawRoundRect(new RectF(0, 0, w, bh), bh / 2, bh / 2, p);
        }
    }

    // ---------------------------------------------------------------- Hint pill

    static final class Hint extends FrameLayout {
        private final TextView label;
        private final Handler h = new Handler(Looper.getMainLooper());
        private final Runnable hide = new Runnable() {
            @Override
            public void run() {
                animate().alpha(0f).translationY(dp(getContext(), 12)).setDuration(250).start();
            }
        };

        Hint(Context c) {
            super(c);
            label = text(c, "", 15, TEXT, medium);
            label.setGravity(Gravity.CENTER_VERTICAL);
            float r = dp(c, 999);
            GradientDrawable border = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                    new int[] { 0xCCFF3D57, 0x33FFFFFF, 0xCCFF3D57 });
            border.setCornerRadius(r);
            GradientDrawable fill = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[] { 0xF21C1C24, 0xF20E0E13 });
            fill.setCornerRadius(r);
            LayerDrawable bg = new LayerDrawable(new Drawable[] { border, fill });
            int in = Math.max(1, dp(c, 1.2f));
            bg.setLayerInset(1, in, in, in, in);
            label.setBackground(bg);
            GradientDrawable dotD = new GradientDrawable();
            dotD.setShape(GradientDrawable.OVAL);
            dotD.setColor(RED_LIGHT);
            dotD.setStroke(dp(c, 2), 0x55FF3D57);
            dotD.setSize(dp(c, 10), dp(c, 10));
            label.setCompoundDrawablesRelativeWithIntrinsicBounds(dotD, null, null, null);
            label.setCompoundDrawablePadding(dp(c, 12));
            label.setPadding(dp(c, 18), dp(c, 13), dp(c, 24), dp(c, 13));
            glow(label, 14);
            setClipChildren(false);
            setClipToPadding(false);
            setPadding(0, dp(c, 16), 0, dp(c, 16));
            addView(label, new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
            setAlpha(0f);
            setClickable(false);
            setFocusable(false);
        }

        void show(String s, long ms) {
            label.setText(s);
            h.removeCallbacks(hide);
            setTranslationY(dp(getContext(), 12));
            animate().alpha(1f).translationY(0).setDuration(250)
                    .setInterpolator(new DecelerateInterpolator()).start();
            h.postDelayed(hide, ms);
        }
    }

    // ---------------------------------------------------------------- Error screen

    static View errorScreen(Context c, View.OnClickListener retry, View.OnClickListener exit,
                            View[] firstButtonOut) {
        FrameLayout root = new FrameLayout(c);
        GradientDrawable bg = new GradientDrawable();
        bg.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        bg.setColors(new int[] { 0xFF1E0A0D, BG });
        bg.setGradientRadius(c.getResources().getDisplayMetrics().widthPixels * 0.7f);
        root.setBackground(bg);
        root.setClickable(true);

        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        GradientDrawable cb = rounded(SURFACE, dp(c, 24));
        cb.setStroke(dp(c, 1), STROKE);
        card.setBackground(cb);
        card.setPadding(dp(c, 36), dp(c, 36), dp(c, 36), dp(c, 32));
        card.setElevation(dp(c, 16));

        card.addView(new OfflineIcon(c), new LinearLayout.LayoutParams(dp(c, 64), dp(c, 64)));

        TextView title = text(c, "Can't connect", 24, TEXT, bold);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tl.topMargin = dp(c, 20);
        card.addView(title, tl);

        TextView body = text(c, "Check your internet connection, then try again.", 16, MUTED, medium);
        body.setGravity(Gravity.CENTER);
        body.setLineSpacing(dp(c, 4), 1f);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bl.topMargin = dp(c, 10);
        card.addView(body, bl);

        LinearLayout buttons = new LinearLayout(c);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setClipChildren(false);
        buttons.setClipToPadding(false);
        buttons.setPadding(dp(c, 12), dp(c, 12), dp(c, 12), dp(c, 12));
        card.setClipChildren(false);
        card.setClipToPadding(false);
        buttons.setGravity(Gravity.CENTER);
        TextView again = button(c, "Try again", true);
        again.setOnClickListener(retry);
        TextView close = button(c, "Exit", false);
        close.setOnClickListener(exit);
        LinearLayout.LayoutParams b1 = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(c, 48));
        buttons.addView(again, b1);
        LinearLayout.LayoutParams b2 = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(c, 48));
        b2.leftMargin = dp(c, 12);
        buttons.addView(close, b2);
        LinearLayout.LayoutParams bwrap = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bwrap.topMargin = dp(c, 16);
        card.addView(buttons, bwrap);

        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(
                Math.min(dp(c, 460), c.getResources().getDisplayMetrics().widthPixels - dp(c, 32)),
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        root.addView(card, clp);
        firstButtonOut[0] = again;
        return root;
    }

    /** Pill button with a clear focus state for the TV remote. */
    static TextView button(final Context c, String label, boolean primary) {
        final TextView b = text(c, label, 16, TEXT, semibold);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(c, 28), 0, dp(c, 28), 0);
        b.setFocusable(true);
        b.setClickable(true);

        float r = dp(c, 999);
        GradientDrawable normal = primary
                ? new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[] { 0xFFF0213A, RED_DARK })
                : new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[] { 0x26FFFFFF, 0x14FFFFFF });
        normal.setCornerRadius(r);
        normal.setStroke(dp(c, 1), primary ? 0x40FFFFFF : 0x1FFFFFFF);
        GradientDrawable focused = primary
                ? new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[] { 0xFFFF5A70, RED })
                : new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[] { 0x4DFFFFFF, 0x26FFFFFF });
        focused.setCornerRadius(r);
        focused.setStroke(dp(c, 2), 0xE6FFFFFF);
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[] { android.R.attr.state_focused }, focused);
        states.addState(new int[] {}, normal);
        Drawable bg = new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), states, null);
        b.setBackground(bg);
        glow(b, primary ? 6 : 0);

        // Focused: grows a little and the red glow slowly breathes.
        final ValueAnimator pulse = ValueAnimator.ofFloat(dp(c, 10), dp(c, 22));
        pulse.setDuration(1300);
        pulse.setRepeatMode(ValueAnimator.REVERSE);
        pulse.setRepeatCount(ValueAnimator.INFINITE);
        pulse.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                b.setTranslationZ((Float) a.getAnimatedValue());
            }
        });
        b.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean has) {
                float s = has ? 1.07f : 1f;
                v.animate().scaleX(s).scaleY(s).setDuration(180)
                        .setInterpolator(new DecelerateInterpolator()).start();
                if (has) {
                    if (Build.VERSION.SDK_INT >= 28) {
                        v.setOutlineAmbientShadowColor(RED);
                        v.setOutlineSpotShadowColor(RED_LIGHT);
                    }
                    pulse.start();
                } else {
                    pulse.cancel();
                    v.animate().translationZ(0).setDuration(180).start();
                }
            }
        });
        return b;
    }

    /** Circle with a slashed signal glyph. */
    static final class OfflineIcon extends View {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);

        OfflineIcon(Context c) {
            super(c);
            fill.setColor(0x26E50914);
            line.setColor(RED_LIGHT);
            line.setStyle(Paint.Style.STROKE);
            line.setStrokeCap(Paint.Cap.ROUND);
            line.setStrokeWidth(dp(c, 3));
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), cx = w / 2, cy = getHeight() / 2;
            c.drawCircle(cx, cy, w / 2, fill);
            float s = w * 0.17f;
            RectF a = new RectF();
            for (int i = 1; i <= 2; i++) {
                float rr = s * i;
                a.set(cx - rr, cy + s * 0.6f - rr, cx + rr, cy + s * 0.6f + rr);
                c.drawArc(a, 225, 90, false, line);
            }
            Paint dot = new Paint(line);
            dot.setStyle(Paint.Style.FILL);
            c.drawCircle(cx, cy + s * 0.6f, dp(getContext(), 2.5f), dot);
            c.drawLine(cx - s * 1.6f, cy - s * 1.4f, cx + s * 1.6f, cy + s * 1.4f, line);
        }
    }
}
