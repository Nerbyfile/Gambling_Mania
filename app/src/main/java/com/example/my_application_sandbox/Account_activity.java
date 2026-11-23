package com.example.my_application_sandbox;

import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import android.graphics.Rect;
import android.view.View;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.material.imageview.ShapeableImageView;

public class Account_activity extends AppCompatActivity {

    private static final int MAX_ATTEMPTS = 5;
    private static final long LOCK_MS = 30_000;

    // static : le compteur survit à la recréation de l'activity
    private static int failedAttempts = 0;
    private static long lockedUntil = 0;

    private EditText editName;
    private EditText editPin;
    private Button btnCreate;

    private ShapeableImageView imgAvatar;
    private int avatarId = 0;   // avatar choisi

    private boolean imeVisible = false;   // clavier affiché ou non

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_account);
        ScrollView scroll = findViewById(R.id.main);

        ViewCompat.setOnApplyWindowInsetsListener(scroll, (v, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            int padding = (int) (32 * getResources().getDisplayMetrics().density);
            v.setPadding(bars.left + padding, bars.top + padding,
                    bars.right + padding, bars.bottom + padding);
            imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime());
            return insets;
        });

        // à chaque fin de mise en page avec le clavier ouvert, on recadre sur le champ actif
        scroll.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (imeVisible) scrollToFocus(scroll);
        });

        editName = findViewById(R.id.edit_name);
        editPin = findViewById(R.id.edit_pin);
        btnCreate = findViewById(R.id.btn_create);
        imgAvatar = findViewById(R.id.img_avatar);
        imgAvatar.setImageResource(Avatar.res(avatarId));

        View.OnFocusChangeListener refocus = (v, hasFocus) -> {
            if (hasFocus) scroll.postDelayed(() -> scrollToFocus(scroll), 200);
        };
        editName.setOnFocusChangeListener(refocus);
        editPin.setOnFocusChangeListener(refocus);

        //select de la pp
        findViewById(R.id.avatar_zone).setOnClickListener(zone ->
                AvatarPicker.show(this, avatarId, id -> {
                    avatarId = id;
                    imgAvatar.setImageResource(Avatar.res(id));
                }));

        btnCreate.setOnClickListener(v -> createAccount());
        editPin.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                createAccount();
                return true;
            }
            return false;
        });
    }

    /** Fait défiler pour que le champ actif soit visible au-dessus du clavier. */
    private void scrollToFocus(ScrollView scroll) {
        View focus = getCurrentFocus();
        if (focus == null) return;

        Rect r = new Rect();
        focus.getDrawingRect(r);
        scroll.offsetDescendantRectToMyCoords(focus, r);   // position dans la zone du ScrollView

        int margin = (int) (24 * getResources().getDisplayMetrics().density);
        int visibleTop = scroll.getPaddingTop();
        int visibleBottom = scroll.getHeight() - scroll.getPaddingBottom();   // au-dessus du clavier

        if (r.bottom + margin > visibleBottom) {
            scroll.smoothScrollBy(0, r.bottom + margin - visibleBottom);
        } else if (r.top - margin < visibleTop) {
            scroll.smoothScrollBy(0, r.top - margin - visibleTop);
        }
    }

    private void createAccount() {
        String name = editName.getText().toString().trim();
        String pin = editPin.getText().toString();

        if (name.length() < 2) {
            editName.setError("2 caractères minimum");
            return;
        }
        if (!name.matches("[\\p{L}0-9 _.-]+")) {
            editName.setError("Lettres, chiffres, espaces, - _ . uniquement");
            return;
        }
        if (!pin.matches("\\d{4}")) {
            editPin.setError("Le PIN doit faire 4 chiffres");
            return;
        }

        long now = SystemClock.elapsedRealtime();
        if (now < lockedUntil) {
            long secondes = (lockedUntil - now + 999) / 1000;
            Toast.makeText(this, "Trop d'essais, réessaie dans " + secondes + " s",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        btnCreate.setEnabled(false);   // évite les doubles envois pendant la requête

        PlayerRepository.createOrGet(name, pin, avatarId, new PlayerRepository.AccountCallback() {
            @Override
            public void onResult(Player player, boolean created) {
                failedAttempts = 0;
                PlayerStore.save(Account_activity.this, player);   // cache local
                Player.setCurrent(player);

                Toast.makeText(Account_activity.this,
                        created ? "Compte créé" : "Compte retrouvé, bon retour " + player.getName(),
                        Toast.LENGTH_SHORT).show();

                startActivity(new Intent(Account_activity.this, MainActivity.class));
                finish();
            }

            @Override
            public void onError(Exception e) {
                btnCreate.setEnabled(true);

                if (e instanceof PlayerRepository.WrongPinException) {
                    failedAttempts++;
                    if (failedAttempts >= MAX_ATTEMPTS) {
                        failedAttempts = 0;
                        lockedUntil = SystemClock.elapsedRealtime() + LOCK_MS;
                        Toast.makeText(Account_activity.this,
                                "Trop d'essais, réessaie dans 30 s", Toast.LENGTH_LONG).show();
                    } else {
                        editPin.setError("PIN incorrect");
                    }
                    editPin.setText("");
                } else {
                    Toast.makeText(Account_activity.this,
                            "Connexion impossible, réessaie", Toast.LENGTH_LONG).show();
                }
            }
        });
    }


}