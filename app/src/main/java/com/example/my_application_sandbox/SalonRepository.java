package com.example.my_application_sandbox;

import com.example.my_application_sandbox.RouletteTouchHelper.Bet;
import com.example.my_application_sandbox.RouletteTouchHelper.Zone;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.WriteBatch;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.Source;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Comparator;

/**
 * salons/{id}                      nom, code, visibilité, nb de membres
 *   members/{playerId}             nom, game ("roulette" si à la table), seatSince
 *   presence/{playerId}            beat (battement de présence)
 *   tables/roulette                phase, round, winningNumber
 *   roulette_bets/{playerId}       mises du tour
 */
public final class SalonRepository {

    public static final int MAX_MEMBERS = 8;
    public static final String GAME_ROULETTE = "roulette";
    private static final int CODE_LENGTH = 5;
    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();
    public static final int[] SEAT_COLORS = {
            0xFF42A5F5,   // bleu
            0xFFFF9800,   // orange
            0xFFBA68C8,   // violet
            0xFFF06292,   // rose
            0xFFFFEB3B,   // jaune
            0xFFFFFFFF,   // blanc
            0xFF1DE9B6,   // turquoise
            0xFFFF5252    // rouge vif
    };

    public static int seatColor(int index) {
        return SEAT_COLORS[Math.floorMod(index, SEAT_COLORS.length)];
    }

    /** Couleur finale de chaque joueur assis : toutes différentes, identiques sur tous les téléphones. */
    public static Map<String, Integer> assignColors(List<SalonMember> seats) {
        List<SalonMember> sorted = new ArrayList<>(seats);
        sorted.sort(Comparator.comparingLong(SalonMember::getJoinedAt).thenComparing(SalonMember::getId));

        Map<String, Integer> out = new HashMap<>();
        boolean[] taken = new boolean[SEAT_COLORS.length];
        List<SalonMember> conflicts = new ArrayList<>();

        // chacun garde sa couleur enregistrée si personne de plus ancien ne l'a prise
        for (SalonMember m : sorted) {
            int c = Math.floorMod(m.getSeatColor(), SEAT_COLORS.length);
            if (!taken[c]) {
                taken[c] = true;
                out.put(m.getId(), SEAT_COLORS[c]);
            } else {
                conflicts.add(m);
            }
        }
        // les doublons reçoivent la première couleur libre
        for (SalonMember m : conflicts) {
            int free = 0;
            while (free < taken.length - 1 && taken[free]) free++;
            taken[free] = true;
            out.put(m.getId(), SEAT_COLORS[free]);
        }
        return out;
    }

    // ---------- Callbacks ----------
    public interface Callback<T> { void onSuccess(T result); void onError(Exception e); }
    public interface MembersListener { void onMembers(List<SalonMember> members); }
    public interface TableListener { void onTable(TableState table); }          // null = salon supprimé
    public interface PresenceListener { void onPresence(Map<String, Long> beats); }
    public interface BetsListener { void onBets(Map<String, RoundBets> betsByPlayer); }

    private SalonRepository() { }

    // ---------- Modèles Firestore ----------

    public static class Salon {
        public static final String PUBLIC = "PUBLIC";
        public static final String PRIVATE = "PRIVATE";

        private String id, name, code, visibility, ownerId,ownerName;
        private int memberCount;
        private long lastActive, createdAt;
        private int colorMask;  // bit i = couleur i de SEAT_COLORS déjà attribuée à un membre

        public Salon() { }
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        public String getVisibility() { return visibility; }
        public void setVisibility(String visibility) { this.visibility = visibility; }
        public String getOwnerId() { return ownerId; }
        public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
        public int getMemberCount() { return memberCount; }
        public void setMemberCount(int memberCount) { this.memberCount = memberCount; }
        public long getLastActive() { return lastActive; }
        public void setLastActive(long lastActive) { this.lastActive = lastActive; }
        public long getCreatedAt() { return createdAt; }
        public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
        public String getOwnerName() { return ownerName; }
        public void setOwnerName(String ownerName) { this.ownerName = ownerName; }
        public int getColorMask() { return colorMask; }
        public void setColorMask(int colorMask) { this.colorMask = colorMask; }

    }

