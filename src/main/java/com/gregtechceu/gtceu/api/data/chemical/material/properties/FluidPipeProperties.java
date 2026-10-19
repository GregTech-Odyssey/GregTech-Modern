package com.gregtechceu.gtceu.api.data.chemical.material.properties;

import com.gregtechceu.gtceu.api.fluids.FluidConstants;
import com.gregtechceu.gtceu.api.fluids.FluidState;
import com.gregtechceu.gtceu.api.fluids.GTFluid;
import com.gregtechceu.gtceu.api.fluids.PropertyFluidFilter;

import net.minecraft.world.level.material.Fluid;

import appeng.api.stacks.AEFluidKey;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

@Getter
public class FluidPipeProperties extends PropertyFluidFilter implements IMaterialProperty {

    private final int throughput;
    private final int maxTemp;
    private final boolean cryoProof;

    public FluidPipeProperties(int throughput, int maxTemp, boolean gasProof, boolean cryoProof, boolean plasmaProof) {
        super(gasProof, plasmaProof);
        this.throughput = throughput;
        this.maxTemp = maxTemp;
        this.cryoProof = cryoProof;
    }

    @Override
    public void verifyProperty(MaterialProperties properties) {
        if (!properties.hasProperty(PropertyKey.WOOD)) {
            properties.ensureSet(PropertyKey.INGOT, true);
        }
        if (properties.hasProperty(PropertyKey.ITEM_PIPE)) {
            throw new IllegalStateException(
                    "Material " + properties.getMaterial() +
                            " has both Fluid and Item Pipe Property, which is not allowed!");
        }
    }

    private boolean isTemperatureValid(int temperature, boolean plasma) {
        if (temperature < FluidConstants.CRYOGENIC_FLUID_THRESHOLD) return cryoProof;
        if (plasma && plasmaProof) return true;
        return temperature <= maxTemp;
    }

    public boolean canTransfer(@NotNull AEFluidKey key) {
        Fluid fluid = key.getFluid();
        boolean isPlasma;
        if (fluid instanceof GTFluid gtFluid) {
            var state = gtFluid.getState();
            if (!canContain(state)) return false;
            isPlasma = state == FluidState.PLASMA;
        } else {
            if (fluid.getFluidType().isLighterThanAir() && !gasProof) return false;
            isPlasma = false;
        }
        return isTemperatureValid(fluid.getFluidType().getTemperature(), isPlasma);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FluidPipeProperties that)) return false;
        return throughput == that.throughput &&
                maxTemp == that.maxTemp &&
                gasProof == that.gasProof &&
                cryoProof == that.cryoProof &&
                plasmaProof == that.plasmaProof;
    }

    @Override
    public int hashCode() {
        return Objects.hash(throughput, maxTemp, gasProof, cryoProof, plasmaProof);
    }

    @Override
    public String toString() {
        return "FluidPipeProperties{" +
                "throughput=" + throughput +
                ", maxTemp=" + maxTemp +
                ", gasProof=" + gasProof +
                ", cryoProof=" + cryoProof +
                ", plasmaProof=" + plasmaProof +
                '}';
    }
}
