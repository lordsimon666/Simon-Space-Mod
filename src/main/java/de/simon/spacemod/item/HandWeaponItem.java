package de.simon.spacemod.item;

import de.simon.spacemod.ModBlocks;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
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
        else if (stack.getItem() instanceof SpaceSuitItem)
            event.addCapability(net.minecraft.resources.Identifier.fromNamespaceAndPath(de.simon.spacemod.SimonSpaceMod.MODID, "energy"),
                new SpaceSuitItem.Energy(stack));
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
        out.accept(Component.translatable(rifle ? "tooltip.simonspacemod.rifle.keys" : "tooltip.simonspacemod.weapon.keys")
            .withStyle(ChatFormatting.DARK_GRAY));
    }

    // ---- Feuern ----

    /** Haltung und Zielen (nur auf dem Client). */
    @Override
    public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientItemExtensions> consumer) {
        consumer.accept(de.simon.spacemod.client.WeaponClientExtensions.create(this));
    }

    /** Gefeuert wird mit Linksklick (halten); Gewehre: Rechtsklick halten = zielen. */
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (rifle) {
            player.startUsingItem(hand);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    /** Linksklick halten feuert – dabei keine Blöcke abbauen. */
    @Override
    public boolean canDestroyBlock(ItemStack stack, BlockState state, Level level, BlockPos pos, LivingEntity user) {
        return false;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity user) {
        return rifle ? 72000 : 0;
    }

    @Override
    public net.minecraft.world.item.ItemUseAnimation getUseAnimation(ItemStack stack) {
        return net.minecraft.world.item.ItemUseAnimation.NONE;
    }

    /** Schuss (Phaser per Rechtsklick, Gewehr per Linksklick über das Netzwerk). */
    public InteractionResult fire(ServerLevel server, ServerPlayer sp, ItemStack stack) {
        Player player = sp;
        Level level = server;
        if (player.getCooldowns().isOnCooldown(stack)) return InteractionResult.FAIL;
        Mode m = mode(stack);
        int cost = cost(m);
        if (!player.getAbilities().instabuild && energy(stack) < cost) {
            sp.sendOverlayMessage(Component.translatable("message.simonspacemod.weapon.empty"));
            level.playSound(null, player.blockPosition(), SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.5F, 1.8F);
            return InteractionResult.FAIL;
        }
        if (!player.getAbilities().instabuild) setEnergy(stack, energy(stack) - cost);
        // feuert 2 Sekunden, danach kurz nachladen
        player.getCooldowns().addCooldown(stack, de.simon.spacemod.weapons.BeamShots.DURATION + (rifle ? 6 : 2));
        level.playSound(null, player.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.8F,
            (rifle ? 1.3F : 1.8F) + (m == Mode.STUN ? 0.2F : 0));
        de.simon.spacemod.weapons.BeamShots.start(new HandShot(server, sp, m));
        return InteractionResult.SUCCESS_SERVER;
    }

    /** Laufender Schuss: Strahl aus der Hand folgt dem Blick, Wirkung alle 0,5 s. */
    private final class HandShot extends de.simon.spacemod.weapons.BeamShots.Shot {
        private final ServerLevel server;
        private final ServerPlayer player;
        private final Mode mode;
        private de.simon.spacemod.entity.PhaserBeamEntity beam;

        HandShot(ServerLevel server, ServerPlayer player, Mode mode) {
            this.server = server;
            this.player = player;
            this.mode = mode;
        }

        @Override
        protected boolean tick() {
            if (age >= de.simon.spacemod.weapons.BeamShots.DURATION || !player.isAlive() || player.level() != server) return false;
            if (!de.simon.spacemod.weapons.Triggers.held(player)) return false; // Abzug losgelassen
            if (player.getMainHandItem().getItem() != HandWeaponItem.this) return false;
            Vec3 hit = strike(server, player, mode, age % de.simon.spacemod.weapons.BeamShots.PULSE == 0);
            Vec3 from = muzzle(player);
            if (beam == null) {
                int c = mode == Mode.STUN ? lighten(color) : color;
                beam = de.simon.spacemod.weapons.BeamShots.beam(server, from, hit, c, core, rifle ? 0.07F : 0.045F, pulse);
            } else beam.aim(from, hit);
            age++;
            return true;
        }

        @Override
        protected void end() {
            if (beam != null) beam.discard();
        }
    }

    /** Mündung: etwas rechts unterhalb der Augen. */
    private static Vec3 muzzle(Player player) {
        Vec3 look = player.getLookAngle();
        Vec3 right = look.cross(new Vec3(0, 1, 0)).normalize();
        return player.getEyePosition().add(right.scale(0.35)).add(0, -0.25, 0).add(look.scale(0.6));
    }

    /** Ziel im Blick bestimmen und (wenn {@code apply}) wirken lassen; liefert den Endpunkt des Strahls. */
    private Vec3 strike(ServerLevel server, ServerPlayer sp, Mode m, boolean apply) {
        Player player = sp;
        Level level = server;
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
        if (!apply) return hitPos;
        if (target != null) {
            switch (m) {
                case STUN -> {
                    target.hurtServer(server, server.damageSources().playerAttack(player), rifle ? 1 : 0.5F);
                    int ticks = 40 + 20 * tier + (rifle ? 40 : 0);
                    target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ticks, 6), player);
                    target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, 2), player);
                    target.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, ticks, 2), player);
                }
                case KILL -> {
                    target.hurtServer(server, server.damageSources().playerAttack(player), killDamage() * 0.45F);
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
        return hitPos;
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

    private static int lighten(int rgb) {
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        return ((r + 255) / 2 << 16) | ((g + 255) / 2 << 8) | (b + 255) / 2;
    }
}
