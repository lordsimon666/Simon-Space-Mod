package de.simon.spacemod.entity;

/**
 * Aussehen und Stärke der Torpedos je Schiff/Epoche. Neuere Torpedos sind stärker und schneller,
 * und jeder Torpedo ist stärker als die Phaser seines Schiffs auf Stufe 3.
 * Farben nach den Filmen/Serien: Photonentorpedos leuchten rot bis rot-orange, Quantentorpedos blau-weiß.
 */
public enum TorpedoStyle {
    /** NX-01: frühe Photonik-Torpedos – klein, gelb-orange, schwach. */
    PHOTONIC_NX(0xFFA030, 0xFFF0C0, 1.8F, 1.6, 4.8F),
    /** NCC-1701: rot-rosa pulsierender Lichtball ohne Schweif (wie in der Originalserie). */
    PHOTON_TOS(0xFF3060, 0xFFC0D0, 2.6F, 1.8, 5.5F),
    /** NCC-1701-A: roter Glutball mit Strahlenkranz (Kinofilme). */
    PHOTON_MOVIE_A(0xFF3A18, 0xFFD8A0, 2.8F, 2.0, 6.0F),
    /** NCC-1701-B: wie A, etwas stärker. */
    PHOTON_MOVIE_B(0xFF3A18, 0xFFD8A0, 2.9F, 2.0, 6.5F),
    /** NCC-1701-C: rot-orange mit Leuchtschweif (Next Generation). */
    PHOTON_TNG_C(0xFF5A1A, 0xFFE8C0, 3.0F, 2.2, 7.0F),
    /** NCC-1701-D: rot-orange mit langem Leuchtschweif. */
    PHOTON_TNG_D(0xFF5A1A, 0xFFE8C0, 3.0F, 2.2, 7.5F),
    /** NCC-1701-E: Photonentorpedo der 2370er – kräftiges Orange. */
    PHOTON_E(0xFF6A20, 0xFFF4D8, 3.2F, 2.4, 8.0F),
    /** NCC-1701-E: Quantentorpedo – großer blau-weißer Ball, am stärksten. */
    QUANTUM(0x5AA8FF, 0xF0F8FF, 3.8F, 2.8, 11.0F);

    public final int glow, core;
    public final float size;
    public final double speed;
    public final float radius;

    TorpedoStyle(int glow, int core, float size, double speed, float radius) {
        this.glow = glow;
        this.core = core;
        this.size = size;
        this.speed = speed;
        this.radius = radius;
    }

    /** @param tier Schiff: 1 = NX-01 ... 7 = Enterprise-E */
    public static TorpedoStyle photon(int tier) {
        return switch (Math.max(1, Math.min(7, tier))) {
            case 1 -> PHOTONIC_NX;
            case 2 -> PHOTON_TOS;
            case 3 -> PHOTON_MOVIE_A;
            case 4 -> PHOTON_MOVIE_B;
            case 5 -> PHOTON_TNG_C;
            case 6 -> PHOTON_TNG_D;
            default -> PHOTON_E;
        };
    }
}
