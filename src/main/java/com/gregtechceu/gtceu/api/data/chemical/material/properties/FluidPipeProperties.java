package com.gregtechceu.gtceu.api.data.chemical.material.properties;

import com.gregtechceu.gtceu.api.capability.IPropertyFluidFilter;
import com.gregtechceu.gtceu.api.fluids.FluidState;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;

@Getter
public class FluidPipeProperties implements IMaterialProperty, IPropertyFluidFilter {

    private final int throughput;

    public FluidPipeProperties(int throughput) {
        this.throughput = throughput;
    }

    @Override
    public void verifyProperty(MaterialProperties properties) {
        if (!properties.hasProperty(PropertyKey.WOOD)) {
            properties.ensureSet(PropertyKey.INGOT, true);
        }
        if (properties.hasProperty(PropertyKey.ITEM_PIPE)) {
            throw new IllegalStateException("Material " + properties.getMaterial() + " has both Fluid and Item Pipe Property, which is not allowed!");
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FluidPipeProperties that)) return false;
        return throughput == that.throughput;
    }

    @Override
    public int hashCode() {
        return throughput;
    }

    @Override
    public String toString() {
        return "FluidPipeProperties{" + "throughput=" + throughput + '}';
    }

    @Override
    public boolean canContain(@NotNull FluidState state) {
        return true;
    }

    public boolean isGasProof() {
        return true;
    }

    public boolean isPlasmaProof() {
        return true;
    }
}
