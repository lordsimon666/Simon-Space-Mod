package de.simon.spacemod.item;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/**
 * Easter Egg: Hühnchen, das in einem laufenden Kernreaktor lag. Immer durchgegart (sättigt wie
 * gebratenes Hühnchen), aber wer es isst, bekommt für eine Minute alle schädlichen Effekte.
 */
public class AtomicChickenItem extends Item {
    public static final int DURATION = 20 * 60;

    public AtomicChickenItem(Properties props) {
        super(props.food(new FoodProperties.Builder().nutrition(6).saturationModifier(0.6F).alwaysEdible().build()));
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide()) {
            for (MobEffect effect : BuiltInRegistries.MOB_EFFECT) {
                if (effect.getCategory() != MobEffectCategory.HARMFUL || effect.isInstantaneous()) continue;
                entity.addEffect(new MobEffectInstance(BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effect), DURATION));
            }
        }
        return super.finishUsingItem(stack, level, entity);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        out.accept(Component.translatable("item.simonspacemod.atomic_chicken.tooltip").withStyle(ChatFormatting.GREEN));
    }
}
