package com.gregtechceu.gtceu.api.misc;

import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.handler.IO;
import com.gregtechceu.gtceu.api.recipe.handler.IRecipeHandler;
import com.gregtechceu.gtceu.api.recipe.ingredient.ItemIngredient;
import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;
import com.gregtechceu.gtceu.utils.function.ObjLongPredicate;

import net.minecraft.world.item.ItemStack;

import lombok.Getter;

import java.util.List;

public class ItemRecipeHandler implements IRecipeHandler<ItemIngredient> {

    @Getter
    public final IO handlerIO;
    public final CustomItemStackHandler storage;

    public ItemRecipeHandler(IO handlerIO, int slots) {
        this.handlerIO = handlerIO;
        this.storage = new CustomItemStackHandler(slots);
    }

    @Override
    public List<ItemIngredient> handleRecipeInner(IO io, GTRecipe recipe, List<ItemIngredient> left, boolean simulate) {
        return NotifiableItemStackHandler.handleRecipe(io, left, simulate, this.handlerIO, storage);
    }

    @Override
    public boolean forEachItems(ObjLongPredicate<ItemStack> function) {
        for (int i = 0; i < storage.size; ++i) {
            var stack = storage.stacks[i];
            var amount = stack.getCount();
            if (amount > 0) {
                if (function.test(stack, amount)) return true;
            }
        }
        return false;
    }

    @Override
    public int getSize() {
        return this.storage.size;
    }

    @Override
    public RecipeCapability<ItemIngredient> getCapability() {
        return ItemRecipeCapability.CAP;
    }
}
