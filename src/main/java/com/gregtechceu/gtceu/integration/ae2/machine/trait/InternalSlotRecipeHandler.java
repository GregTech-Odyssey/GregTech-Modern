package com.gregtechceu.gtceu.integration.ae2.machine.trait;

import com.gregtechceu.gtceu.api.capability.recipe.*;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableRecipeHandlerTrait;
import com.gregtechceu.gtceu.api.machine.trait.RecipeHandlerGroupDistinctness;
import com.gregtechceu.gtceu.api.machine.trait.RecipeHandlerList;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient;
import com.gregtechceu.gtceu.integration.ae2.machine.MEPatternBufferPartMachine;
import com.gregtechceu.gtceu.integration.ae2.machine.MEPatternBufferPartMachine.InternalSlot;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.fluids.FluidStack;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public final class InternalSlotRecipeHandler {

    private final List<RecipeHandlerList> slotHandlers;

    public InternalSlotRecipeHandler(MEPatternBufferPartMachine buffer, InternalSlot[] slots) {
        this.slotHandlers = new ArrayList<>(slots.length);
        for (int i = 0; i < slots.length; i++) {
            var rhl = new SlotRHL(buffer, slots[i], i);
            slotHandlers.add(rhl);
        }
    }

    protected static class SlotRHL extends RecipeHandlerList {

        private final SlotItemRecipeHandler itemRecipeHandler;
        private final SlotFluidRecipeHandler fluidRecipeHandler;

        public SlotRHL(MEPatternBufferPartMachine buffer, InternalSlot slot, int idx) {
            super(IO.IN);
            itemRecipeHandler = new SlotItemRecipeHandler(buffer, slot, idx);
            fluidRecipeHandler = new SlotFluidRecipeHandler(buffer, slot, idx);
            addHandlers(buffer.getCircuitInventory(), buffer.getShareInventory(), buffer.getShareTank(), itemRecipeHandler, fluidRecipeHandler);
            this.setGroup(RecipeHandlerGroupDistinctness.BUS_DISTINCT);
        }

        @Override
        public boolean isDistinct() {
            return true;
        }

        @Override
        public void setDistinct(boolean ignored, boolean notify) {}

        public SlotItemRecipeHandler getItemRecipeHandler() {
            return this.itemRecipeHandler;
        }

        public SlotFluidRecipeHandler getFluidRecipeHandler() {
            return this.fluidRecipeHandler;
        }
    }

    private static class SlotItemRecipeHandler extends NotifiableRecipeHandlerTrait<Ingredient> {

        private final InternalSlot slot;
        private final int priority;
        private final int size = 81;
        private final RecipeCapability<Ingredient> capability = ItemRecipeCapability.CAP;
        private final IO handlerIO = IO.IN;
        private final boolean isDistinct = true;

        private SlotItemRecipeHandler(MEPatternBufferPartMachine buffer, InternalSlot slot, int index) {
            super(buffer);
            this.slot = slot;
            this.priority = IFilteredHandler.HIGH + index + 1;
            slot.setOnContentsChanged(this::notifyListeners);
        }

        @Override
        public List<Ingredient> handleRecipeInner(IO io, GTRecipe recipe, List<Ingredient> left, boolean simulate) {
            if (io != IO.IN || slot.isItemEmpty()) return left;
            return slot.handleItemInternal(left, simulate);
        }

        @Override
        @NotNull
        public List<Object> getContents() {
            return new ArrayList<>(slot.getItems());
        }

        @Override
        public double getTotalContentAmount() {
            return slot.getItems().stream().mapToLong(ItemStack::getCount).sum();
        }

        public InternalSlot getSlot() {
            return this.slot;
        }

        public int getPriority() {
            return this.priority;
        }

        public int getSize() {
            return this.size;
        }

        public RecipeCapability<Ingredient> getCapability() {
            return this.capability;
        }

        public IO getHandlerIO() {
            return this.handlerIO;
        }

        public boolean isDistinct() {
            return this.isDistinct;
        }
    }

    private static class SlotFluidRecipeHandler extends NotifiableRecipeHandlerTrait<FluidIngredient> {

        private final InternalSlot slot;
        private final int priority;
        private final int size = 81;
        private final RecipeCapability<FluidIngredient> capability = FluidRecipeCapability.CAP;
        private final IO handlerIO = IO.IN;
        private final boolean isDistinct = true;

        private SlotFluidRecipeHandler(MEPatternBufferPartMachine buffer, InternalSlot slot, int index) {
            super(buffer);
            this.slot = slot;
            this.priority = IFilteredHandler.HIGH + index + 1;
            slot.setOnContentsChanged(this::notifyListeners);
        }

        @Override
        public List<FluidIngredient> handleRecipeInner(IO io, GTRecipe recipe, List<FluidIngredient> left, boolean simulate) {
            if (io != IO.IN || slot.isFluidEmpty()) return left;
            return slot.handleFluidInternal(left, simulate);
        }

        @Override
        @NotNull
        public List<Object> getContents() {
            return new ArrayList<>(slot.getFluids());
        }

        @Override
        public double getTotalContentAmount() {
            return slot.getFluids().stream().mapToLong(FluidStack::getAmount).sum();
        }

        public InternalSlot getSlot() {
            return this.slot;
        }

        public int getPriority() {
            return this.priority;
        }

        public int getSize() {
            return this.size;
        }

        public RecipeCapability<FluidIngredient> getCapability() {
            return this.capability;
        }

        public IO getHandlerIO() {
            return this.handlerIO;
        }

        public boolean isDistinct() {
            return this.isDistinct;
        }
    }

    public List<RecipeHandlerList> getSlotHandlers() {
        return this.slotHandlers;
    }
}
