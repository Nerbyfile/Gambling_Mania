package com.example.my_application_sandbox;

import android.animation.ValueAnimator;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.view.Gravity;
import android.widget.FrameLayout;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.my_application_sandbox.SalonRepository.SalonMember;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.firebase.firestore.ListenerRegistration;

import java.text.NumberFormat;
import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;

/** Hall des jeux. Solo si lancé sans salon, salon multijoueur si EXTRA_SALON_ID est fourni. */
public class Salon_activity extends AppCompatActivity {

    public static final String EXTRA_SALON_ID = "EXTRA_SALON_ID";
    public static final String EXTRA_SALON_NAME = "EXTRA_SALON_NAME";
    public static final String EXTRA_SALON_CODE = "EXTRA_SALON_CODE";

    private TextView tvBanque;
    private final NumberFormat format = NumberFormat.getIntegerInstance();
    private int soldeAffiche = -1;

    // ---- Multijoueur ----
    private String salonId, salonName, salonCode;     // salonId == null : salon solo
    private TextView tvInfo;
    private LinearLayout membersRow;
    private ListenerRegistration regMembers;
    private FrameLayout rouletteSeats;
    private java.util.Map<String, Integer> memberColors = new java.util.HashMap<>();

    // Placement des joeurs autour de la table
    private static final float[][] ROULETTE_SEATS = {
            {-0.32f, 0.12f}, {-0.32f, 0.40f}, {-0.32f, 0.66f},   // à gauche
            {0.20f, -0.09f}, {0.62f, -0.09f},                    // au-dessus
            {1.10f, 0.28f}                                       // à droite
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Recup de la valeur de l'argent du joueur
        if (!Player.hasCurrent()) {
            Player saved = PlayerStore.load(this);
            if (saved == null) {
                startActivity(new Intent(this, Account_activity.class));
                finish();
                return;
            }
            Player.setCurrent(saved);   // affichage immédiat avec le cache local

            PlayerRepository.reload(saved.getId(), new PlayerRepository.AccountCallback() {
                @Override
                public void onResult(Player player, boolean created) {
                    // on met à jour l'objet existant au lieu de le remplacer
                    saved.setName(player.getName());
                    saved.setBank(player.getBank());
                    PlayerStore.save(Salon_activity.this, saved);
                    if (!isFinishing() && !isDestroyed()) refreshBank();
                }

                @Override
                public void onError(Exception e) {
                    // pas de réseau : on garde le cache local
                }
            });
        }

        EdgeToEdge.enable(this, SystemBarStyle.dark(Color.TRANSPARENT));
        setContentView(R.layout.activity_salon);

        // Barres système : le fond vert passe dessous, le salon reste dans la zone visible
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.salon_root), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());

            // Haut : la barre foncée s'étend sous la barre d'état
            View topBar = findViewById(R.id.topBar);
            int h = (int) (56 * getResources().getDisplayMetrics().density);
            topBar.setPadding(topBar.getPaddingLeft(), bars.top, topBar.getPaddingRight(), 0);
            topBar.getLayoutParams().height = h + bars.top;
            topBar.requestLayout();

            // Côtés et bas
            if (getIntent().getStringExtra(EXTRA_SALON_ID) != null) {
                // multijoueur : la barre des joueurs descend jusqu'en bas de l'écran
                v.setPadding(bars.left, 0, bars.right, 0);
                View membersBar = findViewById(R.id.members_bar);
                membersBar.setPadding(membersBar.getPaddingLeft(), membersBar.getPaddingTop(),
                        membersBar.getPaddingRight(), bars.bottom);
            } else {
                // solo : pas de barre, le vert passe sous la barre de navigation comme avant
                v.setPadding(bars.left, 0, bars.right, bars.bottom);
            }
            return insets;
        });

        // Mode : solo ou salon multijoueur
        salonId = getIntent().getStringExtra(EXTRA_SALON_ID);
        if (salonId != null) setupMultiplayer();

        // On recupère la zone pour ecrire l'argent en banque sur le xml
        tvBanque = findViewById(R.id.tvBanque);

        // Bouton accueil : multijoueur = on quitte le salon (voir onDestroy), solo = retour à MainActivity
        findViewById(R.id.btnHome).setOnClickListener(v -> {
            if (salonId != null) {
                finish();
                return;
            }
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
        });

        // Roulette : rejoint la table du salon en multijoueur, partie solo sinon
        Runnable roulette = () -> {
            Intent i = new Intent(this, Roulette_activity.class);
            if (salonId != null) i.putExtra(EXTRA_SALON_ID, salonId);
            startActivity(i);
        };

        bind(R.id.roulette, roulette);
        bind(R.id.label_roulette, roulette);

        // Poker (2 tables)
        Runnable poker = () -> bientot("Poker");
        bind(R.id.poker, poker);
        bind(R.id.label_poker, poker);

        // Blackjack
        Runnable blackjack = () -> bientot("Blackjack");
        bind(R.id.blackjack, blackjack);
        bind(R.id.label_blackjack, blackjack);

        // Machines à sous
        Runnable slots = () -> bientot("Machines à sous");
        bind(R.id.slot_machines, slots);
        bind(R.id.label_slots, slots);
    }

    // ===================== Multijoueur =====================

    /** Barre du bas : nom, code, et les joueurs présents. */
    private void setupMultiplayer() {
        salonName = getIntent().getStringExtra(EXTRA_SALON_NAME);
        salonCode = getIntent().getStringExtra(EXTRA_SALON_CODE);

        findViewById(R.id.members_bar).setVisibility(View.VISIBLE);
        tvInfo = findViewById(R.id.tv_salon_info);
        membersRow = findViewById(R.id.members_row);

        rouletteSeats = findViewById(R.id.roulette_seats);
        // replace les joueurs si la taille de la table change (rotation, premier affichage)
        rouletteSeats.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if ((r - l) != (or - ol) || (b - t) != (ob - ot)) v.post(this::placeSeats);
        });

        // chat du salon : démarré à l'arrivée, actif jusqu'au départ du salon
        ChatSession.start(salonId);
        ChatOverlayView.attach(this, findViewById(R.id.chat_slot), true);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (salonId != null) regMembers = SalonRepository.listenMembers(salonId, this::showMembers);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (regMembers != null) {
            regMembers.remove();
            regMembers = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (salonId != null) {
            if (isFinishing()) {
                SalonRepository.leaveSalon(salonId, Player.current().getId());
            }
            ChatSession.stop();
        }
    }

    private void showMembers(List<SalonMember> members) {
        String myId = Player.current().getId();
        memberColors = SalonRepository.assignColors(members);

        // toi en premier, puis par ordre alphabétique
        members.sort((a, b) -> {
            boolean ma = myId.equals(a.getId()), mb = myId.equals(b.getId());
            if (ma != mb) return ma ? -1 : 1;
            return a.getName().compareToIgnoreCase(b.getName());
        });

        // répartition : hall (barre du bas) / table de roulette (autour de la table)
        List<SalonMember> inHall = new ArrayList<>();
        List<SalonMember> atTable = new ArrayList<>();
        int atTableCount = 0;
        for (SalonMember m : members) {
            boolean roulette = SalonRepository.GAME_ROULETTE.equals(m.getGame());
            if (roulette) atTableCount++;
            if (roulette && atTable.size() < ROULETTE_SEATS.length) atTable.add(m);
            else inHall.add(m);
        }
        // places stables : le plus ancien assis garde sa place
        atTable.sort(Comparator.comparingLong(SalonMember::getSeatSince).thenComparing(SalonMember::getId));

        // barre du bas : les joueurs dans le hall
        membersRow.removeAllViews();
        for (SalonMember m : inHall) {
            View item = getLayoutInflater().inflate(R.layout.item_membresalon, membersRow, false);
            bindMember(item, m, myId);
            membersRow.addView(item);
        }

        // autour de la roulette : les joueurs assis
        rouletteSeats.removeAllViews();
        for (SalonMember m : atTable) {
            View seat = getLayoutInflater().inflate(R.layout.item_seat, rouletteSeats, false);
            bindMember(seat, m, myId);
            rouletteSeats.addView(seat);
        }
        placeSeats();                          // placement immédiat, avant le premier affichage
        rouletteSeats.post(this::placeSeats);  // et une 2e fois si la taille n'était pas encore connue

        tvInfo.setText(salonName + (salonCode != null ? "  ·  code " + salonCode : "")
                + "  ·  " + members.size() + " joueur(s)");

        // « Roulette (n) » : nombre de joueurs à la table
        View label = findViewById(R.id.label_roulette);
        if (label instanceof TextView) ((TextView) label).setText("Roulette (" + atTableCount + ")");
    }

    /** Avatar, contour (plus épais pour le joueur) et pseudo : commun à la barre et aux places. */
    private void bindMember(View item, SalonMember m, String myId) {
        float d = getResources().getDisplayMetrics().density;
        ShapeableImageView img = item.findViewById(R.id.img_member);
        img.setImageResource(Avatar.res(m.getAvatarId()));

        boolean me = myId.equals(m.getId());
        Integer c = memberColors.get(m.getId());
        img.setStrokeColor(ColorStateList.valueOf(c != null ? c : 0xFFC9A227));
        img.setStrokeWidth((me ? 3 : 2) * d);

        ((TextView) item.findViewById(R.id.txt_member_name)).setText(m.getName());
    }

    /** Positionne chaque joueur assis sur sa place autour de la table. */
    private void placeSeats() {
        int w = rouletteSeats.getWidth(), h = rouletteSeats.getHeight();
        if (w == 0 || h == 0) return;
        float d = getResources().getDisplayMetrics().density;

        for (int i = 0; i < rouletteSeats.getChildCount(); i++) {
            View seat = rouletteSeats.getChildAt(i);
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) seat.getLayoutParams();
            lp.gravity = Gravity.TOP | Gravity.START;
            lp.leftMargin = (int) (ROULETTE_SEATS[i][0] * w - 28 * d);   // 28 = moitié de la largeur (56dp)
            lp.topMargin = (int) (ROULETTE_SEATS[i][1] * h - 26 * d);    // 26 ≈ moitié de la hauteur
            seat.setLayoutParams(lp);
        }
    }

    // ===================== Commun =====================

    // refresh de la banque a chaque retour sur la page
    @Override
    protected void onResume() {
        super.onResume();
        refreshBank();
    }

    private void refreshBank() {
        if (tvBanque == null) return;
        int cible = Player.current().getBank();

        if (soldeAffiche < 0 || soldeAffiche == cible) {
            tvBanque.setText(format.format(cible));
        } else {
            ValueAnimator anim = ValueAnimator.ofInt(soldeAffiche, cible);
            anim.setDuration(600);
            anim.addUpdateListener(a -> tvBanque.setText(format.format((int) a.getAnimatedValue())));
            anim.start();
        }
        soldeAffiche = cible;
    }

    /** Petit effet d'appui, puis lance l'action. */
    private void bind(int id, Runnable action) {
        View v = findViewById(id);
        v.setOnClickListener(view -> view.animate()
                .scaleX(0.94f).scaleY(0.94f).setDuration(80)
                .withEndAction(() -> view.animate()
                        .scaleX(1f).scaleY(1f).setDuration(80)
                        .withEndAction(action).start())
                .start());
    }

    private void bientot(String jeu) {
        Toast.makeText(this, jeu + " : bientôt disponible", Toast.LENGTH_SHORT).show();
    }
}