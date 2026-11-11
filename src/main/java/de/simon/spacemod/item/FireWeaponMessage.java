package de.simon.spacemod.item;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.network.CustomPayloadEvent;

/** Client -> Server: Linksklick mit dem Phasergewehr in der Hand – feuern. */
public record FireWeaponMessage(float yRot, float xRot) {
    public static final StreamCodec<RegistryFriendlyByteBuf, FireWeaponMessage> STREAM_CODEC = StreamCodec.ofMember(
        (msg, buf) -> {
            buf.writeFloat(msg.yRot());
            buf.writeFloat(msg.xRot());
        },
        buf -> new FireWeaponMessage(buf.readFloat(), buf.readFloat()));

    public static void handle(FireWeaponMessage msg, CustomPayloadEvent.Context ctx) {
        ServerPlayer player = ctx.getSender();
        if (player == null) return;
        var stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof HandWeaponItem w) || !w.rifle) return;
        player.setYRot(msg.yRot());
        player.setXRot(msg.xRot());
        w.fire(player.level(), player, stack);
    }
}
