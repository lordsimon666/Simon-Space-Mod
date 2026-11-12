package de.simon.spacemod;

import de.simon.spacemod.command.EnterpriseCommand;
import de.simon.spacemod.ship.ShipBuildManager;
import de.simon.spacemod.turbolift.TurboliftNetwork;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLLoader;

/** Simon Space Mod – bringt die USS Enterprise NCC-1701 nach Minecraft. */
@Mod(SimonSpaceMod.MODID)
public final class SimonSpaceMod {
    public static final String MODID = "simonspacemod";

    public SimonSpaceMod(FMLJavaModLoadingContext context) {
        var modBus = context.getModBusGroup();
        ModBlocks.register(modBus);
        TurboliftNetwork.init();
        RegisterCommandsEvent.BUS.addListener(EnterpriseCommand::onRegisterCommands);
        ShipBuildManager.init();
        de.simon.spacemod.power.PowerNetwork.init();
        de.simon.spacemod.space.SpaceTravel.init();
        de.simon.spacemod.item.SpaceSuitItem.init();
        de.simon.spacemod.assembly.AssemblyRecipes.init();
        de.simon.spacemod.weapons.BeamShots.init();
        de.simon.spacemod.weapons.CameraControl.init();
        net.minecraftforge.event.AttachCapabilitiesEvent.ItemStacks.BUS.addListener(de.simon.spacemod.item.HandWeaponItem::attachEnergy);
        if (FMLLoader.getDist().isClient()) {
            FMLClientSetupEvent.getBus(modBus).addListener(de.simon.spacemod.client.ClientSetup::onClientSetup);
            net.minecraftforge.client.event.EntityRenderersEvent.RegisterRenderers.BUS.addListener(de.simon.spacemod.client.ClientSetup::onRegisterRenderers);
            de.simon.spacemod.client.ClientCamera.init();
            de.simon.spacemod.client.ShipClient.init();
        }
    }
}
