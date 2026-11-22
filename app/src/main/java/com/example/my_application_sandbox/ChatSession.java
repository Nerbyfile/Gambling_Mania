package com.example.my_application_sandbox;

import android.os.SystemClock;

import com.example.my_application_sandbox.ChatRepository.ChatMessage;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Chat du salon en cours, partagé par tous les écrans (hall, table de roulette).
 * Démarré à l'arrivée dans le salon, arrêté quand on le quitte ; garde les messages, les couleurs
 * des joueurs et le compteur de messages non lus. Tout se passe sur le thread principal.
 */
public final class ChatSession {

    private static final long SEND_COOLDOWN_MS = 600;     // anti-spam

    public interface Listener { void onChatChanged(); }

    private static String salonId;
    private static ListenerRegistration regMessages, regMembers;
    private static List<ChatMessage> messages = new ArrayList<>();
    private static Map<String, Integer> colors = new HashMap<>();      // id du joueur -> couleur
    private static final Set<Listener> listeners = new LinkedHashSet<>();
    private static long lastReadMs;
    private static boolean firstSnapshot;
    private static boolean panelOpen;
    private static long lastSendAt;

    private ChatSession() { }

    // ---------- Cycle de vie ----------

    /** Démarre le chat de ce salon (sans effet s'il est déjà démarré). */
    public static void start(String id) {
        if (id.equals(salonId) && regMessages != null) return;
        stop();
        salonId = id;
        firstSnapshot = true;
        lastReadMs = 0;
        panelOpen = false;

        regMessages = ChatRepository.listen(id, list -> {
            messages = list;
            if (firstSnapshot || panelOpen) {          // historique déjà là à l'arrivée, ou chat ouvert : tout est lu
                firstSnapshot = false;
                lastReadMs = lastTime();
            }
            notifyChanged();
        });
        regMembers = SalonRepository.listenMembers(id, members -> {
            colors = SalonRepository.assignColors(members);
            notifyChanged();
        });
    }

    public static void stop() {
        if (regMessages != null) regMessages.remove();
        if (regMembers != null) regMembers.remove();
        regMessages = null;
        regMembers = null;
        salonId = null;
        messages = new ArrayList<>();
        colors = new HashMap<>();
        panelOpen = false;
        notifyChanged();
    }

    public static boolean isActive() {
        return salonId != null;
    }

    // ---------- Lecture ----------

    public static List<ChatMessage> messages() {
        return messages;
    }

    public static ChatMessage last() {
        return messages.isEmpty() ? null : messages.get(messages.size() - 1);
    }

    /** Couleur d'un joueur du salon (la même partout) ; à défaut celle enregistrée dans le message, sinon blanc. */
    public static int colorOf(String playerId, int fallback) {
        Integer c = colors.get(playerId);
        if (c != null) return c;
        return fallback != 0 ? fallback : 0xFFFFFFFF;
    }

    /** Messages des autres arrivés depuis la dernière lecture. */
    public static int unreadCount() {
        String myId = Player.current().getId();
        int n = 0;
        for (ChatMessage m : messages) {
            if (m.timeMillis() > lastReadMs && (myId == null || !myId.equals(m.getSenderId()))) n++;
        }
        return n;
    }

    public static void markRead() {
        long t = lastTime();
        if (t > lastReadMs) {
            lastReadMs = t;
            notifyChanged();
        }
    }

    public static void setPanelOpen(boolean open) {
        panelOpen = open;
    }

    // ---------- Envoi ----------

    /** Envoie un message. Retourne false s'il est vide, trop rapproché du précédent, ou si le chat n'est pas démarré. */
    public static boolean send(String text) {
        if (salonId == null) return false;
        String t = text.trim();
        if (t.isEmpty()) return false;
        if (t.length() > ChatRepository.MAX_LENGTH) t = t.substring(0, ChatRepository.MAX_LENGTH);

        long now = SystemClock.elapsedRealtime();
        if (now - lastSendAt < SEND_COOLDOWN_MS) return false;
        lastSendAt = now;

        Player me = Player.current();
        Integer c = colors.get(me.getId());
        ChatRepository.send(salonId, me, t, c != null ? c : 0);
        return true;
    }

    // ---------- Abonnés ----------

    public static void addListener(Listener l) {
        listeners.add(l);
    }

    public static void removeListener(Listener l) {
        listeners.remove(l);
    }

    private static void notifyChanged() {
        for (Listener l : new ArrayList<>(listeners)) l.onChatChanged();
    }

    private static long lastTime() {
        ChatMessage m = last();
        return m == null ? 0 : m.timeMillis();
    }
}