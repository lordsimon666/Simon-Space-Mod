package de.simon.spacemod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import de.simon.spacemod.block.ChargingStationBlock;
import de.simon.spacemod.block.PhaserHolderBlock;
import de.simon.spacemod.block.RifleRackBlock;
import de.simon.spacemod.block.entity.WeaponHolderBlockEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Zeigt die Waffen in Gewehrständer (4 Gewehre aufrecht nebeneinander), Phaserhalter (2 x 2 an der
 * Wand) und auf der Ladestation (liegend).
 */
public class WeaponHolderRenderer implements BlockEntityRenderer<WeaponHolderBlockEntity, WeaponHolderRenderer.State> {
    public static class State extends BlockEntityRenderState {
        public @Nullable ItemStackRenderState[] items = new ItemStackRenderState[0];
        public Direction facing = Direction.NORTH;
        public int kind; // 0 = Ständer, 1 = Wandhalter, 2 = Ladestation
        public boolean rifle; // Ladestation: Gewehr hängt außen an der Seite
        public boolean rifleLeft; // rechts ist ein Block im Weg -> links aufhängen
    }

    private final ItemModelResolver resolver;

    public WeaponHolderRenderer(BlockEntityRendererProvider.Context context) {
        this.resolver = context.itemModelResolver();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(WeaponHolderBlockEntity be, State state, float partialTicks, Vec3 camera,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(be, state, partialTicks, camera, breakProgress);
        var bs = be.getBlockState();
        state.facing = bs.hasProperty(HorizontalDirectionalBlock.FACING) ? bs.getValue(HorizontalDirectionalBlock.FACING) : Direction.NORTH;
        state.kind = bs.getBlock() instanceof RifleRackBlock ? 0 : bs.getBlock() instanceof PhaserHolderBlock ? 1 : 2;
        var items = be.items();
        state.items = new ItemStackRenderState[items.size()];
        state.rifle = !items.isEmpty() && items.get(0).getItem() instanceof de.simon.spacemod.item.HandWeaponItem w && w.rifle;
        if (state.rifle && be.getLevel() != null) {
            // rechts (vom Betrachter aus) bevorzugt; ist dort ein Block, hängt das Gewehr links
            var lvl = be.getLevel();
            var right = be.getBlockPos().relative(state.facing.getCounterClockWise());
            var left = be.getBlockPos().relative(state.facing.getClockWise());
            state.rifleLeft = !lvl.getBlockState(right).getCollisionShape(lvl, right).isEmpty()
                && lvl.getBlockState(left).getCollisionShape(lvl, left).isEmpty();
        }
        int seed = (int) be.getBlockPos().asLong();
        for (int i = 0; i < items.size(); i++) {
            ItemStack stack = items.get(i);
            if (stack.isEmpty()) continue;
            ItemStackRenderState rs = new ItemStackRenderState();
            resolver.updateForTopItem(rs, stack, ItemDisplayContext.ON_SHELF, be.getLevel(), null, seed + i);
            state.items[i] = rs;
        }
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        for (int i = 0; i < state.items.length; i++) {
            ItemStackRenderState rs = state.items[i];
            if (rs == null) continue;
            pose.pushPose();
            pose.translate(0.5F, 0.5F, 0.5F);
            pose.mulPose(Axis.YP.rotationDegrees(-state.facing.toYRot())); // lokal +z = Vorderseite
            switch (state.kind) {
                case 0 -> { // aufrecht nebeneinander, schmale Seite nach vorne, Lauf nach oben
                    pose.translate(-0.375F + 0.25F * i, 0.45F, -0.05F);
                    pose.mulPose(Axis.YP.rotationDegrees(90));
                    pose.mulPose(Axis.ZP.rotationDegrees(90));
                    pose.scale(0.9F, 0.9F, 0.9F);
                }
                case 1 -> { // 2 x 2 flach an der Wand, Seitenansicht nach vorne
                    pose.translate((i % 2 == 0 ? -0.22F : 0.22F), (i < 2 ? 0.22F : -0.2F), 0.08F);
                    pose.scale(0.42F, 0.42F, 0.42F);
                }
                default -> {
                    if (state.rifle) { // Gewehr hängt mittig außen an der rechten Seite, ragt oben und unten über
                        pose.translate(state.rifleLeft ? -0.43F : 0.43F, 0.0F, -0.05F);
                        pose.mulPose(Axis.YP.rotationDegrees(90));
                        pose.mulPose(Axis.ZP.rotationDegrees(90));
                        pose.scale(0.85F, 0.85F, 0.85F);
                    } else { // Phaser steht in der Halterung hinter der Glasfront, Seite nach vorne
                        pose.translate(0F, -0.1F, 0.02F);
                        pose.mulPose(Axis.YP.rotationDegrees(90)); // Seitenansicht zur Glasfront
                        pose.scale(0.7F, 0.7F, 0.7F);
                    }
                }
            }
            rs.submit(pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
            pose.popPose();
        }
    }

    @Override
    public boolean shouldRenderOffScreen() {
        return true; // Gewehre ragen in den oberen Block des Ständers
    }
}
