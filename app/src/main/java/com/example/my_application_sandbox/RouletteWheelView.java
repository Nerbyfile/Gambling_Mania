package com.example.my_application_sandbox;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

public class RouletteWheelView extends View {

    // Ordre des numéros sur la roue européenne (le 0 est en haut à rotation = 0)
    public static final int[] ORDER = {
            0, 32, 15, 19, 4, 21, 2, 25, 17, 34, 6, 27, 13, 36, 11, 30, 8, 23, 10, 5,
            24, 16, 33, 1, 20, 14, 31, 9, 22, 18, 29, 7, 28, 12, 35, 3, 26
    };
    private static final int[] RED = {1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36};

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();

    public RouletteWheelView(Context c) { super(c); init(); }
    public RouletteWheelView(Context c, AttributeSet a) { super(c, a); init(); }
    public RouletteWheelView(Context c, AttributeSet a, int s) { super(c, a, s); init(); }

    private void init() {
        fill.setStyle(Paint.Style.FILL);
        text.setColor(0xFFFFFFFF);
        text.setTextAlign(Paint.Align.CENTER);
        text.setFakeBoldText(true);
    }

    private static boolean isRed(int n) {
        for (int r : RED) if (r == n) return true;
        return false;
    }

    /** Angle (en degrés, sens horaire depuis le haut) de la case d'un numéro quand la roue a rotation = 0. */
    public static float angleOfNumber(int number) {
        float step = 360f / ORDER.length;
        for (int i = 0; i < ORDER.length; i++) {
            if (ORDER[i] == number) return i * step;
        }
        return 0f;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float size = Math.min(getWidth(), getHeight());
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        float R = size / 2f;
        float step = 360f / ORDER.length;

        // Bois + piste de la balle
        fill.setColor(0xFF5A3A1E);
        canvas.drawCircle(cx, cy, R, fill);
        fill.setColor(0xFF2B1B0E);
        canvas.drawCircle(cx, cy, R * 0.93f, fill);

        // Cases (parts de camembert)
        float outer = R * 0.82f;
        oval.set(cx - outer, cy - outer, cx + outer, cy + outer);
        for (int i = 0; i < ORDER.length; i++) {
            int n = ORDER[i];
            fill.setColor(n == 0 ? 0xFF1FA32A : (isRed(n) ? 0xFFD62828 : 0xFF111111));
            float start = -90f + i * step - step / 2f;
            canvas.drawArc(oval, start, step, true, fill);
        }

        // Disque intérieur (cache la pointe des parts)
        fill.setColor(0xFF0F3A12);
        canvas.drawCircle(cx, cy, R * 0.58f, fill);
        fill.setColor(0xFFC9A227);
        canvas.drawCircle(cx, cy, R * 0.30f, fill);
        fill.setColor(0xFF0F3A12);
        canvas.drawCircle(cx, cy, R * 0.26f, fill);
        fill.setColor(0xFFC9A227);
        canvas.drawCircle(cx, cy, R * 0.08f, fill);

        // Numéros
        text.setTextSize(R * 0.085f);
        float baseline = cy - R * 0.75f - (text.ascent() + text.descent()) / 2f;
        for (int i = 0; i < ORDER.length; i++) {
            canvas.save();
            canvas.rotate(i * step, cx, cy);
            canvas.drawText(String.valueOf(ORDER[i]), cx, baseline, text);
            canvas.restore();
        }
    }
}
