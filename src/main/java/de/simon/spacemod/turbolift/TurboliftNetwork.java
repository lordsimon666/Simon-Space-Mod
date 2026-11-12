package de.simon.spacemod.turbolift;

import de.simon.spacemod.ModBlocks;
import de.simon.spacemod.SimonSpaceMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.SimpleChannel;

/** Netzwerkkanal der Mod: Turbolift-Namen und die Kamerasicht der Waffenstation. */
public final class TurboliftNetwork {
    private TurboliftNetwork() {}

    /** Client -> Server: Turbolift umbenennen bzw. einem anderen Netz zuordnen. */
    public record Rename(BlockPos pos, String name, String network) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Rename> STREAM_CODEC = StreamCodec.ofMember(
            (msg, buf) -> {
                buf.writeBlockPos(msg.pos());
                buf.writeUtf(msg.name(), TurboliftData.MAX_NAME);
                buf.writeUtf(msg.network(), TurboliftData.MAX_NAME);
            },
            buf -> new Rename(buf.readBlockPos(), buf.readUtf(TurboliftData.MAX_NAME), buf.readUtf(TurboliftData.MAX_NAME)));

        static void handle(Rename msg, CustomPayloadEvent.Context ctx) {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            ServerLevel level = player.level();
            BlockPos pos = msg.pos();
            if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64) return;
            if (!de.simon.spacemod.block.TurboliftBlock.isLift(level.getBlockState(pos))) return;
            TurboliftData.get(level).set(pos, msg.name(), msg.network());
            TurboliftBlockHelper.openMenu(player, level, pos); // Liste neu laden
        }
    }

    public static final SimpleChannel CHANNEL = ChannelBuilder
        .named(Identifier.fromNamespaceAndPath(SimonSpaceMod.MODID, "main"))
        .networkProtocolVersion(1)
        .simpleChannel()
            .play()
                .serverbound()
                    .addMain(Rename.class, Rename.STREAM_CODEC, Rename::handle)
                    .addMain(de.simon.spacemod.weapons.CameraAction.class, de.simon.spacemod.weapons.CameraAction.STREAM_CODEC,
                        de.simon.spacemod.weapons.CameraAction::handle)
                    .addMain(de.simon.spacemod.item.WeaponModeMessage.class, de.simon.spacemod.item.WeaponModeMessage.STREAM_CODEC,
                        de.simon.spacemod.item.WeaponModeMessage::handle)
                    .addMain(de.simon.spacemod.item.FireWeaponMessage.class, de.simon.spacemod.item.FireWeaponMessage.STREAM_CODEC,
                        de.simon.spacemod.item.FireWeaponMessage::handle)
                    .addMain(de.simon.spacemod.flight.ShipControlMessage.class, de.simon.spacemod.flight.ShipControlMessage.STREAM_CODEC,
                        de.simon.spacemod.flight.ShipControlMessage::handle)
                    .addMain(de.simon.spacemod.flight.ShipSeatMessage.class, de.simon.spacemod.flight.ShipSeatMessage.STREAM_CODEC,
                        de.simon.spacemod.flight.ShipSeatMessage::handleServer)
                .clientbound()
                    .addMain(de.simon.spacemod.weapons.CameraState.class, de.simon.spacemod.weapons.CameraState.STREAM_CODEC,
                        de.simon.spacemod.weapons.CameraState::handle)
                    .addMain(de.simon.spacemod.flight.ShipStructureMessage.class, de.simon.spacemod.flight.ShipStructureMessage.STREAM_CODEC,
                        de.simon.spacemod.flight.ShipStructureMessage::handle)
                    .addMain(de.simon.spacemod.flight.ShipSeatSync.class, de.simon.spacemod.flight.ShipSeatSync.STREAM_CODEC,
                        de.simon.spacemod.flight.ShipSeatSync::handle)
        .build();

    public static void init() {
        // Laden der Klasse registriert den Kanal
    }

    public static void sendToServer(Object msg) {
        CHANNEL.send(msg, PacketDistributor.SERVER.noArg());
    }
}
