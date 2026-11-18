package com.example.my_application_sandbox;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.animation.BounceInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.TextView;

/** Calque plein écran avec la roue et la balle, affiché pendant la phase SPINNING. */
public class SpinOverlayView extends FrameLayout {

    private final FrameLayout box;      // zone carrée contenant roue + balle
    private final RouletteWheelView wheel;
    private final FrameLayout orbit;    // on tourne ce conteneur pour faire orbiter la balle
    private final View ball;
    private final TextView txtResult;
    private final float dp;
    private AnimatorSet set;
    private final MiniTableView table;

    public SpinOverlayView(Context c) {
        super(c);
        dp = c.getResources().getDisplayMetrics().density;
        setBackgroundColor(0xDD000000);
        setClickable(true);                 // bloque les touches vers la table pendant la roue
        setVisibility(INVISIBLE);           // INVISIBLE (et pas GONE) : la vue reste mesurée

        box = new FrameLayout(c);
        wheel = new RouletteWheelView(c);
        box.addView(wheel, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        orbit = new FrameLayout(c);
        ball = new View(c);
        GradientDrawable oval = new GradientDrawable();
        oval.setShape(GradientDrawable.OVAL);
        oval.setColor(0xFFF5F5F5);
        ball.setBackground(oval);
        LayoutParams bp = new LayoutParams((int) (14 * dp), (int) (14 * dp),
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        bp.topMargin = (int) (10 * dp);
        orbit.addView(ball, bp);
        box.addView(orbit, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        addView(box, new LayoutParams(0, 0, Gravity.TOP | Gravity.CENTER_HORIZONTAL));

        table = new MiniTableView(c);
        addView(table, new LayoutParams(0, 0, Gravity.TOP | Gravity.CENTER_HORIZONTAL));

        txtResult = new TextView(c);
        txtResult.setTextColor(0xFFFFFFFF);
        txtResult.setTextSize(22);
        txtResult.setGravity(Gravity.CENTER);
        txtResult.setTypeface(Typeface.DEFAULT_BOLD);
        txtResult.setVisibility(GONE);
        LayoutParams tp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        tp.bottomMargin = (int) (64 * dp);
        addView(txtResult, tp);
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int w = MeasureSpec.getSize(widthSpec);
        int h = MeasureSpec.getSize(heightSpec);

        int top = (int) (32 * dp);
        int tableW = (int) Math.min(w * 0.94f, 560 * dp);
        int tableH = Math.round(tableW * MiniTableView.ROWS / (float) MiniTableView.COLS);
        LayoutParams tp = (LayoutParams) table.getLayoutParams();
        tp.width = tableW;
        tp.height = tableH;
        tp.topMargin = top;

        int areaTop = top + tableH + (int) (12 * dp);
        int areaH = h - areaTop - (int) (72 * dp);
        int size = (int) Math.max(Math.min(w * 0.85f, areaH), 120 * dp);
        LayoutParams lp = (LayoutParams) box.getLayoutParams();
        lp.width = size;
        lp.height = size;
        lp.topMargin = areaTop + Math.max(0, (areaH - size) / 2);

        super.onMeasure(widthSpec, heightSpec);
    }

    /** Lance l'animation vers ce numéro. onEnd est appelé à la fin (pas si annulée). */
    public void spinTo(int number, long durationMs, Runnable onEnd) {
        if (box.getWidth() == 0) {                       // pas encore mesurée : on réessaie juste après
            post(() -> spinTo(number, durationMs, onEnd));
            return;
        }
        cancel();
        txtResult.setVisibility(GONE);
        setVisibility(VISIBLE);
        table.setWinning(-1);
        wheel.setRotation(0f);
        orbit.setRotation(0f);
        ball.setTranslationY(0f);

        float a = RouletteWheelView.angleOfNumber(number);
        float R = box.getWidth() / 2f;
        float drop = R * 0.35f - (10f * dp + 7f * dp);   // marge haute + rayon de la balle

        // La roue fait 3 tours complets (≡ 0°) ; la balle tourne en sens inverse et finit à l'angle du numéro
        ObjectAnimator wheelSpin = ObjectAnimator.ofFloat(wheel, "rotation", 0f, 1080f);
        wheelSpin.setDuration(durationMs);
        wheelSpin.setInterpolator(new DecelerateInterpolator());

        ObjectAnimator ballSpin = ObjectAnimator.ofFloat(orbit, "rotation", 0f, a - 2160f);
        ballSpin.setDuration(durationMs);
        ballSpin.setInterpolator(new DecelerateInterpolator());

        long dropMs = Math.min(2500, durationMs / 2);
        ObjectAnimator ballDrop = ObjectAnimator.ofFloat(ball, "translationY", 0f, drop);
        ballDrop.setDuration(dropMs);
        ballDrop.setStartDelay(durationMs - dropMs);
        ballDrop.setInterpolator(new BounceInterpolator());

        set = new AnimatorSet();
        set.playTogether(wheelSpin, ballSpin, ballDrop);
        set.addListener(new AnimatorListenerAdapter() {
            boolean canceled = false;
            @Override public void onAnimationCancel(Animator animation) { canceled = true; }
            @Override public void onAnimationEnd(Animator animation) {
                if (canceled) return;
                table.setWinning(number);                    // entoure le numéro quand la balle s'arrête
                if (onEnd != null) onEnd.run();
            }
        });
        set.start();
    }

    public void showResult(String text) {
        txtResult.setText(text);
        txtResult.setVisibility(VISIBLE);
    }

    public void hide() {
        cancel();
        txtResult.setVisibility(GONE);
        setVisibility(INVISIBLE);
    }

    private void cancel() {
        if (set != null) {
            set.cancel();
            set = null;
        }
    }

    public MiniTableView getTable() { return table; }
}