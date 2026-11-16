package de.simon.spacemod.storage;

/** Kurze Mengenangabe für große Stapel in der Großkiste. */
public final class CountFormat {
    private CountFormat() {}

    /** 1234 -> "1234", 12345 -> "12k", 1234567 -> "1.2M", 12345678 -> "12M". */
    public static String shortCount(int n) {
        if (n < 10_000) return String.valueOf(n);
        if (n < 1_000_000) return (n / 1000) + "k";
        if (n < 10_000_000) return String.format(java.util.Locale.ROOT, "%.1fM", Math.floor(n / 100_000.0) / 10);
        return (n / 1_000_000) + "M";
    }
}
