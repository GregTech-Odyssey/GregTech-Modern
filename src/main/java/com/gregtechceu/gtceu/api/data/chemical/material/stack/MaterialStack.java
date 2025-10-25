package com.gregtechceu.gtceu.api.data.chemical.material.stack;

import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.utils.FormattingUtil;

import org.jetbrains.annotations.NotNull;

public record MaterialStack(@NotNull Material material, long amount) {

    public static final MaterialStack EMPTY = new MaterialStack(GTMaterials.NULL, 0);

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
        return this.material == GTMaterials.NULL || this.amount < 1;
    }

    @Override
    public String toString() {
        String string = "";
        if (this.isEmpty()) return "";
        if (material.getChemicalFormula() == null || material.getChemicalFormula().isEmpty()) {
            string += "?";
        } else if (material.getMaterialComponents().size() > 1) {
            string += '(' + material.getChemicalFormula() + ')';
        } else {
            string += material.getChemicalFormula();
        }
        if (amount > 1) {
            string += FormattingUtil.toSmallDownNumbers(Long.toString(amount));
        }
        return string;
    }
}
