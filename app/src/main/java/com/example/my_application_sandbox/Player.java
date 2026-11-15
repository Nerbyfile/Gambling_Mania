package com.example.my_application_sandbox;

import java.util.Locale;

/**
 * Joueur générique, indépendant de tout jeu.
 * Modèle Firestore (constructeur sans argument + getters/setters publics).
 * Porte aussi le joueur courant, partagé entre tous les jeux.
 */
public class Player {

    // ---- Données (stockées dans Firestore) ----
    private String id;         // id du document Firestore ; null = pas encore créé
    private String name;
    private String nameLower;  // pseudo en minuscules : sert à retrouver un compte existant
    private int bank;
    private long createdAt;    // epoch ms

    private int avatarId;   // 0 à 7 (0 par défaut, y compris pour les anciens comptes)

    public int getAvatarId() { return avatarId; }
    public void setAvatarId(int avatarId) { this.avatarId = avatarId; }

    /** Constructeur sans argument : requis par Firestore (toObject). */
    public Player() { }

    /** Crée un nouveau joueur (id attribué plus tard par la base). */
    public static Player create(String name, int bank) {
        Player p = new Player();
        p.setName(name);   // setName initialise aussi nameLower
        p.bank = bank;
        p.createdAt = System.currentTimeMillis();
        return p;
    }

    // ---- Joueur courant, partagé entre les jeux ----
    private static Player current;

    /** Joueur courant (un invité est créé s'il n'y en a pas). */
    public static Player current() {
        if (current == null) current = create("Invité", 1000);
        return current;
    }

    /** Vrai si un joueur courant est déjà défini en mémoire. */
    public static boolean hasCurrent() {
        return current != null;
    }

    public static void setCurrent(Player player) {
        current = player;
    }

    // ---- Opérations sur la banque (utilisées par tous les jeux) ----

    public boolean canAfford(int amount) {
        return amount > 0 && amount <= bank;
    }

    /** Retire "amount" de la banque. Retourne false si le montant est invalide ou le solde insuffisant. */
    public boolean debit(int amount) {
        if (!canAfford(amount)) return false;
        bank -= amount;
        return true;
    }

    /** Ajoute "amount" à la banque. */
    public void credit(int amount) {
        if (amount > 0) bank += amount;
    }

    // ---- Getters / setters (les setters servent à Firestore) ----

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) {
        this.name = name;
        this.nameLower = (name == null) ? null : name.toLowerCase(Locale.ROOT);
    }

    public String getNameLower() { return nameLower; }
    public void setNameLower(String nameLower) { this.nameLower = nameLower; }

    public int getBank() { return bank; }
    public void setBank(int bank) { this.bank = bank; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}