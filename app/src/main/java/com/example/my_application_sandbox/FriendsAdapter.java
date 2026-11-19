package com.example.my_application_sandbox;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/** Liste de l'écran des amis : titres de section, amis et demandes envoyées. */
public class FriendsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface Listener {
        void onRemove(Friendship friendship);   // retirer un ami
        void onCancel(Friendship friendship);   // annuler une demande envoyée
    }

    private static final int TYPE_HEADER = 0, TYPE_FRIEND = 1, TYPE_OUTGOING = 2;

    private static final class Row {
        final int type;
        final String title;               // titre de section (TYPE_HEADER)
        final Friendship friendship;      // ami ou demande (autres types)

        Row(int type, String title, Friendship friendship) {
            this.type = type;
            this.title = title;
            this.friendship = friendship;
        }
    }

    private final String myId;
    private final Listener listener;
    private final List<Row> rows = new ArrayList<>();
    private final DateFormat dateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM);

    public FriendsAdapter(String myId, Listener listener) {
        this.myId = myId;
        this.listener = listener;
    }

    /** Remplace le contenu de la liste (les listes sont courtes : pas besoin de calcul de différences). */
    public void submit(FriendsState state) {
        rows.clear();
        if (!state.friends.isEmpty()) {
            rows.add(new Row(TYPE_HEADER, "Mes amis (" + state.friends.size() + ")", null));
            for (Friendship f : state.friends) rows.add(new Row(TYPE_FRIEND, null, f));
        }
        if (!state.outgoing.isEmpty()) {
            rows.add(new Row(TYPE_HEADER, "Demandes envoyées (" + state.outgoing.size() + ")", null));
            for (Friendship f : state.outgoing) rows.add(new Row(TYPE_OUTGOING, null, f));
        }
        notifyDataSetChanged();
    }

    public boolean isEmpty() {
        return rows.isEmpty();
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
        if (viewType == TYPE_HEADER) {
            return new HeaderHolder(inflater.inflate(R.layout.item_section_header, parent, false));
        }
        return new FriendHolder(inflater.inflate(R.layout.item_friend, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Row row = rows.get(position);

        if (row.type == TYPE_HEADER) {
            ((HeaderHolder) holder).txtHeader.setText(row.title);
            return;
        }

        FriendHolder h = (FriendHolder) holder;
        Friendship f = row.friendship;
        String name = f.otherName(myId);
        h.txtInitial.setText(Friendship.initialOf(name));
        h.txtName.setText(name);

        if (row.type == TYPE_FRIEND) {
            h.txtStatus.setText(f.getAcceptedAt() > 0
                    ? "Ami depuis le " + dateFormat.format(new Date(f.getAcceptedAt()))
                    : "Ami");
            h.btnAction.setText("Retirer");
            h.btnAction.setOnClickListener(v -> listener.onRemove(f));
        } else {
            h.txtStatus.setText("En attente de réponse");
            h.btnAction.setText("Annuler");
            h.btnAction.setOnClickListener(v -> listener.onCancel(f));
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

    static class FriendHolder extends RecyclerView.ViewHolder {
        final TextView txtInitial, txtName, txtStatus;
        final Button btnAction;

        FriendHolder(View v) {
            super(v);
            txtInitial = v.findViewById(R.id.txt_initial);
            txtName = v.findViewById(R.id.txt_name);
            txtStatus = v.findViewById(R.id.txt_status);
            btnAction = v.findViewById(R.id.btn_action);
        }
    }
}