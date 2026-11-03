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

public class Salon_activity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_salon);

        // Barres système : le fond vert passe dessous, le salon reste dans la zone visible
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.salon_root), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
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
