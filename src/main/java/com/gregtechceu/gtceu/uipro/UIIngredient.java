package com.gregtechceu.gtceu.uipro;

import com.gregtechceu.gtceu.GTCEu;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.integration.modules.emi.EmiStackHelper;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.EmiStackInteraction;
import org.jetbrains.annotations.Nullable;

public final class UIIngredient {

    private UIIngredient() {}

    @Nullable
    public static Object of(@Nullable Object ingredient) {
        if (ingredient == null || !GTCEu.Mods.isEMILoaded()) return ingredient;
        return EmiCompat.of(ingredient);
    }

    @Nullable
    public static Object of(@Nullable AEKey key, long amount) {
        return key == null ? null : of(new GenericStack(key, amount));
    }

    private static final class EmiCompat {

        @Nullable
        private static Object of(Object ingredient) {
            if (ingredient instanceof EmiIngredient || ingredient instanceof EmiStackInteraction) return ingredient;
            if (ingredient instanceof ItemStack stack) return stack.isEmpty() ? null : EmiStack.of(stack);
            if (ingredient instanceof FluidStack stack) return stack.isEmpty() ? null : EmiStack.of(stack.getFluid(), stack.getTag(), stack.getAmount());
            if (ingredient instanceof GenericStack stack) return EmiStackHelper.toEmiStack(stack);
            return ingredient;
        }
    }
}
