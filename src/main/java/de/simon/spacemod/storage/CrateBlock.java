package de.simon.spacemod.storage;

import com.mojang.serialization.MapCodec;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * Block der Großkiste (Holz bis Netherit). Baut man daraus einen hohlen Quader (3×3×3 bis 8×8×8), wird
 * er zu einer Kiste mit (Volumen × Plätze der Stufe) Plätzen; die niedrigste verbaute Stufe zählt.
 */
public class CrateBlock extends BaseEntityBlock {
    public static final MapCodec<CrateBlock> CODEC = simpleCodec(p -> new CrateBlock(p, CrateTier.WOOD));
    public final CrateTier tier;

    public CrateBlock(BlockBehaviour.Properties properties, CrateTier tier) {
        super(properties);
        this.tier = tier;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CrateBlockEntity(pos, state);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean movedByPiston) {
        super.onPlace(state, level, pos, old, movedByPiston);
        // erst im nächsten Tick prüfen: dann sind auch verschobene Kisten (Schiff) samt Inhalt geladen
        if (!level.isClientSide() && !old.is(state.getBlock())) level.scheduleTick(pos, this, 1);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        CrateStructure.Box box = tryForm(level, pos);
        if (box == null || !(level.getBlockEntity(box.min()) instanceof CrateBlockEntity m)) return;
        Component msg = Component.translatable("message.simonspacemod.crate.formed", m.sizeText(),
            Component.translatable("block.simonspacemod.crate_" + m.tier().id), m.capacity());
        for (ServerPlayer sp : level.players())
            if (sp.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos)) < 16 * 16) sp.sendOverlayMessage(msg);
    }

    /** Prüft, ob hier eine neue Kiste fertig geworden ist, und bildet sie. */
    public static CrateStructure.@Nullable Box tryForm(Level level, BlockPos pos) {
        Set<BlockPos> blocks = CrateStructure.connected(pos, p -> level.getBlockState(p).getBlock() instanceof CrateBlock);
        for (BlockPos p : blocks) // gehört schon zu einer fertigen Kiste: nichts ändern
            if (level.getBlockEntity(p) instanceof CrateBlockEntity c && c.formed()) return null;
        CrateStructure.Box box = CrateStructure.validate(blocks, p -> level.getBlockState(p).isAir());
        if (box == null || !(level.getBlockEntity(box.min()) instanceof CrateBlockEntity master)) return null;
        CrateTier tier = CrateTier.NETHERITE;
        for (BlockPos p : blocks)
            if (level.getBlockState(p).getBlock() instanceof CrateBlock cb && cb.tier.ordinal() < tier.ordinal()) tier = cb.tier;
        master.formAsMaster(box, tier);
        for (BlockPos p : blocks)
            if (!p.equals(box.min()) && level.getBlockEntity(p) instanceof CrateBlockEntity c) c.join(box.min());
        return box;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide() || !(player instanceof ServerPlayer sp)) return InteractionResult.SUCCESS;
        if (level.getBlockEntity(pos) instanceof CrateBlockEntity be) {
            if (!be.formed()) tryForm(level, pos); // z. B. nach dem Bau per Befehl
            CrateBlockEntity m = be.master();
            if (m != null && !m.intact()) { be.dissolve(pos); m = null; } // z. B. per Befehl ein Loch hineingesetzt
            if (m != null) be.openMenu(sp, pos);
            else sp.sendOverlayMessage(Component.translatable("message.simonspacemod.crate.invalid"));
        }
        return InteractionResult.SUCCESS;
    }
}