    public static class SalonMember {
        private String id, name, game;      // game = null si dans le hall
        private int avatarId;
        private long seatSince;
        private long joinedAt;   // arrivée dans le salon : sert à départager des couleurs en doublon

        private int seatColor;   // index dans SEAT_COLORS

        public int getSeatColor() { return seatColor; }
        public void setSeatColor(int seatColor) { this.seatColor = seatColor; }

        public SalonMember() { }
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getGame() { return game; }
        public void setGame(String game) { this.game = game; }
        public long getSeatSince() { return seatSince; }
        public void setSeatSince(long seatSince) { this.seatSince = seatSince; }
        public int getAvatarId() { return avatarId; }
        public void setAvatarId(int avatarId) { this.avatarId = avatarId; }
        public long getJoinedAt() { return joinedAt; }
        public void setJoinedAt(long joinedAt) { this.joinedAt = joinedAt; }
    }

    public static class TableState {
        public static final String IDLE = "IDLE";           // en pause
        public static final String BETTING = "BETTING";
        public static final String SPINNING = "SPINNING";
        public static final String RESULT = "RESULT";

        private String phase = IDLE;
        private int round;
        private int winningNumber = -1;

        private List<Integer> history = new ArrayList<>();

        public TableState() { }
        private Timestamp phaseStartedAt;   // heure serveur du début de la phase (null le temps que le serveur confirme)
        public Timestamp getPhaseStartedAt() { return phaseStartedAt; }
        public void setPhaseStartedAt(Timestamp t) { this.phaseStartedAt = t; }
        public String getPhase() { return phase; }
        public void setPhase(String phase) { this.phase = phase; }
        public int getRound() { return round; }
        public void setRound(int round) { this.round = round; }
        public int getWinningNumber() { return winningNumber; }
        public void setWinningNumber(int winningNumber) { this.winningNumber = winningNumber; }

        public List<Integer> getHistory() { return history != null ? history : new ArrayList<>(); }
        public void setHistory(List<Integer> history) { this.history = history; }
    }

    public static class BetData {
        private String kind, zone;
        private List<Integer> numbers = new ArrayList<>();
        private int amount;

        public BetData() { }
        public String getKind() { return kind; }
        public void setKind(String kind) { this.kind = kind; }
        public String getZone() { return zone; }
        public void setZone(String zone) { this.zone = zone; }
        public List<Integer> getNumbers() { return numbers; }
        public void setNumbers(List<Integer> numbers) { this.numbers = numbers; }
        public int getAmount() { return amount; }
        public void setAmount(int amount) { this.amount = amount; }

        static BetData from(Bet b) {
            BetData d = new BetData();
            d.kind = b.kind.name();
            d.zone = (b.zone == null) ? null : b.zone.name();
            d.numbers = new ArrayList<>(b.numbers);
            d.amount = b.Mise;
            return d;
        }

        Bet toBet() {
            Bet bet;
            if (zone != null) {
                bet = Bet.ofZone(Zone.valueOf(zone));
            } else {
                Set<Integer> set = new LinkedHashSet<>(numbers);
                bet = (set.size() == 6) ? Bet.ofLine(set) : Bet.ofNumbers(set);
            }
            bet.Mise = amount;
            return bet;
        }
    }

    public static class RoundBets {
        private int round;
        private List<BetData> items = new ArrayList<>();

        public RoundBets() { }
        public int getRound() { return round; }
        public void setRound(int round) { this.round = round; }
        public List<BetData> getItems() { return items; }
        public void setItems(List<BetData> items) { this.items = items; }

        public List<Bet> toBets() {
            List<Bet> out = new ArrayList<>();
            if (items != null) for (BetData d : items) out.add(d.toBet());
            return out;
        }
    }

    // ---------- Références ----------

    private static CollectionReference salons() {
        return FirebaseFirestore.getInstance().collection("salons");
    }

    private static DocumentReference table(String salonId) {
        return salons().document(salonId).collection("tables").document(GAME_ROULETTE);
    }

    private static CollectionReference members(String salonId) {
        return salons().document(salonId).collection("members");
    }

