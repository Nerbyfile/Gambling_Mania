package com.example.my_application_sandbox;

import android.content.Intent;
import android.os.Bundle;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class Account_activity extends AppCompatActivity {

    private static final int START_BANK = 1000;

    private EditText editName;

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
        Button btnCreate = findViewById(R.id.btn_create);

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

        Player player = Player.create(name, START_BANK);
        PlayerStore.save(this, player);
        Player.setCurrent(player);

        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}