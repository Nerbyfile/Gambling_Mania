package com.example.my_application_sandbox;

import android.content.Intent;
import android.os.Bundle;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class Account_activity extends AppCompatActivity {

    private EditText editName;
    private Button btnCreate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_account);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            int padding = (int) (32 * getResources().getDisplayMetrics().density);
            v.setPadding(systemBars.left + padding, systemBars.top + padding,
                    systemBars.right + padding, systemBars.bottom + padding);
            return insets;
        });

        editName = findViewById(R.id.edit_name);
        btnCreate = findViewById(R.id.btn_create);

        btnCreate.setOnClickListener(v -> createAccount());
        editName.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                createAccount();
                return true;
            }
            return false;
        });
    }

    private void createAccount() {
        String name = editName.getText().toString().trim();

        if (name.length() < 2) {
            editName.setError("2 caractères minimum");
            return;
        }
        if (!name.matches("[\\p{L}0-9 _.-]+")) {
            editName.setError("Lettres, chiffres, espaces, - _ . uniquement");
            return;
        }

        btnCreate.setEnabled(false);   // évite les doubles envois pendant la requête

        PlayerRepository.createOrGet(name, new PlayerRepository.AccountCallback() {
            @Override
            public void onResult(Player player, boolean created) {
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
                Toast.makeText(Account_activity.this,
                        "Connexion impossible, réessaie", Toast.LENGTH_LONG).show();
            }
        });
    }
}