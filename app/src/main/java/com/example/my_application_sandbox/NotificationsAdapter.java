package com.example.my_application_sandbox;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Liste de la page de notifications : « À traiter » (notifications qui attendent une réponse)
 * puis « Récentes » (informations). Les notifications arrivées non lues gardent un point doré
 * tant que la page est ouverte, même si elles sont marquées lues entre-temps.
 */
public class NotificationsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface Listener {
        void onAccept(AppNotification n);    // Accepter / Rejoindre
        void onDecline(AppNotification n);   // Refuser / Ignorer
    }

    private static final int TYPE_HEADER = 0, TYPE_REQUEST = 1, TYPE_INFO = 2;

    private static final class Row {
        final int type;
        final String title;
        final AppNotification notification;

        Row(int type, String title, AppNotification notification) {
            this.type = type;
            this.title = title;
            this.notification = notification;
        }
    }

    private final Listener listener;
    private final List<Row> rows = new ArrayList<>();
    private final Set<String> knownIds = new HashSet<>();
    private final Set<String> newIds = new HashSet<>();     // arrivées non lues, affichées avec un point

    public NotificationsAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submit(List<AppNotification> list) {
        List<AppNotification> toDo = new ArrayList<>();
        List<AppNotification> recent = new ArrayList<>();
        for (AppNotification n : list) {
            if (knownIds.add(n.getId()) && !n.isRead()) newIds.add(n.getId());
            if (n.actionable()) toDo.add(n);
            else recent.add(n);
        }

        rows.clear();
        if (!toDo.isEmpty()) {
            rows.add(new Row(TYPE_HEADER, "À traiter", null));
            for (AppNotification n : toDo) rows.add(new Row(TYPE_REQUEST, null, n));
        }
        if (!recent.isEmpty()) {
            rows.add(new Row(TYPE_HEADER, "Récentes", null));
            for (AppNotification n : recent) rows.add(new Row(TYPE_INFO, null, n));
        }
        notifyDataSetChanged();
    }

    public boolean isEmpty() {
        return rows.isEmpty();
    }

    /** La notification d'information à cette position (la seule qu'on peut balayer pour supprimer), sinon null. */
    public AppNotification swipeableAt(int position) {
        if (position < 0 || position >= rows.size()) return null;
        Row r = rows.get(position);
        return r.type == TYPE_INFO ? r.notification : null;
    }

    @Override
    public int getItemViewType(int position) {
        return rows.get(position).type;
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        switch (viewType) {
            case TYPE_HEADER:
                return new HeaderHolder(inflater.inflate(R.layout.item_section_header, parent, false));
            case TYPE_REQUEST:
                return new RequestHolder(inflater.inflate(R.layout.item_notification_request, parent, false));
            default:
                return new InfoHolder(inflater.inflate(R.layout.item_notification_info, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Row row = rows.get(position);

        if (row.type == TYPE_HEADER) {
            ((HeaderHolder) holder).txtHeader.setText(row.title);
            return;
        }

        AppNotification n = row.notification;

        if (row.type == TYPE_REQUEST) {
            RequestHolder h = (RequestHolder) holder;
            h.txtInitial.setText(n.initial());
            h.txtMessage.setText(n.message());
            h.txtTime.setText(n.timeText());

            boolean invite = AppNotification.SALON_INVITE.equals(n.getType());
            h.btnAccept.setText(invite ? "Rejoindre" : "Accepter");
            h.btnDecline.setText(invite ? "Ignorer" : "Refuser");
            h.btnAccept.setOnClickListener(v -> listener.onAccept(n));
            h.btnDecline.setOnClickListener(v -> listener.onDecline(n));
        } else {
            InfoHolder h = (InfoHolder) holder;
            h.txtInitial.setText(n.initial());
            h.txtMessage.setText(n.message());
            h.txtTime.setText(n.timeText());
            h.viewUnread.setVisibility(newIds.contains(n.getId()) ? View.VISIBLE : View.GONE);
        }
    }

    // ---- ViewHolders ----

    static class HeaderHolder extends RecyclerView.ViewHolder {
        final TextView txtHeader;

        HeaderHolder(View v) {
            super(v);
            txtHeader = v.findViewById(R.id.txt_header);
        }
    }

    static class RequestHolder extends RecyclerView.ViewHolder {
        final TextView txtInitial, txtMessage, txtTime;
        final Button btnAccept, btnDecline;

        RequestHolder(View v) {
            super(v);
            txtInitial = v.findViewById(R.id.txt_initial);
            txtMessage = v.findViewById(R.id.txt_message);
            txtTime = v.findViewById(R.id.txt_time);
            btnAccept = v.findViewById(R.id.btn_accept);
            btnDecline = v.findViewById(R.id.btn_decline);
        }
    }

    static class InfoHolder extends RecyclerView.ViewHolder {
        final TextView txtInitial, txtMessage, txtTime;
        final View viewUnread;

        InfoHolder(View v) {
            super(v);
            txtInitial = v.findViewById(R.id.txt_initial);
            txtMessage = v.findViewById(R.id.txt_message);
            txtTime = v.findViewById(R.id.txt_time);
            viewUnread = v.findViewById(R.id.view_unread);
        }
    }
}