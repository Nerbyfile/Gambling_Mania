package com.example.my_application_sandbox;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Document Firestore "friendships/{idA_idB}" : une relation entre deux joueurs.
 * L'id est celui des deux joueurs, triés puis joints par "_" : une seule relation par paire.
 * Les méthodes sans préfixe get/is (pending, accepted...) ne sont pas enregistrées dans Firestore.
 */
public class Friendship {

    public static final String PENDING = "pending";
    public static final String ACCEPTED = "accepted";

    private String id;
    private List<String> users = new ArrayList<>();          // les deux ids de joueurs
    private Map<String, String> names = new HashMap<>();     // id -> pseudo (évite de relire les profils)
    private String status = PENDING;
    private String requestedBy;                               // id de celui qui a envoyé la demande
    private long createdAt;
    private long acceptedAt;
    private boolean acceptedSeen;                             // celui qui a demandé a vu l'acceptation

    /** Constructeur sans argument : requis par Firestore (toObject). */
    public Friendship() { }

    /** Id du document pour deux joueurs : indépendant de l'ordre. */
    public static String pairId(String a, String b) {
        return a.compareTo(b) < 0 ? a + "_" + b : b + "_" + a;
    }

    /** Nouvelle demande d'ami de "from" vers "to". */
    public static Friendship newRequest(Player from, Player to) {
        Friendship f = new Friendship();
        f.id = pairId(from.getId(), to.getId());
        String a = from.getId().compareTo(to.getId()) < 0 ? from.getId() : to.getId();
        String b = a.equals(from.getId()) ? to.getId() : from.getId();
        f.users = new ArrayList<>(Arrays.asList(a, b));
        f.names.put(from.getId(), from.getName());
        f.names.put(to.getId(), to.getName());
        f.status = PENDING;
        f.requestedBy = from.getId();
        f.createdAt = System.currentTimeMillis();
        return f;
    }

    /** Première lettre en majuscule d'un pseudo (pour l'avatar). */
    public static String initialOf(String name) {
        return (name == null || name.isEmpty()) ? "?" : name.substring(0, 1).toUpperCase();
    }

    // ---- Aides de lecture (non enregistrées) ----

    public boolean pending() { return PENDING.equals(status); }

    public boolean accepted() { return ACCEPTED.equals(status); }

    /** Id de l'autre joueur de la relation. */
    public String otherId(String myId) {
        for (String u : users) if (!u.equals(myId)) return u;
        return null;
    }

    /** Pseudo de l'autre joueur de la relation. */
    public String otherName(String myId) {
        String other = otherId(myId);
        String n = (other == null) ? null : names.get(other);
        return n != null ? n : "Joueur";
    }

    // ---- Getters / setters (Firestore) ----

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public List<String> getUsers() { return users; }
    public void setUsers(List<String> users) { this.users = users; }

    public Map<String, String> getNames() { return names; }
    public void setNames(Map<String, String> names) { this.names = names; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getRequestedBy() { return requestedBy; }
    public void setRequestedBy(String requestedBy) { this.requestedBy = requestedBy; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public long getAcceptedAt() { return acceptedAt; }
    public void setAcceptedAt(long acceptedAt) { this.acceptedAt = acceptedAt; }

    public boolean isAcceptedSeen() { return acceptedSeen; }
    public void setAcceptedSeen(boolean acceptedSeen) { this.acceptedSeen = acceptedSeen; }
}
