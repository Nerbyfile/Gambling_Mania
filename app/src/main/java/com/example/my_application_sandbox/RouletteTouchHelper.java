package com.example.my_application_sandbox;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

public class RouletteTouchHelper {

    /** --> Start of Type definition <-- **/

    /*
    Kind : Define the kind of bet being placed to allow gain calculation
    Zone : Null if not a special cells, witch special cell is selected to highlight and witch number it correspond to.
    Bet  : Contain the kind of bet, the zone (if special zone), and the numbers selected

     */

    public enum Kind { PLEIN, CHEVAL, TRIO, CARRE, LIGNE, SIXAIN , DOUZAINE, COLONNE, CHANCE_SIMPLE }
    public enum Zone {
        MANQUE(Kind.CHANCE_SIMPLE), PAIR(Kind.CHANCE_SIMPLE), ROUGE(Kind.CHANCE_SIMPLE),
        NOIR(Kind.CHANCE_SIMPLE), IMPAIR(Kind.CHANCE_SIMPLE), PASSE(Kind.CHANCE_SIMPLE),
        DOUZAINE_1(Kind.DOUZAINE), DOUZAINE_2(Kind.DOUZAINE), DOUZAINE_3(Kind.DOUZAINE),
        COLONNE_1(Kind.COLONNE), COLONNE_2(Kind.COLONNE), COLONNE_3(Kind.COLONNE);


        public final Kind kind;
        Zone(Kind kind) { this.kind = kind; }
        private static final Set<Integer> ROUGES = new HashSet<>(Arrays.asList(
                1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36));
        public boolean contient(int n) {
            switch (this) {
                case MANQUE:     return n >= 1 && n <= 18;
                case PASSE:      return n >= 19 && n <= 36;
                case PAIR:       return n % 2 == 0;
                case IMPAIR:     return n % 2 == 1;
                case ROUGE:      return ROUGES.contains(n);
                case NOIR:       return !ROUGES.contains(n);
                case DOUZAINE_1: return n <= 12;
                case DOUZAINE_2: return n >= 13 && n <= 24;
                case DOUZAINE_3: return n >= 25;
                case COLONNE_1:  return n % 3 == 1;
                case COLONNE_2:  return n % 3 == 2;
                case COLONNE_3:  return n % 3 == 0;
            }
            return false;
        }

        /** Tous les numéros (1-36) couverts par cette zone. */
        public Set<Integer> numeros() {
            Set<Integer> s = new TreeSet<>();
            for (int n = 1; n <= 36; n++) if (contient(n)) s.add(n);
            return s;
        }
    }
    public static final class Bet {
        public final Kind kind;
        public final Set<Integer> numbers;  // numéros couverts (pour la surbrillance et les gains)
        public final Zone zone;             // null pour plein / cheval / trio / carré


        private Bet(Kind kind, Set<Integer> numbers, Zone zone) {
            this.kind = kind;
            this.numbers = numbers;
            this.zone = zone;
        }

        /** Define type of bet depending on the amount of number in Set */
        static Bet ofNumbers(Set<Integer> numbers) {
            Kind k;
            switch (numbers.size()) {
                case 1:
                    k = Kind.PLEIN;
                    break;
                case 2:
                    k = Kind.CHEVAL;
                    break;
                case 3:
                    k = numbers.contains(0) ? Kind.TRIO : Kind.LIGNE;
                    break;  // trio = avec le 0
                default:
                    k = Kind.CARRE;
                    break;
            }
            return new Bet(k, numbers, null);
        }

        /** Pari "ligne" : 3 numéros (ligne), 6 (sixain) ou 4 (0-1-2-3).    relly usefull ?     */
        static Bet ofLine(Set<Integer> numbers) {
            Kind k;
            switch (numbers.size()) {
                case 3:
                    k = Kind.LIGNE;
                    break;
                case 6:
                    k = Kind.SIXAIN;
                    break;
                default:
                    k = Kind.CARRE;
                    break;   // 0-1-2-3
            }
            return new Bet(k, numbers, null);
        }

        static Bet ofZone(Zone z) {
            return new Bet(z.kind, z.numeros(), z);
        }

