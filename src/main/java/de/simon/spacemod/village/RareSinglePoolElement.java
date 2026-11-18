package de.simon.spacemod.village;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElementType;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/**
 * Bauteil, das seltener vorkommt, als es sein kleinstes Gewicht (1) im Vorlagen-Pool erlaubt: Wird es gezogen,
 * dockt es nur mit der Wahrscheinlichkeit {@code chance} an (sonst bietet es keine Anschlüsse an, und der Zufall
 * zieht ein anderes Haus). Der Zufall kommt aus der Weltgenerierung, ist also je Welt-Seed fest.
 */
public class RareSinglePoolElement extends SinglePoolElement {
    public static final MapCodec<RareSinglePoolElement> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            templateCodec(), processorsCodec(), projectionCodec(), overrideLiquidSettingsCodec(),
            Codec.FLOAT.fieldOf("chance").forGetter(e -> e.chance))
        .apply(i, RareSinglePoolElement::new));

    private final float chance;

    public RareSinglePoolElement(Either<Identifier, StructureTemplate> template, Holder<StructureProcessorList> processors,
                                 StructureTemplatePool.Projection projection, Optional<LiquidSettings> liquid, float chance) {
        super(template, processors, projection, liquid);
        this.chance = chance;
    }

    public RareSinglePoolElement(Identifier template, Holder<StructureProcessorList> processors, float chance) {
        this(Either.left(template), processors, StructureTemplatePool.Projection.RIGID, Optional.empty(), chance);
    }

    public float chance() {
        return chance;
    }

    @Override
    public List<StructureTemplate.JigsawBlockInfo> getShuffledJigsawBlocks(StructureTemplateManager manager, BlockPos position,
                                                                          Rotation rotation, RandomSource random) {
        if (random.nextFloat() >= chance) return new ArrayList<>();
        return super.getShuffledJigsawBlocks(manager, position, rotation, random);
    }

    @Override
    public StructurePoolElementType<?> getType() {
        return ShuttleHouses.RARE_SINGLE.get();
    }
}
