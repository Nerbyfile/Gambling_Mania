package com.example.my_application_sandbox;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Photo à un instant de mes relations, déjà triées. Alimente l'écran des amis.
 * Les demandes reçues, elles, passent par les notifications.
 */
public class FriendsState {

    public final List<Friendship> friends = new ArrayList<>();     // relations acceptées
    public final List<Friendship> outgoing = new ArrayList<>();    // demandes envoyées en attente

    private FriendsState() { }

    public static FriendsState from(String myId, List<Friendship> all) {
        FriendsState s = new FriendsState();
        for (Friendship f : all) {
            if (f.accepted()) {
                s.friends.add(f);
            } else if (f.pending() && myId.equals(f.getRequestedBy())) {
                s.outgoing.add(f);
            }
        }
        Collections.sort(s.friends, (a, b) -> a.otherName(myId).compareToIgnoreCase(b.otherName(myId)));
        Collections.sort(s.outgoing, (a, b) -> Long.compare(b.getCreatedAt(), a.getCreatedAt()));
        return s;
    }
}