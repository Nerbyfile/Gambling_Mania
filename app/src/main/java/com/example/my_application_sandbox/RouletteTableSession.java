package com.example.my_application_sandbox;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import com.example.my_application_sandbox.RouletteTouchHelper.Bet;
import com.example.my_application_sandbox.SalonRepository.RoundBets;
import com.example.my_application_sandbox.SalonRepository.SalonMember;
import com.example.my_application_sandbox.SalonRepository.TableState;
import com.google.firebase.firestore.ListenerRegistration;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Tout le réseau de la table de roulette : écouteurs, présence, élection du conducteur
 * (le joueur assis depuis le plus longtemps) et boucle des phases quand on est conducteur.
 */
public class RouletteTableSession {

    public static final long BET_MS = 30_000, SPIN_MS = 6_000, RESULT_MS = 6_000;
    private static final long GRACE_MS = 1_500;          // laisse arriver les dernières mises
    private static final long HEARTBEAT_MS = 60_000;
    private static final long STALE_MS = 150_000;        // 2,5 battements manqués
    private static final long WATCH_MS = 30_000;
    private static final long BET_DEBOUNCE_MS = 1_500;   // regroupe les écritures de jetons
    private static final int MAX_EMPTY_ROUNDS = 2;       // puis la table se met en pause

    public interface Listener {
        void onTable(TableState table);
        void onOthersBets(Map<String, List<Bet>> perPlayer);   // id du joueur -> ses mises
        void onSeats(List<SalonMember> seats);                 // joueurs assis (avec leur couleur)
        void onClosed();
    }

    private final String salonId, myId;
    private final Listener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SecureRandom random = new SecureRandom();

    private ListenerRegistration regTable, regSeats, regPresence, regBets;
    private TableState table;
    private List<SalonMember> seats = new ArrayList<>();
    private Map<String, RoundBets> allBets = new HashMap<>();
    private final Map<String, long[]> seen = new HashMap<>();   // joueur -> {dernier battement, observé à}
    private boolean started = false, arrival = false;
    private int emptyRounds = 0;
    private String driveKey = null;
    private Runnable driveTask = null;

    private List<Bet> pending = null;
    private int pendingRound;

    public RouletteTableSession(String salonId, String myId, Listener listener) {
        this.salonId = salonId;
        this.myId = myId;
        this.listener = listener;
    }

    // ---------- Cycle de vie ----------

    public void start() {
        if (started) return;
        started = true;
        arrival = true;

        SalonRepository.sit(salonId, myId);

        regTable = SalonRepository.listenTable(salonId, t -> {
            if (t == null) { listener.onClosed(); return; }
            table = t;
            if (arrival) {                               // arrivée sur une table en pause : on la relance
                arrival = false;
                if (TableState.IDLE.equals(t.getPhase())) wake();
            }
            listener.onTable(t);
            notifyOthers();
            drive();
        });
        regSeats = SalonRepository.listenSeats(salonId, list -> {
            seats = list;
            listener.onSeats(list);
            drive();
        });
        regPresence = SalonRepository.listenPresence(salonId, beats -> {
            long now = SystemClock.elapsedRealtime();
            for (Map.Entry<String, Long> e : beats.entrySet()) {
                long[] s = seen.get(e.getKey());
                if (s == null || s[0] != e.getValue()) seen.put(e.getKey(), new long[]{e.getValue(), now});
            }
        });
        regBets = SalonRepository.listenBets(salonId, map -> {
            allBets = map;
            notifyOthers();
        });

        handler.post(heartbeatTask);
        handler.postDelayed(watchTask, WATCH_MS);
    }

    /** Appli en arrière-plan : on coupe les écouteurs (le siège reste, la présence s'éteint). */
    public void stop() {
        if (!started) return;
        flushBets();
        started = false;
        if (regTable != null) regTable.remove();
        if (regSeats != null) regSeats.remove();
        if (regPresence != null) regPresence.remove();
        if (regBets != null) regBets.remove();
        regTable = regSeats = regPresence = regBets = null;
        handler.removeCallbacksAndMessages(null);
        seen.clear();
        driveKey = null;
        driveTask = null;
        table = null;
    }

    /** Quitte la table pour de bon. */
    public void leave() {
        stop();
        SalonRepository.unseat(salonId, myId);
    }

    /** Relance une table en pause. */
    public void wake() {
        if (table == null || !TableState.IDLE.equals(table.getPhase())) return;
        SalonRepository.advance(salonId, table.getRound(), TableState.IDLE,
                map("phase", TableState.BETTING, "round", table.getRound() + 1, "winningNumber", -1));
    }

    // ---------- Mises (envoi regroupé) ----------

    public void publishBets(List<Bet> bets, int round) {
        pending = bets;
        pendingRound = round;
        handler.removeCallbacks(sendBets);
        handler.postDelayed(sendBets, BET_DEBOUNCE_MS);
    }

