package de.simon.spacemod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import de.simon.spacemod.entity.FlyingShipEntity;
import de.simon.spacemod.flight.ShipPhysics;
import de.simon.spacemod.flight.ShipStructure;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/** Zeichnet die Blöcke eines fliegenden Schiffs gedreht um den Drehpunkt (nur außen sichtbare Blöcke). */
public class FlyingShipRenderer extends EntityRenderer<FlyingShipEntity, FlyingShipRenderer.State> {
    public static class State extends EntityRenderState {
        public Quaternionf rotation = new Quaternionf();
        public final List<MovingBlockRenderState> blocks = new ArrayList<>();
        public final List<BlockPos> local = new ArrayList<>();
    }

    public FlyingShipRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0F;
    }

    @Override
    public boolean shouldRender(FlyingShipEntity entity, Frustum culler, double camX, double camY, double camZ) {
        return entity.structure() != null && super.shouldRender(entity, culler, camX, camY, camZ);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(FlyingShipEntity entity, State state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        ShipStructure s = entity.structure();
        state.rotation = entity.rotation(partialTicks);
        state.local.clear();
        if (s == null) { state.blocks.clear(); return; }
        Vec3 base = entity.getPosition(partialTicks);
        int i = 0;
        for (ShipStructure.Entry e : s.surface) {
            if (e.state().getRenderShape() != RenderShape.MODEL) continue;
            MovingBlockRenderState b;
            if (i < state.blocks.size()) b = state.blocks.get(i);
            else { b = new MovingBlockRenderState(); state.blocks.add(b); }
            Vec3 w = ShipPhysics.rotate(state.rotation, e.pos().getX(), e.pos().getY(), e.pos().getZ()).add(base);
            BlockPos wp = BlockPos.containing(w);
            b.blockState = e.state();
            b.blockPos = wp;
            b.randomSeedPos = e.pos();
            if (entity.level() instanceof ClientLevel cl) {
                b.biome = cl.getBiome(wp);
                b.cardinalLighting = cl.cardinalLighting();
                b.lightEngine = cl.getLightEngine();
            }
            state.local.add(e.pos());
            i++;
        }
        while (state.blocks.size() > i) state.blocks.remove(state.blocks.size() - 1);
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        poseStack.pushPose();
        poseStack.mulPose(state.rotation);
        for (int i = 0; i < state.blocks.size(); i++) {
            BlockPos l = state.local.get(i);
            poseStack.pushPose();
            poseStack.translate(l.getX() - 0.5, l.getY() - 0.5, l.getZ() - 0.5);
            collector.submitMovingBlock(poseStack, state.blocks.get(i), state.outlineColor);
            poseStack.popPose();
        }
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }
}
