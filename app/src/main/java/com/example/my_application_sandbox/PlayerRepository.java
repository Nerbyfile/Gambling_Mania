package com.example.my_application_sandbox;

import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Locale;

/** Accès à la collection Firestore "players". */
public final class PlayerRepository {

    private static final String COLLECTION = "players";
    private static final int START_BANK = 1000;

    public interface AccountCallback {
        /** created = true si le compte vient d'être créé, false si un compte existant a été retrouvé. */
        void onResult(Player player, boolean created);
        void onError(Exception e);
    }

    private PlayerRepository() { }

    private static CollectionReference players() {
        return FirebaseFirestore.getInstance().collection(COLLECTION);
    }

    /** Récupère le joueur portant ce pseudo, ou le crée avec un id aléatoire unique. */
    public static void createOrGet(String name, AccountCallback cb) {
        String nameLower = name.toLowerCase(Locale.ROOT);

        players().whereEqualTo("nameLower", nameLower).limit(1).get()
                .addOnSuccessListener(snap -> {
                    if (!snap.isEmpty()) {
                        // Compte existant
                        DocumentSnapshot doc = snap.getDocuments().get(0);
                        Player p = doc.toObject(Player.class);
                        if (p == null) {
                            cb.onError(new IllegalStateException("Document joueur illisible"));
                            return;
                        }
                        p.setId(doc.getId());
                        cb.onResult(p, false);
                    } else {
                        // Nouveau compte : id aléatoire unique généré par Firestore
                        DocumentReference ref = players().document();
                        Player p = Player.create(name, START_BANK);
                        p.setId(ref.getId());
                        ref.set(p)
                                .addOnSuccessListener(v -> cb.onResult(p, true))
                                .addOnFailureListener(cb::onError);
                    }
                })
                .addOnFailureListener(cb::onError);
    }

    /** Récupère le profil distant du joueur s'il existe et met à jour le callback. */
    public static void reload(String id, AccountCallback cb) {
        if (id == null) {
            cb.onError(new IllegalArgumentException("ID null"));
            return;
        }
        players().document(id).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        Player p = doc.toObject(Player.class);
                        if (p != null) {
                            p.setId(doc.getId());
                            cb.onResult(p, false);
                        } else {
                            cb.onError(new IllegalStateException("Document joueur illisible"));
                        }
                    } else {
                        cb.onError(new IllegalStateException("Joueur non trouvé sur le serveur"));
                    }
                })
                .addOnFailureListener(cb::onError);
    }

    /** Met à jour le solde du joueur dans Firestore (à appeler quand aucun pari n'est en cours). */
    public static void updateBank(Player p) {
        if (p.getId() == null) return;
        players().document(p.getId()).update("bank", p.getBank());
    }
}