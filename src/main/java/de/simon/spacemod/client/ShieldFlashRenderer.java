package de.simon.spacemod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import de.simon.spacemod.defense.ShieldFlashEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;

/** Zeichnet die getroffenen Schildblöcke als durchscheinende Würfel: blau, gelb (schwach), rot (fast zerstört). */
public class ShieldFlashRenderer extends EntityRenderer<ShieldFlashEntity, ShieldFlashRenderer.State> {
    public static class State extends EntityRenderState {
        public int[] cells;
        public float fade;
    }

    public ShieldFlashRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0F;
    }

    @Override
    public boolean shouldRender(ShieldFlashEntity entity, Frustum culler, double camX, double camY, double camZ) {
        return entity.cells != null;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(ShieldFlashEntity e, State s, float partialTicks) {
        super.extractRenderState(e, s, partialTicks);
        s.cells = e.cells;
        float age = e.tickCount + partialTicks;
        s.fade = Math.max(0, 1 - age / ShieldFlashEntity.LIFE);
    }

    @Override
    public void submit(State s, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        if (s.cells != null && s.fade > 0) {
            final int[] cells = s.cells;
            final float fade = s.fade;
            collector.submitCustomGeometry(pose, RenderTypes.lightning(), (p, buf) -> {
                for (int c : cells) {
                    float x = (c & 0x7F) - 64, y = ((c >> 7) & 0x7F) - 64, z = ((c >> 14) & 0x7F) - 64;
                    float hp = ((c >>> 21) & 0xFF) / 255F;
                    float r, g, b;
                    if (hp > 0.6F) { r = 0.35F; g = 0.65F; b = 1F; }
                    else if (hp > 0.25F) { r = 1F; g = 0.82F; b = 0.2F; }
                    else { r = 1F; g = 0.2F; b = 0.12F; }
                    float a = (hp <= 0 ? 0.15F : 0.4F) * fade;
                    cube(buf, p, x + 0.05F, y + 0.05F, z + 0.05F, x + 0.95F, y + 0.95F, z + 0.95F, r, g, b, a);
                }
            });
        }
        super.submit(s, pose, collector, camera);
    }

    private static void quad(VertexConsumer buf, PoseStack.Pose p, float[][] v, float r, float g, float b, float a) {
        for (float[] q : v) buf.addVertex(p, q[0], q[1], q[2]).setColor(r, g, b, a);
    }

    private static void cube(VertexConsumer buf, PoseStack.Pose p, float x0, float y0, float z0, float x1, float y1, float z1,
                             float r, float g, float b, float a) {
        quad(buf, p, new float[][] {{x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}, {x0, y0, z1}}, r, g, b, a);
        quad(buf, p, new float[][] {{x0, y1, z0}, {x0, y1, z1}, {x1, y1, z1}, {x1, y1, z0}}, r, g, b, a);
        quad(buf, p, new float[][] {{x0, y0, z0}, {x0, y1, z0}, {x1, y1, z0}, {x1, y0, z0}}, r, g, b, a);
        quad(buf, p, new float[][] {{x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}, {x0, y1, z1}}, r, g, b, a);
        quad(buf, p, new float[][] {{x0, y0, z0}, {x0, y0, z1}, {x0, y1, z1}, {x0, y1, z0}}, r, g, b, a);
        quad(buf, p, new float[][] {{x1, y0, z0}, {x1, y1, z0}, {x1, y1, z1}, {x1, y0, z1}}, r, g, b, a);
    }
}
