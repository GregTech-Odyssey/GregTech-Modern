package com.gregtechceu.gtceu.core.mixins.ldlib;

import com.gregtechceu.gtceu.uipro.data.UIBudget;

import com.lowdragmc.lowdraglib.gui.modular.ModularUI;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = ModularUI.class, remap = false)
public abstract class ModularUIBudgetMixin implements UIBudget.Holder {

    @Unique
    private final UIBudget gtceu$budget = new UIBudget();

    @Override
    public UIBudget gtceu$budget() {
        return gtceu$budget;
    }
}
