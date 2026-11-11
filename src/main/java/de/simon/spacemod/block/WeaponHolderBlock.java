package de.simon.spacemod.block;

import de.simon.spacemod.block.entity.WeaponHolderBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Gemeinsame Bedienung aller Waffenhalter: Waffe in der Hand = einsetzen (auf den angeklickten
 * Platz), leere Hand = herausnehmen.
 */
public abstract class WeaponHolderBlock extends FacingBlock implements EntityBlock {
    protected WeaponHolderBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    /** Block mit den Daten (bei mehrteiligen Blöcken der untere). */
    protected BlockPos holderPos(BlockState state, BlockPos pos) {
        return pos;
    }

    /** Welcher Platz wurde angeklickt? */
    protected abstract int slotAt(BlockState state, BlockPos pos, Vec3 hit);

    /** Querachse von links nach rechts, von vorne gesehen: 0..1. */
    protected static double across(BlockState state, BlockPos pos, Vec3 hit) {
        Direction f = state.getValue(FACING);
        Direction right = f.getCounterClockWise(); // rechts aus Sicht des Betrachters vor der Front
        Vec3 local = hit.subtract(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        return local.x * right.getStepX() + local.z * right.getStepZ() + 0.5;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        BlockPos hp = holderPos(state, pos);
        if (!(level.getBlockEntity(hp) instanceof WeaponHolderBlockEntity be) || !be.accepts(stack))
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        int slot = freeSlot(be, slotAt(state, pos, hit.getLocation()));
        if (slot < 0) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (!level.isClientSide()) {
            be.setItem(slot, stack.copyWithCount(1));
            stack.shrink(1);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        BlockPos hp = holderPos(state, pos);
        if (!(level.getBlockEntity(hp) instanceof WeaponHolderBlockEntity be)) return InteractionResult.PASS;
        int slot = slotAt(state, pos, hit.getLocation());
        if (be.items().get(slot).isEmpty()) slot = firstFull(be);
        if (slot < 0) return onEmpty(be, level, player);
        if (!level.isClientSide()) {
            ItemStack out = be.items().get(slot);
            be.setItem(slot, ItemStack.EMPTY);
            if (!player.getInventory().add(out)) player.drop(out, false);
        }
        return InteractionResult.SUCCESS;
    }

    /** Leerer Halter angeklickt (Ladestation zeigt dann ihren Stand). */
    protected InteractionResult onEmpty(WeaponHolderBlockEntity be, Level level, Player player) {
        return InteractionResult.PASS;
    }

    private static int freeSlot(WeaponHolderBlockEntity be, int wanted) {
        if (be.items().get(wanted).isEmpty()) return wanted;
        for (int i = 0; i < be.items().size(); i++) if (be.items().get(i).isEmpty()) return i;
        return -1;
    }

    private static int firstFull(WeaponHolderBlockEntity be) {
        for (int i = 0; i < be.items().size(); i++) if (!be.items().get(i).isEmpty()) return i;
        return -1;
    }
}