        // equals/hashCode : permet de comparer deux Bet (changement de sélection)
        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Bet)) return false;
            Bet b = (Bet) o;
            return zone == b.zone && numbers.equals(b.numbers);
        }

        @Override
        public int hashCode() {
            return Objects.hash(zone, numbers);
        }
    }

    /** --> End of type definition <-- **/

    private float originX;
    private float originY;
    private float cellH;  // hauteur de la ligne du 0 (px)
    private float cellW;   // largeur d'une colonne (px)
    private final float edge;   // part de la case réservée aux bords (0.25 - 0.35)

    private static final Zone[] SIMPLES   = {Zone.MANQUE, Zone.PAIR, Zone.ROUGE,
            Zone.NOIR, Zone.IMPAIR, Zone.PASSE};
    private static final Zone[] DOUZAINES = {Zone.DOUZAINE_1, Zone.DOUZAINE_2, Zone.DOUZAINE_3};
    private static final Zone[] COLONNES  = {Zone.COLONNE_1, Zone.COLONNE_2, Zone.COLONNE_3};


    public RouletteTouchHelper(float originX, float originY, float cellH, float cellW, float edge) {
        this.originX = originX;
        this.originY = originY;
        this.cellH    = cellH;
        this.cellW    = cellW;
        this.edge    = edge;
    }

    public void updateMetrics(float originX, float originY, float cellH, float cellW) {
        this.originX = originX;
        this.originY = originY;
        this.cellH = cellH;
        this.cellW = cellW;
    }

    /** Translate x/y position into one or more cell */
    public Bet resolve(float x, float y) {
        x -= originX;
        y -= originY;
        float originX_numbers = 2*cellW;


        if (x < 0 || x >= cellW * 5 || y < 0 || y > cellH * 14) return null;                       //Outside table
        int col  = (int) (x / cellW);                                                               //Witch column is selected
        float fx = x / cellW - col;                                                                 //Where in the column

        int row  = (int) (y / cellH);                                                               //Witch row is selected
        float fy = y / cellH - row;                                                                 //Where in the row

        if ((row == 0 && col < 2) || (row == 13 && col < 2)) return null;                           //White zone

        // --- 0. Bande "ligne" : de part et d'autre de la frontière douzaines / numéros
        if (Math.abs(x - originX_numbers) < edge * cellW && row <= 12) {
            Bet line = resolveLine(row, fy);
            if (line != null) return line;
        }

        // --- 1. Cases spéciales à gauche : la case entière = la zone, pas de bords
        if (x < cellW) {
            if (row < 1 || row > 12) return null;
            return Bet.ofZone(SIMPLES[(row - 1) / 2]);                                              // 1 cell = 2 row merged
        }
        if (x < originX_numbers) {
            if (row < 1 || row > 12) return null;
            return Bet.ofZone(DOUZAINES[(row - 1) / 4]);                                            // 1 cell = 4 row merged
        }

        // --- 2. Zone des colonnes
        if (row == 13) return Bet.ofZone(COLONNES[col - 2]);  // colonnes 2:1

        // --- 3. Zone des numéros
        row = row - 1;                                // -1 = zéro, 0..11 = numbers
        col = col - 2;                                // 0..2 = colonnes de numéros

        // Près d'un bord : on ajoute la case voisine
        Set<Integer> cols = new LinkedHashSet<>();
        cols.add(col);
        if (fx < edge && col > 0) cols.add(col - 1);                                                // left cell
        if (fx > 1 - edge && col < 2) cols.add(col + 1);                                            // right cell

        Set<Integer> rows = new LinkedHashSet<>();
        rows.add(row);
        if (fy < edge && row >= 0) rows.add(row - 1);                                               // top cell (-1 = zero)
        if (fy > 1 - edge && row >= -1 && row <= 10) rows.add(row + 1);                             // bottom cell

        Set<Integer> numbers = new LinkedHashSet<>();
        for (int r : rows) {
            for (int c : cols) {
                numbers.add(r < 0 ? 0 : r * 3 + c +1);                                             // if -1 -> 0 else it count the correct number
            }
        }

        return Bet.ofNumbers(numbers);


    }
    private Bet resolveLine(int row, float fy) {
        row -= 1;                                                                                   // -1 = zéro
        Set<Integer> rows = new LinkedHashSet<>();                                                  // Selected rows

        if (row < 0) {                                                                              // if zow 0
            if (fy <= 1 - edge) return null;                                                        // ignore top edge
            rows.add(-1);
            rows.add(0);
        } else {
            rows.add(row);
            if (fy < edge) rows.add(row - 1);                                                       // edge of top row
            if (fy > 1 - edge && row <= 10) rows.add(row + 1);                                      // edge of bottom row
        }

        Set<Integer> numbers = new LinkedHashSet<>();
        for (int r : rows) {                                                                        // 1 or 2 row
            if (r < 0) {
                numbers.add(0);
            } else {
                for (int c = 0; c < 3; c++) numbers.add(r * 3 + c + 1);
            }
        }
        return Bet.ofLine(numbers);
    }
}
