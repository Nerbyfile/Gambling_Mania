package com.example.my_application_sandbox;

import java.util.ArrayList;
import java.util.List;

public final class RouletteHistory {
    public static final int MAX = 10;                       // nombre de numéros gardés
    private static final List<Integer> SOLO = new ArrayList<>();

    private RouletteHistory() { }

    /** Plus récent en premier. */
    public static List<Integer> solo() { return SOLO; }

    public static void addSolo(int n) {
        SOLO.add(0, n);
        if (SOLO.size() > MAX) SOLO.remove(SOLO.size() - 1);
    }
}