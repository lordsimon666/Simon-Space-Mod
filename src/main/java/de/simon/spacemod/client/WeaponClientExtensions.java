package de.simon.spacemod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import de.simon.spacemod.item.HandWeaponItem;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

/**
 * Haltung der Handwaffen: Phaser – Arm nach vorne gestreckt, der Lauf verlängert den Arm; Gewehre –
 * mit beiden Händen gerade nach vorne. Beim Zielen mit dem Gewehr (Rechtsklick halten) wandert es in
 * der Ego-Ansicht in die Bildmitte, man schaut durchs Visier.
 */
public final class WeaponClientExtensions implements IClientItemExtensions {
    /** Ein Arm zeigt nach vorne in Blickrichtung (folgt dem Kopf). */
    private static final HumanoidModel.ArmPose PHASER_POINT = HumanoidModel.ArmPose.create("SIMONSPACEMOD_PHASER_POINT", false, false,
        (model, state, arm) -> {
            var part = arm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
            part.xRot = (float) (-Math.PI / 2) + model.head.xRot;
            part.yRot = model.head.yRot;
        });

    /** Ego-Ansicht beim Zielen: Oberkante des Visiers genau unter dem Fadenkreuz (im Spiel ausgemessen). */
    private static final float AIM_X = -0.094F, AIM_Y = -0.239F, AIM_Z = -0.32F;

    private final HandWeaponItem weapon;

    private WeaponClientExtensions(HandWeaponItem weapon) {
        this.weapon = weapon;
    }

    public static IClientItemExtensions create(HandWeaponItem weapon) {
        return new WeaponClientExtensions(weapon);
    }

    public static boolean aiming(LivingEntity entity, ItemStack stack) {
        return stack.getItem() instanceof HandWeaponItem w && w.rifle && entity.isUsingItem() && entity.getUseItem() == stack;
    }

    @Override
    public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
        return weapon.rifle ? HumanoidModel.ArmPose.CROSSBOW_HOLD : PHASER_POINT;
    }

    @Override
    public boolean applyForgeHandTransform(PoseStack pose, LocalPlayer player, HumanoidArm arm, ItemStack stack,
                                           float partialTick, float equipProcess, float swingProcess) {
        if (!aiming(player, stack)) return false;
        int side = arm == HumanoidArm.RIGHT ? 1 : -1;
        pose.translate(side * AIM_X, AIM_Y, AIM_Z);
        return true; // keine Arm-/Schwungbewegung beim Zielen
    }
}