    public void publishNow(List<Bet> bets, int round) {
        pending = bets;
        pendingRound = round;
        flushBets();
    }

    public void flushBets() {
        handler.removeCallbacks(sendBets);
        if (pending != null) {
            SalonRepository.publishBets(salonId, myId, pendingRound, pending);
            pending = null;
        }
    }

    private final Runnable sendBets = this::flushBets;

    private void notifyOthers() {
        int round = (table == null) ? -1 : table.getRound();
        Map<String, List<Bet>> out = new TreeMap<>();            // trié par id : ordre stable
        for (Map.Entry<String, RoundBets> e : allBets.entrySet()) {
            if (e.getKey().equals(myId)) continue;
            if (e.getValue().getRound() != round) continue;      // ignore les mises d'un ancien tour
            out.put(e.getKey(), e.getValue().toBets());
        }
        listener.onOthersBets(out);
    }

    // ---------- Présence ----------

    private final Runnable heartbeatTask = new Runnable() {
        @Override public void run() {
            if (!started) return;
            SalonRepository.heartbeat(salonId, myId, System.currentTimeMillis());
            handler.postDelayed(this, HEARTBEAT_MS);
        }
    };

    private final Runnable watchTask = new Runnable() {
        @Override public void run() {
            if (!started) return;
            drive();
            if (iAmDriver()) evictStale();
            handler.postDelayed(this, WATCH_MS);
        }
    };

    /** Un joueur est « disparu » si son battement n'a pas changé depuis STALE_MS (horloge locale, sans décalage entre téléphones). */
    private boolean isStale(String id) {
        long now = SystemClock.elapsedRealtime();
        long[] s = seen.get(id);
        if (s == null) {
            seen.put(id, new long[]{-1, now});
            return false;
        }
        return now - s[1] > STALE_MS;
    }

    private void evictStale() {
        for (SalonMember m : new ArrayList<>(seats)) {
            if (!m.getId().equals(myId) && isStale(m.getId())) {
                SalonRepository.unseat(salonId, m.getId());
                seen.remove(m.getId());
            }
        }
    }

    // ---------- Conducteur ----------

    /** Conducteur = le plus ancien assis encore vivant. Même calcul sur tous les téléphones. */
    private boolean iAmDriver() {
        for (SalonMember m : seats) {
            if (m.getId().equals(myId)) return true;
            if (!isStale(m.getId())) return false;
        }
        return false;
    }

    private void drive() {
        if (!started || table == null) return;
        boolean driver = iAmDriver();
        String key = driver + ":" + table.getRound() + ":" + table.getPhase();
        if (key.equals(driveKey)) return;                // déjà planifié pour cet état
        driveKey = key;
        if (driveTask != null) handler.removeCallbacks(driveTask);
        driveTask = null;
        if (!driver) return;

        final int round = table.getRound();
        final String phase = table.getPhase();
        long delay;
        switch (phase) {
            case TableState.BETTING:  delay = BET_MS + GRACE_MS; break;
            case TableState.SPINNING: delay = SPIN_MS + 500; break;
            case TableState.RESULT:   delay = RESULT_MS; break;
            default: return;                              // IDLE : on attend un joueur
        }
        driveTask = () -> step(round, phase);
        handler.postDelayed(driveTask, delay);
    }

    private void step(int round, String phase) {
        if (table == null || table.getRound() != round || !phase.equals(table.getPhase())) return;

        switch (phase) {
            case TableState.BETTING:
                if (hasBets(round)) {
                    emptyRounds = 0;
                    SalonRepository.advance(salonId, round, phase,
                            map("phase", TableState.SPINNING, "winningNumber", random.nextInt(37)));
                } else if (++emptyRounds >= MAX_EMPTY_ROUNDS) {
                    emptyRounds = 0;                      // personne ne mise : la table se met en pause
                    SalonRepository.advance(salonId, round, phase, map("phase", TableState.IDLE));
                } else {
                    SalonRepository.advance(salonId, round, phase, map("round", round + 1));
                }
                break;
            case TableState.SPINNING: {
                List<Integer> h = new ArrayList<>(table.getHistory());
                h.add(0, table.getWinningNumber());
                if (h.size() > RouletteHistory.MAX) h = new ArrayList<>(h.subList(0, RouletteHistory.MAX));
                SalonRepository.advance(salonId, round, phase,
                        map("phase", TableState.RESULT, "history", h));
                break;
            }
            case TableState.RESULT:
                SalonRepository.advance(salonId, round, phase,
                        map("phase", TableState.BETTING, "round", round + 1, "winningNumber", -1));
                break;
        }
    }

    private boolean hasBets(int round) {
        for (RoundBets rb : allBets.values()) {
            if (rb.getRound() == round && rb.getItems() != null && !rb.getItems().isEmpty()) return true;
        }
        return false;
    }

    private static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }
}