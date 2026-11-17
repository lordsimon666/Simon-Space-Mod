package de.simon.spacemod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import de.simon.spacemod.display.CameraBlock;
import de.simon.spacemod.display.DisplayBlockEntity;
import de.simon.spacemod.display.DisplayGroup;
import de.simon.spacemod.entity.FlyingShipEntity;
import de.simon.spacemod.flight.ShipPhysics;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Zeichnet das Kamerabild über die ganze Bildfläche (vom Ankerblock unten links aus, die übrigen Blöcke der
 * Fläche zeichnen nichts). Funktioniert auch auf fliegenden Schiffen: dort kommt die Lage der Kamera aus dem
 * Schiff ({@link #ship}, vom Schiffsrenderer gesetzt).
 */
public class DisplayRenderer implements BlockEntityRenderer<DisplayBlockEntity, DisplayRenderer.State> {
    /** Schiff, dessen Blöcke gerade gezeichnet werden (sonst null = Welt). */
    public static @Nullable FlyingShipEntity ship;

    public static class State extends BlockEntityRenderState {
        boolean anchor;
        Identifier tex;
        int w = 1, h = 1;
        Direction facing = Direction.NORTH, right = Direction.WEST;
        float u0, u1 = 1, v0, v1 = 1;
        boolean flipV;
    }

    public DisplayRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public boolean shouldRenderOffScreen() {
        return true; // große Bildflächen: auch zeichnen, wenn der Ankerblock außer Sicht ist
    }

    @Override
    public void extractRenderState(DisplayBlockEntity be, State state, float partial, Vec3 camera,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay crumbling) {
        BlockEntityRenderer.super.extractRenderState(be, state, partial, camera, crumbling);
        FlyingShipEntity s = ship;
        var structure = s != null ? s.structure() : null;
        java.util.function.Function<BlockPos, BlockState> states;
        if (s != null && structure != null) {
            java.util.Map<BlockPos, BlockState> map = shipStates(s);
            states = p -> map.getOrDefault(p, Blocks.AIR.defaultBlockState());
        } else if (be.getLevel() != null) states = be.getLevel()::getBlockState;
        else return;
        DisplayGroup g = DisplayGroup.of(states, be.getBlockPos());
        state.anchor = g.isAnchor(be.getBlockPos());
        state.w = g.width();
        state.h = g.height();
        state.facing = g.facing();
        state.right = g.right();
        if (!state.anchor) return;
        BlockPos cam = be.camera();
        CameraFeeds.Pose pose = null;
        if (cam != null) {
            if (s != null) {
                BlockPos local = cam.subtract(s.plotOrigin());
                BlockState cs = states.apply(local);
                if (cs.getBlock() instanceof CameraBlock) {
                    var q = s.rotation(partial);
                    Direction f = cs.getValue(DirectionalBlock.FACING);
                    Vec3 up = f.getAxis() == Direction.Axis.Y ? new Vec3(0, 0, -1) : new Vec3(0, 1, 0);
                    pose = new CameraFeeds.Pose(ShipPhysics.rotate(q, local.getX(), local.getY(), local.getZ()).add(s.getPosition(partial)),
                        ShipPhysics.rotate(q, f.getStepX(), f.getStepY(), f.getStepZ()), ShipPhysics.rotate(q, up.x, up.y, up.z));
                }
            } else {
                BlockState cs = be.getLevel().getBlockState(cam);
                if (cs.getBlock() instanceof CameraBlock) {
                    Direction f = cs.getValue(DirectionalBlock.FACING);
                    Vec3 up = f.getAxis() == Direction.Axis.Y ? new Vec3(0, 0, -1) : new Vec3(0, 1, 0);
                    pose = new CameraFeeds.Pose(Vec3.atCenterOf(cam), new Vec3(f.getStepX(), f.getStepY(), f.getStepZ()), up);
                }
            }
        }
        String key = (s != null ? "s" + s.getId() : "w" + be.getLevel().dimension().identifier()) + "@" + g.anchor().asLong();
        state.u0 = 0; state.u1 = 1; state.v0 = 0; state.v1 = 1;
        if (pose == null) {
            state.tex = CameraFeeds.noSignal();
            return;
        }
        // echtes Bild (falls möglich), auf das Seitenverhältnis der Bildfläche zugeschnitten
        Identifier real = RealFeeds.texture(key, pose);
        if (real != null) {
            state.tex = real;
            float a = RealFeeds.aspect(key), q = g.width() / (float) g.height();
            if (q < a) { float k = q / a; state.u0 = (1 - k) / 2; state.u1 = 1 - state.u0; }
            else { float k = a / q; state.v0 = (1 - k) / 2; state.v1 = 1 - state.v0; }
            state.flipV = true; // Bilder aus dem Renderziel stehen in OpenGL auf dem Kopf
        } else {
            state.tex = CameraFeeds.feed(key, g.width(), g.height(), pose);
            state.flipV = false;
        }
    }

    private static java.util.Map<BlockPos, BlockState> shipStates(FlyingShipEntity s) {
        java.util.Map<BlockPos, BlockState> map = SHIP_CACHE.get(s);
        if (map == null || map.size() != s.structure().blocks.size()) {
            map = new java.util.HashMap<>();
            for (var e : s.structure().blocks) map.put(e.pos(), e.state());
            SHIP_CACHE.put(s, map);
        }
        return map;
    }

    private static final java.util.Map<FlyingShipEntity, java.util.Map<BlockPos, BlockState>> SHIP_CACHE = new java.util.WeakHashMap<>();

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        if (!state.anchor || state.tex == null) return;
        Direction n = state.facing, r = state.right;
        // Ecke unten links der Vorderseite (vom Betrachter aus), leicht vor der Blockfläche
        float cx = 0.5F + n.getStepX() * 0.502F - r.getStepX() * 0.5F;
        float cy = 0.0F;
        float cz = 0.5F + n.getStepZ() * 0.502F - r.getStepZ() * 0.5F;
        float rx = r.getStepX() * state.w, rz = r.getStepZ() * state.w, h = state.h;
        float nx = n.getStepX(), nz = n.getStepZ();
        int light = 0xF000F0;
        float u0 = state.u0, u1 = state.u1;
        float vb = state.flipV ? state.v0 : state.v1, vt = state.flipV ? state.v1 : state.v0; // unten / oben
        collector.submitCustomGeometry(pose, RenderTypes.entitySolid(state.tex), (p, buf) -> {
            buf.addVertex(p, cx, cy, cz).setColor(255, 255, 255, 255).setUv(u0, vb).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, 0, nz);
            buf.addVertex(p, cx + rx, cy, cz + rz).setColor(255, 255, 255, 255).setUv(u1, vb).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, 0, nz);
            buf.addVertex(p, cx + rx, cy + h, cz + rz).setColor(255, 255, 255, 255).setUv(u1, vt).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, 0, nz);
            buf.addVertex(p, cx, cy + h, cz).setColor(255, 255, 255, 255).setUv(u0, vt).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, 0, nz);
        });
    }
}
