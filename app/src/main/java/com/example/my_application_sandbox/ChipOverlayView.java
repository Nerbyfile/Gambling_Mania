package com.example.my_application_sandbox;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import com.example.my_application_sandbox.RouletteTouchHelper.Bet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;

/** Vue transparente qui dessine les jetons des paris par-dessus la table. Elle ne capte aucune touche. */
public class ChipOverlayView extends View {

    /** Fournit la position des paris (coordonnées de la grille) et la taille d'une case. */
    public interface AnchorProvider {
        void anchorOf(Bet bet, float[] out);   // out[0] = x, out[1] = y
        float cellSize();                      // plus petit côté d'une case, en px
    }

    private static final int MAX_LAYERS = 5;         // hauteur max d'une pile
    private static final float CHIP_SCALE = 0.70f;   // diamètre du jeton / taille d'une case
    private static final float LAYER_OFFSET = 0.10f; // décalage entre deux couches (en diamètres)

    // Valeurs décroissantes. 500 / 1000 / 5000 : affichage uniquement (le joueur ne les pose pas)
    private static final int[] DENOMS = {5000, 1000, 500, 100, 50, 25, 10, 5, 2};
    private static final int[] COLORS = {
            0xFFE75480,   // 5000 rose
            0xFFD4AF37,   // 1000 or
            0xFFF28C28,   // 500  orange
            0xFF5B2A86,   // 100  violet
            0xFF111111,   // 50   noir
            0xFF1FA32A,   // 25   vert
            0xFF3F6FD1,   // 10   bleu
            0xFFE5202A,   // 5    rouge
            0xFFBDBDBD    // 2    gris
    };

    private static final class Stack {
        final Bet bet;
        final String owner;   // null = mes jetons, sinon id du joueur
        final int[] layers;   // indices dans DENOMS, du plus gros (bas) au plus petit (haut)
        final String label;

        Stack(Bet bet, String owner) {
            this.bet = bet;
            this.owner = owner;
            int[] all = decompose(bet.Mise);
            this.layers = Arrays.copyOf(all, Math.min(all.length, MAX_LAYERS));
            this.label = format(bet.Mise);
        }
    }

    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Map<String, Integer> colors = new HashMap<>();   // id du joueur -> couleur
    private int myColor = 0;                                       // 0 = pas d'anneau (solo)

