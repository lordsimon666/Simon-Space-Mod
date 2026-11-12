package de.simon.spacemod.defense;

import it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Laufendes Schild eines Schiffs: geschützter Raum als Bitraster, Leben je Schildblock (die äußere
 * Schicht). Unbeschädigte Blöcke teilen sich einen gemeinsamen Wert ({@link #bulk}), beschädigte
 * stehen einzeln in {@link #damaged}.
 */
public final class Shield {
    public final ServerLevel level;
    public final BlockPos console;
    public final BlockPos origin;
    public final ShieldShape shape;
    public final int tier;
    public final List<BlockPos> emitters;
    public final int cells;
    public final float maxHp;
    public final AABB box;
    float bulk;
    final Long2FloatOpenHashMap damaged = new Long2FloatOpenHashMap();
    /** Schild aktiv (eingeschaltet und mit Strom versorgt). */
    boolean up;

    Shield(ServerLevel level, BlockPos console, BlockPos origin, ShieldShape shape, int tier, List<BlockPos> emitters, int cells) {
        this.level = level;
        this.console = console;
        this.origin = origin;
        this.shape = shape;
        this.tier = tier;
        this.emitters = emitters;
        this.maxHp = ShieldEmitterBlock.maxHp(tier);
        this.bulk = maxHp;
        this.cells = cells;
        this.box = new AABB(Vec3.atLowerCornerOf(origin), Vec3.atLowerCornerOf(origin).add(shape.sx, shape.sy, shape.sz));
    }

    public boolean isUp() {
        return up;
    }

    public boolean contains(Vec3 v) {
        return shape.get((int) Math.floor(v.x) - origin.getX(), (int) Math.floor(v.y) - origin.getY(), (int) Math.floor(v.z) - origin.getZ());
    }

    public boolean shell(int wx, int wy, int wz) {
        return shape.surface(wx - origin.getX(), wy - origin.getY(), wz - origin.getZ());
    }

    /** Im geschützten Raum, aber nicht auf der Schildschicht selbst. */
    public boolean interior(Vec3 v) {
        int x = (int) Math.floor(v.x), y = (int) Math.floor(v.y), z = (int) Math.floor(v.z);
        return contains(v) && !shell(x, y, z);
    }

    public float hp(long key) {
        return damaged.containsKey(key) ? damaged.get(key) : bulk;
    }

    /** Nächster Schildblock (bis {@code reach}) oder null. */
    public @Nullable BlockPos nearestCell(Vec3 at, int reach) {
        int cx = (int) Math.floor(at.x), cy = (int) Math.floor(at.y), cz = (int) Math.floor(at.z);
        BlockPos best = null;
        double bd = Double.MAX_VALUE;
        for (int x = cx - reach; x <= cx + reach; x++)
            for (int y = cy - reach; y <= cy + reach; y++)
                for (int z = cz - reach; z <= cz + reach; z++) {
                    if (!shell(x, y, z)) continue;
                    double d = Vec3.atCenterOf(new BlockPos(x, y, z)).distanceToSqr(at);
                    if (d < bd) { bd = d; best = new BlockPos(x, y, z); }
                }
        return best;
    }

    /**
     * Schaden an den Schildblöcken um {@code at}. @return false, wenn der getroffene Block danach
     * kein Leben mehr hat (das Schild ist dort durchbrochen) oder es dort gar kein Schild gibt.
     */
    public boolean damage(Vec3 at, float amount, double radius) {
        BlockPos hit = nearestCell(at, (int) Math.ceil(radius) + 2);
        if (hit == null) return false;
        long hitKey = hit.asLong();
        if (hp(hitKey) <= 0) return false; // schon durchbrochen
        int r = (int) Math.ceil(radius);
        Vec3 c = Vec3.atCenterOf(hit);
        for (int x = hit.getX() - r; x <= hit.getX() + r; x++)
            for (int y = hit.getY() - r; y <= hit.getY() + r; y++)
                for (int z = hit.getZ() - r; z <= hit.getZ() + r; z++) {
                    if (!shell(x, y, z)) continue;
                    double d = Math.sqrt(Vec3.atCenterOf(new BlockPos(x, y, z)).distanceToSqr(c));
                    if (d > radius) continue;
                    long k = BlockPos.asLong(x, y, z);
                    float dmg = (float) (amount * Math.max(0.15, 1 - d / (radius + 1)));
                    damaged.put(k, Math.max(0, hp(k) - dmg));
                }
        Shields.flash(this, hit);
        return true;
    }

    /** Durchschnittliches Leben aller Schildblöcke (0..1). */
    public float average() {
        if (cells == 0) return 0;
        double sum = (double) bulk * (cells - damaged.size());
        for (float v : damaged.values()) sum += v;
        return (float) (sum / cells / maxHp);
    }

    public float weakest() {
        float m = bulk;
        for (float v : damaged.values()) m = Math.min(m, v);
        return m / maxHp;
    }

    /** Umverteilen: alle Schildblöcke auf den Durchschnitt setzen (Energie von starken zu schwachen). */
    public void rebalance() {
        bulk = average() * maxHp;
        damaged.clear();
    }

    /**
     * Regeneration mit dem vorhandenen Strom: höchstens 2 % des Lebens je Block und Sekunde – mehr
     * Strom hilft bis dahin, darüber nicht (so wird kein Schiff unverwundbar).
     * @param hp verfügbare Lebenspunkte (aus Strom) für diesen Schritt; @param seconds Zeitschritt
     * @return verbrauchte Lebenspunkte
     */
    public float regenerate(float hp, float seconds) {
        float cap = maxHp * 0.02F * seconds;
        float used = 0;
        if (!damaged.isEmpty()) {
            List<Long> full = new ArrayList<>();
            for (var e : damaged.long2FloatEntrySet()) {
                if (used >= hp) break;
                float add = Math.min(cap, Math.min(maxHp - e.getFloatValue(), hp - used));
                float v = e.getFloatValue() + add;
                used += add;
                if (v >= bulk) full.add(e.getLongKey());
                else e.setValue(v);
            }
            for (long k : full) damaged.remove(k);
        }
        if (bulk < maxHp && used < hp) {
            int n = Math.max(1, cells - damaged.size());
            float add = Math.min(cap, Math.min(maxHp - bulk, (hp - used) / n));
            bulk += add;
            used += add * n;
        }
        return used;
    }

    /** Strom für das Halten des Schildes je Tick. */
    public int upkeep() {
        return Math.max(200, (int) (cells * tier * 0.02));
    }
}
