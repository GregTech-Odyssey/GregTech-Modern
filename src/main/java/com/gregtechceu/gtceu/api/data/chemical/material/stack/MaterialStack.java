package com.gregtechceu.gtceu.api.data.chemical.material.stack;

import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.utils.FormattingUtil;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.NoSuchElementException;

public record MaterialStack(@Nullable Material material, long amount) {

    public static final MaterialStack EMPTY = new MaterialStack(null, 0);

    @NotNull
    public Material material() {
        if (material == null) throw new NoSuchElementException("Cannot get the material of an empty material stack");
        return material;
    }

    public MaterialStack copy() {
        if (isEmpty()) return EMPTY;
        return new MaterialStack(material, amount);
    }

    public MaterialStack add(long amount) {
        return new MaterialStack(material, this.amount + amount);
    }

    public MaterialStack multiply(long amount) {
        return new MaterialStack(material, this.amount * amount);
    }

    public boolean isEmpty() {
        return this.material == null || this.amount < 1;
    }

    @Nullable
    public MutableComponent getChemicalFormula() {
        if (this.isEmpty()) return null;
        var chemicalFormula = material.getChemicalFormula();
        if (chemicalFormula == null) return null;
        var component = Component.empty();
        if (material.getMaterialComponents().size() > 1) {
            component.append("(").append(chemicalFormula).append(")");
        } else {
            component.append(chemicalFormula);
        }
        if (amount > 1) {
            component.append(FormattingUtil.toSmallDownNumbers(Long.toString(amount)));
        }
        return component;
    }

    @Override
    public @NotNull String toString() {
        String string = "";
        if (this.isEmpty()) return "";
        var chemicalFormula = material.getChemicalFormula();
        if (chemicalFormula == null) {
            string += "?";
        } else if (material.getMaterialComponents().size() > 1) {
            string += '(' + chemicalFormula.getString() + ')';
        } else {
            string += chemicalFormula.getString();
        }
        if (amount > 1) {
            string += FormattingUtil.toSmallDownNumbers(Long.toString(amount));
        }
        return string;
    }
}
