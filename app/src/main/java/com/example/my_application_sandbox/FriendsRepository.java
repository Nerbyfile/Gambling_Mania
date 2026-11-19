package com.example.my_application_sandbox;

import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Accès Firestore à "friendships" (amis et demandes envoyées).
 * Chaque changement écrit aussi les notifications concernées, dans la même transaction.
 */
public final class FriendsRepository {

    public static final int MAX_FRIENDS = 100;
    public static final int MAX_PENDING_SENT = 20;

    public interface Callback<T> { void onSuccess(T result); void onError(Exception e); }
    public interface StateListener { void onState(FriendsState state); }

    /** Résultat d'une demande d'ami. */
    public enum RequestResult {
        SENT,             // demande envoyée
        AUTO_ACCEPTED,    // l'autre m'avait déjà écrit : amitié créée directement
        ALREADY_FRIENDS,
        ALREADY_SENT
    }

    private FriendsRepository() { }

    private static FirebaseFirestore db() {
        return FirebaseFirestore.getInstance();
    }

    private static CollectionReference col() {
        return db().collection("friendships");
    }

    // ---------- Écouteur temps réel ----------

    /** Écoute mes relations (amis et demandes envoyées). */
    public static ListenerRegistration listen(String myId, StateListener l) {
        return col().whereArrayContains("users", myId).addSnapshotListener((snap, e) -> {
            if (e != null || snap == null) return;
            List<Friendship> all = new ArrayList<>();
            for (DocumentSnapshot d : snap.getDocuments()) {
                Friendship f = d.toObject(Friendship.class);
                if (f != null) {
                    f.setId(d.getId());
                    all.add(f);
                }
            }
            l.onState(FriendsState.from(myId, all));
        });
    }

    // ---------- Envoyer une demande ----------

    /** Envoie une demande au joueur portant ce pseudo (recherche exacte, sans tenir compte de la casse). */
    public static void sendRequest(Player me, String pseudo, Callback<RequestResult> cb) {
        if (me.getId() == null) {
            cb.onError(new IllegalStateException("Compte requis"));
            return;
        }
        String wanted = pseudo.trim().toLowerCase(Locale.ROOT);
        if (wanted.isEmpty()) {
            cb.onError(new IllegalArgumentException("Entre un pseudo"));
            return;
        }
        if (wanted.equals(me.getNameLower())) {
            cb.onError(new IllegalArgumentException("Tu ne peux pas t'ajouter toi-même"));
            return;
        }

        db().collection("players").whereEqualTo("nameLower", wanted).limit(1).get()
                .addOnSuccessListener(snap -> {
                    if (snap.isEmpty()) {
                        cb.onError(new IllegalArgumentException("Joueur introuvable"));
                        return;
                    }
                    DocumentSnapshot pd = snap.getDocuments().get(0);
                    Player target = pd.toObject(Player.class);
                    if (target == null) {
                        cb.onError(new IllegalStateException("Profil illisible"));
                        return;
                    }
                    target.setId(pd.getId());
                    checkLimits(me, cb, () -> sendTransaction(me, target, cb));
                })
                .addOnFailureListener(cb::onError);
    }

    /** Vérifie les limites (amis, demandes en attente) avant d'envoyer. */
    private static void checkLimits(Player me, Callback<?> cb, Runnable next) {
        col().whereArrayContains("users", me.getId()).get()
                .addOnSuccessListener(snap -> {
                    int friends = 0, sent = 0;
                    for (DocumentSnapshot d : snap.getDocuments()) {
                        Friendship f = d.toObject(Friendship.class);
                        if (f == null) continue;
                        if (f.accepted()) friends++;
                        else if (f.pending() && me.getId().equals(f.getRequestedBy())) sent++;
                    }
                    if (friends >= MAX_FRIENDS) {
                        cb.onError(new IllegalStateException("Limite de " + MAX_FRIENDS + " amis atteinte"));
                    } else if (sent >= MAX_PENDING_SENT) {
                        cb.onError(new IllegalStateException(
                                "Trop de demandes en attente (" + MAX_PENDING_SENT + " maximum)"));
                    } else {
                        next.run();
                    }
                })
                .addOnFailureListener(cb::onError);
    }

