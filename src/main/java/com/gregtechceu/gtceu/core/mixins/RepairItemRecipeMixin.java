package com.gregtechceu.gtceu.core.mixins;

import com.gregtechceu.gtceu.api.item.IGTTool;

import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RepairItemRecipe;

import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = RepairItemRecipe.class)
public abstract class RepairItemRecipeMixin extends CustomRecipe {

    public RepairItemRecipeMixin(ResourceLocation id, CraftingBookCategory category) {
        super(id, category);
    }

    @Override
    public @NotNull NonNullList<ItemStack> getRemainingItems(@NotNull CraftingContainer container) {
        return NonNullList.withSize(0, ItemStack.EMPTY);
    }

    @Redirect(method = "matches(Lnet/minecraft/world/inventory/CraftingContainer;Lnet/minecraft/world/level/Level;)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;isEmpty()Z"))
    private boolean matches(ItemStack instance) {
        var item = instance.getItem();
        return item == Items.AIR || item instanceof IGTTool;
    }
}
