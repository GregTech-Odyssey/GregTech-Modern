package com.gregtechceu.gtceu.core.mixins;

import com.gregtechceu.gtceu.api.item.ComponentItem;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Enchantment.class)
public class EnchantmentMixin {

    @Inject(method = "canEnchant", at = @At("HEAD"), cancellable = true)
    private void gtceu$componentItemCanEnchant(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (stack.getItem() instanceof ComponentItem) {
            cir.setReturnValue(stack.canApplyAtEnchantingTable((Enchantment) (Object) this));
        }
    }
}
