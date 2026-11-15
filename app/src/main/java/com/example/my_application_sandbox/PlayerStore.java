package com.example.my_application_sandbox;

import android.content.Context;
import android.content.SharedPreferences;

/** Cache local du joueur, pour démarrer sans attendre le réseau. */
public final class PlayerStore {

    private static final String PREFS = "player_store";
    private static final String K_ID = "player_id";    // nouvelle clé : l'ancien id était un long
    private static final String K_NAME = "name";
    private static final String K_BANK = "bank";
    private static final String K_CREATED = "created_at";
    private static final String K_AVATAR = "avatar_id";

    private PlayerStore() { }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Retourne le joueur enregistré, ou null si aucun compte n'existe (première ouverture). */
    public static Player load(Context ctx) {
        SharedPreferences sp = prefs(ctx);
        if (!sp.contains(K_ID) || !sp.contains(K_NAME)) return null;
        Player p = new Player();
        p.setId(sp.getString(K_ID, null));
        p.setName(sp.getString(K_NAME, "Joueur"));
        p.setBank(sp.getInt(K_BANK, 0));
        p.setCreatedAt(sp.getLong(K_CREATED, 0L));
        p.setAvatarId(sp.getInt(K_AVATAR, 0));
        return p;
    }

    public static void save(Context ctx, Player p) {
        prefs(ctx).edit().putInt(K_AVATAR, p.getAvatarId())
                .putString(K_ID, p.getId())
                .putString(K_NAME, p.getName())
                .putInt(K_BANK, p.getBank())
                .putLong(K_CREATED, p.getCreatedAt())
                .apply();
    }

    public static void clear(Context ctx) {
        prefs(ctx).edit().clear().apply();
    }
}