package com.gregtechceu.gtceu.utils;

import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;

import com.gto.datasynclib.DataFieldDefinition;
import it.unimi.dsi.fastutil.Hash;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public interface FluidStackHashStrategy extends Hash.Strategy<FluidStack> {

    Hash.Strategy<FluidStack> ALL = new FluidStackHashStrategy() {

        @Override
        public int hashCode(@Nullable FluidStack o) {
            if (o == null) return 0;
            var fluid = o.getFluid();
            if (fluid == Fluids.EMPTY) return 0;
            return Objects.hash(fluid, o.getAmount(), o.getTag());
        }

        @Override
        public boolean equals(@Nullable FluidStack a, @Nullable FluidStack b) {
            if (a == b) return true;
            if (a == null) return b.isEmpty();
            if (b == null) return a.isEmpty();
            if (a.getAmount() != b.getAmount()) return false;
            if (a.getFluid() != b.getFluid()) return false;
            return Objects.equals(a.getTag(), b.getTag());
        }
    };

    Hash.Strategy<FluidStack> FLUID_AND_TAG = DataFieldDefinition.OBJECT_STRATEGY;

    Hash.Strategy<FluidStack> FLUID = new FluidStackHashStrategy() {

        @Override
        public int hashCode(FluidStack o) {
            if (o == null) return 0;
            var fluid = o.getFluid();
            if (fluid == Fluids.EMPTY) return 0;
            return fluid.hashCode();
        }

        @Override
        public boolean equals(FluidStack a, FluidStack b) {
            if (a == b) return true;
            if (a == null) return b.isEmpty();
            if (b == null) return a.isEmpty();
            return a.getFluid() == b.getFluid();
        }
    };
}
