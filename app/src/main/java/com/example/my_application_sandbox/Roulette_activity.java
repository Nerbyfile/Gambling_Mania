package com.example.my_application_sandbox;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.widget.GridLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.my_application_sandbox.RouletteTouchHelper.Bet;
import com.example.my_application_sandbox.RouletteTouchHelper.Zone;

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

    private TextView txtBank, txtTotalBet, txtChip;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        setContentView(R.layout.activity_roulette);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            int padding = (int) (10 * getResources().getDisplayMetrics().density);
            v.setPadding(systemBars.left + padding, systemBars.top + padding, systemBars.right + padding, systemBars.bottom + padding);
            return insets;
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

        refreshUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // au retour de la roue, le solde a changé
        if (player != null) refreshUi();
    }

    private void initViews() {
        txtBank     = findViewById(R.id.txt_bank);
        txtTotalBet = findViewById(R.id.txt_total_bet);
        txtChip     = findViewById(R.id.txt_chip);

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
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupTouch(GridLayout grid) {

        RouletteTouchHelper helper = new RouletteTouchHelper(0, 0, 1, 1, edge_w_ratio);
        grid.setOnTouchListener((v, event) -> {

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

    private void refreshUi() {
        txtBank.setText("Solde : " + player.getBank());
        txtTotalBet.setText("Mise totale : " + player.getTotalStake());
        txtChip.setText(mise > 0 ? "Jeton : " + mise : "Jeton : aucun");
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
}