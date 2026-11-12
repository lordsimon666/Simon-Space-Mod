package de.simon.spacemod.reactor;

/** Kennzahlen des Reaktors je Kantenlänge n (3 bis 8). */
public final class ReactorMath {
    public static final int MIN = 3, MAX = 8;
    /** Leistung bei 8 x 8 x 8 (FE / Tick). */
    public static final int MAX_OUTPUT = 10_000;
    /** Ein Uranbarren reicht beim kleinsten Reaktor so viele Ticks (5 Minuten). */
    public static final int BASE_BURN = 6000;

    private ReactorMath() {}

    /** Leistung wächst mit dem Volumen: 10 000 FE/t bei 8³. */
    public static int output(int n) {
        if (n < MIN) return 0;
        double f = n / (double) MAX;
        return (int) Math.round(MAX_OUTPUT * f * f * f);
    }

    /** Je größer, desto schneller wird ein Barren verbraucht. */
    public static int burnTicks(int n) {
        if (n < MIN) return 0;
        double f = MIN / (double) n;
        return (int) Math.round(BASE_BURN * f * f);
    }

    /** Eingabeplätze: 1 beim kleinsten, 9 (3 x 3) beim größten Reaktor. */
    public static int slots(int n) {
        if (n < MIN) return 0;
        return 1 + (int) Math.round((n - MIN) * 8.0 / (MAX - MIN));
    }
}
