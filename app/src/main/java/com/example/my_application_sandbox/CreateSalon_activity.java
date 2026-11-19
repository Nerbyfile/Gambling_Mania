package com.example.my_application_sandbox;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.ListenerRegistration;

import java.util.List;

/** Création d'un salon : nom, public ou privé, et envoi d'invitations à des amis. */
public class CreateSalon_activity extends AppCompatActivity {

    private Player me;
    private EditText editName;
    private CheckBox checkPrivate;
    private Button btnCreate;
    private TextView txtInviteTitle, txtFriendsEmpty;
    private RecyclerView list;
    private InviteFriendsAdapter adapter;
    private ListenerRegistration regFriends;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this, SystemBarStyle.dark(Color.TRANSPARENT));
        setContentView(R.layout.activity_create_salon);

        // Barres système et clavier (le layout a déjà 16 dp de marge, on la remet par-dessus)
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            v.setPadding(pad + bars.left, pad + bars.top, pad + bars.right,
                    pad + Math.max(bars.bottom, ime.bottom));
            return insets;
        });

        me = Player.current();
        if (me.getId() == null) {
            toast("Crée d'abord un compte");
            finish();
            return;
        }

        editName = findViewById(R.id.edit_salon_name);
        checkPrivate = findViewById(R.id.check_private);
        btnCreate = findViewById(R.id.btn_create);
        txtInviteTitle = findViewById(R.id.txt_invite_title);
        txtFriendsEmpty = findViewById(R.id.txt_friends_empty);
        list = findViewById(R.id.list_invite_friends);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        // on ne peut pas inviter plus de joueurs que la place disponible (le créateur occupe déjà une place)
        adapter = new InviteFriendsAdapter(me.getId(), SalonRepository.MAX_MEMBERS - 1,
                new InviteFriendsAdapter.Listener() {
                    @Override public void onSelectionChanged(int count) {
                        txtInviteTitle.setText(count == 0
                                ? "Inviter des amis"
                                : "Inviter des amis (" + count + ")");
                    }

                    @Override public void onLimitReached(int max) {
                        toast("Maximum " + max + " invitations");
                    }
                });
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);

        btnCreate.setOnClickListener(v -> createSalon());
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (me == null || me.getId() == null) return;
        regFriends = FriendsRepository.listen(me.getId(), state -> {
            adapter.submit(state.friends);
            boolean empty = adapter.isEmpty();
            txtFriendsEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
            list.setVisibility(empty ? View.GONE : View.VISIBLE);
        });
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (regFriends != null) {
            regFriends.remove();
            regFriends = null;
        }
    }

    private void createSalon() {
        String name = editName.getText().toString().trim();
        if (name.isEmpty()) name = "Salon de " + me.getName();
        List<String> invited = adapter.selectedIds();

        btnCreate.setEnabled(false);
        SalonRepository.createSalon(me, name, checkPrivate.isChecked(),
                new SalonRepository.Callback<SalonRepository.Salon>() {
                    @Override public void onSuccess(SalonRepository.Salon salon) {
                        if (!invited.isEmpty()) {
                            AppNotification invite = AppNotification.create(
                                    AppNotification.SALON_INVITE, me, salon.getId());
                            invite.getData().put("salonName", salon.getName());
                            invite.getData().put("code", salon.getCode());
                            NotificationRepository.sendToMany(invited, invite);
                            toast(invited.size() + " invitation(s) envoyée(s)");
                        }

                        Intent i = new Intent(CreateSalon_activity.this, Salon_activity.class);
                        i.putExtra(Salon_activity.EXTRA_SALON_ID, salon.getId());
                        i.putExtra(Salon_activity.EXTRA_SALON_NAME, salon.getName());
                        i.putExtra(Salon_activity.EXTRA_SALON_CODE, salon.getCode());
                        startActivity(i);
                        finish();
                    }

                    @Override public void onError(Exception e) {
                        btnCreate.setEnabled(true);
                        toast(e.getMessage() != null ? e.getMessage() : "Connexion impossible, réessaie");
                    }
                });
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}