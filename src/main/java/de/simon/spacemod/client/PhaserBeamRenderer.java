package de.simon.spacemod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import de.simon.spacemod.entity.PhaserBeamEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Zeichnet den Phaserstrahl mit der Leuchtfeuer-Strahltextur: farbiger Mantel, heller Kern. */
public class PhaserBeamRenderer extends EntityRenderer<PhaserBeamEntity, PhaserBeamRenderer.State> {
    public static class State extends EntityRenderState {
        public Vec3 dir = Vec3.ZERO;
        public float length, width, time;
        public int color, core;
        public boolean visible;
    }

    public PhaserBeamRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0F;
    }

    @Override
    public boolean shouldRender(PhaserBeamEntity entity, Frustum culler, double camX, double camY, double camZ) {
        return true; // lange Strahlen reichen weit über den eigenen Kasten hinaus
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(PhaserBeamEntity e, State s, float partialTicks) {
        super.extractRenderState(e, s, partialTicks);
        Vec3 d = e.to().subtract(e.getPosition(partialTicks));
        s.length = (float) d.length();
        s.dir = s.length > 1e-3 ? d.scale(1 / s.length) : new Vec3(0, 1, 0);
        s.width = e.width();
        s.color = e.color();
        s.core = e.core();
        s.time = e.tickCount + partialTicks;
        // Pulswaffen (Phasenpistole/-kanone der NX-01): Strahl blinkt
        s.visible = !e.pulse() || ((int) (s.time / 3)) % 2 == 0;
    }

    @Override
    public void submit(State s, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        if (s.visible && s.length > 0.05F) {
            pose.pushPose();
            // +Y des Strahls auf die Schussrichtung drehen
            pose.mulPose(new Quaternionf().rotationTo(new Vector3f(0, 1, 0), new Vector3f((float) s.dir.x, (float) s.dir.y, (float) s.dir.z)));
            int h = Math.max(1, (int) Math.ceil(s.length));
            pose.scale(1F, s.length / h, 1F);
            pose.translate(-0.5F, 0F, -0.5F);
            BeaconRenderer.submitBeaconBeam(pose, collector, BeaconRenderer.BEAM_LOCATION, 1F, s.time, 0, h, s.color, s.width, s.width * 1.6F);
            BeaconRenderer.submitBeaconBeam(pose, collector, BeaconRenderer.BEAM_LOCATION, 1F, s.time, 0, h, s.core, s.width * 0.45F, s.width * 0.6F);
            pose.popPose();
        }
        super.submit(s, pose, collector, camera);
    }
}
