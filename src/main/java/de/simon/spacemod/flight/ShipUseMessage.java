package de.simon.spacemod.flight;

import de.simon.spacemod.entity.FlyingShipEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.network.CustomPayloadEvent;

/**
 * Blöcke im fliegenden Schiff benutzen. Client -> Server: angeklickter Block (lokal) und Seite.
 * Der Server benutzt den echten Block in der Werft – Kisten, Öfen, Reaktor, Maschinen anderer
 * Mods öffnen ihr Menü wie am Boden. Blöcke setzen geht im Flug nicht.
 */
public record ShipUseMessage(int entityId, BlockPos local, Direction face) {
    public static final double REACH = 5.5;

    public static final StreamCodec<RegistryFriendlyByteBuf, ShipUseMessage> STREAM_CODEC = StreamCodec.ofMember(
        (msg, buf) -> {
            buf.writeVarInt(msg.entityId());
            buf.writeBlockPos(msg.local());
            buf.writeEnum(msg.face());
        },
        buf -> new ShipUseMessage(buf.readVarInt(), buf.readBlockPos(), buf.readEnum(Direction.class)));

    public static void handle(ShipUseMessage msg, CustomPayloadEvent.Context ctx) {
        ServerPlayer player = ctx.getSender();
        if (player == null || !(player.getVehicle() instanceof FlyingShipEntity ship) || ship.getId() != msg.entityId()) return;
        ShipStructure s = ship.structure();
        if (s == null || s.indexOf(msg.local()) < 0) return;
        Vec3 eye = ship.seat(player).add(0, player.getEyeHeight(), 0);
        if (eye.distanceTo(Vec3.atLowerCornerOf(msg.local())) > REACH + 1) return;
        ServerLevel sy = Shipyard.level(player.level().getServer());
        if (sy == null || ship.plot() < 0) {
            player.sendOverlayMessage(Component.translatable("message.simonspacemod.ship.no_use"));
            return;
        }
        BlockPos pos = ship.plotOrigin().offset(msg.local());
        BlockState st = sy.getBlockState(pos);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos).add(Vec3.atLowerCornerOf(msg.face().getUnitVec3i()).scale(0.5)),
            msg.face(), pos, false);
        InteractionHand hand = InteractionHand.MAIN_HAND;
        InteractionResult r = st.useItemOn(player.getItemInHand(hand), sy, player, hand, hit);
        if (r instanceof InteractionResult.TryEmptyHandInteraction) r = st.useWithoutItem(sy, player, hit);
        if (!r.consumesAction()) player.sendOverlayMessage(Component.translatable("message.simonspacemod.ship.no_use"));
    }
}
