package com.example.my_application_sandbox;

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.ListenerRegistration;

/** Liste des amis, ajout par pseudo et demandes envoyées. */
public class Friends_activity extends AppCompatActivity {

    private Player me;
    private EditText editName;
    private Button btnAdd;
    private RecyclerView list;
    private TextView txtEmpty;
    private FriendsAdapter adapter;
    private ListenerRegistration regFriends;

    private final FriendsRepository.Callback<Void> deleteCallback = new FriendsRepository.Callback<Void>() {
        @Override public void onSuccess(Void result) { /* la liste se met à jour toute seule */ }
        @Override public void onError(Exception e) { toast("Action impossible, réessaie"); }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this, SystemBarStyle.dark(Color.TRANSPARENT));
        setContentView(R.layout.activity_friends);

        // Barres système : la barre du haut passe sous la barre d'état, le clavier remonte le contenu
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            View topBar = findViewById(R.id.topBar);
            int h = (int) (56 * getResources().getDisplayMetrics().density);
            topBar.setPadding(topBar.getPaddingLeft(), bars.top, topBar.getPaddingRight(), 0);
            topBar.getLayoutParams().height = h + bars.top;
            topBar.requestLayout();
            v.setPadding(bars.left, 0, bars.right, Math.max(bars.bottom, ime.bottom));
            return insets;
        });

        me = Player.current();
        if (me.getId() == null) {
            toast("Crée d'abord un compte");
            finish();
            return;
        }

        editName = findViewById(R.id.edit_friend_name);
        btnAdd = findViewById(R.id.btn_add_friend);
        list = findViewById(R.id.list_friends);
        txtEmpty = findViewById(R.id.txt_empty);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        adapter = new FriendsAdapter(me.getId(), new FriendsAdapter.Listener() {
            @Override public void onRemove(Friendship f) { confirmRemove(f); }
            @Override public void onCancel(Friendship f) { FriendsRepository.delete(f.getId(), deleteCallback); }
        });
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);

        btnAdd.setOnClickListener(v -> addFriend());
        editName.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                addFriend();
                return true;
            }
            return false;
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (me == null || me.getId() == null) return;
        regFriends = FriendsRepository.listen(me.getId(), state -> {
            adapter.submit(state);
            boolean empty = adapter.isEmpty();
            txtEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
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

    private void addFriend() {
        String pseudo = editName.getText().toString().trim();
        if (pseudo.isEmpty()) {
            editName.setError("Entre un pseudo");
            return;
        }

        btnAdd.setEnabled(false);
        FriendsRepository.sendRequest(me, pseudo, new FriendsRepository.Callback<FriendsRepository.RequestResult>() {
            @Override public void onSuccess(FriendsRepository.RequestResult result) {
                btnAdd.setEnabled(true);
                switch (result) {
                    case SENT:            toast("Demande envoyée à " + pseudo); break;
                    case AUTO_ACCEPTED:   toast("Vous êtes maintenant amis avec " + pseudo); break;
                    case ALREADY_FRIENDS: toast("Vous êtes déjà amis"); break;
                    case ALREADY_SENT:    toast("Demande déjà envoyée"); break;
                }
                editName.setText("");
            }

            @Override public void onError(Exception e) {
                btnAdd.setEnabled(true);
                toast(e.getMessage() != null ? e.getMessage() : "Connexion impossible, réessaie");
            }
        });
    }

    private void confirmRemove(Friendship f) {
        new AlertDialog.Builder(this)
                .setTitle("Retirer un ami")
                .setMessage("Retirer " + f.otherName(me.getId()) + " de tes amis ?")
                .setPositiveButton("Retirer", (d, w) -> FriendsRepository.delete(f.getId(), deleteCallback))
                .setNegativeButton("Annuler", null)
                .show();
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}