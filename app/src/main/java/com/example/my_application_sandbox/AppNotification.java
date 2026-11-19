package com.example.my_application_sandbox;

import android.text.format.DateUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * Document Firestore "players/{destinataire}/notifications/{type_refId}".
 * Notification générique : le type décide du texte et des actions, "refId" désigne l'objet concerné
 * (id d'amitié, de salon...) et "data" porte des informations propres au type.
 * Les méthodes sans préfixe get/is (actionable, message...) ne sont pas enregistrées dans Firestore.
 */
public class AppNotification {

    public static final String FRIEND_REQUEST = "FRIEND_REQUEST";     // action : accepter ou refuser
    public static final String FRIEND_ACCEPTED = "FRIEND_ACCEPTED";   // information
    public static final String SALON_INVITE = "SALON_INVITE";         // prévu : invitation dans un salon (data : salonName, code)

    private String id;
    private String type;
    private String fromId;
    private String fromName;
    private String refId;
    private Map<String, String> data = new HashMap<>();
    private long createdAt;
    private boolean read;

    /** Constructeur sans argument : requis par Firestore (toObject). */
    public AppNotification() { }

    /** Id du document : déterministe, pour qu'un même événement ne crée jamais deux notifications. */
    public static String idOf(String type, String refId) {
        return type + "_" + refId;
    }

    /** Nouvelle notification (non lue) envoyée par "from". */
    public static AppNotification create(String type, Player from, String refId) {
        AppNotification n = new AppNotification();
        n.id = idOf(type, refId);
        n.type = type;
        n.fromId = from.getId();
        n.fromName = from.getName();
        n.refId = refId;
        n.createdAt = System.currentTimeMillis();
        n.read = false;
        return n;
    }

    // ---- Aides de lecture (non enregistrées) ----

    /** Vrai si la notification attend une réponse : elle reste « non lue » tant qu'on n'a pas répondu. */
    public boolean actionable() {
        return FRIEND_REQUEST.equals(type) || SALON_INVITE.equals(type);
    }

    /** Texte principal de la ligne. */
    public String message() {
        String who = (fromName != null) ? fromName : "Un joueur";
        if (FRIEND_REQUEST.equals(type)) return who + " veut devenir ton ami";
        if (FRIEND_ACCEPTED.equals(type)) return who + " a accepté ta demande";
        if (SALON_INVITE.equals(type)) {
            String salon = data.get("salonName");
            return who + " t'invite" + (salon != null ? " dans le salon " + salon : "");
        }
        return "Nouvelle notification";
    }

    /** Lettre de l'avatar. */
    public String initial() {
        return Friendship.initialOf(fromName);
    }

    /** Date relative : « il y a 5 minutes », « hier »... */
    public String timeText() {
        return DateUtils.getRelativeTimeSpanString(
                createdAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString();
    }

    // ---- Getters / setters (Firestore) ----

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getFromId() { return fromId; }
    public void setFromId(String fromId) { this.fromId = fromId; }

    public String getFromName() { return fromName; }
    public void setFromName(String fromName) { this.fromName = fromName; }

    public String getRefId() { return refId; }
    public void setRefId(String refId) { this.refId = refId; }

    public Map<String, String> getData() { return data; }
    public void setData(Map<String, String> data) { this.data = data; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public boolean isRead() { return read; }
    public void setRead(boolean read) { this.read = read; }
}