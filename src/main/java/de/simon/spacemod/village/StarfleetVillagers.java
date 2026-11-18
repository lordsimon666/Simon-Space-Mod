package de.simon.spacemod.village;

import com.google.common.collect.ImmutableSet;
import de.simon.spacemod.ModBlocks;
import de.simon.spacemod.SimonSpaceMod;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.util.Set;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.trading.TradeSet;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * Sternenflotten-Dorfbewohner: Beruf mit dem Shuttle-Pilotensitz als Arbeitsplatz. Handel (Uniformen, auf höheren
 * Stufen Stühle und Deko, nie funktionale Blöcke) steht in data/simonspacemod/trade_set und villager_trade.
 */
public final class StarfleetVillagers {
    public static final DeferredRegister<PoiType> POI_TYPES = DeferredRegister.create(Registries.POINT_OF_INTEREST_TYPE, SimonSpaceMod.MODID);
    public static final DeferredRegister<VillagerProfession> PROFESSIONS = DeferredRegister.create(Registries.VILLAGER_PROFESSION, SimonSpaceMod.MODID);

    public static final ResourceKey<PoiType> JOB_SITE = ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, id("shuttle_chair"));
    public static final ResourceKey<VillagerProfession> STARFLEET_KEY = ResourceKey.create(Registries.VILLAGER_PROFESSION, id("starfleet"));

    public static final RegistryObject<PoiType> SHUTTLE_CHAIR = POI_TYPES.register("shuttle_chair",
        () -> new PoiType(Set.copyOf(ModBlocks.SHUTTLE_CHAIR.get().getStateDefinition().getPossibleStates()), 1, 1));

    public static final RegistryObject<VillagerProfession> STARFLEET = PROFESSIONS.register("starfleet", () -> new VillagerProfession(
        Component.translatable("entity." + SimonSpaceMod.MODID + ".villager.starfleet"),
        poi -> poi.is(JOB_SITE), poi -> poi.is(JOB_SITE), ImmutableSet.of(), ImmutableSet.of(), SoundEvents.VILLAGER_WORK_CARTOGRAPHER,
        Int2ObjectMap.ofEntries(
            Int2ObjectMap.entry(1, trades(1)), Int2ObjectMap.entry(2, trades(2)), Int2ObjectMap.entry(3, trades(3)),
            Int2ObjectMap.entry(4, trades(4)), Int2ObjectMap.entry(5, trades(5)))));

    private StarfleetVillagers() {}

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(SimonSpaceMod.MODID, path);
    }

    private static ResourceKey<TradeSet> trades(int level) {
        return ResourceKey.create(Registries.TRADE_SET, id("starfleet/level_" + level));
    }

    public static void register(BusGroup modBus) {
        POI_TYPES.register(modBus);
        PROFESSIONS.register(modBus);
        // aus Bauvorlagen oder alten Spielständen geladene Sternenflotten-Dorfbewohner ohne Namen benennen
        net.minecraftforge.event.entity.EntityJoinLevelEvent.BUS.addListener(e -> {
            if (!e.getLevel().isClientSide() && e.getEntity() instanceof net.minecraft.world.entity.npc.villager.Villager v) name(v);
        });
    }

    /** Sternenflotten-Dorfbewohner ohne Namen bekommen den einer Star-Trek-Figur. */
    public static void name(net.minecraft.world.entity.npc.villager.Villager v) {
        if (v.hasCustomName() || !v.getVillagerData().profession().is(STARFLEET_KEY)) return;
        v.setCustomName(Component.literal(StarfleetCrew.random(v.getRandom()).name()));
    }
}
