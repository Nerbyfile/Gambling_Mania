package com.example.my_application_sandbox;
import android.os.Parcel;
import android.os.Parcelable;

/**
 * Joueur générique, indépendant de tout jeu.
 * Objet de données (champs privés + getters/setters, constructeur sans argument),
 * donc directement compatible avec Room ou une conversion vers SQLite.
 * Porte aussi le joueur courant, partagé entre tous les jeux.
 */
// Si tu utilises Room : @Entity(tableName = "players")
public class Player implements Parcelable {

    // ---- Données (stockées en base) ----

    // @PrimaryKey(autoGenerate = true)
    private long id;          // 0 = pas encore enregistré en base
    private String name;
    private int bank;         // solde du joueur
    private long createdAt;   // date de création (epoch ms)

    /** Constructeur sans argument : requis pour charger depuis une base de données. */
    public Player() { }

    /** Crée un nouveau joueur (id attribué plus tard par la base). */
    public static Player create(String name, int bank) {
        Player p = new Player();
        p.name = name;
        p.bank = bank;
        p.createdAt = System.currentTimeMillis();
        return p;
    }
    // Constructeur de dé-sérialisation (l'ordre de lecture doit être IDENTIQUE à l'écriture)
    protected Player(Parcel in) {
        name = in.readString();
        bank = in.readInt();
        createdAt = in.readLong();
    }

    // Écriture des données dans le Parcel
    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(name);
        dest.writeInt(bank);
        dest.writeLong(createdAt);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    // Générateur obligatoire pour Parcelable
    public static final Creator<Player> CREATOR = new Creator<Player>() {
        @Override
        public Player createFromParcel(Parcel in) {
            return new Player(in);
        }

        @Override
        public Player[] newArray(int size) {
            return new Player[size];
        }
    };

    // ---- Joueur courant, partagé entre les jeux (champ static : ignoré par Room) ----

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

    // ---- Getters / setters (les setters servent à la base de données) ----

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public int getBank() { return bank; }
    public void setBank(int bank) { this.bank = bank; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}