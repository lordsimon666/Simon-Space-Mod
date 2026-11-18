package de.simon.spacemod.village;

import com.mojang.datafixers.util.Pair;
import de.simon.spacemod.SimonSpaceMod;
import de.simon.spacemod.mixin.StructureTemplatePoolAccessor;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElementType;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * Umgebaute Shuttle-Wracks als Wohnhaus in Dörfern (Ebene, Wüste, Savanne, Schnee, Taiga): etwa jedes fünfzigste Dorf
 * bekommt eines. Darin wohnt ein Sternenflotten-Dorfbewohner (Raumschiff-Bett, Shuttle-Pilotensitz als Arbeitsplatz).
 */
public final class ShuttleHouses {
    public static final DeferredRegister<StructurePoolElementType<?>> ELEMENT_TYPES =
        DeferredRegister.create(Registries.STRUCTURE_POOL_ELEMENT, SimonSpaceMod.MODID);
    public static final RegistryObject<StructurePoolElementType<RareSinglePoolElement>> RARE_SINGLE =
        ELEMENT_TYPES.register("rare_single_pool_element", () -> () -> RareSinglePoolElement.CODEC);

    public static final Identifier TEMPLATE = Identifier.fromNamespaceAndPath(SimonSpaceMod.MODID, "village/shuttle_house");
    public static final String[] VILLAGES = {"plains", "desert", "savanna", "snowy", "taiga"};
    private ShuttleHouses() {}

    public static void register(BusGroup modBus) {
        ELEMENT_TYPES.register(modBus);
        net.minecraftforge.event.server.ServerAboutToStartEvent.BUS.addListener(e -> inject(e.getServer()));
    }

    private static void inject(MinecraftServer server) {
        var pools = server.registryAccess().lookupOrThrow(Registries.TEMPLATE_POOL);
        var processors = server.registryAccess().lookupOrThrow(Registries.PROCESSOR_LIST).getOrThrow(ResourceKey.create(Registries.PROCESSOR_LIST, Identifier.withDefaultNamespace("empty")));
        for (String village : VILLAGES) {
            var key = ResourceKey.create(Registries.TEMPLATE_POOL, Identifier.withDefaultNamespace("village/" + village + "/houses"));
            StructureTemplatePool pool = pools.get(key).map(h -> h.value()).orElse(null);
            if (pool == null) continue;
            var acc = (StructureTemplatePoolAccessor) pool;
            if (acc.simonspacemod$templates().stream().anyMatch(t -> t instanceof RareSinglePoolElement)) continue; // schon drin
            int total = 0;
            for (Pair<StructurePoolElement, Integer> p : acc.simonspacemod$rawTemplates()) total += p.getSecond();
            RareSinglePoolElement house = new RareSinglePoolElement(TEMPLATE, processors, VillageShare.chance(total));
            acc.simonspacemod$templates().add(house);
            List<Pair<StructurePoolElement, Integer>> raw = new ArrayList<>(acc.simonspacemod$rawTemplates());
            raw.add(Pair.of(house, 1));
            acc.simonspacemod$setRawTemplates(raw);
            com.mojang.logging.LogUtils.getLogger().info("Shuttle-Haus im Dorf-Pool {} (Gesamtgewicht {}, Chance je Ziehung {})",
                key.identifier(), total, house.chance());
        }
    }
}
