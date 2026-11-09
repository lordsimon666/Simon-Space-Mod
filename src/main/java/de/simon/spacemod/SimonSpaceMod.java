package de.simon.spacemod;

import de.simon.spacemod.command.EnterpriseCommand;
import de.simon.spacemod.ship.ShipBuildManager;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/** Simon Space Mod – bringt die USS Enterprise NCC-1701 nach Minecraft. */
@Mod(SimonSpaceMod.MODID)
public final class SimonSpaceMod {
    public static final String MODID = "simonspacemod";

    public SimonSpaceMod(FMLJavaModLoadingContext context) {
        ModBlocks.register(context.getModBusGroup());
        RegisterCommandsEvent.BUS.addListener(EnterpriseCommand::onRegisterCommands);
        ShipBuildManager.init();
    }
}
