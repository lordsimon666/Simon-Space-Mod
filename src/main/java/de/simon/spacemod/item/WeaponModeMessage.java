package de.simon.spacemod.item;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.network.CustomPayloadEvent;

/** Client -> Server: V gedrückt – Modus des Phasers in der Hand weiterschalten. */
public record WeaponModeMessage() {
    public static final StreamCodec<RegistryFriendlyByteBuf, WeaponModeMessage> STREAM_CODEC =
        StreamCodec.ofMember((msg, buf) -> {}, buf -> new WeaponModeMessage());

    public static void handle(WeaponModeMessage msg, CustomPayloadEvent.Context ctx) {
        ServerPlayer player = ctx.getSender();
        if (player == null) return;
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.getItem() instanceof HandWeaponItem) {
                HandWeaponItem.cycleMode(player, stack);
                return;
            }
        }
    }
}
