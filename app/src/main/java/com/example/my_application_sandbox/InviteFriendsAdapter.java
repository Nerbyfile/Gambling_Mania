package com.example.my_application_sandbox;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Liste d'amis à cocher pour les inviter dans le salon en cours de création. */
public class InviteFriendsAdapter extends RecyclerView.Adapter<InviteFriendsAdapter.Holder> {

    public interface Listener {
        void onSelectionChanged(int count);
        void onLimitReached(int max);
    }

    private final String myId;
    private final int maxSelection;
    private final Listener listener;
    private final List<Friendship> friends = new ArrayList<>();
    private final Set<String> selected = new LinkedHashSet<>();     // ids des joueurs cochés

    public InviteFriendsAdapter(String myId, int maxSelection, Listener listener) {
        this.myId = myId;
        this.maxSelection = maxSelection;
        this.listener = listener;
    }

    /** Met à jour la liste des amis en gardant la sélection (sauf pour les amis retirés entre-temps). */
    public void submit(List<Friendship> list) {
        friends.clear();
        friends.addAll(list);

        Set<String> ids = new HashSet<>();
        for (Friendship f : list) ids.add(f.otherId(myId));
        selected.retainAll(ids);

        notifyDataSetChanged();
        listener.onSelectionChanged(selected.size());
    }

    public boolean isEmpty() {
        return friends.isEmpty();
    }

    /** Ids des joueurs à inviter. */
    public List<String> selectedIds() {
        return new ArrayList<>(selected);
    }

    private void toggle(String playerId, int position) {
        if (selected.contains(playerId)) {
            selected.remove(playerId);
        } else if (selected.size() >= maxSelection) {
            listener.onLimitReached(maxSelection);
            return;
        } else {
            selected.add(playerId);
        }
        if (position != RecyclerView.NO_POSITION) notifyItemChanged(position);
        listener.onSelectionChanged(selected.size());
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_invite_friend, parent, false);
        return new Holder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        Friendship f = friends.get(position);
        String playerId = f.otherId(myId);
        String name = f.otherName(myId);

        h.txtInitial.setText(Friendship.initialOf(name));
        h.txtName.setText(name);
        h.check.setChecked(selected.contains(playerId));
        h.itemView.setOnClickListener(v -> toggle(playerId, h.getAdapterPosition()));
    }

    @Override
    public int getItemCount() {
        return friends.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final TextView txtInitial, txtName;
        final CheckBox check;

        Holder(View v) {
            super(v);
            txtInitial = v.findViewById(R.id.txt_initial);
            txtName = v.findViewById(R.id.txt_name);
            check = v.findViewById(R.id.check_invite);
        }
    }
}