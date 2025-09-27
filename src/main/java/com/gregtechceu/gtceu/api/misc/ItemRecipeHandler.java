package com.gregtechceu.gtceu.api.misc;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.IRecipeHandler;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.function.ItemPredicate;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;

import net.minecraft.world.item.crafting.Ingredient;

import java.util.List;

public class ItemRecipeHandler implements IRecipeHandler<Ingredient> {

    public final IO handlerIO;
    public final CustomItemStackHandler storage;

    public ItemRecipeHandler(IO handlerIO, int slots) {
        this.handlerIO = handlerIO;
        this.storage = new CustomItemStackHandler(slots);
    }

    @Override
    public List<Ingredient> handleRecipeInner(IO io, GTRecipe recipe, List<Ingredient> left, boolean simulate) {
        return NotifiableItemStackHandler.handleRecipe(io, recipe, left, simulate, this.handlerIO, storage);
    }

    @Override
    public boolean forEachItems(ItemPredicate function) {
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
    public RecipeCapability<Ingredient> getCapability() {
        return ItemRecipeCapability.CAP;
    }

    public IO getHandlerIO() {
        return this.handlerIO;
    }
}
