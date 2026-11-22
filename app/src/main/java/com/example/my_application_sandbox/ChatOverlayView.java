package com.example.my_application_sandbox;

import android.content.Context;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.activity.ComponentActivity;
import androidx.activity.OnBackPressedCallback;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.my_application_sandbox.ChatRepository.ChatMessage;

import java.util.List;

/**
 * Chat du salon : panneau de discussion en calque plein écran, ouvert depuis une entrée placée dans la barre
 * du haut de l'écran (aperçu du dernier message, ou simple icône avec le nombre de messages non lus).
 */
public class ChatOverlayView extends FrameLayout implements ChatSession.Listener {

    private final View panelRoot, panel, emptyText;
    private final EditText input;
    private final RecyclerView list;
    private final ChatAdapter adapter = new ChatAdapter();
    private final float density;

    private TextView entryText;        // texte de l'aperçu (null en mode compact)
    private TextView entryBadge;       // bulle des messages non lus
    private boolean open = false;
    private OnBackPressedCallback backCallback;

    public ChatOverlayView(Context c) {
        super(c);
        density = c.getResources().getDisplayMetrics().density;
        LayoutInflater.from(c).inflate(R.layout.view_chat_overlay, this, true);

        panelRoot = findViewById(R.id.chat_panel_root);
        panel = findViewById(R.id.chat_panel);
        emptyText = findViewById(R.id.chat_empty);
        input = findViewById(R.id.chat_input);
        list = findViewById(R.id.chat_list);

        LinearLayoutManager lm = new LinearLayoutManager(c);
        lm.setStackFromEnd(true);                       // les messages s'empilent depuis le bas
        list.setLayoutManager(lm);
        list.setAdapter(adapter);

        panelRoot.setOnClickListener(v -> close());     // tap sur le fond assombri
        findViewById(R.id.chat_close).setOnClickListener(v -> close());
        findViewById(R.id.chat_send).setOnClickListener(v -> send());
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                send();
                return true;
            }
            return false;
        });

        // Barres système et clavier : le panneau remonte au-dessus du clavier
        int pad = (int) (12 * density);
        ViewCompat.setOnApplyWindowInsetsListener(this, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());

            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) panel.getLayoutParams();
            lp.topMargin = bars.top + (int) (24 * density);
            panel.setLayoutParams(lp);
            panel.setPadding(pad, pad, pad, pad + Math.max(bars.bottom, ime.bottom));
            return insets;
        });
    }

    /**
     * Installe le chat sur l'écran d'une activity. À appeler après ChatSession.start et, de préférence,
     * après l'ajout des autres calques (pour rester au-dessus). Le bouton retour du système ferme le panneau.
     *
     * @param slot    conteneur de la barre du haut qui recevra l'entrée du chat
     * @param preview true = aperçu du dernier message ; false = simple icône avec la bulle de messages non lus
     */
    public static ChatOverlayView attach(ComponentActivity activity, ViewGroup slot, boolean preview) {
        ChatOverlayView overlay = new ChatOverlayView(activity);
        activity.addContentView(overlay, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        overlay.backCallback = new OnBackPressedCallback(false) {
            @Override public void handleOnBackPressed() {
                overlay.close();
            }
        };
        activity.getOnBackPressedDispatcher().addCallback(activity, overlay.backCallback);

        if (slot != null) overlay.installEntry(slot, preview);
        return overlay;
    }

    private void installEntry(ViewGroup slot, boolean preview) {
        slot.removeAllViews();
        View entry = LayoutInflater.from(getContext()).inflate(
                preview ? R.layout.view_chat_entry_preview : R.layout.view_chat_entry_compact, slot, false);
        slot.addView(entry);
        slot.setVisibility(View.VISIBLE);

        entryText = entry.findViewById(R.id.chat_entry_text);      // absent en mode compact
        entryBadge = entry.findViewById(R.id.chat_entry_badge);
        entry.setOnClickListener(v -> open());
        onChatChanged();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        ChatSession.addListener(this);
        onChatChanged();
        requestApplyInsets();
    }

    @Override
    protected void onDetachedFromWindow() {
        ChatSession.removeListener(this);
        super.onDetachedFromWindow();
    }

    // ---------- Affichage ----------

    @Override
    public void onChatChanged() {
        List<ChatMessage> messages = ChatSession.messages();
        adapter.submit(messages);
        emptyText.setVisibility(messages.isEmpty() ? View.VISIBLE : View.GONE);

        // aperçu (hall) : pseudo en couleur + début du message
        if (entryText != null) {
            ChatMessage last = ChatSession.last();
            if (last == null) {
                entryText.setText("Chat · touche pour écrire");
            } else {
                String name = last.getSenderName() != null ? last.getSenderName() : "Joueur";
                SpannableStringBuilder sb = new SpannableStringBuilder(name + " : " + last.getText());
                int color = ChatSession.colorOf(last.getSenderId(), last.getColor());
                sb.setSpan(new ForegroundColorSpan(color), 0, name.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                sb.setSpan(new StyleSpan(Typeface.BOLD), 0, name.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                entryText.setText(sb);
            }
        }

        // bulle des messages non lus (jamais pendant que le chat est ouvert)
        if (entryBadge != null) {
            int unread = open ? 0 : ChatSession.unreadCount();
            entryBadge.setVisibility(unread > 0 ? View.VISIBLE : View.GONE);
            entryBadge.setText(unread > 9 ? "9+" : String.valueOf(unread));
        }

        if (open) {
            if (!messages.isEmpty()) list.scrollToPosition(messages.size() - 1);
            ChatSession.markRead();
        }
    }

    // ---------- Panneau ----------

    private void open() {
        if (!ChatSession.isActive()) return;
        open = true;
        ChatSession.setPanelOpen(true);
        panelRoot.setVisibility(View.VISIBLE);
        if (backCallback != null) backCallback.setEnabled(true);
        ChatSession.markRead();
        onChatChanged();
    }

    private void close() {
        open = false;
        ChatSession.setPanelOpen(false);
        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
        panelRoot.setVisibility(View.GONE);
        if (backCallback != null) backCallback.setEnabled(false);
        onChatChanged();
    }

    private void send() {
        if (ChatSession.send(input.getText().toString())) input.setText("");
    }
}