    private static SalonMember memberOf(Player p, int color, long now) {
        SalonMember m = new SalonMember();
        m.setId(p.getId());
        m.setName(p.getName());
        m.setAvatarId(p.getAvatarId());
        m.setGame(null);
        m.setSeatSince(0);
        m.setSeatColor(color);     // couleur attribuée dès l'arrivée dans le salon
        m.setJoinedAt(now);
        return m;
    }

    /** Première couleur libre du salon (masque de bits) ; si tout est pris, une couleur répétée. */
    private static int firstFreeColor(int mask, int memberCount) {
        for (int i = 0; i < SEAT_COLORS.length; i++) {
            if ((mask & (1 << i)) == 0) return i;
        }
        return Math.floorMod(memberCount, SEAT_COLORS.length);
    }

    private static String randomCode() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < CODE_LENGTH; i++) sb.append(CODE_CHARS.charAt(RANDOM.nextInt(CODE_CHARS.length())));
        return sb.toString();
    }

    // ---------- Salons : création, liste, arrivée, départ ----------

    public static void createSalon(Player owner, String name, boolean isPrivate, Callback<Salon> cb) {
        tryCreate(owner, name, isPrivate, 0, cb);
    }

    private static void tryCreate(Player owner, String name, boolean isPrivate, int attempt, Callback<Salon> cb) {
        if (attempt >= 5) {
            cb.onError(new IllegalStateException("Impossible de générer un code, réessaie"));
            return;
        }
        String code = randomCode();
        salons().whereEqualTo("code", code).limit(1).get()
                .addOnSuccessListener(snap -> {
                    if (!snap.isEmpty()) {
                        tryCreate(owner, name, isPrivate, attempt + 1, cb);
                        return;
                    }
                    long now = System.currentTimeMillis();
                    DocumentReference ref = salons().document();
                    Salon s = new Salon();
                    s.setId(ref.getId());
                    s.setName(name);
                    s.setCode(code);
                    s.setVisibility(isPrivate ? Salon.PRIVATE : Salon.PUBLIC);
                    s.setOwnerId(owner.getId());
                    s.setOwnerName(owner.getName());
                    s.setMemberCount(1);
                    s.setColorMask(1);                       // bit 0 : couleur du créateur
                    s.setLastActive(now);
                    s.setCreatedAt(now);

                    TableState t = new TableState();   // table de roulette : en pause jusqu'au premier joueur

                    t.setHistory(RouletteHistory.randomPast(RouletteHistory.MAX));

                    WriteBatch batch = FirebaseFirestore.getInstance().batch();
                    batch.set(ref, s);
                    batch.set(ref.collection("members").document(owner.getId()), memberOf(owner, 0, now));                    batch.set(ref.collection("tables").document(GAME_ROULETTE), t);

                    batch.commit()
                            .addOnSuccessListener(v -> cb.onSuccess(s))
                            .addOnFailureListener(cb::onError);
                })
                .addOnFailureListener(cb::onError);
    }

    /** Salons publics encore actifs (lecture unique, pas d'écouteur). */
    public static void listPublicSalons(Callback<List<Salon>> cb) {
        salons().whereEqualTo("visibility", Salon.PUBLIC).get()
                .addOnSuccessListener(snap -> {
                    long limit = System.currentTimeMillis() - 12L * 3600_000L;
                    List<Salon> out = new ArrayList<>();
                    for (DocumentSnapshot d : snap.getDocuments()) {
                        Salon s = d.toObject(Salon.class);
                        if (s == null) continue;
                        s.setId(d.getId());
                        if (s.getMemberCount() > 0 && s.getLastActive() > limit) out.add(s);
                    }
                    Collections.sort(out, (a, b) -> Integer.compare(b.getMemberCount(), a.getMemberCount()));
                    cb.onSuccess(out);
                })
                .addOnFailureListener(cb::onError);
    }

    public static void joinById(String salonId, Player p, Callback<Salon> cb) {
        join(salons().document(salonId), p, cb);
    }

    public static void joinByCode(String code, Player p, Callback<Salon> cb) {
        salons().whereEqualTo("code", code.toUpperCase(Locale.ROOT)).limit(1).get()
                .addOnSuccessListener(snap -> {
                    if (snap.isEmpty()) {
                        cb.onError(new IllegalArgumentException("Salon introuvable"));
                        return;
                    }
                    join(snap.getDocuments().get(0).getReference(), p, cb);
                })
                .addOnFailureListener(cb::onError);
    }

    private static void join(DocumentReference ref, Player p, Callback<Salon> cb) {
        DocumentReference memberRef = ref.collection("members").document(p.getId());
        FirebaseFirestore.getInstance().runTransaction(tr -> {
            DocumentSnapshot s = tr.get(ref);
            DocumentSnapshot m = tr.get(memberRef);
            Salon salon = s.exists() ? s.toObject(Salon.class) : null;
            if (salon == null) {
                throw new FirebaseFirestoreException("Salon introuvable", FirebaseFirestoreException.Code.NOT_FOUND);
            }
            salon.setId(s.getId());
            long now = System.currentTimeMillis();
            if (!m.exists()) {
                if (salon.getMemberCount() >= MAX_MEMBERS) {
                    throw new FirebaseFirestoreException("Salon plein", FirebaseFirestoreException.Code.ABORTED);
                }
                int color = firstFreeColor(salon.getColorMask(), salon.getMemberCount());
                salon.setMemberCount(salon.getMemberCount() + 1);
                salon.setColorMask(salon.getColorMask() | (1 << color));
                tr.update(ref, "memberCount", salon.getMemberCount(),
                        "lastActive", now,
                        "colorMask", salon.getColorMask());
                tr.set(memberRef, memberOf(p, color, now));
            } else {
                tr.update(ref, "lastActive", now);
            }
            return salon;
        }).addOnSuccessListener(cb::onSuccess).addOnFailureListener(cb::onError);
    }

    /** Quitte le salon ; le dernier à partir supprime le salon. */
    public static void leaveSalon(String salonId, String playerId) {
        DocumentReference ref = salons().document(salonId);
        DocumentReference memberRef = ref.collection("members").document(playerId);
        FirebaseFirestore.getInstance().runTransaction(tr -> {
            DocumentSnapshot s = tr.get(ref);
            DocumentSnapshot m = tr.get(memberRef);
            if (m.exists()) {
                tr.delete(memberRef);
                if (s.exists()) {
                    Long c = s.getLong("memberCount");
                    long count = (c == null) ? 0 : c;
                    if (count <= 1) {
                        tr.delete(ref);
                    } else {
                        Long maskValue = s.getLong("colorMask");
                        int mask = (maskValue == null) ? 0 : maskValue.intValue();
                        Long color = m.getLong("seatColor");
                        if (color != null && color >= 0 && color < SEAT_COLORS.length) {
                            mask &= ~(1 << color.intValue());      // la couleur redevient disponible
                        }
                        tr.update(ref, "memberCount", count - 1,
                                "lastActive", System.currentTimeMillis(),
                                "colorMask", mask);
                    }
                }
            }
            return null;
        });
        ref.collection("presence").document(playerId).delete();
        ref.collection("roulette_bets").document(playerId).delete();
    }

    // ---------- Table de roulette ----------

    /** S'assoit à la table (garde l'ancienneté si déjà assis). La couleur est celle attribuée à l'arrivée dans le salon. */
    public static void sit(String salonId, String playerId, long now) {
        DocumentReference m = members(salonId).document(playerId);
        m.get().addOnSuccessListener(d -> {
            if (d.exists() && GAME_ROULETTE.equals(d.getString("game"))) return;
            m.update("game", GAME_ROULETTE, "seatSince", now);
        });
    }

    /** Quitte la table (reste dans le salon). */
    public static void unseat(String salonId, String playerId) {
        DocumentReference ref = salons().document(salonId);
        ref.collection("members").document(playerId).update("game", null);
        ref.collection("presence").document(playerId).delete();
        ref.collection("roulette_bets").document(playerId).delete();
    }

    public static void heartbeat(String salonId, String playerId, long beat) {
        salons().document(salonId).collection("presence").document(playerId)
                .set(Collections.singletonMap("beat", beat));
    }

    public interface OffsetListener { void onOffset(long offsetMs); }

    /** Décalage = heure serveur − heure du téléphone (ms). 0 si ça échoue. */
    public static void measureServerOffset(String salonId, String playerId, OffsetListener l) {
        DocumentReference ref = salons().document(salonId).collection("presence").document(playerId + "_clock");
        long before = System.currentTimeMillis();
        ref.set(Collections.singletonMap("ts", FieldValue.serverTimestamp()))
                .addOnSuccessListener(v -> ref.get(Source.SERVER).addOnSuccessListener(d -> {
                    long after = System.currentTimeMillis();
                    Timestamp ts = d.getTimestamp("ts");
                    ref.delete();
                    l.onOffset(ts == null ? 0 : ts.toDate().getTime() - (before + after) / 2);
                }).addOnFailureListener(e -> l.onOffset(0)))
                .addOnFailureListener(e -> l.onOffset(0));
    }

    /** Change la phase de la table seulement si le tour et la phase attendus sont toujours les bons. */
    public static void advance(String salonId, int expectedRound, String expectedPhase,
                               Map<String, Object> updates) {
        DocumentReference t = table(salonId);
        FirebaseFirestore.getInstance().runTransaction(tr -> {
            DocumentSnapshot s = tr.get(t);
            Long round = s.getLong("round");
            if (s.exists() && expectedPhase.equals(s.getString("phase"))
                    && round != null && round.intValue() == expectedRound) {
                Map<String, Object> u = new HashMap<>(updates);
                u.put("phaseStartedAt", FieldValue.serverTimestamp());
                tr.update(t, u);
            }
            return null;
        });
    }

    public static void publishBets(String salonId, String playerId, int round, List<Bet> bets) {
        RoundBets rb = new RoundBets();
        rb.setRound(round);
        List<BetData> items = new ArrayList<>();
        for (Bet b : bets) items.add(BetData.from(b));
        rb.setItems(items);
        salons().document(salonId).collection("roulette_bets").document(playerId).set(rb);
    }

    // ---------- Écouteurs ----------

    private static List<SalonMember> toMembers(Iterable<DocumentSnapshot> docs) {
        List<SalonMember> list = new ArrayList<>();
        for (DocumentSnapshot d : docs) {
            SalonMember m = d.toObject(SalonMember.class);
            if (m != null) { m.setId(d.getId()); list.add(m); }
        }
        return list;
    }

    /** Tous les membres du salon (hall). */
    public static ListenerRegistration listenMembers(String salonId, MembersListener l) {
        return members(salonId).addSnapshotListener((snap, e) -> {
            if (e != null || snap == null) return;
            l.onMembers(toMembers(snap.getDocuments()));
        });
    }

    /** Joueurs assis à la table, du plus ancien au plus récent. */
    public static ListenerRegistration listenSeats(String salonId, MembersListener l) {
        return members(salonId).whereEqualTo("game", GAME_ROULETTE).addSnapshotListener((snap, e) -> {
            if (e != null || snap == null) return;
            List<SalonMember> list = toMembers(snap.getDocuments());
            Collections.sort(list, (a, b) -> {
                int c = Long.compare(a.getSeatSince(), b.getSeatSince());
                return c != 0 ? c : a.getId().compareTo(b.getId());
            });
            l.onMembers(list);
        });
    }

    public static ListenerRegistration listenTable(String salonId, TableListener l) {
        return table(salonId).addSnapshotListener((doc, e) -> {
            if (e != null) return;
            if (doc == null || !doc.exists()) { l.onTable(null); return; }
            TableState t = doc.toObject(TableState.class);
            l.onTable(t);
        });
    }

    public static ListenerRegistration listenPresence(String salonId, PresenceListener l) {
        return salons().document(salonId).collection("presence").addSnapshotListener((snap, e) -> {
            if (e != null || snap == null) return;
            Map<String, Long> beats = new HashMap<>();
            for (DocumentSnapshot d : snap.getDocuments()) {
                Long b = d.getLong("beat");
                if (b != null) beats.put(d.getId(), b);
            }
            l.onPresence(beats);
        });
    }

    public static ListenerRegistration listenBets(String salonId, BetsListener l) {
        return salons().document(salonId).collection("roulette_bets").addSnapshotListener((snap, e) -> {
            if (e != null || snap == null) return;
            Map<String, RoundBets> map = new LinkedHashMap<>();
            for (DocumentSnapshot d : snap.getDocuments()) {
                RoundBets rb = d.toObject(RoundBets.class);
                if (rb != null) map.put(d.getId(), rb);
            }
            l.onBets(map);
        });
    }
}