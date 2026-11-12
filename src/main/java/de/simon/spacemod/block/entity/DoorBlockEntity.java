package de.simon.spacemod.block.entity;

import de.simon.spacemod.ModBlocks;
import de.simon.spacemod.block.StarshipDoorBlock;
import de.simon.spacemod.item.KeycardItem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;

/**
 * Steuerung einer 2x2-Tür (im unteren linken Teil): öffnet sie für berechtigte Spieler in der Nähe
 * und merkt sich, ob und mit welcher Karte sie verschlossen wurde.
 */
public class DoorBlockEntity extends BlockEntity {
    /** null = offen, sonst die Karte, mit der verschlossen wurde. */
    private KeycardItem.Level lock;

    public DoorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.DOOR_BE.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, DoorBlockEntity be) {
        if (level.getGameTime() % 4 != Math.floorMod(pos.asLong(), 4)) return;
        if (!(state.getBlock() instanceof StarshipDoorBlock door)) return;
        boolean open = be.lock == null && be.someoneWaiting(level, pos, state, door);
        if (open == state.getValue(StarshipDoorBlock.OPEN)) return;
        for (int p = 0; p < 4; p++) {
            BlockPos q = StarshipDoorBlock.partPos(pos, state, p);
            BlockState s = level.getBlockState(q);
            if (s.getBlock() == door) level.setBlock(q, s.setValue(StarshipDoorBlock.OPEN, open), Block.UPDATE_CLIENTS);
        }
        level.playSound(null, pos, open ? SoundEvents.PISTON_EXTEND : SoundEvents.PISTON_CONTRACT, SoundSource.BLOCKS, 0.35F, 1.9F);
    }

    /** Steht ein berechtigter Spieler (nicht schleichend) vor oder hinter der Tür? */
    private boolean someoneWaiting(Level level, BlockPos pos, BlockState state, StarshipDoorBlock door) {
        BlockPos far = StarshipDoorBlock.partPos(pos, state, 3);
        var normal = state.getValue(StarshipDoorBlock.FACING);
        AABB box = new AABB(pos).minmax(new AABB(far)).expandTowards(normal.getStepX() * 2.5, 0, normal.getStepZ() * 2.5)
            .expandTowards(-normal.getStepX() * 2.5, 0, -normal.getStepZ() * 2.5);
        java.util.List<Player> near = new java.util.ArrayList<>(level.getEntitiesOfClass(Player.class, box));
        // Tür eines fliegenden Schiffs (steht in der Werft): Spieler im Schiff umgerechnet
        if (de.simon.spacemod.flight.Shipyard.is(level)) near.addAll(de.simon.spacemod.flight.Shipyard.playersIn(level, box));
        for (Player p : near) {
            if (p.isSpectator() || p.isShiftKeyDown()) continue;
            if (allowed(p, door.kind)) return true;
        }
        return false;
    }

    public static boolean allowed(Player p, StarshipDoorBlock.Kind kind) {
        return switch (kind) {
            case NORMAL -> true;
            case SECURITY -> KeycardItem.has(p, KeycardItem.Level.SECURITY);
            case COMMAND -> KeycardItem.has(p, KeycardItem.Level.COMMAND);
        };
    }

    /** Shift-Rechtsklick mit Karte: offen -> verschließen; verschlossen -> öffnen, wenn der Rang reicht. */
    public void toggleLock(Player player, KeycardItem.Level card) {
        if (lock == null) {
            lock = card;
            message(player, "message.simonspacemod.door.locked", Component.translatable("keycard.simonspacemod." + card.name().toLowerCase()));
        } else if (card.rank >= lock.rank) {
            lock = null;
            message(player, "message.simonspacemod.door.unlocked");
        } else {
            message(player, "message.simonspacemod.door.denied_captain");
            return;
        }
        setChanged();
        if (level != null) level.playSound(null, worldPosition, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.5F, lock == null ? 1.4F : 0.8F);
    }

    public void showStatus(Player player) {
        if (lock != null) message(player, "message.simonspacemod.door.is_locked", Component.translatable("keycard.simonspacemod." + lock.name().toLowerCase()));
        else if (getBlockState().getBlock() instanceof StarshipDoorBlock d && !allowed(player, d.kind))
            message(player, d.kind == StarshipDoorBlock.Kind.SECURITY ? "message.simonspacemod.door.need_security" : "message.simonspacemod.door.need_command");
    }

    private static void message(Player player, String key, Object... args) {
        if (player instanceof ServerPlayer sp) sp.sendOverlayMessage(Component.translatable(key, args));
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        int l = input.getIntOr("lock", -1);
        lock = l < 0 || l >= KeycardItem.Level.values().length ? null : KeycardItem.Level.values()[l];
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("lock", lock == null ? -1 : lock.ordinal());
    }
}
