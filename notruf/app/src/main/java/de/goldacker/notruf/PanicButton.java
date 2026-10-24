package de.goldacker.notruf;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.LinearInterpolator;

/** Big round red button that fires only after being held for HOLD_MS. */
public class PanicButton extends View {
    interface OnTriggerListener {
        void onTrigger();
    }

    private static final long HOLD_MS = 2000;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint title = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arc = new RectF();
    private final float density;

    private OnTriggerListener listener;
    private ValueAnimator hold;
    private float progress;
    private boolean triggered;
    private String hintText = "2 Sekunden gedrückt halten";

    public PanicButton(Context context) {
        super(context);
        density = getResources().getDisplayMetrics().density;
        fill.setColor(0xFFD32F2F);
        ring.setColor(0xFFFFFFFF);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeCap(Paint.Cap.ROUND);
        ring.setStrokeWidth(10 * density);
        title.setColor(0xFFFFFFFF);
        title.setTextAlign(Paint.Align.CENTER);
        title.setFakeBoldText(true);
        hint.setColor(0xDDFFFFFF);
        hint.setTextAlign(Paint.Align.CENTER);
        setContentDescription("Notruf auslösen, zwei Sekunden gedrückt halten");
    }

    void setOnTriggerListener(OnTriggerListener l) {
        listener = l;
    }

    void setHint(String text) {
        hintText = text;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        float r = Math.min(getWidth(), getHeight()) / 2f - 12 * density;
        if (r <= 0) return;

        fill.setColor(isEnabled() ? (progress > 0 ? 0xFFB71C1C : 0xFFD32F2F) : 0xFF6D2A2A);
        canvas.drawCircle(cx, cy, r, fill);

        if (progress > 0) {
            float inset = ring.getStrokeWidth();
            arc.set(cx - r + inset, cy - r + inset, cx + r - inset, cy + r - inset);
            canvas.drawArc(arc, -90, 360 * progress, false, ring);
        }

        title.setTextSize(r * 0.36f);
        canvas.drawText("NOTRUF", cx, cy + r * 0.12f, title);
        hint.setTextSize(r * 0.09f);
        canvas.drawText(hintText, cx, cy + r * 0.42f, hint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (!isEnabled()) return false;
        int action = e.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            if (!insideCircle(e.getX(), e.getY())) return false;
            startHold();
        } else if (action == MotionEvent.ACTION_UP) {
            performClick();
            cancelHold();
        } else if (action == MotionEvent.ACTION_CANCEL) {
            cancelHold();
        }
        return true;
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    private boolean insideCircle(float x, float y) {
        float dx = x - getWidth() / 2f, dy = y - getHeight() / 2f;
        float r = Math.min(getWidth(), getHeight()) / 2f;
        return dx * dx + dy * dy <= r * r;
    }

    private void startHold() {
        cancelHold();
        triggered = false;
        hold = ValueAnimator.ofFloat(0f, 1f);
        hold.setDuration(HOLD_MS);
        hold.setInterpolator(new LinearInterpolator());
        hold.addUpdateListener(a -> {
            progress = (float) a.getAnimatedValue();
            invalidate();
        });
        hold.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;

            @Override
            public void onAnimationCancel(Animator animation) {
                cancelled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (cancelled) return;
                triggered = true;
                progress = 0;
                invalidate();
                if (listener != null) listener.onTrigger();
            }
        });
        hold.start();
    }

    private void cancelHold() {
        if (hold != null) {
            hold.cancel();
            hold = null;
        }
        if (!triggered) {
            progress = 0;
            invalidate();
        }
    }
}
