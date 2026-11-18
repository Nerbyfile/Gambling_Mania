package com.example.my_application_sandbox;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.widget.FrameLayout;

import com.example.my_application_sandbox.RouletteTouchHelper.Bet;
import com.example.my_application_sandbox.RouletteTouchHelper.Kind;
import com.example.my_application_sandbox.RouletteTouchHelper.Zone;

import java.util.List;
import java.util.Map;

/** Table de roulette à l'horizontale (lecture seule), avec les jetons des paris posés dessus. */
public class MiniTableView extends FrameLayout implements ChipOverlayView.AnchorProvider {

    public static final int COLS = 14;   // 0 | 12 colonnes de numéros | « 2:1 »
    public static final int ROWS = 5;    // 3 lignes de numéros, douzaines, chances simples

    private static final int OUTSIDE = 0x33FFFFFF;
    private static final int RED = 0xFFD62828;
    private static final int BLACK = 0xFF111111;

    private static final Zone[] SIMPLES = {Zone.MANQUE, Zone.PAIR, Zone.ROUGE,
            Zone.NOIR, Zone.IMPAIR, Zone.PASSE};
    private static final String[] SIMPLE_LABELS = {"1-18", "PAIR", "ROUGE", "NOIR", "IMPAIR", "19-36"};
    private static final String[] DOUZAINE_LABELS = {"1-12", "13-24", "25-36"};

    private final ChipOverlayView chips;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gold = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();      // dessin
    private final RectF tmp = new RectF();    // calcul des ancrages
    private final float dp;
    private float cw, ch;                     // taille d'une case
    private int winning = -1;                 // numéro à entourer (-1 = aucun)

    public MiniTableView(Context c) { this(c, null); }

    public MiniTableView(Context c, AttributeSet a) {
        super(c, a);
        dp = c.getResources().getDisplayMetrics().density;
        setWillNotDraw(false);

        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(dp);
        line.setColor(0x88FFFFFF);

        gold.setStyle(Paint.Style.STROKE);
        gold.setStrokeWidth(3f * dp);
        gold.setColor(0xFFF3C300);

        text.setTextAlign(Paint.Align.CENTER);
        text.setTypeface(Typeface.DEFAULT_BOLD);
        text.setColor(0xFFFFFFFF);

        chips = new ChipOverlayView(c);
        chips.setAnchorProvider(this);
        addView(chips, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    // ---- API ----

    public void setBets(List<Bet> bets) { chips.setBets(bets); }
    public void setOtherBets(Map<String, List<Bet>> perPlayer) { chips.setOtherBets(perPlayer); }
    public void setColors(Map<String, Integer> byPlayer, int mine) { chips.setColors(byPlayer, mine); }

    /** Entoure le numéro gagnant (-1 pour effacer). */
    public void setWinning(int n) { winning = n; invalidate(); }

    // ---- Mesure : hauteur imposée par la largeur ----

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int w = MeasureSpec.getSize(widthSpec);
        int h = Math.round(w * ROWS / (float) COLS);
        super.onMeasure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        cw = w / (float) COLS;
        ch = h / (float) ROWS;
    }

    // ---- Géométrie ----

    private void cell(int col, int row, int colSpan, int rowSpan, RectF out) {
        out.set(col * cw, row * ch, (col + colSpan) * cw, (row + rowSpan) * ch);
    }

    /** 1 en bas à gauche, 3 en haut à gauche, 4 à droite du 1, etc. */
    private void numberCell(int n, RectF out) {
        cell((n - 1) / 3 + 1, 2 - (n - 1) % 3, 1, 1, out);
    }

    private void zoneCell(Zone z, RectF out) {
        switch (z) {
            case COLONNE_1:  cell(13, 2, 1, 1, out); return;
            case COLONNE_2:  cell(13, 1, 1, 1, out); return;
            case COLONNE_3:  cell(13, 0, 1, 1, out); return;
            case DOUZAINE_1: cell(1, 3, 4, 1, out);  return;
            case DOUZAINE_2: cell(5, 3, 4, 1, out);  return;
            case DOUZAINE_3: cell(9, 3, 4, 1, out);  return;
            default:
                for (int i = 0; i < SIMPLES.length; i++)
                    if (SIMPLES[i] == z) { cell(1 + 2 * i, 4, 2, 1, out); return; }
        }
    }

    // ---- Position des jetons (AnchorProvider) ----

    @Override
    public void anchorOf(Bet bet, float[] out) {
        if (bet.zone != null) {                       // douzaine, colonne, chance simple : centre de la case
            zoneCell(bet.zone, tmp);
            out[0] = tmp.centerX();
            out[1] = tmp.centerY();
            return;
        }
        float sx = 0, sy = 0;
        int k = 0;
        for (int n : bet.numbers) {
            if (n == 0) continue;
            numberCell(n, tmp);
            sx += tmp.centerX();
            sy += tmp.centerY();
            k++;
        }
        if (k == 0) {                                 // plein sur le 0
            out[0] = cw / 2f;
            out[1] = ROWS == 0 ? 0 : 1.5f * ch;
        } else if (bet.numbers.contains(0)) {         // cheval / trio / carré avec le 0 : à cheval sur la bordure du 0
            out[0] = cw;
            out[1] = sy / k;
        } else {                                      // plein, cheval, carré : moyenne des cases = bon endroit
            out[0] = sx / k;
            out[1] = (bet.kind == Kind.LIGNE || bet.kind == Kind.SIXAIN) ? 3f * ch : sy / k;
        }
    }

    @Override
    public float cellSize() { return Math.min(cw, ch); }

    // ---- Dessin ----

    @Override
    protected void onDraw(Canvas canvas) {
        cell(0, 0, 1, 3, r);
        box(canvas, r, RouletteWheelView.colorOf(0), "0");

        for (int n = 1; n <= 36; n++) {
            numberCell(n, r);
            box(canvas, r, RouletteWheelView.colorOf(n), String.valueOf(n));
        }
        for (int i = 0; i < 3; i++) {                 // colonnes « 2:1 »
            cell(13, i, 1, 1, r);
            box(canvas, r, OUTSIDE, "2:1");
        }
        for (int i = 0; i < 3; i++) {                 // douzaines
            cell(1 + 4 * i, 3, 4, 1, r);
            box(canvas, r, OUTSIDE, DOUZAINE_LABELS[i]);
        }
        for (int i = 0; i < SIMPLES.length; i++) {    // chances simples
            cell(1 + 2 * i, 4, 2, 1, r);
            int color = SIMPLES[i] == Zone.ROUGE ? RED : SIMPLES[i] == Zone.NOIR ? BLACK : OUTSIDE;
            box(canvas, r, color, SIMPLE_LABELS[i]);
        }

        if (winning >= 0) {                           // numéro gagnant entouré d'or
            if (winning == 0) cell(0, 0, 1, 3, r); else numberCell(winning, r);
            float in = gold.getStrokeWidth() / 2f;
            canvas.drawRect(r.left + in, r.top + in, r.right - in, r.bottom - in, gold);
        }
    }

    private void box(Canvas c, RectF rect, int color, String label) {
        fill.setColor(color);
        c.drawRect(rect, fill);
        c.drawRect(rect, line);

        text.setTextSize(ch * 0.42f);
        float w = text.measureText(label), max = rect.width() * 0.9f;
        if (w > max) text.setTextSize(text.getTextSize() * max / w);
        float y = rect.centerY() - (text.ascent() + text.descent()) / 2f;
        c.drawText(label, rect.centerX(), y, text);
    }
}