package de.simon.spacemod.client.guide;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import de.simon.spacemod.guide.GuideStructure;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * Shows a structure of the guide book in the world as a building plan: where the player looks (or a few blocks
 * in front of them), turned to face them, every block still missing is a small glowing ghost block. Ghosts
 * disappear as the right blocks are placed; once all are there, or after a while, the plan goes away.
 * The ghosts are block displays that only this client knows about, so nothing changes on the server.
 */
public final class StructurePreview {
    private static final Logger LOGGER = LogUtils.getLogger();
    // How long a plan stays, in ticks: 20 minutes
    private static final int LIFETIME = 20 * 60 * 20;
    private static final float GHOST_SIZE = 0.6F;
    // The ghosts get ids no entity from the server has
    private static int nextId = -1_000_000;

    // The block displays' data: which block they show (found by its type) and their size and offset
    @Nullable
    private static final EntityDataAccessor<Object> BLOCK_STATE = accessor(Display.BlockDisplay.class, "DATA_BLOCK_STATE_ID");
    @Nullable
    private static final EntityDataAccessor<Object> SCALE = accessor(Display.class, "DATA_SCALE_ID");
    @Nullable
    private static final EntityDataAccessor<Object> TRANSLATION = accessor(Display.class, "DATA_TRANSLATION_ID");
    // Full brightness, so the ghosts can be seen at night and in caves too
    @Nullable
    private static final EntityDataAccessor<Object> BRIGHTNESS = accessor(Display.class, "DATA_BRIGHTNESS_OVERRIDE_ID");
    // The glowing outline: on a client the glow comes from the entity's flags, which the server normally sends
    @Nullable
    private static final EntityDataAccessor<Object> FLAGS = accessor(Entity.class, "DATA_SHARED_FLAGS_ID");
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final byte GLOWING = 1 << 6;

    /** A block of the plan: where it goes, what goes there and its ghost while it is missing. */
    private static final class Ghost {
        final BlockPos pos;
        final BlockState state;
        @Nullable
        Display.BlockDisplay display;

        Ghost(BlockPos pos, BlockState state) {
            this.pos = pos;
            this.state = state;
        }
    }

    @Nullable
    private static GuideStructure structure;
    @Nullable
    private static ClientLevel level;
    private static final List<Ghost> GHOSTS = new ArrayList<>();
    private static int age;

    private StructurePreview() {
    }

    /** Whether this structure's plan is shown right now. */
    public static boolean isShowing(GuideStructure shown) {
        return structure == shown;
    }

    public static boolean isActive() {
        return structure != null;
    }

    /** Shows the structure's plan where the player looks, facing them; an older plan goes away. */
    public static void show(GuideStructure shown) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null || BLOCK_STATE == null)
            return;
        stop();
        Direction facing = player.getDirection();
        BlockPos origin;
        HitResult hit = minecraft.hitResult;
        if (hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK)
            origin = block.getBlockPos().relative(block.getDirection());
        else
            origin = player.blockPosition().relative(facing, 3);
        // The structures are drawn across the player's view when they look north or south
        Rotation rotation = facing.getAxis() == Direction.Axis.X ? Rotation.CLOCKWISE_90 : Rotation.NONE;
        for (GuideStructure.Part part : shown.parts()) {
            if (part.build())
                GHOSTS.add(new Ghost(origin.offset(part.pos().rotate(rotation)), part.state().get().rotate(rotation)));
        }
        structure = shown;
        level = minecraft.level;
        age = 0;
        tick();
    }

    /** Takes the plan away. */
    public static void stop() {
        for (Ghost ghost : GHOSTS)
            remove(ghost);
        GHOSTS.clear();
        structure = null;
        level = null;
    }

    /** Every client tick: ghosts where blocks are missing, none where they are placed; done when all are there. */
    public static void tick() {
        if (structure == null)
            return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != level || minecraft.level == null) {
            // Another world or dimension: its ghosts are gone with the old one
            GHOSTS.clear();
            structure = null;
            level = null;
            return;
        }
        if (++age > LIFETIME) {
            stop();
            return;
        }
        boolean complete = true;
        for (Ghost ghost : GHOSTS) {
            boolean placed = level.getBlockState(ghost.pos).is(ghost.state.getBlock());
            complete &= placed;
            if (placed)
                remove(ghost);
            else if (ghost.display == null || ghost.display.isRemoved())
                ghost.display = spawn(level, ghost);
        }
        if (complete) {
            stop();
            minecraft.gui.hud.getChat().addClientSystemMessage(Component.translatable("message.simonspacemod.guide.preview_done"));
        }
    }

    @Nullable
    private static Display.BlockDisplay spawn(ClientLevel level, Ghost ghost) {
        Display.BlockDisplay display = EntityTypes.BLOCK_DISPLAY.create(level, EntitySpawnReason.LOAD);
        if (display == null || BLOCK_STATE == null)
            return null;
        display.setId(nextId--);
        display.setPos(ghost.pos.getX(), ghost.pos.getY(), ghost.pos.getZ());
        display.getEntityData().set(BLOCK_STATE, ghost.state);
        // Smaller than a real block and in the middle of its space, so it is plain to see what is a plan
        if (SCALE != null && TRANSLATION != null) {
            float offset = (1 - GHOST_SIZE) / 2;
            display.getEntityData().set(SCALE, new Vector3f(GHOST_SIZE, GHOST_SIZE, GHOST_SIZE));
            display.getEntityData().set(TRANSLATION, new Vector3f(offset, offset, offset));
        }
        if (BRIGHTNESS != null)
            display.getEntityData().set(BRIGHTNESS, FULL_BRIGHT);
        display.setGlowingTag(true);
        if (FLAGS != null)
            display.getEntityData().set(FLAGS, GLOWING);
        level.addEntity(display);
        return display;
    }

    private static void remove(Ghost ghost) {
        if (ghost.display != null && level != null)
            level.removeEntity(ghost.display.getId(), Entity.RemovalReason.DISCARDED);
        ghost.display = null;
    }

    /**
     * One of the entities' data fields, which they keep to themselves. The block state is found by its
     * type if the name doesn't fit; without it there is no preview.
     */
    @Nullable
    @SuppressWarnings("unchecked")
    private static EntityDataAccessor<Object> accessor(Class<?> owner, String name) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return (EntityDataAccessor<Object>) field.get(null);
        } catch (ReflectiveOperationException | RuntimeException e) {
            if (owner == Display.BlockDisplay.class) {
                for (Field field : owner.getDeclaredFields()) {
                    try {
                        if (Modifier.isStatic(field.getModifiers()) && field.getType() == EntityDataAccessor.class) {
                            field.setAccessible(true);
                            EntityDataAccessor<Object> accessor = (EntityDataAccessor<Object>) field.get(null);
                            if (accessor.serializer() == (Object) EntityDataSerializers.BLOCK_STATE)
                                return accessor;
                        }
                    } catch (ReflectiveOperationException | RuntimeException ignored) {
                        // Try the next field
                    }
                }
            }
            LOGGER.warn("Guide book: no {} in {}, the structure preview won't be as planned", name, owner.getSimpleName());
            return null;
        }
    }
}
