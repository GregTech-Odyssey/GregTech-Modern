package com.gregtechceu.gtceu.api.item.component;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeMod;

public interface IInteractionItem extends IItemComponent {

    default InteractionResult onItemUseFirst(ItemStack itemStack, UseOnContext context) {
        return InteractionResult.PASS;
    }

    default InteractionResult useOn(UseOnContext context) {
        return InteractionResult.PASS;
    }

    default InteractionResultHolder<ItemStack> use(Item item, Level level, Player player, InteractionHand usedHand) {
        if (item.isEdible()) {
            ItemStack itemStack = player.getItemInHand(usedHand);
            if (player.canEat(itemStack.getFoodProperties(player).canAlwaysEat())) {
                player.startUsingItem(usedHand);
                return InteractionResultHolder.consume(itemStack);
            } else {
                return InteractionResultHolder.fail(itemStack);
            }
        } else {
            return InteractionResultHolder.pass(player.getItemInHand(usedHand));
        }
    }

    default ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity livingEntity) {
        return stack.isEdible() ? livingEntity.eat(level, stack) : stack;
    }

    default UseAnim getUseAnimation(ItemStack stack) {
        return stack.getItem().isEdible() ? UseAnim.EAT : UseAnim.NONE;
    }

    default boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        return false;
    }

    default InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity interactionTarget,
                                                   InteractionHand usedHand) {
        return InteractionResult.PASS;
    }

    default boolean sneakBypassUse(ItemStack stack, LevelReader level, BlockPos pos, Player player) {
        return false;
    }

    default boolean onEntitySwing(ItemStack stack, LivingEntity entity) {
        return false;
    }

    static BlockHitResult getPlayerPOVHitResult(Level level, Player player, ClipContext.Fluid fluidMode) {
        float f = player.getXRot();
        float f1 = player.getYRot();
        Vec3 vec3 = player.getEyePosition();
        float f2 = Mth.cos(-f1 * ((float) Math.PI / 180F) - (float) Math.PI);
        float f3 = Mth.sin(-f1 * ((float) Math.PI / 180F) - (float) Math.PI);
        float f4 = -Mth.cos(-f * ((float) Math.PI / 180F));
        float f5 = Mth.sin(-f * ((float) Math.PI / 180F));
        float f6 = f3 * f4;
        float f7 = f2 * f4;
        double d0 = player.getAttributeValue(ForgeMod.BLOCK_REACH.get()) + (double) 0.5F;
        Vec3 vec31 = vec3.add((double) f6 * d0, (double) f5 * d0, (double) f7 * d0);
        return level.clip(new ClipContext(vec3, vec31, net.minecraft.world.level.ClipContext.Block.OUTLINE, fluidMode, player));
    }
}
