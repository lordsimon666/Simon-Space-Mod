package de.simon.spacemod.mixin;

import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.List;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Zugriff auf die Bauteile eines Vorlagen-Pools (Shuttle-Häuser in Dörfern einfügen). */
@Mixin(StructureTemplatePool.class)
public interface StructureTemplatePoolAccessor {
    @Accessor("templates")
    ObjectArrayList<StructurePoolElement> simonspacemod$templates();

    @Accessor("rawTemplates")
    List<Pair<StructurePoolElement, Integer>> simonspacemod$rawTemplates();

    @Mutable
    @Accessor("rawTemplates")
    void simonspacemod$setRawTemplates(List<Pair<StructurePoolElement, Integer>> raw);
}
