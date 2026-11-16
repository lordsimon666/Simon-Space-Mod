package de.simon.spacemod.client.guide;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.gui.navigation.ScreenPosition;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.minecraft.core.BlockPos;
import de.simon.spacemod.guide.GuideStructure;

/**
 * Draws a structure of the guide book in 3D into its box on the page, with the real block models: turned by
 * {@code yaw} around the middle and tilted by {@code pitch} to be seen from above, one block {@code blockSize}
 * GUI pixels big, and moved by {@code panX} and {@code panY} GUI pixels. With {@code layers} below the
 * structure's height only the lowest layers are drawn.
 */
public class StructurePictureRenderer extends PictureInPictureRenderer<StructurePictureRenderer.State> {
    /** What to draw where. */
    public record State(GuideStructure structure, float yaw, float pitch, float blockSize, float panX, float panY, int layers,
                        int x0, int y0, int x1, int y1, ScreenRectangle scissorArea, ScreenRectangle bounds)
        implements PictureInPictureRenderState {
        public State(GuideStructure structure, float yaw, float pitch, float blockSize, float panX, float panY, int layers,
                     int x0, int y0, int x1, int y1) {
            this(structure, yaw, pitch, blockSize, panX, panY, layers, x0, y0, x1, y1, box(x0, y0, x1, y1),
                PictureInPictureRenderState.getBounds(x0, y0, x1, y1, box(x0, y0, x1, y1)));
        }

        private static ScreenRectangle box(int x0, int y0, int x1, int y1) {
            return new ScreenRectangle(new ScreenPosition(x0, y0), x1 - x0, y1 - y0);
        }

        @Override
        public float scale() {
            return this.blockSize;
        }
    }

    @Override
    public Class<State> getRenderStateClass() {
        return State.class;
    }

    @Override
    protected void renderToTexture(State state, PoseStack poseStack, SubmitNodeCollector collector) {
        GuideStructure structure = state.structure();
        // The middle of the structure (of the layers shown) goes into the middle of the box
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        for (GuideStructure.Part part : structure.parts()) {
            BlockPos pos = part.pos();
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX() + 1);
            maxY = Math.max(maxY, Math.min(pos.getY() + 1, state.layers()));
            maxZ = Math.max(maxZ, pos.getZ() + 1);
        }
        // The picture starts at the bottom middle of the box (where an entity would stand): up to the middle,
        // then moved as far as the player dragged it
        poseStack.translate(state.panX() / state.blockSize(), (state.panY() - (state.y1() - state.y0()) / 2F) / state.blockSize(), 0);
        // The picture's y goes down the screen, like in the inventory's player picture: turned over, world up is up
        poseStack.mulPose(Axis.ZP.rotation((float) Math.PI));
        poseStack.mulPose(Axis.XP.rotationDegrees(-state.pitch()));
        poseStack.mulPose(Axis.YP.rotationDegrees(state.yaw()));
        poseStack.translate(-(minX + maxX) / 2, -(minY + maxY) / 2, -(minZ + maxZ) / 2);
        for (GuideStructure.Part part : structure.parts()) {
            BlockPos pos = part.pos();
            if (pos.getY() >= state.layers())
                continue;
            poseStack.pushPose();
            poseStack.translate(pos.getX(), pos.getY(), pos.getZ());
            MovingBlockRenderState block = new MovingBlockRenderState();
            block.blockState = part.state().get();
            collector.submitMovingBlock(poseStack, block, 0);
            poseStack.popPose();
        }
    }

    @Override
    protected String getTextureLabel() {
        return "simonspacemod guide structure";
    }
}
