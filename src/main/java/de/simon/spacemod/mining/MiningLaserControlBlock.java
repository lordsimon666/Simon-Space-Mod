package de.simon.spacemod.mining;

import com.mojang.serialization.MapCodec;
import de.simon.spacemod.block.FacingBlock;
import de.simon.spacemod.power.ShipPower;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Laser-Steuerkonsole: schaltet alle Bergbaulaser des Schiffs (über Blöcke der Mod verbunden) an oder aus,
 * auch die, an die man nicht herankommt. Rechtsklick: sind welche an, gehen alle aus, sonst alle an.
 */
public class MiningLaserControlBlock extends FacingBlock {
    public static final MapCodec<MiningLaserControlBlock> CODEC = simpleCodec(MiningLaserControlBlock::new);
    /** Höchstens so viele Blöcke werden nach Lasern abgesucht. */
    private static final int SEARCH_LIMIT = 60_000;

    public MiningLaserControlBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    /** Alle Laser, die über Blöcke der Mod (Hülle, Innenausbau) mit der Konsole verbunden sind. */
    public static List<MiningLaserBlockEntity> lasers(Level level, BlockPos start) {
        List<MiningLaserBlockEntity> out = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> q = new ArrayDeque<>();
        seen.add(start);
        q.add(start);
        while (!q.isEmpty() && seen.size() < SEARCH_LIMIT) {
            BlockPos p = q.poll();
            if (level.getBlockEntity(p) instanceof MiningLaserBlockEntity l) out.add(l);
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                if (seen.contains(n) || !level.isLoaded(n)) continue;
                seen.add(n);
                if (ShipPower.conducts(level.getBlockState(n))) q.add(n.immutable());
            }
        }
        return out;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide() || !(player instanceof ServerPlayer sp)) return InteractionResult.SUCCESS;
        List<MiningLaserBlockEntity> all = lasers(level, pos);
        if (all.isEmpty()) {
            sp.sendOverlayMessage(Component.translatable("message.simonspacemod.laser_control.none"));
            return InteractionResult.SUCCESS;
        }
        boolean anyOn = all.stream().anyMatch(MiningLaserBlockEntity::isOn);
        for (MiningLaserBlockEntity l : all) l.setOn(!anyOn);
        level.playSound(null, pos, anyOn ? SoundEvents.BEACON_DEACTIVATE : SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.6F, 1.4F);
        sp.sendOverlayMessage(Component.translatable(anyOn ? "message.simonspacemod.laser_control.off" : "message.simonspacemod.laser_control.on", all.size()));
        return InteractionResult.SUCCESS;
    }
}
