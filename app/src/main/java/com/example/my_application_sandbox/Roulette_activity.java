package com.example.my_application_sandbox;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.text.InputFilter;
import android.text.InputType;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.content.Context;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.content.res.ColorStateList;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.appcompat.app.AlertDialog;

import com.example.my_application_sandbox.RouletteTouchHelper.Bet;
import com.example.my_application_sandbox.RouletteTouchHelper.Zone;
import com.example.my_application_sandbox.RouletteTouchHelper.Kind;
import com.google.android.material.button.MaterialButton;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

public class Roulette_activity extends AppCompatActivity {

    private final TextView[] cells = new TextView[37];   // list containing all cell
    private final Map<Zone, TextView> zoneViews = new EnumMap<>(Zone.class);

    private Bet current = null;                          // bet being held by finger
    private float edge_w_ratio = 0.30f;
    private Roulette_player player;

    private View jetonSelectionne = null;
    private int mise = 0;

    private TextView txtBank, txtChip;
    private Button txtTotalBet;
    private ChipOverlayView chipOverlay;

    private Button btnGomme;
    private boolean gommeActive = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        EdgeToEdge.enable(this, SystemBarStyle.dark(Color.TRANSPARENT));
        setContentView(R.layout.activity_roulette);

        // Barres système : le fond vert passe dessous, le salon reste dans la zone visible
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());

            // Haut : la barre foncée s'étend sous la barre d'état
            View topBar = findViewById(R.id.topBar);
            int h = (int) (56 * getResources().getDisplayMetrics().density);
            topBar.setPadding(topBar.getPaddingLeft(), bars.top, topBar.getPaddingRight(), 0);
            topBar.getLayoutParams().height = h + bars.top;
            topBar.requestLayout();

            // Côtés et bas : le vert passe sous la barre de navigation
            v.setPadding(bars.left, 0, bars.right, bars.bottom);
            return insets;
        });

        // Bouton accueil : retour au salon
        findViewById(R.id.btnHome).setOnClickListener(v -> {
            Intent intent = new Intent(this, Salon_activity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
        });

        initViews();                                                                                // Get all cells from grid
        GridLayout grid = findViewById(R.id.table_grid);
        grid.post(() -> setupTouch(grid));

        player = Roulette_player.getInstance();   // joueur créé dans MainActivity.enterRoulette()

        // Jetons
        int[] ids = { R.id.chip2, R.id.chip5, R.id.chip10,
                R.id.chip25, R.id.chip50, R.id.chip100 };
        int[] valeurs = { 2, 5, 10, 25, 50, 100 };

        for (int i = 0; i < ids.length; i++) {
            int valeur = valeurs[i];
            findViewById(ids[i]).setOnClickListener(v -> selectionnerJeton(v, valeur));
        }

        //listener changer val jeton
        findViewById(R.id.btn_edit_chip).setOnClickListener(v -> demanderValeurJeton());
        //listener gomme
        btnGomme.setOnClickListener(v -> toggleGomme());

        refreshUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // au retour de la roue, le solde a changé
        if (player != null) refreshUi();
    }

    private void initViews() {
        txtBank = findViewById(R.id.txt_bank);
        txtTotalBet = findViewById(R.id.button_total_bet);
        txtChip = findViewById(R.id.txt_chip);
        btnGomme = findViewById(R.id.btn_gomme);

        cells[0] = findViewById(R.id.leZ);
        for (int i = 1; i <= 36; i++) {
            int id = getResources().getIdentifier("n" + i, "id", getPackageName());
            cells[i] = findViewById(id);
        }

        zoneViews.put(Zone.MANQUE,     findViewById(R.id.bet_low));
        zoneViews.put(Zone.PAIR,       findViewById(R.id.bet_even));
        zoneViews.put(Zone.ROUGE,      findViewById(R.id.bet_red));
        zoneViews.put(Zone.NOIR,       findViewById(R.id.bet_black));
        zoneViews.put(Zone.IMPAIR,     findViewById(R.id.bet_odd));
        zoneViews.put(Zone.PASSE,      findViewById(R.id.bet_high));
        zoneViews.put(Zone.DOUZAINE_1, findViewById(R.id.douz1));
        zoneViews.put(Zone.DOUZAINE_2, findViewById(R.id.douz2));
        zoneViews.put(Zone.DOUZAINE_3, findViewById(R.id.douz3));
        zoneViews.put(Zone.COLONNE_1,  findViewById(R.id.col1));
        zoneViews.put(Zone.COLONNE_2,  findViewById(R.id.col2));
        zoneViews.put(Zone.COLONNE_3,  findViewById(R.id.col3));

        chipOverlay = findViewById(R.id.chip_overlay);
        chipOverlay.setAnchorProvider(new ChipOverlayView.AnchorProvider() {
            @Override
            public void anchorOf(Bet bet, float[] out) {
                computeAnchor(bet, out);
            }

            @Override
            public float cellSize() {
                return Math.min(cells[1].getWidth(), cells[1].getHeight());
            }
        });
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupTouch(GridLayout grid) {

        RouletteTouchHelper helper = new RouletteTouchHelper(0, 0, 1, 1, edge_w_ratio);

        grid.setOnTouchListener((v, event) -> {

            // Mode gomme : un appui (ou un glissé) sur une pile de jetons la supprime
            if (gommeActive) {
                int a = event.getActionMasked();
                if (a == MotionEvent.ACTION_DOWN || a == MotionEvent.ACTION_MOVE) {
                    Bet cible = chipOverlay.betAt(event.getX(), event.getY());
                    if (cible != null) {
                        player.eraseBet(cible);
                        v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                        refreshUi();
                    }
                } else if (a == MotionEvent.ACTION_UP) {
                    v.performClick();
                }
                return true;
            }

            // Recalcul de la taille des cellules en temps réel (immunisé contre les redimensionnements de l'UI dus aux insets)
            float cell_w = (cells[3].getLeft() - cells[1].getLeft()) / 2f;
            float cell_h = (cells[34].getTop() - cells[1].getTop()) / 11f;
            float x_origin = cells[1].getLeft() - 2f * cell_w;
            float y_origin = cells[1].getTop() - cell_h;

            helper.updateMetrics(x_origin, y_origin, cell_h, cell_w);

            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:                                                       //Finger pressed (unused but avoid warnings)
                case MotionEvent.ACTION_MOVE:                                                       //Finger moving
                    Bet bet = helper.resolve(event.getX(), event.getY());
                    if (!Objects.equals(bet, current)) {     // on ne réagit qu'aux changements
                        current = bet;
                        highlight(bet);
                        if (bet != null) v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                    }
                    return true;

                case MotionEvent.ACTION_UP:                                                         //Finger releasing
                    if (current != null) placeBet(current);  // le doigt se lève : on valide
                    resetSelection();
                    v.performClick();
                    for (TextView c : cells) c.setAlpha(1f);
                    for (TextView z : zoneViews.values()) z.setAlpha(1f);
                    return true;

                case MotionEvent.ACTION_CANCEL:                                                     //Action cancelled by android
                    resetSelection();                        // geste annulé : on ne mise pas
                    return true;
            }
            return false;
        });
    }

    private void highlight(Bet bet) {
        for (TextView c : cells) c.setAlpha(0.2f);
        for (TextView z : zoneViews.values()) z.setAlpha(0.2f);
        if (bet == null) return;

        for (int n : bet.numbers) cells[n].setAlpha(1f);
        if (bet.zone != null) zoneViews.get(bet.zone).setAlpha(1f);
    }

    private void resetSelection() {
        current = null;
        highlight(null);
    }

    /** Enregistre le pari tenu par le doigt avec le jeton sélectionné. */
    private void placeBet(Bet bet) {
        if (mise == 0) {
            toast("Sélectionne d'abord un jeton");
            return;
        }
        if (!player.placeBet(bet, mise)) {
            toast("Solde insuffisant");
            return;
        }
        refreshUi();
    }

    // ---- Boutons (android:onClick) ----
    public void undo_bet(View view) {
        player.removeBet();
        refreshUi();
    }

    public void clear_bets(View view) {
        player.removeAllBets();
        refreshUi();
    }

    private void selectionnerJeton(View jeton, int valeur) {
        // remet l'ancien jeton à sa taille normale
        if (jetonSelectionne != null) {
            jetonSelectionne.animate().scaleX(1f).scaleY(1f).setDuration(150).start();
        }

        // un 2e clic sur le même jeton le désélectionne
        if (jetonSelectionne == jeton) {
            jetonSelectionne = null;
            mise = 0;
            refreshUi();
            return;
        }

        // agrandit le jeton cliqué
        jeton.animate().scaleX(1.2f).scaleY(1.2f).setDuration(150).start();
        jetonSelectionne = jeton;
        mise = valeur;
        refreshUi();
    }

    /** Active ou désactive le mode gomme. */
    private void toggleGomme() {
        if (!gommeActive && player.getBets().isEmpty()) {
            toast("Aucun jeton sur la table");
            return;
        }
        setGomme(!gommeActive);
    }

    private void setGomme(boolean actif) {
        gommeActive = actif;
        styleGomme();
        refreshUi();
    }

    /** bouton gomme rouge au repos, passe au blanc pour le mettre en actif */
    private void styleGomme() {
        int fond  = gommeActive ? 0xFFFFFFFF : 0xFFE53935;
        int icone = gommeActive ? 0xFFE53935 : 0xFFFFFFFF;
        btnGomme.setBackgroundTintList(ColorStateList.valueOf(fond));
        if (btnGomme instanceof MaterialButton) {
            ((MaterialButton) btnGomme).setIconTint(ColorStateList.valueOf(icone));
        }
    }

    /** Fenêtre jeton personnalisé. */
    private void demanderValeurJeton() {
        if (player.getBank() <= 0) {
            toast("Solde insuffisant");
            return;
        }

        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setFilters(new InputFilter[]{ new InputFilter.LengthFilter(9) });   // limiter val max
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);   // la touche v du clavier valide
        input.setHint("De 1 à " + player.getBank());
        if (mise > 0) {
            input.setText(String.valueOf(mise));   // pré-rempli avec le jeton actuel
            input.selectAll();
        }

        // marges autour du champ
        FrameLayout box = new FrameLayout(this);
        int m = (int) (20 * getResources().getDisplayMetrics().density);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(m, m / 2, m, 0);
        box.addView(input, lp);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Valeur du jeton")
                .setMessage("Entre le montant à miser à chaque pari.")
                .setView(box)
                .setPositiveButton("Valider", null)   // redéfini plus bas pour garder la fenêtre ouverte en cas d'erreur
                .setNeutralButton("ALL IN", null)
                .setNegativeButton("Annuler", null)
                .create();

        // le clavier s'ouvre tout seul, le curseur est déjà dans le champ
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        dialog.setOnShowListener(d -> {
            input.requestFocus();
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
        });
        dialog.show();

        // la touche v du clavier valide
        input.setOnEditorActionListener((tv, actionId, ev) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                return true;
            }
            return false;
        });

        //bouton all in
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(b -> {
            appliquerJetonPerso(player.getBank());   // tout le solde restant devient la valeur du jeton
            dialog.dismiss();
        });

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b -> {
            int valeur;
            try {
                valeur = Integer.parseInt(input.getText().toString().trim());
            } catch (NumberFormatException e) {
                input.setError("Entre un nombre");
                return;
            }
            if (valeur < 1) {
                input.setError("Minimum 1");
                return;
            }
            if (valeur > player.getBank()) {
                input.setError("Maximum " + player.getBank());
                return;
            }
            appliquerJetonPerso(valeur);
            dialog.dismiss();
        });
    }

    /** Le jeton perso remplace le jeton sélectionné. */
    private void appliquerJetonPerso(int valeur) {
        if (jetonSelectionne != null) {
            jetonSelectionne.animate().scaleX(1f).scaleY(1f).setDuration(150).start();
            jetonSelectionne = null;   // plus aucun jeton de la colonne n'est actif
        }
        mise = valeur;
        refreshUi();                   // le texte affiche « Jeton : 75 »
    }

    private void refreshUi() {

        //desac la gomme si il n'y a plus rien a enlever
        if (gommeActive && player.getBets().isEmpty()) {
            gommeActive = false;
            styleGomme();
        }

        txtBank.setText("Solde : " + player.getBank());

        //boutton de play split en 2 pour ecrire le haut plus gros que le bas
        String texteBouton = "PLAY\nMise totale : " + player.getTotalStake();
        SpannableString spannable = new SpannableString(texteBouton);
        spannable.setSpan(new RelativeSizeSpan(0.7f), 5, texteBouton.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        txtTotalBet.setText(spannable);

        if (gommeActive) txtChip.setText("Gomme active");
        else txtChip.setText(mise > 0 ? "Jeton : " + mise : "Jeton : aucun");
        chipOverlay.setBets(player.getBets());chipOverlay.setBets(player.getBets());
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    public void start_roulette(View view) {
        if (player.getBets().isEmpty()) {
            toast("Place au moins un pari");
            return;
        }
        Intent intent = new Intent(this, Roulette_rotate_activity.class);
        startActivity(intent);
    }
    /** Point d'ancrage du jeton d'un pari, en coordonnées de la grille. */
    private void computeAnchor(Bet bet, float[] out) {
        // Chances simples, douzaines, colonnes : centre de la zone
        if (bet.zone != null) {
            View z = zoneViews.get(bet.zone);
            out[0] = (z.getLeft() + z.getRight()) / 2f;
            out[1] = (z.getTop() + z.getBottom()) / 2f;
            return;
        }

        boolean hasZero = bet.numbers.contains(0);
        boolean line = bet.kind == Kind.LIGNE || bet.kind == Kind.SIXAIN
                || (bet.kind == Kind.CARRE && hasZero);

        // Ligne, sixain, 0-1-2-3 : sur la frontière gauche des numéros, entre les lignes concernées
        if (line) {
            boolean[] rowUsed = new boolean[13];   // 0 = ligne du zéro, 1..12 = lignes de numéros
            for (int n : bet.numbers) rowUsed[n == 0 ? 0 : (n - 1) / 3 + 1] = true;
            float sumY = 0;
            int count = 0;
            for (int r = 0; r < 13; r++) {
                if (!rowUsed[r]) continue;
                TextView ref = (r == 0) ? cells[0] : cells[(r - 1) * 3 + 1];
                sumY += (ref.getTop() + ref.getBottom()) / 2f;
                count++;
            }
            out[0] = cells[1].getLeft();
            out[1] = sumY / count;
            return;
        }

        // Cheval ou trio avec le 0 : sur le bord haut de la première ligne
        if (hasZero && bet.numbers.size() > 1) {
            float sumX = 0;
            int count = 0;
            for (int n : bet.numbers) {
                if (n == 0) continue;
                sumX += (cells[n].getLeft() + cells[n].getRight()) / 2f;
                count++;
            }
            out[0] = sumX / count;
            out[1] = cells[1].getTop();
            return;
        }

        // Plein, cheval, carré : moyenne des centres des cases couvertes
        float sumX = 0, sumY = 0;
        int count = 0;
        for (int n : bet.numbers) {
            TextView c = cells[n];
            sumX += (c.getLeft() + c.getRight()) / 2f;
            sumY += (c.getTop() + c.getBottom()) / 2f;
            count++;
        }
        out[0] = sumX / count;
        out[1] = sumY / count;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isFinishing()) player.removeAllBets();
    }
}