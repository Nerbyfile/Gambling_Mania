package com.example.my_application_sandbox;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
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

        EdgeToEdge.enable(this, SystemBarStyle.dark(Color.TRANSPARENT));
        setContentView(R.layout.activity_main);

        // Barres système : le fond vert passe dessous, la page reste dans la zone visible
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
    }

    /** Solo : le salon habituel. Multijoueur : la liste des salons. */
    public void playSolo(View v)  { startActivity(new Intent(this, Salon_activity.class)); }
    public void playMulti(View v) { startActivity(new Intent(this, SalonList_activity.class)); }

    public void comingSoon(View v) {
        Toast.makeText(this, "Bientôt disponible", Toast.LENGTH_SHORT).show();
    }
}