package com.example.my_application_sandbox;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.widget.GridLayout;
import android.widget.TextView;

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
import java.util.Set;

public class Roulette_activity extends AppCompatActivity {


    private final TextView[] cells = new TextView[37];   // list containing all cell
    private final Map<Zone, TextView> zoneViews = new EnumMap<>(Zone.class);

    private Bet current = null;                          // bet being held by finger
    private float edge_w_ratio = 0.05f ;

    private View jetonSelectionne = null;
    private int mise = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);



        setContentView(R.layout.activity_roulette);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        initViews();                                                                                // Get all cells from grid
        GridLayout grid = findViewById(R.id.table_grid);
        grid.post(() -> setupTouch(grid));

        // Jetons
        int[] ids = { R.id.chip2, R.id.chip5, R.id.chip10,
                R.id.chip25, R.id.chip50, R.id.chip100 };
        int[] valeurs = { 2, 5, 10, 25, 50, 100 };

        for (int i = 0; i < ids.length; i++) {
            int valeur = valeurs[i];
            findViewById(ids[i]).setOnClickListener(v -> selectionnerJeton(v, valeur));
        }
    }

    private void initViews() {
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

        /*
        //debug
        for (int i = 0; i < cells.length; i++) {
            if (cells[i] == null) Log.e("ROULETTE", "cells[" + i + "] est null");
        }
        for (Zone z : Zone.values()) {
            if (zoneViews.get(z) == null) Log.e("ROULETTE", "zone " + z + " : vue introuvable");
        }

         */
    }
    @SuppressLint("ClickableViewAccessibility")
    private void setupTouch(GridLayout grid){

        float cell_w = cells[2].getLeft() - cells[1].getLeft();
        float cell_h = cells[4].getTop()  - cells[1].getTop();
        float x_origin = cells[1].getLeft() - 2 * cell_w;
        float y_origin = cells[0].getTop();


        RouletteTouchHelper helper = new RouletteTouchHelper(x_origin,y_origin,cell_w,cell_h,edge_w_ratio);
        grid.setOnTouchListener((v, event) -> {
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

    private void placeBet(Bet bet) {
        switch (bet.kind) {
            case PLEIN:          /* bet.numbers : 1 numéro                  */ break;
            case CHEVAL:         /* bet.numbers : 2 numéros                 */ break;
            case TRIO:           /* bet.numbers : 0-1-2 ou 0-2-3            */ break;
            case LIGNE:          /* bet.numbers : 3 numéros d'une ligne     */ break;
            case CARRE:          /* bet.numbers : 4 numéros (dont 0-1-2-3)  */ break;
            case SIXAIN:         /* bet.numbers : 6 numéros, 2 lignes       */ break;
            case CHANCE_SIMPLE:  /* bet.zone : PAIR, ROUGE, ...             */ break;
            case DOUZAINE:       /* bet.zone : DOUZAINE_1..3                */ break;
            case COLONNE:        /* bet.zone : COLONNE_1..3                 */ break;
        }
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
            return;
        }

        // agrandit le jeton cliqué
        jeton.animate().scaleX(1.2f).scaleY(1.2f).setDuration(150).start();
        jetonSelectionne = jeton;
        mise = valeur;
    }
}