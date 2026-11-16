package de.simon.spacemod.storage;

import java.util.function.Consumer;
import de.simon.spacemod.ModBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Stufen-Upgrade (wie bei Sophisticated Storage): Rechtsklick auf eine Großkiste hebt sie auf die Zielstufe
 * (auch mehrere Stufen auf einmal, z. B. Holz -> Netherit),
 * Inhalt und Upgrades bleiben. Man braucht ein Upgrade je Block der Kiste – bei 3×3×3 also 26.
 * Auf einen einzelnen Kistenblock angewandt kostet es eins.
 */
public class TierUpgradeItem extends Item {
    public final CrateTier from, to;

    public TierUpgradeItem(Item.Properties properties, CrateTier from, CrateTier to) {
        super(properties);
        this.from = from;
        this.to = to;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        if (!(level.getBlockState(pos).getBlock() instanceof CrateBlock cb)) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        ServerPlayer sp = ctx.getPlayer() instanceof ServerPlayer p ? p : null;
        if (cb.tier != from) {
            msg(sp, Component.translatable("message.simonspacemod.crate.wrong_tier", Component.translatable("block.simonspacemod.crate_" + from.id)));
            return InteractionResult.FAIL;
        }
        ItemStack stack = ctx.getItemInHand();
        boolean creative = sp != null && sp.getAbilities().instabuild;
        CrateBlockEntity be = level.getBlockEntity(pos) instanceof CrateBlockEntity c ? c : null;
        CrateBlockEntity m = be == null ? null : be.master();
        Block target = ModBlocks.CRATES.get(to).get();
        if (m == null || !m.intact()) { // einzelner Block
            if (!creative) stack.shrink(1);
            level.setBlock(pos, target.defaultBlockState(), Block.UPDATE_ALL);
            done(level, pos, sp, 1);
            return InteractionResult.SUCCESS;
        }
        int need = m.shellBlocks();
        if (!creative && stack.getCount() < need) {
            msg(sp, Component.translatable("message.simonspacemod.crate.need_upgrades", need, stack.getCount()));
            return InteractionResult.FAIL;
        }
        if (!creative) stack.shrink(need);
        BlockPos min = m.getBlockPos();
        CrateStructure.Box box = m.box();
        BlockState st = target.defaultBlockState();
        for (BlockPos p : BlockPos.betweenClosed(min, box.max())) // nur die Wände dieser Kiste; Blockdaten bleiben
            if (box.onShell(p)) level.setBlock(p.immutable(), st, Block.UPDATE_ALL);
        m.changeTier(to);
        done(level, pos, sp, need);
        return InteractionResult.SUCCESS;
    }

    private static void msg(ServerPlayer sp, Component c) {
        if (sp != null) sp.sendOverlayMessage(c);
    }

    private void done(Level level, BlockPos pos, ServerPlayer sp, int used) {
        level.playSound(null, pos, SoundEvents.SMITHING_TABLE_USE, SoundSource.BLOCKS, 1F, 1F);
        msg(sp, Component.translatable("message.simonspacemod.crate.upgraded", Component.translatable("block.simonspacemod.crate_" + to.id), used));
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        out.accept(Component.translatable("tooltip.simonspacemod.tier_upgrade").withStyle(ChatFormatting.GRAY));
    }
}
