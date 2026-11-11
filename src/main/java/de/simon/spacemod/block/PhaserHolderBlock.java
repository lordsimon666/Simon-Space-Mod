package de.simon.spacemod.block;

import com.mojang.serialization.MapCodec;
import de.simon.spacemod.ModBlocks;
import de.simon.spacemod.block.entity.WeaponHolderBlockEntity;
import de.simon.spacemod.item.HandWeaponItem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Wandhalter für 4 Handphaser (2 x 2), ein Block groß; die Rückseite liegt an der Wand. */
public class PhaserHolderBlock extends WeaponHolderBlock {
    public PhaserHolderBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return simpleCodec(PhaserHolderBlock::new);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes4.box(state.getValue(FACING), 0, 0, 10, 16, 16, 16);
    }

    @Override
    protected int slotAt(BlockState state, BlockPos pos, Vec3 hit) {
        int col = across(state, pos, hit) >= 0.5 ? 1 : 0;
        int row = hit.y - pos.getY() >= 0.5 ? 0 : 1;
        return row * 2 + col;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HolderBE(pos, state);
    }

    /** Nur Handphaser, keine Gewehre. */
    public static class HolderBE extends WeaponHolderBlockEntity {
        public HolderBE(BlockPos pos, BlockState state) {
            super(ModBlocks.PHASER_HOLDER_BE.get(), pos, state, 4);
        }

        @Override
        public boolean accepts(ItemStack stack) {
            return stack.getItem() instanceof HandWeaponItem w && !w.rifle;
        }
    }
}
