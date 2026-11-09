package com.example.my_application_sandbox;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Récupération du joueur dans le cache et dans firestore
        if (!Player.hasCurrent()) {

            // Chargement du cache
            Player saved = PlayerStore.load(this);
            if (saved == null) {                       // première ouverture : pas de compte
                startActivity(new Intent(this, Account_activity.class));
                finish();
                return;
            }
            Player.setCurrent(saved);                  // On défini le compte local pour ne pas bloquer l'UI

            // Rechargement asynchrone depuis la base de données
            PlayerRepository.reload(saved.getId(), new PlayerRepository.AccountCallback() {
                @Override
                public void onResult(Player player, boolean created) {
                    Player.setCurrent(player);                          // Màj du joueur en mémoire
                    PlayerStore.save(MainActivity.this, player);    // Màj du cache local
                }

                @Override
                public void onError(Exception e) {
                    // Échec du réseau ou document supprimé : on continue avec la sauvegarde locale
                }
            });
        }

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    /** Solo : le salon habituel. Multijoueur : la liste des salons. */
    public void enterSalon(View view) {
        new AlertDialog.Builder(this)
                .setTitle("Jouer")
                .setItems(new CharSequence[]{"Jouer seul", "Salons multijoueur"}, (d, which) -> {
                    if (which == 0) {
                        startActivity(new Intent(this, Salon_activity.class));
                    } else {
                        startActivity(new Intent(this, SalonList_activity.class));
                    }
                })
                .show();
    }
}