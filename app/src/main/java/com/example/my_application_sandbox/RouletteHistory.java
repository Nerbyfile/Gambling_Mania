package com.example.my_application_sandbox;

import java.util.ArrayList;
import java.util.List;

public final class RouletteHistory {
    public static final int MAX = 10;                       // nombre de numéros gardés
    private static final List<Integer> SOLO = new ArrayList<>();

    static { SOLO.addAll(randomPast(MAX)); }

    private RouletteHistory() { }

    /** Plus récent en premier. */
    public static List<Integer> solo() { return SOLO; }

    public static void addSolo(int n) {
        SOLO.add(0, n);
        if (SOLO.size() > MAX) SOLO.remove(SOLO.size() - 1);
    }

    /** Faux anciens tirages (plus récent en premier) pour qu'une table neuve ne soit pas vide. */
    public static List<Integer> randomPast(int count) {
        java.util.Random rnd = new java.util.Random();
        List<Integer> l = new ArrayList<>();
        for (int i = 0; i < count; i++) l.add(rnd.nextInt(37));
        return l;
    }
}