    private final List<Stack> stacks = new ArrayList<>();   // mes jetons
    private final List<Stack> others = new ArrayList<>();   // jetons des autres joueurs
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] anchor = new float[2];
    private AnchorProvider provider;
    private float density;
    private float chipAlpha = 1f;

    public ChipOverlayView(Context c) { super(c); init(); }
    public ChipOverlayView(Context c, AttributeSet a) { super(c, a); init(); }
    public ChipOverlayView(Context c, AttributeSet a, int s) { super(c, a, s); init(); }

    private void init() {
        density = getResources().getDisplayMetrics().density;
        fill.setStyle(Paint.Style.FILL);
        text.setTextAlign(Paint.Align.CENTER);
        text.setFakeBoldText(true);
        text.setColor(0xFF1A1A1A);
    }

    // ---- API ----

    public void setAnchorProvider(AnchorProvider provider) {
        this.provider = provider;
        invalidate();
    }

    /** Met à jour mes jetons à partir de mes paris. */
    public void setBets(List<Bet> bets) {
        stacks.clear();
        for (Bet b : bets) {
            if (b.Mise <= 0) continue;
            Stack s = new Stack(b, null);
            if (s.layers.length > 0) stacks.add(s);
        }
        invalidate();
    }

    /** Mises des autres joueurs (id -> mises), dessinées semi-transparentes. */
    public void setOtherBets(Map<String, List<Bet>> perPlayer) {
        others.clear();
        for (Map.Entry<String, List<Bet>> e : perPlayer.entrySet()) {
            for (Bet b : e.getValue()) {
                if (b.Mise <= 0) continue;
                Stack s = new Stack(b, e.getKey());
                if (s.layers.length > 0) others.add(s);
            }
        }
        invalidate();
    }

    /** Couleur d'anneau de chaque joueur (les autres, puis joueur) */
    public void setColors(Map<String, Integer> byPlayer, int mine) {
        colors.clear();
        colors.putAll(byPlayer);
        myColor = mine;
        invalidate();
    }

    /** Retourne mon pari dont la pile de jetons est sous ce point (coordonnées de la vue), ou null. */
    public Bet betAt(float x, float y) {
        if (provider == null || stacks.isEmpty()) return null;

        float d = Math.max(provider.cellSize() * CHIP_SCALE, 14f * density);
        float r = d / 2f;
        float off = d * LAYER_OFFSET;
        float slop = 6f * density;            // marge pour le doigt

        Bet found = null;
        float bestDist = Float.MAX_VALUE;
        for (Stack s : stacks) {
            provider.anchorOf(s.bet, anchor);
            int n = s.layers.length;
            float height = d + (n - 1) * off;
            float top = anchor[1] - height / 2f;
            top = Math.max(0f, Math.min(top, getHeight() - height));
            float cx = Math.max(r, Math.min(anchor[0], getWidth() - r));

            if (x < cx - r - slop || x > cx + r + slop) continue;
            if (y < top - slop || y > top + height + slop) continue;

            // si deux piles se chevauchent, on prend celle dont le centre est le plus proche
            float dx = x - cx, dy = y - (top + height / 2f);
            float dist = dx * dx + dy * dy;
            if (dist < bestDist) { bestDist = dist; found = s.bet; }
        }
        return found;
    }

    // ---- Décomposition d'un montant en un minimum de jetons (fusion par valeur) ----

    private static int[] decompose(int amount) {
        final int INF = Integer.MAX_VALUE;
        int[] best = new int[amount + 1];
        int[] pick = new int[amount + 1];   // indice du dernier jeton utilisé
        for (int a = 1; a <= amount; a++) {
            best[a] = INF;
            for (int i = DENOMS.length - 1; i >= 0; i--) {   // du plus petit au plus grand
                int d = DENOMS[i];
                if (d > a) break;
                if (best[a - d] != INF && best[a - d] + 1 < best[a]) {
                    best[a] = best[a - d] + 1;
                    pick[a] = i;
                }
            }
        }
        int a = amount;
        while (a > 0 && best[a] == INF) a--;   // montant irreprésentable (1, 3) : sécurité
        int[] res = new int[best[a]];
        for (int k = 0; a > 0; k++) {
            int i = pick[a];
            res[k] = i;
            a -= DENOMS[i];
        }
        Arrays.sort(res);   // indices croissants = valeurs décroissantes
        return res;
    }

    private static String format(int amount) {
        if (amount < 10000) return String.valueOf(amount);          // montant exact
        if (amount % 1000 == 0) return (amount / 1000) + "k";
        return (amount / 1000) + "," + ((amount % 1000) / 100) + "k";
    }

    // ---- Mesure : ne jamais agrandir le parent, on prend la taille imposée ----

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int w = MeasureSpec.getMode(widthSpec) == MeasureSpec.EXACTLY ? MeasureSpec.getSize(widthSpec) : 0;
        int h = MeasureSpec.getMode(heightSpec) == MeasureSpec.EXACTLY ? MeasureSpec.getSize(heightSpec) : 0;
        setMeasuredDimension(w, h);
    }

    // ---- Dessin ----

    @Override
    protected void onDraw(Canvas canvas) {
        if (provider == null || (stacks.isEmpty() && others.isEmpty())) return;

        float d = Math.max(provider.cellSize() * CHIP_SCALE, 14f * density);
        float r = d / 2f;
        float off = d * LAYER_OFFSET;

        // nombre de piles déjà posées à chaque endroit : mes piles comptent d'office
        Map<String, Integer> used = new HashMap<>();
        for (Stack s : stacks) used.put(keyOf(s.bet), 1);

        chipAlpha = 0.6f;                                   // les autres joueurs d'abord, en dessous
        drawStacks(canvas, others, d, r, off, used);
        chipAlpha = 1f;                                     // puis mes jetons par-dessus
        drawStacks(canvas, stacks, d, r, off, null);
    }

    /** Identifie un endroit de la table : même zone et mêmes numéros = même case. */
    private static String keyOf(Bet b) {
        return b.zone + ":" + new TreeSet<>(b.numbers);
    }

    private void drawStacks(Canvas canvas, List<Stack> list, float d, float r, float off,
                            Map<String, Integer> used) {
        for (Stack s : list) {
            provider.anchorOf(s.bet, anchor);

            int n = s.layers.length;
            float height = d + (n - 1) * off;
            // pile centrée sur le point d'ancrage, mais gardée dans la vue
            float top = anchor[1] - height / 2f;
            top = Math.max(0f, Math.min(top, getHeight() - height));

            // exactement sur la case ; léger décalage seulement si une pile est déjà au même endroit
            int rank = (used == null) ? 0 : used.merge(keyOf(s.bet), 1, Integer::sum) - 1;
            float cx = anchor[0] + rank * d * 0.30f;
            cx = Math.max(r, Math.min(cx, getWidth() - r));
            float baseCy = top + height - r;                // centre du jeton du bas

            int ringColor = (s.owner == null) ? myColor : colors.getOrDefault(s.owner, 0);

            for (int i = 0; i < n; i++) {
                float cy = baseCy - i * off;
                drawChip(canvas, cx, cy, r, COLORS[s.layers[i]], i == n - 1 ? s.label : null, ringColor);
            }
        }
    }

    /** Applique la transparence courante à une couleur ARGB. */
    private int a(int argb) {
        int alpha = (int) (((argb >>> 24) & 0xFF) * chipAlpha);
        return (argb & 0x00FFFFFF) | (alpha << 24);
    }

    private void drawChip(Canvas canvas, float cx, float cy, float r, int color, String label, int ringColor) {
        // ombre
        fill.setColor(a(0x55000000));
        canvas.drawCircle(cx + r * 0.08f, cy + r * 0.08f, r, fill);

        // anneau de la couleur du joueur, collé autour du jeton
        if (ringColor != 0) {
            float w = r * 0.22f;
            ring.setStyle(Paint.Style.STROKE);
            ring.setStrokeWidth(w);
            ring.setColor(a(ringColor));
            canvas.drawCircle(cx, cy, r + w / 2f - 0.5f, ring);
        }

        // corps
        fill.setColor(a(color));
        canvas.drawCircle(cx, cy, r, fill);

        // marques blanches sur la tranche
        fill.setColor(a(0xFFFFFFFF));
        for (int i = 0; i < 8; i++) {
            canvas.save();
            canvas.rotate(i * 45f, cx, cy);
            canvas.drawRect(cx - r * 0.11f, cy - r, cx + r * 0.11f, cy - r * 0.80f, fill);
            canvas.restore();
        }

        // disque intérieur clair
        fill.setColor(a(0xFFF2F2F2));
        canvas.drawCircle(cx, cy, r * 0.66f, fill);

        // montant (seulement sur le jeton du dessus)
        if (label != null) {
            text.setColor(a(0xFF1A1A1A));
            text.setTextSize(r * 0.9f);
            float w = text.measureText(label);
            float max = r * 1.15f;
            if (w > max) text.setTextSize(text.getTextSize() * max / w);
            float baseline = cy - (text.ascent() + text.descent()) / 2f;
            canvas.drawText(label, cx, baseline, text);
        }
    }

    /** Fin de tirage : ne garde que les piles dont le pari contient le numéro gagnant (les miennes et celles des autres). */
    public void keepWinning(int n) {
        stacks.removeIf(s -> !s.bet.numbers.contains(n));
        others.removeIf(s -> !s.bet.numbers.contains(n));
        invalidate();
    }
}