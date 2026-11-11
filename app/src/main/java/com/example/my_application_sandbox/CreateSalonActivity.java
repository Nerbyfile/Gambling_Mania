package com.example.my_application_sandbox;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.my_application_sandbox.SalonRepository.Salon;

public class CreateSalonActivity extends AppCompatActivity {

    private Player me;
    private EditText editName;
    private CheckBox checkPrivate;
    private Button btnCreate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_create_salon);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            int pad = (int) (16 * getResources().getDisplayMetrics().density);
            v.setPadding(bars.left + pad, bars.top + pad, bars.right + pad, bars.bottom + pad);
            return insets;
        });

        me = Player.current();
        if (me.getId() == null) {
            Toast.makeText(this, "Crée d'abord un compte", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        editName = findViewById(R.id.edit_salon_name);
        checkPrivate = findViewById(R.id.check_private);
        btnCreate = findViewById(R.id.btn_create);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        btnCreate.setOnClickListener(v -> {
            String name = editName.getText().toString().trim();
            if (name.isEmpty()) name = "Salon de " + me.getName();
            btnCreate.setEnabled(false);
            SalonRepository.createSalon(me, name, checkPrivate.isChecked(),
                    new SalonRepository.Callback<Salon>() {
                        @Override public void onSuccess(Salon s) {
                            Intent i = new Intent(CreateSalonActivity.this, Salon_activity.class);
                            i.putExtra(Salon_activity.EXTRA_SALON_ID, s.getId());
                            i.putExtra(Salon_activity.EXTRA_SALON_NAME, s.getName());
                            i.putExtra(Salon_activity.EXTRA_SALON_CODE, s.getCode());
                            startActivity(i);
                            finish();   // le retour depuis le salon revient à la liste
                        }
                        @Override public void onError(Exception e) {
                            btnCreate.setEnabled(true);
                            Toast.makeText(CreateSalonActivity.this,
                                    e.getMessage() != null ? e.getMessage() : "Création impossible",
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
        });
    }
}