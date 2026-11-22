package com.example.my_application_sandbox;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.ServerTimestamp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Messages d'un salon : salons/{salonId}/messages/{messageId}. */
public final class ChatRepository {

    public static final int MAX_LENGTH = 200;   // caractères par message
    public static final int HISTORY = 50;       // messages chargés

    public interface MessagesListener { void onMessages(List<ChatMessage> messages); }

    public static class ChatMessage {
        private String id, senderId, senderName, text;
        private int color;                 // couleur de l'expéditeur à l'envoi (secours s'il a quitté le salon)
        private Timestamp createdAt;

        public ChatMessage() { }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getSenderId() { return senderId; }
        public void setSenderId(String senderId) { this.senderId = senderId; }
        public String getSenderName() { return senderName; }
        public void setSenderName(String senderName) { this.senderName = senderName; }
        public String getText() { return text; }
        public void setText(String text) { this.text = text; }
        public int getColor() { return color; }
        public void setColor(int color) { this.color = color; }

        /** Heure du serveur : laissée à null à l'envoi, Firestore la remplit. */
        @ServerTimestamp
        public Timestamp getCreatedAt() { return createdAt; }
        public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

        /** Heure du message en ms (estimée tant que le serveur n'a pas confirmé). */
        public long timeMillis() {
            return createdAt != null ? createdAt.toDate().getTime() : System.currentTimeMillis();
        }
    }

    private ChatRepository() { }

    private static CollectionReference messages(String salonId) {
        return FirebaseFirestore.getInstance()
                .collection("salons").document(salonId).collection("messages");
    }

    public static void send(String salonId, Player me, String text, int color) {
        ChatMessage m = new ChatMessage();
        m.setSenderId(me.getId());
        m.setSenderName(me.getName());
        m.setText(text);
        m.setColor(color);
        messages(salonId).add(m);
    }

    /** Écoute les derniers messages (du plus ancien au plus récent). */
    public static ListenerRegistration listen(String salonId, MessagesListener l) {
        return messages(salonId).orderBy("createdAt", Query.Direction.DESCENDING).limit(HISTORY)
                .addSnapshotListener((snap, e) -> {
                    if (e != null || snap == null) return;
                    List<ChatMessage> list = new ArrayList<>();
                    for (DocumentSnapshot d : snap.getDocuments()) {
                        ChatMessage m = d.toObject(ChatMessage.class,
                                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE);
                        if (m != null) {
                            m.setId(d.getId());
                            list.add(m);
                        }
                    }
                    Collections.reverse(list);
                    l.onMessages(list);
                });
    }
}