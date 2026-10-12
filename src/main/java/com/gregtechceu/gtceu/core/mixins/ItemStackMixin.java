package com.gregtechceu.gtceu.core.mixins;

import com.gregtechceu.gtceu.api.item.IDataComponentItem;
import com.gregtechceu.gtceu.api.item.ItemDataComponentMap;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import javax.annotation.Nullable;

@Mixin(ItemStack.class)
public class ItemStackMixin implements IDataComponentItem {

    @Shadow
    @Final
    @Nullable
    private Item item;

    @Unique
    private ItemDataComponentMap gtceu$dataComponentMap;

    @Override
    public ItemDataComponentMap gtceu$getDataComponents() {
        var map = this.gtceu$dataComponentMap;
        if (map == null) {
            map = this.gtceu$dataComponentMap = new ItemDataComponentMap((ItemStack) (Object) this);
        }
        return map;
    }

    @Override
    public Item gtceu$getItem() {
        return item;
    }

    @Redirect(method = "copy", at = @At(value = "INVOKE", target = "Lnet/minecraft/nbt/CompoundTag;copy()Lnet/minecraft/nbt/CompoundTag;"))
    private CompoundTag copy(CompoundTag original, @Local(name = "itemstack") ItemStack itemstack) {
        var map = this.gtceu$dataComponentMap;
        if (map != null) {
            ((ItemStackMixin) (Object) itemstack).gtceu$dataComponentMap = map.copy(itemstack);
        }
        return original.copy();
    }
}
