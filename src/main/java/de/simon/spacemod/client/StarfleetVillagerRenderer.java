package de.simon.spacemod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import de.simon.spacemod.village.StarfleetCrew;
import de.simon.spacemod.village.StarfleetVillagers;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.VillagerRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.npc.villager.Villager;

/**
 * Dorfbewohner wie gewohnt – außer Sternenflotten-Dorfbewohnern: die sehen aus wie Spieler, mit der Haut der
 * Figur, deren Namen sie tragen, und ihrer (zufälligen) Uniform.
 */
public class StarfleetVillagerRenderer extends VillagerRenderer {
    public static class State extends VillagerRenderState {
        boolean starfleet;
        final Crew crewState = new Crew();
    }

    static class Crew extends HumanoidRenderState {
        Identifier texture;
    }

    private final Human human;

    public StarfleetVillagerRenderer(EntityRendererProvider.Context context) {
        super(context);
        human = new Human(context);
    }

    @Override
    public VillagerRenderState createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(Villager entity, VillagerRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        State s = (State) state;
        s.starfleet = entity.getVillagerData().profession().is(StarfleetVillagers.STARFLEET_KEY) && !entity.isBaby();
        if (!s.starfleet) return;
        human.extractRenderState(entity, s.crewState, partialTicks);
        String name = entity.hasCustomName() ? entity.getCustomName().getString() : "";
        s.crewState.texture = CrewSkins.texture(StarfleetCrew.byName(name, entity.getUUID()).skin(), StarfleetCrew.uniform(entity.getUUID()));
    }

    @Override
    public void submit(VillagerRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state instanceof State s && s.starfleet) human.submit(s.crewState, poseStack, collector, camera);
        else super.submit(state, poseStack, collector, camera);
    }

    /** Spielermodell für Dorfbewohner. */
    static class Human extends HumanoidMobRenderer<Villager, Crew, HumanoidModel<Crew>> {
        Human(EntityRendererProvider.Context context) {
            super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.5F);
        }

        @Override
        public Crew createRenderState() {
            return new Crew();
        }

        @Override
        public Identifier getTextureLocation(Crew state) {
            return state.texture;
        }
    }
}
