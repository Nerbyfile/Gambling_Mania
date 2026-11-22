package com.example.my_application_sandbox;

import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.my_application_sandbox.ChatRepository.ChatMessage;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/** Bulles de messages : pseudo dans la couleur du joueur ; mes messages à droite. */
public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.Holder> {

    private final List<ChatMessage> messages = new ArrayList<>();
    private final DateFormat timeFormat = DateFormat.getTimeInstance(DateFormat.SHORT);

    public void submit(List<ChatMessage> list) {
        messages.clear();
        messages.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_message, parent, false);
        return new Holder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        ChatMessage m = messages.get(position);
        String myId = Player.current().getId();
        boolean mine = myId != null && myId.equals(m.getSenderId());

        String name = m.getSenderName() != null ? m.getSenderName() : "Joueur";
        h.name.setText(mine ? name + " (toi)" : name);
        h.name.setTextColor(ChatSession.colorOf(m.getSenderId(), m.getColor()));
        h.text.setText(m.getText());
        h.time.setText(timeFormat.format(new Date(m.timeMillis())));

        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) h.bubble.getLayoutParams();
        lp.gravity = mine ? Gravity.END : Gravity.START;
        h.bubble.setLayoutParams(lp);
        h.bubble.setBackgroundResource(mine ? R.drawable.bg_chat_me : R.drawable.bg_card);
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final View bubble;
        final TextView name, text, time;

        Holder(View v) {
            super(v);
            bubble = v.findViewById(R.id.chat_bubble);
            name = v.findViewById(R.id.chat_name);
            text = v.findViewById(R.id.chat_text);
            time = v.findViewById(R.id.chat_time);
        }
    }
}