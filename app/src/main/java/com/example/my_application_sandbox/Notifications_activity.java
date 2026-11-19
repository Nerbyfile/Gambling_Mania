package com.example.my_application_sandbox;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.ListenerRegistration;

/** Page de notifications : demandes à traiter, puis informations (à balayer pour les supprimer). */
public class Notifications_activity extends AppCompatActivity {

    private Player me;
    private NotificationsAdapter adapter;
    private RecyclerView list;
    private TextView txtEmpty;
    private ListenerRegistration regNotifications;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this, SystemBarStyle.dark(Color.TRANSPARENT));
        setContentView(R.layout.activity_notifications);

        // Barres système : la barre du haut passe sous la barre d'état
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            View topBar = findViewById(R.id.topBar);
            int h = (int) (56 * getResources().getDisplayMetrics().density);
            topBar.setPadding(topBar.getPaddingLeft(), bars.top, topBar.getPaddingRight(), 0);
            topBar.getLayoutParams().height = h + bars.top;
            topBar.requestLayout();
            v.setPadding(bars.left, 0, bars.right, bars.bottom);
            return insets;
        });

        me = Player.current();
        if (me.getId() == null) {
            toast("Crée d'abord un compte");
            finish();
            return;
        }

        list = findViewById(R.id.list_notifications);
        txtEmpty = findViewById(R.id.txt_empty);
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        adapter = new NotificationsAdapter(new NotificationsAdapter.Listener() {
            @Override public void onAccept(AppNotification n) { handleAccept(n); }
            @Override public void onDecline(AppNotification n) { handleDecline(n); }
        });
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);
        attachSwipeToDismiss();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (me == null || me.getId() == null) return;
        regNotifications = NotificationRepository.listen(me.getId(), notifications -> {
            adapter.submit(notifications);
            boolean empty = adapter.isEmpty();
            txtEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
            list.setVisibility(empty ? View.GONE : View.VISIBLE);

            // la page est ouverte : les informations sont lues (les demandes restent à traiter)
            NotificationRepository.markRead(me.getId(), notifications);
            NotificationRepository.purgeOld(me.getId(), notifications);
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

    // ---- Actions ----

    private void handleAccept(AppNotification n) {
        if (AppNotification.FRIEND_REQUEST.equals(n.getType())) {
            FriendsRepository.accept(n.getRefId(), me, new FriendsRepository.Callback<Void>() {
                @Override public void onSuccess(Void result) {
                    toast("Tu es maintenant ami avec " + n.getFromName());
                }

                @Override public void onError(Exception e) {
                    String msg = e.getMessage();
                    toast(msg != null ? msg : "Connexion impossible, réessaie");
                    // demande annulée entre-temps : on retire la notification devenue sans objet
                    if (msg != null && msg.contains("introuvable")) {
                        NotificationRepository.delete(me.getId(), n.getId());
                    }
                }
            });
        } else if (AppNotification.SALON_INVITE.equals(n.getType())) {
            joinInvitedSalon(n);
        } else {
            toast("Bientôt disponible");
        }
    }

    private void handleDecline(AppNotification n) {
        if (AppNotification.FRIEND_REQUEST.equals(n.getType())) {
            // supprime la relation et les notifications de demande des deux côtés
            FriendsRepository.delete(n.getRefId(), null);
        } else {
            NotificationRepository.delete(me.getId(), n.getId());
        }
    }

    /** Balayer une information vers la gauche ou la droite la supprime. */
    private void attachSwipeToDismiss() {
        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0,
                ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {

            @Override
            public boolean onMove(RecyclerView rv, RecyclerView.ViewHolder vh, RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public int getSwipeDirs(RecyclerView rv, RecyclerView.ViewHolder vh) {
                // seules les informations se balaient (pas les titres ni les demandes à traiter)
                return adapter.swipeableAt(vh.getAdapterPosition()) != null ? super.getSwipeDirs(rv, vh) : 0;
            }

            @Override
            public void onSwiped(RecyclerView.ViewHolder vh, int direction) {
                AppNotification n = adapter.swipeableAt(vh.getAdapterPosition());
                if (n != null) NotificationRepository.delete(me.getId(), n.getId());
            }
        }).attachToRecyclerView(list);
    }

    /** Rejoint le salon de l'invitation (même s'il est privé : l'invitation en donne l'accès). */
    private void joinInvitedSalon(AppNotification n) {
        SalonRepository.joinById(n.getRefId(), me, new SalonRepository.Callback<SalonRepository.Salon>() {
            @Override public void onSuccess(SalonRepository.Salon salon) {
                NotificationRepository.delete(me.getId(), n.getId());
                Intent i = new Intent(Notifications_activity.this, Salon_activity.class);
                i.putExtra(Salon_activity.EXTRA_SALON_ID, salon.getId());
                i.putExtra(Salon_activity.EXTRA_SALON_NAME, salon.getName());
                i.putExtra(Salon_activity.EXTRA_SALON_CODE, salon.getCode());
                startActivity(i);
                finish();
            }

            @Override public void onError(Exception e) {
                String msg = e.getMessage();
                toast(msg != null ? msg : "Connexion impossible, réessaie");
                // le salon n'existe plus : l'invitation est périmée
                if (msg != null && msg.contains("introuvable")) {
                    NotificationRepository.delete(me.getId(), n.getId());
                }
            }
        });
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}