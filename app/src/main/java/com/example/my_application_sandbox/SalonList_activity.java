package com.example.my_application_sandbox;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.appcompat.app.AlertDialog;

import com.example.my_application_sandbox.SalonRepository.Salon;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Liste des salons publics, création (public / privé) et accès par code. */
public class SalonList_activity extends AppCompatActivity {

    private Player me;
    private Button btnCreate;
    private EditText editSearch;
    private final List<Salon> allSalons = new ArrayList<>();
    private final List<Salon> shown = new ArrayList<>();
    private SalonAdapter adapter;

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

        EdgeToEdge.enable(this, SystemBarStyle.dark(Color.TRANSPARENT));
        setContentView(R.layout.activity_salon_list);

        // Barres système : le fond vert passe dessous, le salon reste dans la zone visible
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

        me = Player.current();
        if (me.getId() == null) {
            toast("Crée d'abord un compte");
            finish();
            return;
        }

        btnCreate = findViewById(R.id.btn_create_salon);
        editSearch = findViewById(R.id.edit_search);

        adapter = new SalonAdapter();
        ListView list = findViewById(R.id.list_salons);
        list.setAdapter(adapter);
        list.setEmptyView(findViewById(R.id.txt_empty));
        list.setOnItemClickListener((p, v, pos, id) -> {
            setBusy(true);
            SalonRepository.joinById(shown.get(pos).getId(), me, openCallback);
        });

        editSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) { applyFilter(); }
        });

        findViewById(R.id.btnHome).setOnClickListener(v -> finish());
        btnCreate.setOnClickListener(v ->
                startActivity(new Intent(this, CreateSalon_activity.class)));   // Multi 2
        findViewById(R.id.btn_join_code).setOnClickListener(v -> askCode());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (me != null && me.getId() != null) refresh();
    }

    private void refresh() {
        SalonRepository.listPublicSalons(new SalonRepository.Callback<List<Salon>>() {
            @Override public void onSuccess(List<Salon> result) {
                allSalons.clear();
                allSalons.addAll(result);
                applyFilter();
            }
            @Override public void onError(Exception e) { toast("Impossible de charger les salons"); }
        });
    }

    private void applyFilter() {
        String q = editSearch.getText().toString().trim().toLowerCase(Locale.ROOT);
        shown.clear();
        for (Salon s : allSalons) {
            String n = s.getName() != null ? s.getName().toLowerCase(Locale.ROOT) : "";
            if (q.isEmpty() || n.contains(q)) shown.add(s);
        }
        adapter.notifyDataSetChanged();
    }

    private void askCode() {
        EditText input = new EditText(this);
        input.setHint("CODE");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(5)});
        input.setSingleLine(true);
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(0x99FFFFFF);
        input.setBackgroundTintList(ColorStateList.valueOf(0xFFC9A227));

        FrameLayout box = new FrameLayout(this);
        int pad = (int) (24 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(input);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("Rejoindre par code")
                .setView(box)
                .setPositiveButton("Rejoindre", (d, w) -> {
                    String code = input.getText().toString().trim().toUpperCase(Locale.ROOT);
                    if (code.length() != 5) { toast("Le code fait 5 caractères"); return; }
                    setBusy(true);
                    SalonRepository.joinByCode(code, me, openCallback);
                })
                .setNegativeButton("Annuler", null)
                .create();

        // le clavier s'ouvre tout seul, le curseur est déjà dans le champ
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        dialog.setOnShowListener(d -> {
            input.requestFocus();
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
        });
        dialog.getWindow().setDimAmount(0.75f);
        dialog.show();

        // la touche v du clavier valide
        input.setOnEditorActionListener((tv, actionId, ev) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                return true;
            }
            return false;
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
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    /** Une ligne = une carte avec le nom du salon, l'hôte et la flèche. */
    private class SalonAdapter extends ArrayAdapter<Salon> {
        SalonAdapter() { super(SalonList_activity.this, 0, shown); }

        @Override
        public View getView(int pos, View cv, ViewGroup parent) {
            if (cv == null) cv = getLayoutInflater().inflate(R.layout.item_salon, parent, false);
            Salon s = getItem(pos);
            String host = s.getOwnerName() != null ? s.getOwnerName() : "inconnu";
            ((TextView) cv.findViewById(R.id.txt_salon_name)).setText(s.getName());
            ((TextView) cv.findViewById(R.id.txt_salon_host))
                    .setText("Hôte : " + host + "  ·  " + s.getMemberCount() + " joueur(s)");
            return cv;
        }
    }
}