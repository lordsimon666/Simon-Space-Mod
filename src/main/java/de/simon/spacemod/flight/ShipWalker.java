package de.simon.spacemod.flight;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Laufen im fliegenden Schiff: Bewegung im Schiffssystem (künstliche Schwerkraft zum Schiffsboden),
 * Kollision gegen die Blöcke des Schiffs, Springen, Stufen bis 0,6 Blöcke.
 */
public final class ShipWalker {
    public record State(Vec3 pos, Vec3 vel, boolean onGround) {}

    private static final double WIDTH = 0.6, HEIGHT = 1.8, STEP = 0.6;
    private static final double WALK = 0.13, SPRINT = 0.17, AIR = 0.05, GRAVITY = 0.08, JUMP = 0.42;

    private ShipWalker() {}

    /**
     * @param strafe   Eingabe links (+) / rechts (-)
     * @param forward  Eingabe vor (+) / zurück (-)
     * @param localYaw Blickrichtung des Spielers im Schiffssystem (Grad)
     */
    public static State step(ShipStructure s, State st, float strafe, float forward, float localYaw, boolean jump, boolean sprint) {
        return step(s.collision(), st, strafe, forward, localYaw, jump, sprint);
    }

    public static State step(java.util.Map<BlockPos, VoxelShape> s, State st, float strafe, float forward, float localYaw, boolean jump, boolean sprint) {
        // Eingabe -> waagerechte Geschwindigkeit (wie Minecraft: links/vor, nach Blickrichtung gedreht)
        double len = Math.sqrt(strafe * strafe + forward * forward);
        double sx = strafe, sz = forward;
        if (len > 1) { sx /= len; sz /= len; }
        double speed = st.onGround() ? (sprint ? SPRINT : WALK) : AIR;
        float sin = Mth.sin(localYaw * Mth.DEG_TO_RAD), cos = Mth.cos(localYaw * Mth.DEG_TO_RAD);
        double vx = (sx * cos - sz * sin) * speed, vz = (sz * cos + sx * sin) * speed;
        if (!st.onGround()) { vx = st.vel().x * 0.91 + vx; vz = st.vel().z * 0.91 + vz; }
        double vy = (st.vel().y - GRAVITY) * 0.98;
        if (jump && st.onGround()) vy = JUMP;

        AABB box = box(st.pos());
        Vec3 want = new Vec3(vx, vy, vz);
        Vec3 moved = collide(s, box, want);
        // Stufe hochgehen
        if (st.onGround() && (moved.x != want.x || moved.z != want.z)) {
            Vec3 up = collide(s, box, new Vec3(0, STEP, 0));
            Vec3 side = collide(s, box.move(up), new Vec3(want.x, 0, want.z));
            Vec3 down = collide(s, box.move(up).move(side), new Vec3(0, -STEP - 0.01, 0));
            Vec3 stepped = up.add(side).add(down);
            if (stepped.x * stepped.x + stepped.z * stepped.z > moved.x * moved.x + moved.z * moved.z + 1e-6) moved = stepped;
        }
        boolean hitY = moved.y != want.y;
        boolean onGround = hitY && want.y < 0;
        Vec3 pos = st.pos().add(moved);
        Vec3 vel = new Vec3(moved.x == want.x ? vx : 0, hitY ? 0 : vy, moved.z == want.z ? vz : 0);
        return new State(pos, vel, onGround);
    }

    public static AABB box(Vec3 feet) {
        return new AABB(feet.x - WIDTH / 2, feet.y, feet.z - WIDTH / 2, feet.x + WIDTH / 2, feet.y + HEIGHT, feet.z + WIDTH / 2);
    }

    /** Bewegung achsenweise gegen die Schiffsblöcke begrenzen (wie Minecraft: erst Y, dann X/Z). */
    static Vec3 collide(java.util.Map<BlockPos, VoxelShape> s, AABB box, Vec3 move) {
        List<VoxelShape> shapes = near(s, box.expandTowards(move).inflate(1));
        double y = Shapes.collide(Direction.Axis.Y, box, shapes, move.y);
        box = box.move(0, y, 0);
        boolean xFirst = Math.abs(move.x) >= Math.abs(move.z);
        double x, z;
        if (xFirst) {
            x = Shapes.collide(Direction.Axis.X, box, shapes, move.x);
            box = box.move(x, 0, 0);
            z = Shapes.collide(Direction.Axis.Z, box, shapes, move.z);
        } else {
            z = Shapes.collide(Direction.Axis.Z, box, shapes, move.z);
            box = box.move(0, 0, z);
            x = Shapes.collide(Direction.Axis.X, box, shapes, move.x);
        }
        return new Vec3(x, y, z);
    }

    private static List<VoxelShape> near(java.util.Map<BlockPos, VoxelShape> map, AABB area) {
        List<VoxelShape> out = new ArrayList<>();
        int x0 = Mth.floor(area.minX + 0.5), x1 = Mth.floor(area.maxX + 0.5);
        int y0 = Mth.floor(area.minY + 0.5), y1 = Mth.floor(area.maxY + 0.5);
        int z0 = Mth.floor(area.minZ + 0.5), z1 = Mth.floor(area.maxZ + 0.5);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int x = x0; x <= x1; x++)
            for (int y = y0; y <= y1; y++)
                for (int z = z0; z <= z1; z++) {
                    VoxelShape v = map.get(p.set(x, y, z));
                    if (v != null) out.add(v);
                }
        return out;
    }
}
