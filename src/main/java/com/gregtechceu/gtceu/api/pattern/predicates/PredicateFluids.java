package com.gregtechceu.gtceu.api.pattern.predicates;

import com.lowdragmc.lowdraglib.utils.BlockInfo;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import java.util.Objects;

public class PredicateFluids extends SimplePredicate {

    protected Fluid fluid;

    public PredicateFluids(Fluid fluid) {
        this.fluid = fluid;
        buildPredicate();
    }

    @Override
    public SimplePredicate buildPredicate() {
        if (fluid == null) fluid = Fluids.WATER;
        predicate = state -> state.getBlockState().getFluidState().getType() == fluid;
        var block = new Block[] { fluid.defaultFluidState().createLegacyBlock().getBlock() };
        candidates = () -> block;
        var info = BlockInfo.fromBlock(block[0]);
        blockInfo = () -> info;
        return this;
    }

    @Override
    public int hashCode() {
        var hash = fluid.hashCode();
        hash = 31 * hash + minCount;
        hash = 31 * hash + maxCount;
        hash = 31 * hash + minLayerCount;
        hash = 31 * hash + maxLayerCount;
        hash = 31 * hash + previewCount;
        hash = 31 * hash + (disableRenderFormed ? 1231 : 1237);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (!(obj instanceof PredicateFluids other)) return false;
        return Objects.equals(fluid, other.fluid) && minCount == other.minCount && maxCount == other.maxCount && minLayerCount == other.minLayerCount && maxLayerCount == other.maxLayerCount && previewCount == other.previewCount && disableRenderFormed == other.disableRenderFormed;
    }
}
