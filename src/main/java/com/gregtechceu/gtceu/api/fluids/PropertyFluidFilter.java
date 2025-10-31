package com.gregtechceu.gtceu.api.fluids;

import com.gregtechceu.gtceu.api.capability.IPropertyFluidFilter;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;

@Getter
public class PropertyFluidFilter implements IPropertyFluidFilter {

    private final boolean gasProof;
    private final boolean plasmaProof;

    public PropertyFluidFilter(boolean gasProof, boolean plasmaProof) {
        this.gasProof = gasProof;
        this.plasmaProof = plasmaProof;
    }

    @Override
    public boolean canContain(@NotNull FluidState state) {
        return switch (state) {
            case LIQUID -> true;
            case GAS -> gasProof;
            case PLASMA -> plasmaProof;
        };
    }

    @Override
    public String toString() {
        return "SimplePropertyFluidFilter{" + ", gasProof=" + gasProof + ", plasmaProof=" + plasmaProof + '}';
    }
}
