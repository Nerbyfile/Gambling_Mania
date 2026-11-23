package com.example.my_application_sandbox;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.NumberFormat;
import java.util.Locale;

import com.google.firebase.firestore.ListenerRegistration;

public class MainActivity extends AppCompatActivity {

    private TextView txtBadge;
    private ListenerRegistration regNotifications;

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
        txtBadge = findViewById(R.id.txt_badge_notif);

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

    public void showProfile(View v) {
        Player p = Player.current();
        View view = getLayoutInflater().inflate(R.layout.profile_popup, null);

        // avatar
        ImageView imgAvatar = view.findViewById(R.id.img_profile_avatar);
        imgAvatar.setImageResource(Avatar.res(p.getAvatarId()));

        view.findViewById(R.id.avatar_edit_zone).setOnClickListener(zone ->
                AvatarPicker.show(this, p.getAvatarId(), id -> {
                    p.setAvatarId(id);                              // joueur en mémoire
                    imgAvatar.setImageResource(Avatar.res(id)); // mise à jour immédiate dans la popup
                    PlayerStore.save(this, p);                      // cache local
                    PlayerRepository.updateAvatar(p);               // Firestore
                }));

        // Nom#id : le nom en crème, le #id plus petit et en gris-vert
        String id = p.getId() != null ? p.getId() : "";
        String tag = id.isEmpty() ? "" : "#" + id.substring(0, Math.min(4, id.length())).toUpperCase(Locale.ROOT);
        SpannableString name = new SpannableString(p.getName() + tag);
        int start = p.getName().length();
        name.setSpan(new ForegroundColorSpan(0xFF9FBF9F), start, name.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        name.setSpan(new RelativeSizeSpan(0.7f), start, name.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ((TextView) view.findViewById(R.id.txt_profile_name)).setText(name);

        // banque (même format que la roulette : 1 200)
        ((TextView) view.findViewById(R.id.txt_profile_bank))
                .setText(NumberFormat.getIntegerInstance().format(p.getBank()));

        AlertDialog dialog = new MaterialAlertDialogBuilder(this).setView(view).create();
        view.findViewById(R.id.btn_logout).setOnClickListener(b -> {
            dialog.dismiss();
            confirmLogout();
        });
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent); // la carte a son propre fond
        dialog.show();
        dialog.getWindow().setDimAmount(0.8f);
    }

    private void confirmLogout() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Se déconnecter ?")
                .setMessage("Tu pourras te reconnecter avec ton pseudo et ton code PIN.")
                .setPositiveButton("Déconnexion", (d, w) -> logout())
                .setNegativeButton("Annuler", null)
                .show();
    }

    private void logout() {
        PlayerRepository.updateBank(Player.current());   // sauvegarde le solde avant de partir
        PlayerStore.clear(this);                         // efface le compte du cache local
        Player.setCurrent(null);                         // oublie le joueur en mémoire

        // retour à l'écran de compte, en vidant l'historique (le retour ne revient pas dans l'app)
        Intent i = new Intent(this, Account_activity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
    }

    @Override
    protected void onStart() {
        super.onStart();
        // badge de notifications : nombre de notifications non lues
        if (txtBadge == null || !Player.hasCurrent() || Player.current().getId() == null) return;
        regNotifications = NotificationRepository.listen(Player.current().getId(), list -> {
            int unread = NotificationRepository.unreadCount(list);
            txtBadge.setVisibility(unread > 0 ? View.VISIBLE : View.GONE);
            txtBadge.setText(unread > 9 ? "9+" : String.valueOf(unread));
        });
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (regNotifications != null) {
            regNotifications.remove();
            regNotifications = null;
        }
    }

    public void openFriends(View view) {
        startActivity(new Intent(this, Friends_activity.class));
    }

    public void openNotifications(View view) {
        startActivity(new Intent(this, Notifications_activity.class));
    }

}