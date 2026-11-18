package de.simon.spacemod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import de.simon.spacemod.SimonSpaceMod;
import de.simon.spacemod.village.CrewFeatures;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

/** Zeichnet die 3D-Merkmale am Kopf (Ohren, Implantat, Frisur …) mit dem Kopf mit. */
public class CrewFeatureLayer extends RenderLayer<StarfleetVillagerRenderer.Crew, HumanoidModel<StarfleetVillagerRenderer.Crew>> {
    private final Map<String, ModelPart> parts = new HashMap<>();

    public CrewFeatureLayer(RenderLayerParent<StarfleetVillagerRenderer.Crew, HumanoidModel<StarfleetVillagerRenderer.Crew>> parent) {
        super(parent);
    }

    private ModelPart part(String skin) {
        return parts.computeIfAbsent(skin, s -> {
            List<CrewFeatures.Box> boxes = CrewFeatures.of(s);
            if (boxes.isEmpty()) return null;
            MeshDefinition mesh = new MeshDefinition();
            int i = 0;
            for (CrewFeatures.Box b : boxes)
                mesh.getRoot().addOrReplaceChild("f" + (i++), CubeListBuilder.create().texOffs(0, b.band() * 16)
                    .addBox(b.x(), b.y(), b.z(), b.w(), b.h(), b.d()), PartPose.ZERO);
            return LayerDefinition.create(mesh, 64, 64).bakeRoot();
        });
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, StarfleetVillagerRenderer.Crew state, float yRot, float xRot) {
        if (state.skin == null || state.isInvisible) return;
        ModelPart part = part(state.skin);
        if (part == null) return;
        poseStack.pushPose();
        getParentModel().root().translateAndRotate(poseStack);
        getParentModel().head.translateAndRotate(poseStack);
        Identifier tex = Identifier.fromNamespaceAndPath(SimonSpaceMod.MODID, "textures/entity/starfleet/features_" + state.skin + ".png");
        collector.submitModelPart(part, poseStack, RenderTypes.entityCutout(tex), light, LivingEntityRenderer.getOverlayCoords(state, 0), null);
        poseStack.popPose();
    }
}
