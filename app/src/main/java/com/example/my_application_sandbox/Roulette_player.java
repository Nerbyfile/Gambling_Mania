package com.example.my_application_sandbox;

import com.example.my_application_sandbox.Player;
import com.example.my_application_sandbox.RouletteTouchHelper.Bet;
import com.example.my_application_sandbox.RouletteTouchHelper.Kind;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

public class Roulette_player {

    // ---- Session unique partagée entre les activities (= Singleton) ----
    private static Roulette_player instance;

    /** Crée un nouveau joueur (à appeler à l'entrée dans le jeu). */
    public static Roulette_player newSession(Player player) {
        Player.setCurrent(player);
        instance = new Roulette_player(player);
        return instance;
    }

    /** Récupère le joueur courant (en crée un par sécurité s'il n'existe pas). */
    public static Roulette_player getInstance() {
        Player p = Player.current();
        if (instance == null || instance.player != p) instance = new Roulette_player(p);
        return instance;
    }

    // ---- Données du joueur ----
    private final Player player;                                 // joueur général (id, nom, banque)
    private final List<Bet> bets = new ArrayList<>();            // paris en cours (mises fusionnées)
    private final Deque<Placement> history = new ArrayDeque<>(); // pour annuler le dernier jeton posé

    private static final class Placement {
        final Bet bet;
        final int amount;
        Placement(Bet bet, int amount) { this.bet = bet; this.amount = amount; }
    }
    public Roulette_player(Player player) {
        this.player = player;
    }

    // ---- Accesseurs ----
    public Player getPlayer() { return player; }
    public int getBank() { return player.getBank(); }
    public List<Bet> getBets() { return Collections.unmodifiableList(bets); }

    public int getTotalStake() {
        int total = 0;
        for (Bet b : bets) total += b.Mise;
        return total;
    }


    // ---- Paris ----

    /** Pose "amount" sur ce pari. Retourne false si la mise est invalide ou le solde insuffisant. */
    public boolean placeBet(Bet bet, int amount) {
        if (!player.debit(amount)) return false;

        Bet existing = null;
        for (Bet b : bets) {
            if (b.equals(bet)) { existing = b; break; }
        }
        if (existing != null) {
            existing.Mise += amount;           // même case : on cumule
            history.push(new Placement(existing, amount));
        } else {
            bet.Mise = amount;
            bets.add(bet);
            history.push(new Placement(bet, amount));
        }
        return true;
    }

    /** Annule le dernier jeton posé et rembourse. */
    public void removeBet() {
        Placement last = history.poll();
        if (last == null) return;
        last.bet.Mise -= last.amount;
        player.credit(last.amount);
        if (last.bet.Mise <= 0) bets.remove(last.bet);
    }

    /** Retire tous les paris et rembourse. */
    public void removeAllBets() {
        player.credit(getTotalStake());
        bets.clear();
        history.clear();
    }

    // ---- Gains ----

    /** Multiplicateur de gain (hors mise) de chaque type de pari. */
    public static int payoutMultiplier(Kind kind) {
        switch (kind) {
            case PLEIN:         return 35;
            case CHEVAL:        return 17;
            case TRIO:
            case LIGNE:         return 11;
            case CARRE:         return 8;
            case SIXAIN:        return 5;
            case DOUZAINE:
            case COLONNE:       return 2;
            case CHANCE_SIMPLE: return 1;
        }
        return 0;
    }

    /** Somme rendue au joueur (mise + gain) pour ce numéro, sans modifier l'état. */
    public int calcGain(int result) {
        int total = 0;
        for (Bet bet : bets) {
            if (bet.numbers.contains(result)) {
                total += bet.Mise * (payoutMultiplier(bet.kind) + 1);
            }
        }
        return total;
    }

    /** Règle le tour : crédite les gains, vide les paris. Retourne la somme créditée. */
    public int settle(int result) {
        int payout = calcGain(result);
        player.credit(payout);
        bets.clear();
        history.clear();
        return payout;
    }
}