package com.example.my_application_sandbox;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.InputFilter;
import android.text.InputType;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.LinearLayout;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.my_application_sandbox.RouletteTouchHelper.Bet;
import com.example.my_application_sandbox.RouletteTouchHelper.Kind;
import com.example.my_application_sandbox.RouletteTouchHelper.Zone;
import com.example.my_application_sandbox.SalonRepository.TableState;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.example.my_application_sandbox.SalonRepository.SalonMember;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.imageview.ShapeableImageView;

import java.text.NumberFormat;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ArrayList;
import java.util.HashMap;

/** Table de roulette. Solo si lancée sans salon ; table d'un salon multijoueur si EXTRA_SALON_ID est fourni. */
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
    private boolean gommeGesture = false;

    // ---- Multijoueur (table d'un salon) ----
    private String salonId = null;                       // null = partie solo
    private RouletteTableSession session;
    private TableState table;
    private SpinOverlayView spin;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private String phaseKey = null;                      // "tour:phase" déjà traité
    private long phaseDeadline = 0;
    private int settledRound = -1;                       // dernier tour réglé
    private int betsRound = -1;                          // tour où j'ai posé mes jetons
    private boolean bettingOpen = true;

    private final NumberFormat format = NumberFormat.getIntegerInstance();

    private List<SalonMember> seats = new ArrayList<>();
    private LinearLayout playersList;      // non null seulement quand la popup est ouverte

    private Map<String, Integer> seatColors = new HashMap<>();   // id du joueur -> couleur

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

        // Bouton accueil : retour au salon (en multijoueur, on revient simplement au hall du salon)
        findViewById(R.id.btnHome).setOnClickListener(v -> {
            if (salonId != null) {
                finish();
                return;
            }
            Intent intent = new Intent(this, Salon_activity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
        });

        initViews();                                                                                // Get all cells from grid
        GridLayout grid = findViewById(R.id.table_grid);
        grid.post(() -> setupTouch(grid));

        player = Roulette_player.getInstance();   // joueur courant

        // Mode : solo ou table d'un salon
        salonId = getIntent().getStringExtra(Salon_activity.EXTRA_SALON_ID);
        if (salonId != null) setupMultiplayer();

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
    protected void onStart() {
        super.onStart();
        if (session != null) session.start();         // multijoueur : on s'assoit et on écoute la table
    }

    @Override
    protected void onResume() {
        super.onResume();
        // au retour de la roue, le solde a changé
        if (player != null) refreshUi();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (session != null) session.stop();
        handler.removeCallbacksAndMessages(null);
        phaseKey = null;                              // à la reconnexion, l'état est retraité
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

            int a = event.getActionMasked();
            if (a == MotionEvent.ACTION_DOWN) gommeGesture = gommeActive;   // on fige le mode

            // Mode gomme pour supprimer pile de jeton
            if (gommeGesture) {
                if (a == MotionEvent.ACTION_DOWN || a == MotionEvent.ACTION_MOVE) {
                    Bet cible = chipOverlay.betAt(event.getX(), event.getY());
                    if (cible != null && (salonId == null || bettingOpen)) {
                        player.eraseBet(cible);
                        v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                        betsChanged();
                    }
                } else if (a == MotionEvent.ACTION_UP) {
                    v.performClick();
                }
                if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
                    gommeGesture = false;
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
        if (!canEdit()) return;
        if (mise == 0) {
            toast("Sélectionne d'abord un jeton");
            return;
        }
        if (!player.placeBet(bet, mise)) {
            toast("Solde insuffisant");
            return;
        }
        if (table != null) betsRound = table.getRound();
        betsChanged();
    }

    // ---- Boutons (android:onClick) ----
    public void undo_bet(View view) {
        if (!canEdit()) return;
        player.removeBet();
        betsChanged();
    }

    public void clear_bets(View view) {
        if (!canEdit()) return;
        player.removeAllBets();
        betsChanged();
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
        input.setTextColor(0xFFFFFFFF);
        input.setHintTextColor(0x99FFFFFF);
        input.setBackgroundTintList(ColorStateList.valueOf(0xFFC9A227));
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

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
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
        dialog.getWindow().setDimAmount(0.75f);
        dialog.show();

        //modif bouton ok du clavier
        Button ok = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        ok.setBackgroundTintList(ColorStateList.valueOf(0xFFC9A227));
        ok.setTextColor(0xFF0A0A0A);

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

        txtBank.setText(format.format(Player.current().getBank()));

        // Titre du bouton PLAY : "PLAY" en solo, phase et compte à rebours en multijoueur
        String titre = "PLAY";
        if (salonId != null) {
            if (table == null) {
                titre = "CONNEXION…";
            } else {
                switch (table.getPhase()) {
                    case TableState.BETTING:
                        long left = Math.max(0, (phaseDeadline - SystemClock.elapsedRealtime() + 999) / 1000);
                        titre = left > 0 ? "MISEZ · " + left + " s" : "MISES FERMÉES";
                        break;
                    case TableState.SPINNING: titre = "LA ROUE TOURNE"; break;
                    case TableState.RESULT:   titre = "RÉSULTAT"; break;
                    default:                  titre = "▶ REPRENDRE"; break;
                }
            }
        }

        //boutton de play split en 2 pour ecrire le haut plus gros que le bas
        String texteBouton = titre + "\nMise totale : " + player.getTotalStake();
        SpannableString spannable = new SpannableString(texteBouton);
        spannable.setSpan(new RelativeSizeSpan(0.7f), titre.length() + 1, texteBouton.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        txtTotalBet.setText(spannable);

        if (gommeActive) txtChip.setText("Gomme active");
        else txtChip.setText(mise > 0 ? "Jeton : " + mise : "Jeton : aucun");
        chipOverlay.setBets(player.getBets());
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    /** Bouton PLAY : solo = lance la roue ; multijoueur = relance une table en pause. */
    public void start_roulette(View view) {
        if (salonId != null) {
            if (table != null && TableState.IDLE.equals(table.getPhase())) session.wake();
            return;
        }
        if (player.getBets().isEmpty()) {
            toast("Place au moins un pari");
            return;
        }
        Intent intent = new Intent(this, Roulette_rotate_activity.class);
        startActivity(intent);
    }

    // ===================== Multijoueur =====================

    private void setupMultiplayer() {
        bettingOpen = false;                             // fermé tant qu'on n'a pas l'état de la table
        spin = new SpinOverlayView(this);
        addContentView(spin, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        View btnPlayers = findViewById(R.id.btn_players);
        btnPlayers.setVisibility(View.VISIBLE);
        btnPlayers.setOnClickListener(v -> showPlayers());

        session = new RouletteTableSession(salonId, Player.current().getId(),
                new RouletteTableSession.Listener() {
                    @Override public void onTable(TableState t) { onTableChanged(t); }

                    @Override public void onOthersBets(Map<String, List<Bet>> perPlayer) {
                        chipOverlay.setOtherBets(perPlayer);
                    }

                    @Override public void onSeats(List<SalonMember> list) {
                        seats = list;
                        seatColors = SalonRepository.assignColors(list);

                        // mes jetons : ma couleur ; les autres : leurs couleurs
                        String myId = Player.current().getId();
                        Map<String, Integer> others = new HashMap<>(seatColors);
                        Integer mine = others.remove(myId);
                        chipOverlay.setColors(others, mine != null ? mine : 0);

                        ((TextView) findViewById(R.id.txt_players_count)).setText(String.valueOf(list.size()));
                        if (playersList != null) fillPlayers();   // popup ouverte : mise à jour
                    }

                    @Override public void onClosed() {
                        toast("Le salon a été fermé");
                        finish();
                    }
                });
    }

    private void showPlayers() {
        View view = getLayoutInflater().inflate(R.layout.dialog_players, null);
        playersList = view.findViewById(R.id.players_list);
        fillPlayers();

        AlertDialog dialog = new MaterialAlertDialogBuilder(this).setView(view).create();
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dialog.setOnDismissListener(d -> playersList = null);
        dialog.show();
        dialog.getWindow().setDimAmount(0.8f);
    }

    private void fillPlayers() {
        playersList.removeAllViews();
        String myId = Player.current().getId();
        for (SalonMember m : seats) {
            View row = getLayoutInflater().inflate(R.layout.item_player_row, playersList, false);

            ShapeableImageView img = row.findViewById(R.id.img_player);
            img.setImageResource(Avatar.res(m.getAvatarId()));
            Integer c = seatColors.get(m.getId());
            img.setStrokeColor(ColorStateList.valueOf(c != null ? c : 0xFFC9A227));

            String name = m.getName() + (m.getId().equals(myId) ? " (toi)" : "");
            ((TextView) row.findViewById(R.id.txt_player_name)).setText(name);
            playersList.addView(row);
        }
    }

    /** Réagit à l'état de la table (basé sur l'état : on peut arriver à n'importe quel moment). */
    private void onTableChanged(TableState t) {
        table = t;
        String key = t.getRound() + ":" + t.getPhase();
        boolean fresh = !key.equals(phaseKey);
        phaseKey = key;

        switch (t.getPhase()) {
            case TableState.BETTING:
                bettingOpen = true;
                spin.hide();
                if (fresh) {
                    // des mises d'un tour que j'ai manqué (appli en arrière-plan) : remboursées
                    if (!player.getBets().isEmpty() && betsRound != t.getRound()) {
                        player.removeAllBets();
                        toast("Mises du tour précédent remboursées");
                    }
                    phaseDeadline = SystemClock.elapsedRealtime() + RouletteTableSession.BET_MS;
                    handler.removeCallbacks(ticker);
                    handler.post(ticker);
                }
                break;

            case TableState.SPINNING:
                bettingOpen = false;
                if (fresh) {
                    spin.spinTo(t.getWinningNumber(), RouletteTableSession.SPIN_MS, () -> settleRound(t));
                }
                break;

            case TableState.RESULT:
                bettingOpen = false;
                settleRound(t);                          // sans effet si déjà réglé à la fin de la roue
                break;

            default:                                     // IDLE : table en pause
                bettingOpen = false;
                spin.hide();
                break;
        }
        refreshUi();
    }

    /** Règle mes mises du tour : gains, sauvegarde, et retire mes jetons chez les autres. */
    private void settleRound(TableState t) {
        if (settledRound == t.getRound()) return;
        settledRound = t.getRound();

        int winning = t.getWinningNumber();
        int stake = player.getTotalStake();
        int payout = player.settle(winning);
        saveBank();
        session.publishNow(player.getBets(), t.getRound());     // liste vide maintenant

        String txt = "Numéro " + winning;
        if (stake > 0) txt += payout > 0 ? "\nTu gagnes " + payout : "\nPerdu";
        spin.showResult(txt);
        refreshUi();
    }

    private void saveBank() {
        PlayerStore.save(this, player.getPlayer());
        PlayerRepository.updateBank(player.getPlayer());
    }

    /** Compte à rebours affiché sur le bouton PLAY ; ferme mes mises à 0 s. */
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (bettingOpen && SystemClock.elapsedRealtime() >= phaseDeadline) {
                bettingOpen = false;
                session.flushBets();                     // envoie tout de suite mes dernières mises
            }
            refreshUi();
            if (table != null && TableState.BETTING.equals(table.getPhase())) handler.postDelayed(this, 1000);
        }
    };

    private boolean canEdit() {
        if (salonId != null && !bettingOpen) {
            toast("Les mises sont fermées");
            return false;
        }
        return true;
    }

    /** À appeler après chaque changement de mises : met l'écran à jour et publie (envoi regroupé). */
    private void betsChanged() {
        refreshUi();
        if (session != null && table != null) session.publishBets(player.getBets(), table.getRound());
    }

    // ===================== Position des jetons =====================

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
        if (!isFinishing()) return;

        if (session == null) {                           // solo
            player.removeAllBets();
            return;
        }

        spin.hide();
        boolean roundRunning = table != null
                && (TableState.SPINNING.equals(table.getPhase()) || TableState.RESULT.equals(table.getPhase()));
        if (roundRunning && settledRound != table.getRound()) {
            player.settle(table.getWinningNumber());     // le numéro est déjà connu : mise réglée maintenant
            saveBank();
        } else {
            player.removeAllBets();                      // mises encore ouvertes ou table en pause : remboursées
        }
        session.leave();
    }
}