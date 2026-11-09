package com.example.my_application_sandbox;

import android.animation.ValueAnimator;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.my_application_sandbox.SalonRepository.SalonMember;
import com.google.firebase.firestore.ListenerRegistration;

import java.text.NumberFormat;
import java.util.List;

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
    private ListenerRegistration regMembers;

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

            // Côtés et bas : le vert passe sous la barre de navigation
            v.setPadding(bars.left, 0, bars.right, bars.bottom);
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

    /** Bandeau en bas de l'écran : nom, code, joueurs présents. */
    private void setupMultiplayer() {
        salonName = getIntent().getStringExtra(EXTRA_SALON_NAME);
        salonCode = getIntent().getStringExtra(EXTRA_SALON_CODE);

        tvInfo = new TextView(this);
        tvInfo.setTextColor(Color.WHITE);
        tvInfo.setBackgroundColor(0xAA000000);
        int pad = (int) (12 * getResources().getDisplayMetrics().density);
        tvInfo.setPadding(pad, pad, pad, pad);
        addContentView(tvInfo, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));
        ViewCompat.setOnApplyWindowInsetsListener(tvInfo, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(pad, pad, pad, pad + bars.bottom);
            return insets;
        });
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
        if (isFinishing() && salonId != null) {
            SalonRepository.leaveSalon(salonId, Player.current().getId());
        }
    }

    private void showMembers(List<SalonMember> members) {
        int atTable = 0;
        StringBuilder names = new StringBuilder();
        for (SalonMember m : members) {
            if (SalonRepository.GAME_ROULETTE.equals(m.getGame())) atTable++;
            if (names.length() > 0) names.append(", ");
            names.append(m.getName());
        }
        tvInfo.setText(salonName + (salonCode != null ? "  ·  code " + salonCode : "") + "\n"
                + members.size() + " joueur(s) : " + names);

        // « Roulette (n) » : nombre de joueurs à la table
        View label = findViewById(R.id.label_roulette);
        if (label instanceof TextView) ((TextView) label).setText("Roulette (" + atTable + ")");
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