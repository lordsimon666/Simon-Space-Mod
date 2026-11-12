package de.simon.spacemod.block;

import de.simon.spacemod.ModBlocks;
import de.simon.spacemod.SimonSpaceMod;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.registries.ForgeRegistries;
import com.mojang.serialization.MapCodec;

/**
 * Transporterplattform: Rechtsklick öffnet das Transporter-Menü – auf den Boden unter dem Schiff oder
 * zu einer anderen Plattform im Umkreis von 500 Blöcken. Mit dem Kommunikator kommt man wieder
 * zurück auf die zuletzt benutzte Plattform.
 */
public class TransporterPadBlock extends Block {
    public static final String LAST_PAD_TAG = SimonSpaceMod.MODID + ":last_pad";
    private static final VoxelShape SHAPE = box(0, 0, 0, 16, 3, 16);

    public TransporterPadBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return simpleCodec(TransporterPadBlock::new);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer sp)) {
            return InteractionResult.SUCCESS;
        }
        de.simon.spacemod.transporter.Transporters.openMenu(server, pos, sp);
        return InteractionResult.CONSUME;
    }

    /** Sucht unterhalb des Schiffs den ersten festen Boden (kein Schiffsblock) mit 2 Blöcken Luft darüber. */
    public static BlockPos findGroundBelow(ServerLevel level, BlockPos pad) {
        BlockPos.MutableBlockPos p = pad.mutable();
        boolean leftShip = false;
        for (int y = pad.getY() - 1; y > level.getMinY(); y--) {
            p.setY(y);
            BlockState s = level.getBlockState(p);
            if (isShipBlock(s)) {
                leftShip = false;
                continue;
            }
            if (s.isAir()) {
                leftShip = true;
                continue;
            }
            if (leftShip && level.getBlockState(p.above()).isAir() && level.getBlockState(p.above(2)).isAir()) {
                return p.above().immutable();
            }
        }
        return null;
    }

    private static boolean isShipBlock(BlockState s) {
        Identifier id = ForgeRegistries.BLOCKS.getKey(s.getBlock());
        return id != null && id.getNamespace().equals(SimonSpaceMod.MODID);
    }

    /** Für Transporte zwischen Plattformen merken (auch Plattformen gespawnter Schiffe). */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        super.onPlace(state, level, pos, old, moved);
        if (level instanceof ServerLevel sl && !old.is(this)) de.simon.spacemod.transporter.TransporterData.get(sl).add(pos);
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        de.simon.spacemod.transporter.TransporterData.get(level).add(pos);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(2) == 0) {
            level.addParticle(ParticleTypes.END_ROD,
                pos.getX() + 0.2 + random.nextDouble() * 0.6, pos.getY() + 0.25, pos.getZ() + 0.2 + random.nextDouble() * 0.6,
                0, 0.04 + random.nextDouble() * 0.04, 0);
        }
    }
}
