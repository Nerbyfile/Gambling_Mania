package com.example.my_application_sandbox;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.my_application_sandbox.SalonRepository.Salon;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Liste des salons publics, création (public / privé) et accès par code. */
public class SalonList_activity extends AppCompatActivity {

    private Player me;
    private EditText editName, editCode;
    private CheckBox checkPrivate;
    private Button btnCreate, btnJoin;
    private final List<Salon> salons = new ArrayList<>();
    private final List<String> labels = new ArrayList<>();
    private ArrayAdapter<String> adapter;

    private final SalonRepository.Callback<Salon> openCallback = new SalonRepository.Callback<Salon>() {
        @Override public void onSuccess(Salon s) {
            setBusy(false);
            open(s);
        }
        @Override public void onError(Exception e) {
            setBusy(false);
            toast(e.getMessage() != null ? e.getMessage() : "Connexion impossible");
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_salon_list);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            int pad = (int) (16 * getResources().getDisplayMetrics().density);
            v.setPadding(bars.left + pad, bars.top + pad, bars.right + pad, bars.bottom + pad);
            return insets;
        });

        me = Player.current();
        if (me.getId() == null) {
            toast("Crée d'abord un compte");
            finish();
            return;
        }

        editName = findViewById(R.id.edit_salon_name);
        editCode = findViewById(R.id.edit_code);
        checkPrivate = findViewById(R.id.check_private);
        btnCreate = findViewById(R.id.btn_create_salon);
        btnJoin = findViewById(R.id.btn_join_code);

        adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, labels) {
            @Override public View getView(int position, View convertView, ViewGroup parent) {
                View v = super.getView(position, convertView, parent);
                ((TextView) v).setTextColor(Color.WHITE);
                return v;
            }
        };
        ListView list = findViewById(R.id.list_salons);
        list.setAdapter(adapter);
        list.setEmptyView(findViewById(R.id.txt_empty));
        list.setOnItemClickListener((p, v, pos, id) -> {
            setBusy(true);
            SalonRepository.joinById(salons.get(pos).getId(), me, openCallback);
        });

        btnCreate.setOnClickListener(v -> {
            String name = editName.getText().toString().trim();
            if (name.isEmpty()) name = "Salon de " + me.getName();
            setBusy(true);
            SalonRepository.createSalon(me, name, checkPrivate.isChecked(), openCallback);
        });

        btnJoin.setOnClickListener(v -> {
            String code = editCode.getText().toString().trim().toUpperCase(Locale.ROOT);
            if (code.length() != 5) {
                editCode.setError("Le code fait 5 caractères");
                return;
            }
            setBusy(true);
            SalonRepository.joinByCode(code, me, openCallback);
        });

        findViewById(R.id.btn_refresh).setOnClickListener(v -> refresh());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (me != null && me.getId() != null) refresh();
    }

    private void refresh() {
        SalonRepository.listPublicSalons(new SalonRepository.Callback<List<Salon>>() {
            @Override public void onSuccess(List<Salon> result) {
                salons.clear();
                salons.addAll(result);
                labels.clear();
                for (Salon s : result) labels.add(s.getName() + "   ·   " + s.getMemberCount() + " joueur(s)");
                adapter.notifyDataSetChanged();
            }
            @Override public void onError(Exception e) {
                toast("Impossible de charger les salons");
            }
        });
    }

    private void open(Salon s) {
        Intent i = new Intent(this, Salon_activity.class);
        i.putExtra(Salon_activity.EXTRA_SALON_ID, s.getId());
        i.putExtra(Salon_activity.EXTRA_SALON_NAME, s.getName());
        i.putExtra(Salon_activity.EXTRA_SALON_CODE, s.getCode());
        startActivity(i);
    }

    private void setBusy(boolean busy) {
        btnCreate.setEnabled(!busy);
        btnJoin.setEnabled(!busy);
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}