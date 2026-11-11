package de.simon.spacemod.item;

import de.simon.spacemod.ModBlocks;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Handphaser bzw. Phasergewehr einer Epoche. Rechtsklick feuert, V wechselt zwischen Betäuben,
 * Töten und Abbauen. Braucht Energie (RF), geladen wird an der Ladestation der Waffenkammer.
 * Neuere Epochen sind stärker, Gewehre stärker als Handphaser.
 */
public class HandWeaponItem extends Item {
    public enum Mode { STUN, KILL, MINING }

    /** Epoche: 1 = NX-01 (2150er) ... 5 = Enterprise-E (2370er). */
    public final int tier;
    public final boolean rifle;
    /** Strahlfarbe und Farbe des Kerns. */
    private final int color, core;
    /** Phasenpistole der NX-01 schießt Pulse statt Dauerstrahl. */
    private final boolean pulse;

    public HandWeaponItem(Item.Properties props, int tier, boolean rifle, int color, int core, boolean pulse) {
        super(props.stacksTo(1));
        this.tier = tier;
        this.rifle = rifle;
        this.color = color;
        this.core = core;
        this.pulse = pulse;
    }

    // ---- Werte ----

    public int capacity() {
        int hand = 4000 + 2000 * tier; // NX 6000 ... E 14000 RF
        return rifle ? hand * 5 / 2 : hand;
    }

    /** Größte Ladeleistung pro Tick an der Ladestation. */
    public int chargeRate() {
        return rifle ? 400 : 200;
    }

    private int cost(Mode m) {
        int c = switch (m) { case STUN -> 100; case KILL -> 300; case MINING -> 200; };
        return rifle ? c * 3 / 2 : c;
    }

    private float killDamage() {
        float hand = 4 + 2 * tier; // NX 6 ... E 14
        return rifle ? hand * 1.6F + 2 : hand;
    }

    private double range() {
        return rifle ? 64 + 8 * tier : 32 + 4 * tier;
    }

    /** Härtester Block, den der Abbaumodus noch schafft (Obsidian bleibt Gewehren der späten Epochen vorbehalten). */
    private float maxHardness() {
        return rifle ? 10 + 12 * tier : 4 + 3 * tier;
    }

    // ---- Daten am Item ----

    public static int energy(ItemStack stack) {
        return stack.getOrDefault(ModBlocks.WEAPON_ENERGY.get(), 0);
    }

    public static void setEnergy(ItemStack stack, int value) {
        stack.set(ModBlocks.WEAPON_ENERGY.get(), Math.max(0, value));
    }

    public static Mode mode(ItemStack stack) {
        int m = stack.getOrDefault(ModBlocks.WEAPON_MODE.get(), 0);
        return Mode.values()[Math.floorMod(m, Mode.values().length)];
    }

    public static void cycleMode(ServerPlayer player, ItemStack stack) {
        Mode next = Mode.values()[(mode(stack).ordinal() + 1) % Mode.values().length];
        stack.set(ModBlocks.WEAPON_MODE.get(), next.ordinal());
        player.sendOverlayMessage(Component.translatable("message.simonspacemod.weapon.mode",
            Component.translatable("mode.simonspacemod." + next.name().toLowerCase())));
        player.level().playSound(null, player.blockPosition(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.4F, 1.6F);
    }

    /**
     * Energiezelle als Forge-Energie (FE/RF) anhängen – Ladegeräte anderer Mods können die Waffe laden.
     * (Über das Anhänge-Ereignis: Forge wertet {@code getCapabilityProvider} nur aus, wenn jemand darauf hört.)
     */
    public static void attachEnergy(net.minecraftforge.event.AttachCapabilitiesEvent.ItemStacks event) {
        ItemStack stack = event.getObject();
        if (stack.getItem() instanceof HandWeaponItem w)
            event.addCapability(net.minecraft.resources.Identifier.fromNamespaceAndPath(de.simon.spacemod.SimonSpaceMod.MODID, "energy"),
                new WeaponEnergy(stack, w));
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13F * energy(stack) / capacity());
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return Mth.hsvToRgb(0.55F, 0.7F, 1F); // blau wie eine Energieanzeige
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        out.accept(Component.translatable("tooltip.simonspacemod.weapon.energy", energy(stack), capacity()).withStyle(ChatFormatting.AQUA));
        out.accept(Component.translatable("tooltip.simonspacemod.weapon.mode",
            Component.translatable("mode.simonspacemod." + mode(stack).name().toLowerCase())).withStyle(ChatFormatting.GRAY));
        out.accept(Component.translatable("tooltip.simonspacemod.weapon.keys").withStyle(ChatFormatting.DARK_GRAY));
    }