    private static void sendTransaction(Player me, Player target, Callback<RequestResult> cb) {
        String pairId = Friendship.pairId(me.getId(), target.getId());
        DocumentReference ref = col().document(pairId);

        DocumentReference targetRequestNotif = NotificationRepository.ref(
                target.getId(), AppNotification.idOf(AppNotification.FRIEND_REQUEST, pairId));
        DocumentReference myRequestNotif = NotificationRepository.ref(
                me.getId(), AppNotification.idOf(AppNotification.FRIEND_REQUEST, pairId));
        DocumentReference targetAcceptedNotif = NotificationRepository.ref(
                target.getId(), AppNotification.idOf(AppNotification.FRIEND_ACCEPTED, pairId));

        db().runTransaction(tr -> {
            DocumentSnapshot s = tr.get(ref);

            if (s.exists()) {
                Friendship existing = s.toObject(Friendship.class);
                if (existing != null && existing.accepted()) return RequestResult.ALREADY_FRIENDS;
                if (existing != null && me.getId().equals(existing.getRequestedBy())) {
                    return RequestResult.ALREADY_SENT;
                }
                // Demandes croisées : l'autre m'avait déjà écrit, on accepte directement.
                tr.update(ref, "status", Friendship.ACCEPTED, "acceptedAt", System.currentTimeMillis());
                tr.delete(myRequestNotif);   // sa demande n'attend plus de réponse
                tr.set(targetAcceptedNotif,
                        AppNotification.create(AppNotification.FRIEND_ACCEPTED, me, pairId));
                return RequestResult.AUTO_ACCEPTED;
            }

            tr.set(ref, Friendship.newRequest(me, target));
            tr.set(targetRequestNotif,
                    AppNotification.create(AppNotification.FRIEND_REQUEST, me, pairId));
            return RequestResult.SENT;
        }).addOnSuccessListener(cb::onSuccess).addOnFailureListener(cb::onError);
    }

    // ---------- Répondre ----------

    /**
     * Accepte la demande reçue "friendshipId" (le refId de la notification).
     * Vérifie qu'elle existe encore et n'est pas de moi, puis prévient le demandeur.
     */
    public static void accept(String friendshipId, Player me, Callback<Void> cb) {
        DocumentReference ref = col().document(friendshipId);

        db().runTransaction(tr -> {
            DocumentSnapshot s = tr.get(ref);
            Friendship current = s.exists() ? s.toObject(Friendship.class) : null;
            if (current == null || !current.pending() || me.getId().equals(current.getRequestedBy())) {
                throw new FirebaseFirestoreException("Demande introuvable ou déjà traitée",
                        FirebaseFirestoreException.Code.ABORTED);
            }
            tr.update(ref, "status", Friendship.ACCEPTED, "acceptedAt", System.currentTimeMillis());
            tr.delete(NotificationRepository.ref(
                    me.getId(), AppNotification.idOf(AppNotification.FRIEND_REQUEST, friendshipId)));
            tr.set(NotificationRepository.ref(current.getRequestedBy(),
                            AppNotification.idOf(AppNotification.FRIEND_ACCEPTED, friendshipId)),
                    AppNotification.create(AppNotification.FRIEND_ACCEPTED, me, friendshipId));
            return null;
        }).addOnSuccessListener(v -> cb.onSuccess(null)).addOnFailureListener(cb::onError);
    }

    /**
     * Supprime la relation et les notifications de demande des deux côtés.
     * Sert à : refuser une demande reçue, annuler une demande envoyée, retirer un ami.
     * L'id d'une amitié est "idA_idB" (ids Firestore, sans "_") : on en déduit les deux joueurs. cb peut être null.
     */
    public static void delete(String friendshipId, Callback<Void> cb) {
        WriteBatch batch = db().batch();
        batch.delete(col().document(friendshipId));
        for (String playerId : friendshipId.split("_")) {
            batch.delete(NotificationRepository.ref(
                    playerId, AppNotification.idOf(AppNotification.FRIEND_REQUEST, friendshipId)));
        }
        batch.commit()
                .addOnSuccessListener(v -> { if (cb != null) cb.onSuccess(null); })
                .addOnFailureListener(e -> { if (cb != null) cb.onError(e); });
    }
}