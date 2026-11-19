package com.example.my_application_sandbox;

import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.List;

/** Accès Firestore aux notifications : players/{playerId}/notifications. Sert à tous les types de notification. */
public final class NotificationRepository {

    private static final long PURGE_AFTER_MS = 30L * 24 * 3600 * 1000;   // 30 jours
    private static final int PURGE_MAX = 50;                              // par passage

    public interface ListListener { void onNotifications(List<AppNotification> list); }

    private NotificationRepository() { }

    public static CollectionReference col(String playerId) {
        return FirebaseFirestore.getInstance()
                .collection("players").document(playerId).collection("notifications");
    }

    /** Référence d'une notification (utilisée aussi dans les transactions d'autres dépôts). */
    public static DocumentReference ref(String playerId, String notificationId) {
        return col(playerId).document(notificationId);
    }

    /** Écoute mes notifications, de la plus récente à la plus ancienne : page et badge. */
    public static ListenerRegistration listen(String myId, ListListener l) {
        return col(myId).orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener((snap, e) -> {
                    if (e != null || snap == null) return;
                    List<AppNotification> list = new ArrayList<>();
                    for (DocumentSnapshot d : snap.getDocuments()) {
                        AppNotification n = d.toObject(AppNotification.class);
                        if (n != null) {
                            n.setId(d.getId());
                            list.add(n);
                        }
                    }
                    l.onNotifications(list);
                });
    }

    /** Nombre affiché sur la cloche. */
    public static int unreadCount(List<AppNotification> list) {
        int count = 0;
        for (AppNotification n : list) if (!n.isRead()) count++;
        return count;
    }

    /** Envoie une notification à un joueur (écrase la précédente de même type et même objet). */
    public static void send(String toPlayerId, AppNotification n) {
        ref(toPlayerId, n.getId()).set(n);
    }

    /** Marque comme lues les notifications d'information ; les demandes restent non lues jusqu'à la réponse. */
    public static void markRead(String myId, List<AppNotification> list) {
        WriteBatch batch = FirebaseFirestore.getInstance().batch();
        int count = 0;
        for (AppNotification n : list) {
            if (!n.isRead() && !n.actionable()) {
                batch.update(ref(myId, n.getId()), "read", true);
                count++;
            }
        }
        if (count > 0) batch.commit();
    }

    public static void delete(String myId, String notificationId) {
        ref(myId, notificationId).delete();
    }

    /** Supprime les notifications lues, sans action attendue, de plus de 30 jours (à appeler à l'ouverture de la page). */
    public static void purgeOld(String myId, List<AppNotification> list) {
        long limit = System.currentTimeMillis() - PURGE_AFTER_MS;
        WriteBatch batch = FirebaseFirestore.getInstance().batch();
        int count = 0;
        for (AppNotification n : list) {
            if (count >= PURGE_MAX) break;
            if (n.isRead() && !n.actionable() && n.getCreatedAt() < limit) {
                batch.delete(ref(myId, n.getId()));
                count++;
            }
        }
        if (count > 0) batch.commit();
    }
}