    // ---- Feuern ----

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer sp)) return InteractionResult.SUCCESS;
        Mode m = mode(stack);
        int cost = cost(m);
        if (!player.getAbilities().instabuild && energy(stack) < cost) {
            sp.sendOverlayMessage(Component.translatable("message.simonspacemod.weapon.empty"));
            level.playSound(null, player.blockPosition(), SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.5F, 1.8F);
            return InteractionResult.FAIL;
        }
        if (!player.getAbilities().instabuild) setEnergy(stack, energy(stack) - cost);
        player.getCooldowns().addCooldown(stack, rifle ? 14 : 8);

        Vec3 eye = player.getEyePosition(), look = player.getLookAngle();
        Vec3 end = eye.add(look.scale(range()));
        BlockHitResult blockHit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 hitPos = blockHit.getType() == HitResult.Type.MISS ? end : blockHit.getLocation();
        // Kreatur vor dem Block?
        LivingEntity target = null;
        double best = eye.distanceTo(hitPos);
        for (Entity e : level.getEntities(player, new AABB(eye, hitPos).inflate(1), e -> e instanceof LivingEntity le && le.isAlive())) {
            var h = e.getBoundingBox().inflate(0.3).clip(eye, hitPos);
            if (h.isPresent() && eye.distanceTo(h.get()) < best) {
                best = eye.distanceTo(h.get());
                target = (LivingEntity) e;
                hitPos = h.get();
            }
        }
        beam(server, player, hitPos, m);
        if (target != null) {
            switch (m) {
                case STUN -> {
                    target.hurtServer(server, server.damageSources().playerAttack(player), rifle ? 2 : 1);
                    int ticks = 40 + 20 * tier + (rifle ? 40 : 0);
                    target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ticks, 6), player);
                    target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, 2), player);
                    target.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, ticks, 2), player);
                }
                case KILL -> {
                    target.hurtServer(server, server.damageSources().playerAttack(player), killDamage());
                    target.igniteForSeconds(rifle ? 4 : 2);
                }
                case MINING -> target.hurtServer(server, server.damageSources().playerAttack(player), 1);
            }
        } else if (m == Mode.MINING && blockHit.getType() == HitResult.Type.BLOCK) {
            BlockPos center = blockHit.getBlockPos();
            mine(server, sp, center);
            if (rifle) { // Gewehre bauen eine 3x3-Fläche ab
                var dir = blockHit.getDirection();
                for (int a = -1; a <= 1; a++)
                    for (int b = -1; b <= 1; b++) {
                        if (a == 0 && b == 0) continue;
                        BlockPos p = switch (dir.getAxis()) {
                            case X -> center.offset(0, a, b);
                            case Y -> center.offset(a, 0, b);
                            case Z -> center.offset(a, b, 0);
                        };
                        mine(server, sp, p);
                    }
            }
        }
        level.playSound(null, player.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.8F,
            (rifle ? 1.3F : 1.8F) + (m == Mode.STUN ? 0.2F : 0));
        return InteractionResult.SUCCESS_SERVER;
    }

    private void mine(ServerLevel level, ServerPlayer player, BlockPos pos) {
        BlockState st = level.getBlockState(pos);
        float hardness = st.getDestroySpeed(level, pos);
        if (st.isAir() || hardness < 0 || hardness > maxHardness()) return;
        if (!level.mayInteract(player, pos) || !player.mayBuild()) return;
        // Beute wie mit einer Diamantspitzhacke
        Block.dropResources(st, level, pos, level.getBlockEntity(pos), player, new ItemStack(Items.DIAMOND_PICKAXE));
        level.destroyBlock(pos, false, player);
    }

    private void beam(ServerLevel level, Player player, Vec3 to, Mode m) {
        // aus der Hand: etwas rechts unterhalb der Augen
        Vec3 look = player.getLookAngle();
        Vec3 right = look.cross(new Vec3(0, 1, 0)).normalize();
        Vec3 from = player.getEyePosition().add(right.scale(0.35)).add(0, -0.25, 0).add(look.scale(0.6));
        Vec3 d = to.subtract(from);
        double len = d.length();
        if (len < 0.1) return;
        d = d.scale(1 / len);
        int c = m == Mode.STUN ? lighten(color) : color;
        DustParticleOptions outer = new DustParticleOptions(c, rifle ? 1.1F : 0.8F);
        DustParticleOptions inner = new DustParticleOptions(core, 0.5F);
        for (double t = 0; t < len; t += 0.25) {
            if (pulse && (t % 3.0) > 1.2) continue;
            Vec3 v = from.add(d.scale(t));
            level.sendParticles(outer, true, true, v.x, v.y, v.z, 1, 0, 0, 0, 0);
            level.sendParticles(inner, true, true, v.x, v.y, v.z, 1, 0, 0, 0, 0);
        }
    }

    private static int lighten(int rgb) {
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        return ((r + 255) / 2 << 16) | ((g + 255) / 2 << 8) | (b + 255) / 2;
    }
}
