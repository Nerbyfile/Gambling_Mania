package com.example.my_application_sandbox;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import android.animation.ValueAnimator;
import android.widget.TextView;
import java.text.NumberFormat;

import androidx.activity.SystemBarStyle;
import android.graphics.Color;

public class Salon_activity extends AppCompatActivity {

    private TextView tvBanque;
    private final NumberFormat format = NumberFormat.getIntegerInstance();
    private int soldeAffiche = -1;

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
                    // on met à jour l'objet existant au lieu de le remplacer (voir plus bas)
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

        // On recupère la zone pour ecrire l'argent en banque sur le xml
        tvBanque = findViewById(R.id.tvBanque);

        // Bouton accueil : retour à MainActivity
        findViewById(R.id.btnHome).setOnClickListener(v -> {
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
        });

        // Roulette
        Runnable roulette = () -> startActivity(new Intent(this, Roulette_activity.class));

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
